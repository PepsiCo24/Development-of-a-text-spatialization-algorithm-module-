import json

import httpx

from app.core.config import Settings, get_settings
from app.models.graph import QuestionResponse
from app.services.graph_store import Neo4jGraphStore
from app.services.llm_entities import GeologicalEntityExtractor, LlmExtractionError
from app.services.vector_store import QdrantVectorStore

SYSTEM_PROMPT = """你是地质科研问答助手。只能使用提供的检索段落和知识图谱实体回答，不得编造事实。
若证据不足，明确说明。输出严格 JSON：{"answer":"回答正文"}，不输出 Markdown。"""


class GeologicalRagService:
    def __init__(self, settings: Settings | None = None, client: httpx.Client | None = None, vectors: QdrantVectorStore | None = None, graph: Neo4jGraphStore | None = None) -> None:
        self.settings = settings or get_settings(); self.client = client or httpx.Client(timeout=self.settings.llm_timeout_seconds)
        self.providers = GeologicalEntityExtractor(self.settings, self.client); self.vectors = vectors or QdrantVectorStore(self.settings); self.graph = graph or Neo4jGraphStore(self.settings)

    def ask(self, question: str, provider_name: str | None, limit: int) -> QuestionResponse:
        provider = self.providers.resolve_provider(provider_name or self.settings.llm_default_provider)
        sources = self.vectors.search(question, limit); document_ids = list({int(item["document_id"]) for item in sources})
        entities = self.graph.context_for_documents(document_ids)
        evidence = json.dumps({"paragraphs":sources,"entities":entities}, ensure_ascii=False)
        request = {"model":provider.model,"temperature":provider.temperature,"max_tokens":self.settings.llm_max_tokens,"response_format":{"type":"json_object"},"messages":[{"role":"system","content":self.providers._system_prompt(provider,SYSTEM_PROMPT)},{"role":"user","content":f"问题：{question}\n证据：{evidence}"}]}
        try:
            response = self.client.post(f"{provider.base_url}/chat/completions",headers={"Authorization":f"Bearer {provider.api_key}"},json=request); response.raise_for_status()
            payload = self.providers.decode_json(response.json()["choices"][0]["message"]["content"]); answer = str(payload.get("answer", "")).strip()
        except (httpx.HTTPError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exception:
            raise LlmExtractionError(f"{provider.name} 智能问答调用失败: {exception}") from exception
        related = [{"id":e["id"],"name":e["name"],"nodeType":e["nodeType"],"page":e["page"]} for e in entities[:20]]
        locations = [{"entityId":e["id"],"name":e["name"],"longitude":e["longitude"],"latitude":e["latitude"]} for e in entities if e.get("longitude") is not None and e.get("latitude") is not None]
        citations = [{"documentId":s["document_id"],"documentName":s["document_name"],"chunkId":s["chunk_id"],"pageStart":s["page_start"],"pageEnd":s["page_end"],"content":s["content"],"score":s["score"]} for s in sources]
        return QuestionResponse(answer=answer or "现有资料不足以回答该问题。",related_entities=related,spatial_locations=locations,sources=citations,provider=provider.name,model=provider.model)
