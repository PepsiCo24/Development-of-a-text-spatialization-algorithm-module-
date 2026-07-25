import json
import re
from collections.abc import Iterator

import httpx

from app.core.config import Settings, get_settings
from app.models.graph import QuestionResponse
from app.services.graph_store import Neo4jGraphStore
from app.services.llm_entities import GeologicalEntityExtractor, ProviderConfig
from app.services.vector_store import QdrantVectorStore

SYSTEM_PROMPT = """你是地质科研问答助手。只能使用提供的检索段落和知识图谱实体回答，不得编造事实。
若证据不足，明确说明。输出严格 JSON：{\"answer\":\"回答正文\"}，不输出 Markdown。"""
STREAM_SYSTEM_PROMPT = """你是地质科研问答助手。只能使用提供的检索段落和知识图谱实体回答，不得编造事实。
若证据不足，明确说明。直接输出简洁、完整的中文回答正文，不输出 JSON、Markdown、思考过程或无关说明。"""


class GeologicalRagService:
    def __init__(
        self,
        settings: Settings | None = None,
        client: httpx.Client | None = None,
        vectors: QdrantVectorStore | None = None,
        graph: Neo4jGraphStore | None = None,
    ) -> None:
        self.settings = settings or get_settings()
        self.client = client or httpx.Client(
            timeout=httpx.Timeout(25.0, connect=5.0, pool=5.0),
            trust_env=self.settings.llm_trust_env_proxy,
        )
        self.providers = GeologicalEntityExtractor(self.settings, self.client)
        self.vectors = vectors or QdrantVectorStore(self.settings)
        self.graph = graph or Neo4jGraphStore(self.settings)

    def ask(self, question: str, provider_name: str | None, limit: int) -> QuestionResponse:
        provider = self.providers.resolve_provider(provider_name or self.settings.llm_default_provider)
        sources, entities = self._retrieve(question, limit)
        evidence = self._evidence(sources, entities)
        request = self._completion_request(provider, question, evidence, stream=False, system=SYSTEM_PROMPT)
        request["response_format"] = {"type": "json_object"}
        answer = ""
        try:
            response = self.client.post(
                self.providers._chat_completions_url(provider.base_url),
                headers={"Authorization": f"Bearer {provider.api_key}"},
                json=request,
            )
            response.raise_for_status()
            payload = self.providers.decode_json(response.json()["choices"][0]["message"]["content"])
            answer = str(payload.get("answer", "")).strip()
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            answer = self._extractive_answer(sources, question)

        metadata = self._metadata(sources, entities, provider.name, provider.model)
        return QuestionResponse(answer=answer or "现有资料不足以回答该问题。", **metadata)

    def stream(self, question: str, provider_name: str | None, limit: int) -> Iterator[tuple[str, dict]]:
        provider = self.providers.resolve_provider(provider_name or self.settings.llm_default_provider)
        yield "status", {"stage": "retrieving", "message": "正在检索证据"}

        sources = self._dedupe_sources(self.vectors.search(question, limit))
        yield "draft", {"content": self._extractive_answer(sources, question)}

        document_ids = list(dict.fromkeys(int(item["document_id"]) for item in sources))
        entities = self._dedupe_entities(self.graph.context_for_documents(document_ids))
        yield "metadata", self._metadata(sources, entities, provider.name, provider.model)
        yield "status", {"stage": "generating", "message": "证据检索完成，正在生成回答"}

        evidence = self._evidence(sources, entities)
        request = self._completion_request(provider, question, evidence, stream=True, system=STREAM_SYSTEM_PROMPT)

        emitted = False
        try:
            with self.client.stream(
                "POST",
                self.providers._chat_completions_url(provider.base_url),
                headers={"Authorization": f"Bearer {provider.api_key}"},
                json=request,
            ) as response:
                response.raise_for_status()
                for line in response.iter_lines():
                    delta = self._stream_delta(line)
                    if not delta:
                        continue
                    if not emitted:
                        yield "reset", {}
                        emitted = True
                    yield "delta", {"content": delta}
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            if not emitted:
                fallback = self._nonstream_fallback(provider, question, evidence)
                if fallback:
                    yield "reset", {}
                    emitted = True
                    yield "delta", {"content": fallback}
                else:
                    yield "warning", {"message": "远程模型暂不可用，已返回原文证据摘要"}
        yield "complete", {"message": "回答完成"}

    def _completion_request(
        self,
        provider: ProviderConfig,
        question: str,
        evidence: str,
        *,
        stream: bool,
        system: str,
    ) -> dict:
        request = {
            "model": provider.model,
            "temperature": min(provider.temperature, 0.2),
            "max_tokens": min(self.settings.llm_max_tokens, 1024),
            "stream": stream,
            "messages": [
                {"role": "system", "content": self.providers._system_prompt(provider, system)},
                {"role": "user", "content": f"问题：{question}\n证据：{evidence}"},
            ],
        }
        request.update(self.providers._thinking_options(provider))
        return request

    def _nonstream_fallback(self, provider: ProviderConfig, question: str, evidence: str) -> str:
        request = self._completion_request(provider, question, evidence, stream=False, system=STREAM_SYSTEM_PROMPT)
        request["max_tokens"] = min(int(request["max_tokens"]), 512)
        try:
            response = self.client.post(
                self.providers._chat_completions_url(provider.base_url),
                headers={"Authorization": f"Bearer {provider.api_key}"},
                json=request,
            )
            response.raise_for_status()
            message = response.json()["choices"][0]["message"]
            return str(message.get("content") or "").strip()
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            return ""

    @staticmethod
    def _stream_delta(line: str) -> str:
        if not line.startswith("data:"):
            return ""
        data = line[5:].strip()
        if not data or data == "[DONE]":
            return ""
        payload = json.loads(data)
        delta = payload["choices"][0].get("delta", {})
        content = delta.get("content")
        return str(content) if content else ""

    def _retrieve(self, question: str, limit: int) -> tuple[list[dict], list[dict]]:
        sources = self._dedupe_sources(self.vectors.search(question, limit))
        document_ids = list(dict.fromkeys(int(item["document_id"]) for item in sources))
        entities = self._dedupe_entities(self.graph.context_for_documents(document_ids))
        return sources, entities

    @staticmethod
    def _dedupe_sources(raw_sources: list[dict]) -> list[dict]:
        source_map: dict[int, dict] = {}
        for source in raw_sources:
            chunk_id = int(source["chunk_id"])
            if chunk_id not in source_map or float(source.get("score", 0)) > float(source_map[chunk_id].get("score", 0)):
                source_map[chunk_id] = source
        return sorted(source_map.values(), key=lambda item: float(item.get("score", 0)), reverse=True)

    @staticmethod
    def _dedupe_entities(raw_entities: list[dict]) -> list[dict]:
        entity_map: dict[int, dict] = {}
        for entity in raw_entities:
            entity_map.setdefault(int(entity["id"]), entity)
        return list(entity_map.values())[:40]

    @staticmethod
    def _evidence(sources: list[dict], entities: list[dict]) -> str:
        compact_sources = [
            {
                "document_name": source.get("document_name"),
                "page_start": source.get("page_start"),
                "page_end": source.get("page_end"),
                "content": str(source.get("content", ""))[:900],
            }
            for source in sources[:4]
        ]
        compact_entities = [
            {"name": entity.get("name"), "nodeType": entity.get("nodeType"), "page": entity.get("page")}
            for entity in entities[:12]
        ]
        return json.dumps({"paragraphs": compact_sources, "entities": compact_entities}, ensure_ascii=False)

    @staticmethod
    def _metadata(sources: list[dict], entities: list[dict], provider: str, model: str) -> dict:
        related = [
            {"id": e["id"], "name": e["name"], "nodeType": e["nodeType"], "page": e["page"]}
            for e in entities[:20]
        ]
        locations = [
            {
                "entityId": e["id"],
                "name": e["name"],
                "longitude": e["longitude"],
                "latitude": e["latitude"],
            }
            for e in entities
            if e.get("longitude") is not None and e.get("latitude") is not None
        ]
        citations = [
            {
                "documentId": s["document_id"],
                "documentName": s["document_name"],
                "chunkId": s["chunk_id"],
                "pageStart": s["page_start"],
                "pageEnd": s["page_end"],
                "content": s["content"],
                "score": s["score"],
            }
            for s in sources
        ]
        return {
            "related_entities": related,
            "spatial_locations": locations,
            "sources": citations,
            "provider": provider,
            "model": model,
        }

    @staticmethod
    def _extractive_answer(sources: list[dict], question: str = "") -> str:
        if not sources:
            return "现有资料不足以回答该问题。"
        keywords = [token for token in re.findall(r"[\u4e00-\u9fff]{2,}|[A-Za-z]+\d+|\d+(?:\.\d+)?%?", question) if token]
        bullets: list[str] = []
        for source in sources[:3]:
            content = str(source.get("content", "")).strip()
            document_name = str(source.get("document_name", "原始资料")).strip()
            if not content:
                continue
            sentences = [part.strip() for part in re.split(r"(?<=[。；;！？\n])", content) if part.strip()]
            ranked = sorted(
                sentences,
                key=lambda sentence: sum(1 for keyword in keywords if keyword and keyword in sentence),
                reverse=True,
            )
            chosen = ranked[0] if ranked else content
            if len(chosen) > 180:
                chosen = chosen[:177] + "…"
            page_start = source.get("page_start")
            page_hint = f"第{page_start}页" if page_start is not None else "原文"
            bullets.append(f"《{document_name}》{page_hint}：{chosen}")
        if not bullets:
            return "现有资料不足以回答该问题。"
        if len(bullets) == 1:
            return f"根据检索到的原文证据：{bullets[0]}"
        return "根据检索到的原文证据，可归纳如下：\n" + "\n".join(f"{index}. {item}" for index, item in enumerate(bullets, start=1))
