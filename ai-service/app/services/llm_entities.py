import json
import re
from concurrent.futures import ThreadPoolExecutor
from dataclasses import dataclass
from typing import Any

import httpx

from app.core.config import Settings, get_settings
from app.models.entity import EntityChunk, ExtractedEntity
from app.services.runtime_config import get_runtime_provider

ENTITY_TYPES = {
    "STRATUM", "LITHOLOGY", "ROCK_BODY", "FAULT", "MINERAL", "ORE_BODY",
    "MINERALIZATION_ZONE", "GEOLOGICAL_AGE", "PLACE", "COORDINATE", "GRADE",
    "THICKNESS", "DIP_DIRECTION", "DIP_ANGLE",
}

SYSTEM_PROMPT = """你是地质调查文本知识抽取专家。请逐句扫描并完整识别全部地质实体，只依据输入原文，禁止补充原文不存在的信息。
实体类型仅允许：STRATUM(地层)、LITHOLOGY(岩性)、ROCK_BODY(岩体)、FAULT(断裂)、MINERAL(矿种)、ORE_BODY(矿体)、MINERALIZATION_ZONE(矿化带)、GEOLOGICAL_AGE(地质年代)、PLACE(地名)、COORDINATE(坐标)、GRADE(品位)、THICKNESS(厚度)、DIP_DIRECTION(倾向)、DIP_ANGLE(倾角)。
confidence 必须根据证据明确程度给出 0.50 到 0.99 之间的数值，禁止统一返回 0。
sourceText 必须逐字来自原文并包含实体；坐标、品位、厚度、倾向和倾角不得遗漏。相同名称在不同证据句中可分别返回。chunkId 必须使用输入中对应的文本块编号。
严格返回 JSON 对象：{"entities":[{"chunkId":1,"entityName":"原文实体","entityType":"类型","confidence":0.85,"sourceText":"包含实体的最短完整证据句","page":1}]}。没有实体时返回 {"entities":[]}。不得返回 Markdown。"""


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
    temperature: float
    prompt_template: str | None


