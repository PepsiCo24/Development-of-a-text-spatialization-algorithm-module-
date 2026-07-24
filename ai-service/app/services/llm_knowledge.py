import json
import re
from concurrent.futures import ThreadPoolExecutor
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
        self.client = client or httpx.Client(
            timeout=min(self.settings.llm_timeout_seconds, 15.0),
            trust_env=self.settings.llm_trust_env_proxy,
        )
        self.provider_resolver = GeologicalEntityExtractor(self.settings, self.client)

    def extract(self, chunks: list[KnowledgeChunk], provider_name: str | None = None) -> tuple[ProviderConfig, list[ExtractedAttribute], list[ExtractedRelation]]:
        provider = self.provider_resolver.resolve_provider(provider_name or self.settings.llm_default_provider)
        attributes: list[ExtractedAttribute] = []
        relations: list[ExtractedRelation] = []
        batches = self._batches(chunks)
        workers = max(1, min(self.settings.llm_parallel_workers, len(batches)))
        with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="knowledge-extract") as pool:
            batch_results = list(pool.map(lambda batch: self._extract_batch(provider, batch), batches))
        for batch_attributes, batch_relations in batch_results:
            attributes.extend(batch_attributes)
            relations.extend(batch_relations)
        self._recalibrate_uniform_attribute_confidence(attributes, chunks)
        attribute_map = {(a.entity_id, a.attribute_type, a.original_value, a.page): a for a in attributes}
        relation_map = {(r.source_entity_id, r.target_entity_id, r.relation_type, r.page): r for r in relations}
        return provider, list(attribute_map.values()), list(relation_map.values())

    def _batches(self, chunks: list[KnowledgeChunk]) -> list[list[KnowledgeChunk]]:
        batches: list[list[KnowledgeChunk]] = []
        current: list[KnowledgeChunk] = []
        current_chars = 0
        for chunk in chunks:
            if current and (len(current) >= self.settings.llm_batch_chunk_limit
                            or current_chars + len(chunk.content) > self.settings.llm_batch_char_limit):
                batches.append(current)
                current = []
                current_chars = 0
            current.append(chunk)
            current_chars += len(chunk.content)
        if current:
            batches.append(current)
        return batches

    def _extract_batch(self, provider: ProviderConfig, chunks: list[KnowledgeChunk]) -> tuple[list[ExtractedAttribute], list[ExtractedRelation]]:
        try:
            return self._normalize_batch(self._call_batch(provider, chunks), chunks)
        except LlmExtractionError:
            attributes: list[ExtractedAttribute] = []
            relations: list[ExtractedRelation] = []
            for chunk in chunks:
                chunk_attributes, chunk_relations = self._evidence_fallback(chunk)
                attributes.extend(chunk_attributes)
                relations.extend(chunk_relations)
            return attributes, relations

    def _recalibrate_uniform_attribute_confidence(self, attributes: list[ExtractedAttribute], chunks: list[KnowledgeChunk]) -> None:
        if len(attributes) < 2 or len({round(item.confidence, 4) for item in attributes}) != 1:
            return
        evidence_by_entity = {
            entity.entity_id: (entity, chunk.content)
            for chunk in chunks for entity in chunk.entities
        }
        for attribute in attributes:
            evidence = evidence_by_entity.get(attribute.entity_id)
            if evidence is None:
                continue
            entity, content = evidence
            attribute.confidence = self.provider_resolver.evidence_confidence(
                attribute.original_value, entity.entity_type, attribute.source_text, content,
            )

    def _call(self, provider: ProviderConfig, chunk: KnowledgeChunk) -> Any:
        return self._call_batch(provider, [chunk])

    def _call_batch(self, provider: ProviderConfig, chunks: list[KnowledgeChunk]) -> Any:
        source = "\n\n".join(
            f"### chunkId={chunk.chunk_id} pages={chunk.page_start}-{chunk.page_end}\n"
            f"实体清单：{json.dumps([item.model_dump(by_alias=True) for item in chunk.entities], ensure_ascii=False)}\n"
            f"原文：{chunk.content}"
            for chunk in chunks
        )
        request = {
            "model": provider.model, "temperature": min(provider.temperature, .1),
            "max_tokens": min(self.settings.llm_max_tokens, 2048), "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": self.provider_resolver._system_prompt(provider, SYSTEM_PROMPT)},
                {"role": "user", "content": f"请一次完成以下 {len(chunks)} 个文本块的属性和关系抽取；允许识别跨文本块关系，证据必须来自所给原文：\n{source}"},
            ],
        }
        if self.provider_resolver._is_siliconflow_qwen3(provider):
            request["enable_thinking"] = False
        try:
            response = self.client.post(self.provider_resolver._chat_completions_url(provider.base_url), headers={"Authorization": f"Bearer {provider.api_key}"}, json=request)
            response.raise_for_status()
            return self.provider_resolver.decode_json(response.json()["choices"][0]["message"]["content"])
        except (httpx.HTTPError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exception:
            raise LlmExtractionError(f"{provider.name} 属性关系抽取调用失败: {exception}") from exception

    def _normalize_batch(self, payload: Any, chunks: list[KnowledgeChunk]) -> tuple[list[ExtractedAttribute], list[ExtractedRelation]]:
        valid_ids = {entity.entity_id for chunk in chunks for entity in chunk.entities}
        entity_chunks = {entity.entity_id: chunk for chunk in chunks for entity in chunk.entities}
        attributes: list[ExtractedAttribute] = []
        relations: list[ExtractedRelation] = []
        if not isinstance(payload, dict):
            return attributes, relations
        for row in payload.get("attributes", []):
            if not isinstance(row, dict):
                continue
            entity_id = int(row.get("entityId", -1)); attribute_type = str(row.get("attributeType", "")).upper()
            value = str(row.get("value", "")).strip(); source = str(row.get("sourceText", "")).strip()
            chunk = self._evidence_chunk(chunks, source, entity_chunks.get(entity_id))
            if entity_id not in valid_ids or attribute_type not in ATTRIBUTE_TYPES or not value or not source or chunk is None:
                continue
            attributes.append(ExtractedAttribute(entity_id=entity_id, attribute_type=attribute_type, original_value=value,
                confidence=self._confidence(row), source_text=source, page=self._page(row, chunk)))
        for row in payload.get("relations", []):
            if not isinstance(row, dict):
                continue
            source_id = int(row.get("sourceEntityId", -1)); target_id = int(row.get("targetEntityId", -1))
            relation_type = str(row.get("relationType", "")).upper(); source = str(row.get("sourceText", "")).strip()
            chunk = self._evidence_chunk(chunks, source, entity_chunks.get(source_id))
            if source_id not in valid_ids or target_id not in valid_ids or source_id == target_id or relation_type not in RELATION_TYPES or not source or chunk is None:
                continue
            relations.append(ExtractedRelation(source_entity_id=source_id, target_entity_id=target_id, relation_type=relation_type,
                confidence=self._confidence(row), source_text=source, page=self._page(row, chunk)))
        return attributes, relations

    @staticmethod
    def _evidence_chunk(chunks: list[KnowledgeChunk], source: str, fallback: KnowledgeChunk | None) -> KnowledgeChunk | None:
        normalized_source = GeologicalEntityExtractor._normalized_text(source)
        if normalized_source:
            for chunk in chunks:
                if normalized_source in GeologicalEntityExtractor._normalized_text(chunk.content):
                    return chunk
        return fallback

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

    def _evidence_fallback(self, chunk: KnowledgeChunk) -> tuple[list[ExtractedAttribute], list[ExtractedRelation]]:
        """Keep evidence extraction usable when the configured remote model is unavailable."""
        attributes: list[ExtractedAttribute] = []
        relations: list[ExtractedRelation] = []
        attribute_types = {
            "GEOLOGICAL_AGE": "AGE", "THICKNESS": "THICKNESS",
            "GRADE": "GRADE", "LITHOLOGY": "LITHOLOGY",
        }
        by_name = {entity.entity_name: entity for entity in chunk.entities}
        for entity in chunk.entities:
            attribute_type = attribute_types.get(entity.entity_type)
            if not attribute_type or entity.entity_name not in chunk.content:
                continue
            source = self._evidence_sentence(chunk.content, entity.entity_name)
            confidence = self.provider_resolver.evidence_confidence(
                entity.entity_name, entity.entity_type, source, chunk.content,
            )
            attributes.append(ExtractedAttribute(
                entity_id=entity.entity_id, attribute_type=attribute_type,
                original_value=entity.entity_name, confidence=confidence,
                source_text=source, page=chunk.page_start,
            ))

        for sentence in re.split(r"[。；;\n]+", chunk.content):
            names = [name for name in by_name if name in sentence]
            if "侵入" in sentence and len(names) >= 2:
                before, after = sentence.split("侵入", 1)
                sources = [by_name[name] for name in names if name in before and by_name[name].entity_type in {"ROCK_BODY", "LITHOLOGY"}]
                targets = [by_name[name] for name in names if name in after and by_name[name].entity_type in {"ROCK_BODY", "STRATUM", "LITHOLOGY"}]
                for source_entity in sources:
                    for target_entity in targets:
                        relations.append(ExtractedRelation(
                            source_entity_id=source_entity.entity_id,
                            target_entity_id=target_entity.entity_id,
                            relation_type="INTRUDES", confidence=0.94,
                            source_text=sentence.strip(), page=chunk.page_start,
                        ))
            if "位于" in sentence:
                places = [by_name[name] for name in names if by_name[name].entity_type == "PLACE"]
                subjects = [entity for entity in chunk.entities if entity.entity_type == "ROCK_BODY"]
                for subject in subjects[:1]:
                    for place in places:
                        relations.append(ExtractedRelation(
                            source_entity_id=subject.entity_id, target_entity_id=place.entity_id,
                            relation_type="LOCATED_IN", confidence=0.82,
                            source_text=sentence.strip(), page=chunk.page_start,
                        ))
        return attributes, relations

    @staticmethod
    def _evidence_sentence(content: str, name: str) -> str:
        for sentence in re.split(r"[。；;\n]+", content):
            if name in sentence:
                return sentence.strip()
        return name
