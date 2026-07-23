import json

import httpx

from app.core.config import Settings
from app.models.knowledge import KnowledgeChunk, KnowledgeEntity
from app.services.llm_knowledge import GeologicalKnowledgeExtractor


def handler(request: httpx.Request) -> httpx.Response:
    payload = json.loads(request.content)
    assert payload["response_format"] == {"type": "json_object"}
    return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({
        "attributes": [{"entityId": 10, "attributeType": "AGE", "value": "燕山期", "confidence": .94,
                        "sourceText": "燕山期花岗岩体侵入寒武系灰岩。", "page": 2}],
        "relations": [{"sourceEntityId": 10, "targetEntityId": 11, "relationType": "INTRUDES", "confidence": .97,
                       "sourceText": "燕山期花岗岩体侵入寒武系灰岩。", "page": 2},
                      {"sourceEntityId": 10, "targetEntityId": 999, "relationType": "INTRUDES", "confidence": 1,
                       "sourceText": "无效实体应被丢弃", "page": 2}],
    }, ensure_ascii=False)}}]})


def test_extracts_attributes_and_relations_with_known_entity_ids():
    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://deepseek.test/v1")
    client = httpx.Client(transport=httpx.MockTransport(handler))
    chunk = KnowledgeChunk(chunk_id=3, content="燕山期花岗岩体侵入寒武系灰岩。", page_start=2, page_end=2, entities=[
        KnowledgeEntity(entity_id=10, entity_name="燕山期花岗岩体", entity_type="ROCK_BODY"),
        KnowledgeEntity(entity_id=11, entity_name="寒武系", entity_type="STRATUM"),
    ])

    provider, attributes, relations = GeologicalKnowledgeExtractor(settings, client).extract([chunk], "deepseek")

    assert provider.model == "deepseek-chat"
    assert attributes[0].attribute_type == "AGE"
    assert attributes[0].original_value == "燕山期"
    assert len(relations) == 1
    assert relations[0].relation_type == "INTRUDES"


def test_falls_back_to_evidence_rules_when_remote_model_is_unavailable():
    def unavailable(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectTimeout("offline", request=request)

    settings = Settings(qwen_api_key="test-key", qwen_base_url="https://api.siliconflow.cn/v1")
    client = httpx.Client(transport=httpx.MockTransport(unavailable))
    chunk = KnowledgeChunk(
        chunk_id=7,
        content="38.20—126.40 m 为下三叠统大冶组灰岩。闪长玢岩侵入大冶组灰岩。",
        page_start=1,
        page_end=1,
        entities=[
            KnowledgeEntity(entity_id=1, entity_name="38.20—126.40 m", entity_type="THICKNESS"),
            KnowledgeEntity(entity_id=2, entity_name="下三叠统", entity_type="GEOLOGICAL_AGE"),
            KnowledgeEntity(entity_id=3, entity_name="大冶组", entity_type="STRATUM"),
            KnowledgeEntity(entity_id=4, entity_name="灰岩", entity_type="LITHOLOGY"),
            KnowledgeEntity(entity_id=5, entity_name="闪长玢岩", entity_type="LITHOLOGY"),
        ],
    )

    _, attributes, relations = GeologicalKnowledgeExtractor(settings, client).extract([chunk], "qwen")

    assert {item.attribute_type for item in attributes} == {"THICKNESS", "AGE", "LITHOLOGY"}
    assert any(item.relation_type == "INTRUDES" for item in relations)


def test_uniform_model_attribute_confidence_is_recalibrated_from_evidence():
    def uniform_handler(request: httpx.Request) -> httpx.Response:
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps({
            "attributes": [
                {"entityId": 1, "attributeType": "THICKNESS", "value": "38.20—126.40 m", "confidence": .9, "sourceText": "38.20—126.40 m 为灰岩。", "page": 1},
                {"entityId": 2, "attributeType": "LITHOLOGY", "value": "灰岩", "confidence": .9, "sourceText": "38.20—126.40 m 为灰岩。", "page": 1},
            ], "relations": [],
        }, ensure_ascii=False)}}]})

    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://deepseek.test/v1")
    chunk = KnowledgeChunk(chunk_id=1, content="38.20—126.40 m 为灰岩。", page_start=1, page_end=1, entities=[
        KnowledgeEntity(entity_id=1, entity_name="38.20—126.40 m", entity_type="THICKNESS"),
        KnowledgeEntity(entity_id=2, entity_name="灰岩", entity_type="LITHOLOGY"),
    ])

    _, attributes, _ = GeologicalKnowledgeExtractor(settings, httpx.Client(transport=httpx.MockTransport(uniform_handler))).extract([chunk], "deepseek")

    assert len({item.confidence for item in attributes}) > 1
