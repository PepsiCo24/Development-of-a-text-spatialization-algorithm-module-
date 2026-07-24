import json
from collections.abc import Iterator

import httpx

from app.core.config import Settings, get_settings
from app.models.graph import QuestionResponse
from app.services.graph_store import Neo4jGraphStore
from app.services.llm_entities import GeologicalEntityExtractor
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
            timeout=min(self.settings.llm_timeout_seconds, 20.0),
            trust_env=self.settings.llm_trust_env_proxy,
        )
        self.providers = GeologicalEntityExtractor(self.settings, self.client)
        self.vectors = vectors or QdrantVectorStore(self.settings)
        self.graph = graph or Neo4jGraphStore(self.settings)

    def ask(self, question: str, provider_name: str | None, limit: int) -> QuestionResponse:
        provider = self.providers.resolve_provider(provider_name or self.settings.llm_default_provider)
        sources, entities = self._retrieve(question, limit)
        evidence = self._evidence(sources, entities)
        request = {
            "model": provider.model,
            "temperature": min(provider.temperature, .2),
            "max_tokens": min(self.settings.llm_max_tokens, 1024),
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": self.providers._system_prompt(provider, SYSTEM_PROMPT)},
                {"role": "user", "content": f"问题：{question}\n证据：{evidence}"},
            ],
        }
        if self.providers._is_siliconflow_qwen3(provider):
            request["enable_thinking"] = False

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
            answer = self._extractive_answer(sources)

        metadata = self._metadata(sources, entities, provider.name, provider.model)
        return QuestionResponse(answer=answer or "现有资料不足以回答该问题。", **metadata)

    def stream(self, question: str, provider_name: str | None, limit: int) -> Iterator[tuple[str, dict]]:
        provider = self.providers.resolve_provider(provider_name or self.settings.llm_default_provider)
        yield "status", {"stage": "retrieving", "message": "正在检索相关段落与知识图谱"}
        sources, entities = self._retrieve(question, limit)
        yield "metadata", self._metadata(sources, entities, provider.name, provider.model)
        yield "status", {"stage": "generating", "message": "证据检索完成，正在生成回答"}
        yield "draft", {"content": self._extractive_answer(sources)}
        request = {
            "model": provider.model,
            "temperature": min(provider.temperature, .2),
            "max_tokens": min(self.settings.llm_max_tokens, 1024),
            "stream": True,
            "messages": [
                {"role": "system", "content": self.providers._system_prompt(provider, STREAM_SYSTEM_PROMPT)},
                {"role": "user", "content": f"问题：{question}\n证据：{self._evidence(sources, entities)}"},
            ],
        }
        if self.providers._is_siliconflow_qwen3(provider):
            request["enable_thinking"] = False

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
                    if not line.startswith("data:"):
                        continue
                    data = line[5:].strip()
                    if not data or data == "[DONE]":
                        continue
                    payload = json.loads(data)
                    delta = payload["choices"][0].get("delta", {}).get("content", "")
                    if delta:
                        if not emitted:
                            yield "reset", {}
                        emitted = True
                        yield "delta", {"content": str(delta)}
        except (httpx.HTTPError, KeyError, IndexError, TypeError, ValueError, json.JSONDecodeError):
            if not emitted:
                yield "warning", {"message": "远程模型暂不可用，已返回原文证据摘要"}
        yield "complete", {"message": "回答完成"}

    def _retrieve(self, question: str, limit: int) -> tuple[list[dict], list[dict]]:
        raw_sources = self.vectors.search(question, limit)
        source_map: dict[int, dict] = {}
        for source in raw_sources:
            chunk_id = int(source["chunk_id"])
            if chunk_id not in source_map or float(source.get("score", 0)) > float(source_map[chunk_id].get("score", 0)):
                source_map[chunk_id] = source
        sources = list(source_map.values())
        document_ids = list(dict.fromkeys(int(item["document_id"]) for item in sources))
        entity_map: dict[int, dict] = {}
        for entity in self.graph.context_for_documents(document_ids):
            entity_map.setdefault(int(entity["id"]), entity)
        entities = list(entity_map.values())[:40]
        return sources, entities

    @staticmethod
    def _evidence(sources: list[dict], entities: list[dict]) -> str:
        compact_sources = [
            {**source, "content": str(source.get("content", ""))[:1800]}
            for source in sources
        ]
        return json.dumps({"paragraphs": compact_sources, "entities": entities[:40]}, ensure_ascii=False)

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
    def _extractive_answer(sources: list[dict]) -> str:
        if not sources:
            return "现有资料不足以回答该问题。"
        best = sources[0]
        content = str(best.get("content", "")).strip()
        document_name = str(best.get("document_name", "原始资料")).strip()
        return f"根据《{document_name}》中的原文证据：{content}" if content else "现有资料不足以回答该问题。"