class GeologicalEntityExtractor:
    def __init__(self, settings: Settings | None = None, client: httpx.Client | None = None) -> None:
        self.settings = settings or get_settings()
        self.client = client or httpx.Client(
            timeout=self.settings.llm_timeout_seconds,
            trust_env=self.settings.llm_trust_env_proxy,
        )

    def extract(self, chunks: list[EntityChunk], provider_name: str | None = None) -> tuple[ProviderConfig, list[ExtractedEntity]]:
        provider = self.resolve_provider(provider_name or self.settings.llm_default_provider)
        if not chunks:
            return provider, []
        evidence_entities = [entity for chunk in chunks for entity in self._evidence_fallback(chunk)]
        evidence_types = {entity.entity_type for entity in evidence_entities}
        if (sum(len(chunk.content) for chunk in chunks) <= self.settings.llm_fast_path_max_chars
                and len(evidence_entities) >= self.settings.llm_fast_path_min_entities
                and len(evidence_types) >= self.settings.llm_fast_path_min_types):
            return provider, self._deduplicate(evidence_entities)
        batches = self._batches(chunks)
        workers = max(1, min(self.settings.llm_parallel_workers, len(batches)))
        with ThreadPoolExecutor(max_workers=workers, thread_name_prefix="entity-extract") as pool:
            batch_results = list(pool.map(lambda batch: self._extract_batch(provider, batch), batches))
        chunk_results = [result for batch in batch_results for result in batch]
        entities = [entity for result in chunk_results for entity in result]
        return provider, self._deduplicate(entities)

    @staticmethod
    def _deduplicate(entities: list[ExtractedEntity]) -> list[ExtractedEntity]:
        unique: dict[tuple[str, str, int, int], ExtractedEntity] = {}
        for entity in entities:
            key = (entity.entity_name, entity.entity_type, entity.page, entity.chunk_id)
            if key not in unique or entity.confidence > unique[key].confidence:
                unique[key] = entity
        return list(unique.values())

    def _batches(self, chunks: list[EntityChunk]) -> list[list[EntityChunk]]:
        batches: list[list[EntityChunk]] = []
        current: list[EntityChunk] = []
        current_chars = 0
        for chunk in chunks:
            if current and (len(current) >= self.settings.llm_batch_chunk_limit or current_chars + len(chunk.content) > self.settings.llm_batch_char_limit):
                batches.append(current); current = []; current_chars = 0
            current.append(chunk); current_chars += len(chunk.content)
        if current:
            batches.append(current)
        return batches

    def _extract_batch(self, provider: ProviderConfig, chunks: list[EntityChunk]) -> list[list[ExtractedEntity]]:
        try:
            payload = self._call_batch(provider, chunks)
            rows = payload.get("entities", []) if isinstance(payload, dict) else []
            grouped: dict[int, list[dict[str, Any]]] = {chunk.chunk_id: [] for chunk in chunks}
            for row in rows:
                if not isinstance(row, dict):
                    continue
                chunk_id = row.get("chunkId") or row.get("chunk_id")
                target = next((chunk for chunk in chunks if chunk.chunk_id == chunk_id), None)
                if target is None:
                    name = str(row.get("entityName", "")).strip()
                    source = self._normalized_text(str(row.get("sourceText", "")))
                    target = next((chunk for chunk in chunks if name in chunk.content or (source and source in self._normalized_text(chunk.content))), None)
                if target is not None:
                    grouped[target.chunk_id].append(row)
            return [self._normalize({"entities": grouped[chunk.chunk_id]}, chunk) for chunk in chunks]
        except LlmExtractionError:
            return [self._evidence_fallback(chunk) for chunk in chunks]

    def _evidence_fallback(self, chunk: EntityChunk) -> list[ExtractedEntity]:
        rules = {
            "STRATUM": r"(?:(?:上|中|下)?(?:寒武系|奥陶系|志留系|泥盆系|石炭系|二叠系|三叠系|侏罗系|白垩系|三叠统)|(?:上|中|下)三叠统)?[\u4e00-\u9fff]{1,6}组",
            "FAULT": r"(?:F\s*\d+|[\u4e00-\u9fff]{1,8})(?:断裂|断层)",
            "MINERALIZATION_ZONE": r"[\u4e00-\u9fff]{1,10}?(?:矿化带|蚀变带|岩化带)",
            "ORE_BODY": r"(?:[ⅠⅡⅢⅣⅤⅥⅦⅧⅨⅩIVX一二三四五六七八九十\d]+号)?[\u4e00-\u9fff]{0,6}矿体",
            "ROCK_BODY": r"(?:闪长玢岩|花岗岩体|闪长岩体|辉绿岩体)",
            "LITHOLOGY": r"(?:灰岩|砂岩|页岩|泥岩|花岗岩|闪长岩|玄武岩|大理岩|矽卡岩)",
            "MINERAL": r"(?:黄铜矿|磁铁矿|赤铁矿|方铅矿|闪锌矿|辉钼矿)",
            "GEOLOGICAL_AGE": r"(?:早|中|晚)?(?:寒武纪|奥陶纪|志留纪|泥盆纪|石炭纪|二叠纪|三叠纪|侏罗纪|白垩纪|三叠世|燕山期)",
            "PLACE": r"[\u4e00-\u9fff]{2,8}?(?:矿区|矿段|省|市|县)",
            "COORDINATE": r"(?:东经|西经)\s*\d{1,3}(?:\.\d+)?\s*[°度]?.{0,8}(?:北纬|南纬)\s*\d{1,2}(?:\.\d+)?\s*[°度]?",
            "GRADE": r"\d+(?:\.\d+)?\s*%",
            "THICKNESS": r"(?<=厚度)\s*\d+(?:\.\d+)?\s*(?:m|米)",
            "DIP_DIRECTION": r"(?<=倾向)(?:北东|北西|南东|南西|正北|正南|正东|正西)",
            "DIP_ANGLE": r"(?<=倾角)\s*\d+(?:\.\d+)?\s*[°度]",
        }
        found: list[ExtractedEntity] = []
        for entity_type, pattern in rules.items():
            for match in re.finditer(pattern, chunk.content, re.IGNORECASE):
                name = match.group(0).strip()
                if entity_type == "PLACE":
                    name = re.sub(r"^.*(?:位于|地处|包括)", "", name)
                    if len(name) < 2:
                        continue
                if entity_type == "ORE_BODY" and name == "矿体":
                    continue
                source = self._evidence_sentence(chunk.content, match.start())
                found.append(ExtractedEntity(entity_name=name, entity_type=entity_type,
                    confidence=self.evidence_confidence(name, entity_type, source, chunk.content),
                    source_text=source, page=chunk.page_start, chunk_id=chunk.chunk_id,
                    source_start=match.start(), source_end=match.end()))
        return found

    @staticmethod
    def _evidence_sentence(content: str, position: int) -> str:
        start = max(content.rfind(mark, 0, position) for mark in ("。", "；", ";", "\n")) + 1
        ends = [index for mark in ("。", "；", ";", "\n") if (index := content.find(mark, position)) >= 0]
        end = min(ends) + 1 if ends else len(content)
        return content[start:end].strip()

    def resolve_provider(self, name: str) -> ProviderConfig:
        normalized = name.strip().lower()
        runtime = get_runtime_provider(normalized)
        if runtime:
            return ProviderConfig(normalized, runtime.base_url, runtime.api_key, runtime.model, runtime.temperature, runtime.prompt_template)
        if normalized == "deepseek":
            values = (self.settings.deepseek_base_url, self.settings.deepseek_api_key, self.settings.deepseek_model)
        elif normalized == "qwen":
            values = (self.settings.qwen_base_url, self.settings.qwen_api_key, self.settings.qwen_model)
        else:
            raise LlmConfigurationError("LLM provider 仅支持 deepseek 或 qwen")
        if not values[1]:
            raise LlmConfigurationError(f"未配置 {normalized.upper()}_API_KEY")
        return ProviderConfig(normalized, values[0].rstrip("/"), values[1], values[2], self.settings.llm_temperature, None)

    def _call(self, provider: ProviderConfig, chunk: EntityChunk) -> Any:
        return self._call_batch(provider, [chunk])

    def _call_batch(self, provider: ProviderConfig, chunks: list[EntityChunk]) -> Any:
        source = "\n\n".join(f"### chunkId={chunk.chunk_id} pages={chunk.page_start}-{chunk.page_end}\n{chunk.content}" for chunk in chunks)
        request = {
            "model": provider.model,
            "temperature": min(provider.temperature, 0.1),
            "max_tokens": min(self.settings.llm_max_tokens, 2048),
            "response_format": {"type": "json_object"},
            "messages": [
                {"role": "system", "content": self._system_prompt(provider, SYSTEM_PROMPT)},
                {"role": "user", "content": f"请完整抽取以下 {len(chunks)} 个文本块：\n{source}"},
            ],
        }
        if self._is_siliconflow_qwen3(provider):
            # Entity extraction needs deterministic JSON rather than a long
            # reasoning trace. SiliconFlow supports this switch for Qwen3.
            request["enable_thinking"] = False
        try:
            response = self.client.post(
                self._chat_completions_url(provider.base_url),
                headers={"Authorization": f"Bearer {provider.api_key}"}, json=request,
            )
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
            return self.decode_json(content)
        except (httpx.HTTPError, KeyError, IndexError, TypeError, json.JSONDecodeError) as exception:
            raise LlmExtractionError(f"{provider.name} 实体识别调用失败: {exception}") from exception

    @staticmethod
    def _chat_completions_url(base_url: str) -> str:
        """Accept both an OpenAI-compatible base URL and a full chat endpoint."""
        normalized = base_url.strip().rstrip("/")
        suffix = "/chat/completions"
        return normalized if normalized.endswith(suffix) else f"{normalized}{suffix}"

    @staticmethod
    def _is_siliconflow_qwen3(provider: ProviderConfig) -> bool:
        return "siliconflow.cn" in provider.base_url.lower() and provider.model.lower().startswith("qwen/qwen3")

    @staticmethod
    def _system_prompt(provider: ProviderConfig, built_in: str) -> str:
        return f"{provider.prompt_template}\n\n{built_in}" if provider.prompt_template else built_in

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
            confidence = self._confidence(row.get("confidence"), name, entity_type, source, chunk.content)
            normalized.append(ExtractedEntity(
                entity_name=name, entity_type=entity_type, confidence=confidence,
                source_text=source, page=page, chunk_id=chunk.chunk_id,
                source_start=source_start if source_start >= 0 else None,
                source_end=source_start + len(name) if source_start >= 0 else None,
            ))
        return normalized

    @staticmethod
    def _confidence(raw_value: Any, name: str, entity_type: str, source: str, content: str) -> float:
        try:
            value = float(raw_value)
        except (TypeError, ValueError):
            value = 0.0
        if value > 0:
            return min(value, 1.0)
        return GeologicalEntityExtractor.evidence_confidence(name, entity_type, source, content)

    @staticmethod
    def evidence_confidence(name: str, entity_type: str, source: str, content: str) -> float:
        """Score independently verifiable evidence when an LLM omits confidence.

        The score is intentionally continuous: exact evidence alignment, contextual
        specificity and type-format consistency contribute separately, so unrelated
        entities do not receive one fabricated fallback value.
        """
        normalized_name = GeologicalEntityExtractor._normalized_text(name)
        normalized_source = GeologicalEntityExtractor._normalized_text(source)
        normalized_content = GeologicalEntityExtractor._normalized_text(content)
        score = 0.35
        if normalized_name and normalized_name in normalized_source:
            score += 0.20
        if normalized_source and normalized_source in normalized_content:
            score += 0.12
        if normalized_name and normalized_name in normalized_content:
            score += 0.10
            if normalized_content.count(normalized_name) == 1:
                score += 0.05
        if GeologicalEntityExtractor._type_matches(entity_type, name, source):
            score += 0.10
        context_overhead = max(len(normalized_source) - len(normalized_name), 0)
        score += 0.08 * max(0.0, 1.0 - min(context_overhead, 120) / 120)
        return round(min(max(score, 0.50), 0.98), 2)

    @staticmethod
    def _normalized_text(value: str) -> str:
        return re.sub(r"[\s，,。；;：:]", "", value or "")

    @staticmethod
    def _type_matches(entity_type: str, name: str, source: str) -> bool:
        text = f"{name} {source}"
        rules = {
            "COORDINATE": r"(?:东经|西经|北纬|南纬|\d{2,3}(?:\.\d+)?°)",
            "THICKNESS": r"\d+(?:\.\d+)?\s*(?:m|米)|\d+(?:\.\d+)?\s*[—-]\s*\d+(?:\.\d+)?",
            "GRADE": r"\d+(?:\.\d+)?\s*(?:%|克/吨|g/t)",
            "DIP_ANGLE": r"\d+(?:\.\d+)?\s*°",
            "DIP_DIRECTION": r"(?:北东|北西|南东|南西|正北|正南|正东|正西)",
            "GEOLOGICAL_AGE": r"(?:系|统|纪|世|代)$",
            "LITHOLOGY": r"(?:岩|土|砂|砾)$",
            "STRATUM": r"(?:组|层|段|群)$",
            "FAULT": r"(?:断裂|断层)|^F\d+",
            "PLACE": r"(?:省|市|县|区|镇|乡|村|矿区|矿段|山)$",
        }
        pattern = rules.get(entity_type)
        return bool(pattern and re.search(pattern, text, flags=re.IGNORECASE))
