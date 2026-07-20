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
