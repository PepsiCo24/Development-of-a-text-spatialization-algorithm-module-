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
