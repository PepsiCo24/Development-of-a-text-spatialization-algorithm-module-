import json
from typing import Any

import httpx

from app.core.config import Settings, get_settings
from app.models.knowledge import ExtractedAttribute, ExtractedRelation, KnowledgeChunk
from app.services.llm_entities import GeologicalEntityExtractor, LlmExtractionError, ProviderConfig

ATTRIBUTE_TYPES = {"AGE", "THICKNESS", "SCALE", "GRADE", "LITHOLOGY"}
RELATION_TYPES = {"LOCATED_IN", "OCCURS_IN", "INTRUDES", "CONTACTS", "CONTROLS", "CONTAINS"}
SYSTEM_PROMPT = """你是地质知识抽取专家。仅根据给定原文和实体清单抽取属性与实体关系，不得虚构实体或证据。
属性类型仅允许 AGE(年代)、THICKNESS(厚度)、SCALE(规模)、GRADE(品位)、LITHOLOGY(岩性)。
关系类型仅允许 LOCATED_IN(位于)、OCCURS_IN(赋存于)、INTRUDES(侵入)、CONTACTS(接触)、CONTROLS(控制)、CONTAINS(包含)。
sourceEntityId、targetEntityId 和 entityId 必须使用输入清单中的数字 ID。
严格输出 JSON：{"attributes":[{"entityId":1,"attributeType":"AGE","value":"燕山期","confidence":0.9,"sourceText":"证据句","page":1}],"relations":[{"sourceEntityId":1,"targetEntityId":2,"relationType":"INTRUDES","confidence":0.9,"sourceText":"证据句","page":1}]}。无结果时返回空数组，不得返回 Markdown。"""


class GeologicalKnowledgeExtractor:
    def __init__(self, settings: Settings | None = None, client: httpx.Client | None = None) -> None:
        self.settings = settings or get_settings()
        self.client = client or httpx.Client(timeout=self.settings.llm_timeout_seconds)
        self.provider_resolver = GeologicalEntityExtractor(self.settings, self.client)

    def extract(self, chunks: list[KnowledgeChunk], provider_name: str | None = None) -> tuple[ProviderConfig, list[ExtractedAttribute], list[ExtractedRelation]]:
        provider = self.provider_resolver.resolve_provider(provider_name or self.settings.llm_default_provider)
        attributes: list[ExtractedAttribute] = []
        relations: list[ExtractedRelation] = []
        for chunk in chunks:
            payload = self._call(provider, chunk)
            chunk_attributes, chunk_relations = self._normalize(payload, chunk)
            attributes.extend(chunk_attributes)
            relations.extend(chunk_relations)
        attribute_map = {(a.entity_id, a.attribute_type, a.original_value, a.page): a for a in attributes}
        relation_map = {(r.source_entity_id, r.target_entity_id, r.relation_type, r.page): r for r in relations}
        return provider, list(attribute_map.values()), list(relation_map.values())

    def _call(self, provider: ProviderConfig, chunk: KnowledgeChunk) -> Any:
        entity_json = json.dumps([item.model_dump(by_alias=True) for item in chunk.entities], ensure_ascii=False)
        request = {
            "model": provider.model, "temperature": self.settings.llm_temperature,
            "max_tokens": self.settings.llm_max_tokens, "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"页码范围：{chunk.page_start}-{chunk.page_end}\n实体清单：{entity_json}\n原文：\n{chunk.content}"},
            ],
        }
        try:
            response = self.client.post(f"{provider.base_url}/chat/completions", headers={"Authorization": f"Bearer {provider.api_key}"}, json=request)
            response.raise_for_status()
            return self.provider_resolver.decode_json(response.json()["choices"][0]["message"]["content"])
        except (httpx.HTTPError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exception:
            raise LlmExtractionError(f"{provider.name} 属性关系抽取调用失败: {exception}") from exception

    def _normalize(self, payload: Any, chunk: KnowledgeChunk) -> tuple[list[ExtractedAttribute], list[ExtractedRelation]]:
        valid_ids = {entity.entity_id for entity in chunk.entities}
        attributes: list[ExtractedAttribute] = []
        relations: list[ExtractedRelation] = []
        if not isinstance(payload, dict):
            return attributes, relations
        for row in payload.get("attributes", []):
            entity_id = int(row.get("entityId", -1)); attribute_type = str(row.get("attributeType", "")).upper()
            value = str(row.get("value", "")).strip(); source = str(row.get("sourceText", "")).strip()
            if entity_id not in valid_ids or attribute_type not in ATTRIBUTE_TYPES or not value or not source:
                continue
            attributes.append(ExtractedAttribute(entity_id=entity_id, attribute_type=attribute_type, original_value=value,
                confidence=self._confidence(row), source_text=source, page=self._page(row, chunk)))
        for row in payload.get("relations", []):
            source_id = int(row.get("sourceEntityId", -1)); target_id = int(row.get("targetEntityId", -1))
            relation_type = str(row.get("relationType", "")).upper(); source = str(row.get("sourceText", "")).strip()
            if source_id not in valid_ids or target_id not in valid_ids or source_id == target_id or relation_type not in RELATION_TYPES or not source:
                continue
            relations.append(ExtractedRelation(source_entity_id=source_id, target_entity_id=target_id, relation_type=relation_type,
                confidence=self._confidence(row), source_text=source, page=self._page(row, chunk)))
        return attributes, relations

    @staticmethod
    def _confidence(row: dict[str, Any]) -> float:
        return min(max(float(row.get("confidence", .5)), 0), 1)

    @staticmethod
    def _page(row: dict[str, Any], chunk: KnowledgeChunk) -> int:
        return min(max(int(row.get("page") or chunk.page_start), chunk.page_start), chunk.page_end)
