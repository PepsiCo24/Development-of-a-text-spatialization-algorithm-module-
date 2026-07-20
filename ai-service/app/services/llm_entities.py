import json
import re
from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import Settings, get_settings
from app.models.entity import EntityChunk, ExtractedEntity

ENTITY_TYPES = {
    "STRATUM", "LITHOLOGY", "ROCK_BODY", "FAULT", "MINERAL", "ORE_BODY",
    "MINERALIZATION_ZONE", "GEOLOGICAL_AGE", "PLACE", "COORDINATE", "GRADE",
    "THICKNESS", "DIP_DIRECTION", "DIP_ANGLE",
}

SYSTEM_PROMPT = """你是地质调查文本知识抽取专家。请只依据输入原文识别地质实体，禁止补充原文不存在的信息。
实体类型仅允许：STRATUM(地层)、LITHOLOGY(岩性)、ROCK_BODY(岩体)、FAULT(断裂)、MINERAL(矿种)、ORE_BODY(矿体)、MINERALIZATION_ZONE(矿化带)、GEOLOGICAL_AGE(地质年代)、PLACE(地名)、COORDINATE(坐标)、GRADE(品位)、THICKNESS(厚度)、DIP_DIRECTION(倾向)、DIP_ANGLE(倾角)。
严格返回 JSON 对象：{"entities":[{"entityName":"原文实体","entityType":"类型","confidence":0.0,"sourceText":"包含实体的最短完整证据句","page":1}]}。没有实体时返回 {"entities":[]}。不得返回 Markdown。"""


class LlmConfigurationError(RuntimeError):
    pass


class LlmExtractionError(RuntimeError):
    pass


@dataclass(frozen=True)
class ProviderConfig:
    name: str
    base_url: str
    api_key: str
    model: str


class GeologicalEntityExtractor:
    def __init__(self, settings: Settings | None = None, client: httpx.Client | None = None) -> None:
        self.settings = settings or get_settings()
        self.client = client or httpx.Client(timeout=self.settings.llm_timeout_seconds)

    def extract(self, chunks: list[EntityChunk], provider_name: str | None = None) -> tuple[ProviderConfig, list[ExtractedEntity]]:
        provider = self.resolve_provider(provider_name or self.settings.llm_default_provider)
        entities: list[ExtractedEntity] = []
        for chunk in chunks:
            payload = self._call(provider, chunk)
            entities.extend(self._normalize(payload, chunk))
        unique: dict[tuple[str, str, int, int], ExtractedEntity] = {}
        for entity in entities:
            key = (entity.entity_name, entity.entity_type, entity.page, entity.chunk_id)
            if key not in unique or entity.confidence > unique[key].confidence:
                unique[key] = entity
        return provider, list(unique.values())

    def resolve_provider(self, name: str) -> ProviderConfig:
        normalized = name.strip().lower()
        if normalized == "deepseek":
            values = (self.settings.deepseek_base_url, self.settings.deepseek_api_key, self.settings.deepseek_model)
        elif normalized == "qwen":
            values = (self.settings.qwen_base_url, self.settings.qwen_api_key, self.settings.qwen_model)
        else:
            raise LlmConfigurationError("LLM provider 仅支持 deepseek 或 qwen")
        if not values[1]:
            raise LlmConfigurationError(f"未配置 {normalized.upper()}_API_KEY")
        return ProviderConfig(normalized, values[0].rstrip("/"), values[1], values[2])

    def _call(self, provider: ProviderConfig, chunk: EntityChunk) -> Any:
        request = {
            "model": provider.model,
            "temperature": self.settings.llm_temperature,
            "max_tokens": self.settings.llm_max_tokens,
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": SYSTEM_PROMPT},
                {"role": "user", "content": f"页码范围：{chunk.page_start}-{chunk.page_end}\n原文：\n{chunk.content}"},
            ],
        }
        try:
            response = self.client.post(
                f"{provider.base_url}/chat/completions",
                headers={"Authorization": f"Bearer {provider.api_key}"}, json=request,
            )
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return self.decode_json(content)
        except (httpx.HTTPError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exception:
            raise LlmExtractionError(f"{provider.name} 实体识别调用失败: {exception}") from exception

    @staticmethod
    def decode_json(content: str) -> Any:
        cleaned = re.sub(r"^```(?:json)?\s*|\s*```$", "", content.strip(), flags=re.IGNORECASE)
        return json.loads(cleaned)

    def _normalize(self, payload: Any, chunk: EntityChunk) -> list[ExtractedEntity]:
        rows = payload.get("entities", []) if isinstance(payload, dict) else []
        normalized: list[ExtractedEntity] = []
        for row in rows:
            if not isinstance(row, dict):
                continue
            name = str(row.get("entityName", "")).strip()
            entity_type = str(row.get("entityType", "")).strip().upper()
            source = str(row.get("sourceText", "")).strip()
            if not name or entity_type not in ENTITY_TYPES or not source:
                continue
            source_start = chunk.content.find(name)
            page = int(row.get("page") or chunk.page_start)
            page = min(max(page, chunk.page_start), chunk.page_end)
            confidence = min(max(float(row.get("confidence", 0.5)), 0.0), 1.0)
            normalized.append(ExtractedEntity(
                entity_name=name, entity_type=entity_type, confidence=confidence,
                source_text=source, page=page, chunk_id=chunk.chunk_id,
                source_start=source_start if source_start >= 0 else None,
                source_end=source_start + len(name) if source_start >= 0 else None,
            ))
        return normalized
