import json

import httpx

from app.core.config import Settings
from app.models.entity import EntityChunk
from app.services.llm_entities import GeologicalEntityExtractor


def response_handler(request: httpx.Request) -> httpx.Response:
    assert request.url.path.endswith("/chat/completions")
    assert request.headers["authorization"] == "Bearer test-key"
    body = json.loads(request.content)
    assert body["response_format"] == {"type": "json_object"}
    return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({
        "entities": [
            {"entityName": "燕山期花岗岩体", "entityType": "ROCK_BODY", "confidence": 0.96,
             "sourceText": "燕山期花岗岩体侵入寒武系灰岩。", "page": 3},
            {"entityName": "寒武系", "entityType": "STRATUM", "confidence": 0.91,
             "sourceText": "燕山期花岗岩体侵入寒武系灰岩。", "page": 3},
        ]
    }, ensure_ascii=False)}}]})


def test_deepseek_extraction_keeps_evidence_offsets():
    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://deepseek.test/v1")
    client = httpx.Client(transport=httpx.MockTransport(response_handler))
    extractor = GeologicalEntityExtractor(settings, client)
    source = "调查显示，燕山期花岗岩体侵入寒武系灰岩。"

    provider, entities = extractor.extract([EntityChunk(chunk_id=8, content=source, page_start=3, page_end=3)], "deepseek")

    assert provider.name == "deepseek"
    assert len(entities) == 2
    assert entities[0].source_start == source.index("燕山期花岗岩体")
    assert entities[0].source_end == source.index("燕山期花岗岩体") + len("燕山期花岗岩体")
    assert entities[0].chunk_id == 8


def test_qwen_uses_dashscope_compatible_provider():
    settings = Settings(qwen_api_key="test-key", qwen_base_url="https://qwen.test/compatible-mode/v1")
    client = httpx.Client(transport=httpx.MockTransport(response_handler))
    provider, entities = GeologicalEntityExtractor(settings, client).extract(
        [EntityChunk(chunk_id=1, content="燕山期花岗岩体侵入寒武系灰岩。", page_start=3, page_end=3)], "qwen"
    )
    assert provider.name == "qwen"
    assert provider.model == "qwen-plus"
    assert entities[1].entity_type == "STRATUM"


def test_full_openai_compatible_endpoint_is_not_duplicated():
    seen: dict[str, str] = {}

    def siliconflow_handler(request: httpx.Request) -> httpx.Response:
        seen["url"] = str(request.url)
        return response_handler(request)

    settings = Settings(
        qwen_api_key="test-key",
        qwen_base_url="https://api.siliconflow.test/v1/chat/completions",
    )
    client = httpx.Client(transport=httpx.MockTransport(siliconflow_handler))
    GeologicalEntityExtractor(settings, client).extract(
        [EntityChunk(chunk_id=1, content="test", page_start=3, page_end=3)],
        "qwen",
    )

    assert seen["url"] == "https://api.siliconflow.test/v1/chat/completions"


def test_siliconflow_qwen3_disables_thinking_for_structured_extraction():
    seen: dict[str, object] = {}

    def siliconflow_handler(request: httpx.Request) -> httpx.Response:
        seen.update(json.loads(request.content))
        return response_handler(request)

    settings = Settings(
        qwen_api_key="test-key",
        qwen_base_url="https://api.siliconflow.cn/v1",
        qwen_model="Qwen/Qwen3.5-4B",
    )
    client = httpx.Client(transport=httpx.MockTransport(siliconflow_handler))
    GeologicalEntityExtractor(settings, client).extract(
        [EntityChunk(chunk_id=1, content="test", page_start=3, page_end=3)],
        "qwen",
    )

    assert seen["enable_thinking"] is False


def test_default_client_ignores_environment_proxy_for_llm_requests():
    extractor = GeologicalEntityExtractor(Settings())

    assert extractor.client._trust_env is False


def test_zero_model_confidence_uses_evidence_based_fallback():
    chunk = EntityChunk(chunk_id=3, content="燕山期花岗岩体侵入灰岩。", page_start=1, page_end=1)
    entities = GeologicalEntityExtractor(Settings())._normalize(
        {"entities": [{
            "entityName": "燕山期花岗岩体",
            "entityType": "ROCK_BODY",
            "confidence": 0,
            "sourceText": "燕山期花岗岩体侵入灰岩。",
            "page": 1,
        }]},
        chunk,
    )

    assert entities[0].confidence == 0.90


def test_evidence_confidence_varies_with_alignment_and_type_support():
    exact = GeologicalEntityExtractor.evidence_confidence(
        "68°", "DIP_ANGLE", "倾角68°", "F1断裂倾角68°。",
    )
    weak = GeologicalEntityExtractor.evidence_confidence(
        "ZK001", "ROCK_BODY", "钻孔编号ZK001", "大冶矿区钻孔编号ZK001。",
    )

    assert exact > weak
    assert exact != weak


def test_short_chunks_are_batched_into_one_model_request():
    requests = 0

    def batched_handler(request: httpx.Request) -> httpx.Response:
        nonlocal requests
        requests += 1
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"entities":[]}'}}]})

    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://deepseek.test/v1")
    chunks = [EntityChunk(chunk_id=index, content=f"第{index}段地质资料。", page_start=index, page_end=index) for index in (1, 2, 3)]

    GeologicalEntityExtractor(settings, httpx.Client(transport=httpx.MockTransport(batched_handler))).extract(chunks, "deepseek")

    assert requests == 1


def test_malformed_model_json_falls_back_to_verifiable_evidence():
    def malformed_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": [{"message": {"content": '{"entities":[{"entityName":"F1断裂"}'}}]})

    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://deepseek.test/v1")
    chunk = EntityChunk(chunk_id=5, content="F1断裂倾向南东，倾角68°，控制赋存于矽卡岩化带的Ⅰ号铜铁矿体。", page_start=3, page_end=3)

    _, entities = GeologicalEntityExtractor(settings, httpx.Client(transport=httpx.MockTransport(malformed_handler))).extract([chunk], "deepseek")

    assert {entity.entity_type for entity in entities} >= {"FAULT", "DIP_DIRECTION", "DIP_ANGLE", "ORE_BODY", "MINERALIZATION_ZONE"}
