import json

import httpx
import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.models.graph import GraphNode, GraphRelation, VectorChunk
from app.services.graph_store import Neo4jGraphStore
from app.services.rag import GeologicalRagService
from app.services.vector_store import QdrantVectorStore


class StubEmbedder:
    def encode(self, texts):
        return [[0.1, 0.2, 0.3] for _ in texts]


def test_qdrant_indexes_and_searches_document_chunks():
    calls = []

    def handler(request: httpx.Request) -> httpx.Response:
        calls.append((request.method, request.url.path))
        if request.method == "GET":
            return httpx.Response(404)
        if request.url.path.endswith("/points/search"):
            return httpx.Response(200, json={"result":[{"score":0.93,"payload":{"chunk_id":8,"document_id":3,"document_name":"调查报告","content":"矿体受断裂控制。","page_start":2,"page_end":2}}]})
        return httpx.Response(200, json={"result":{}})

    settings = Settings(qdrant_url="https://qdrant.test", qdrant_collection="chunks")
    client = httpx.Client(base_url=settings.qdrant_url, transport=httpx.MockTransport(handler))
    store = QdrantVectorStore(settings, client, StubEmbedder())
    chunk = VectorChunk(chunk_id=8,document_id=3,document_name="调查报告",content="矿体受断裂控制。",page_start=2,page_end=2)

    assert store.index(3, [chunk]) == 1
    assert store.search("矿体受什么控制？", 5)[0]["score"] == 0.93
    assert ("PUT", "/collections/chunks") in calls
    assert ("PUT", "/collections/chunks/points") in calls


class StubVectors:
    def search(self, question, limit):
        return [{"chunk_id":8,"document_id":3,"document_name":"调查报告","content":"矿体受北东向断裂控制。","page_start":2,"page_end":2,"score":0.95}]


class StubGraph:
    def context_for_documents(self, document_ids):
        assert document_ids == [3]
        return [{"id":12,"name":"一号矿体","nodeType":"ORE_BODY","page":2,"longitude":114.9,"latitude":30.1,"documentId":3,"sourceText":"矿体受北东向断裂控制。"}]


def test_rag_answer_keeps_entities_locations_and_source_paragraphs():
    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert "矿体受北东向断裂控制" in body["messages"][1]["content"]
        return httpx.Response(200, json={"choices":[{"message":{"content":"{\"answer\":\"一号矿体受北东向断裂控制。\"}"}}]})

    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://llm.test/v1")
    client = httpx.Client(transport=httpx.MockTransport(handler))
    result = GeologicalRagService(settings, client, StubVectors(), StubGraph()).ask("矿体受什么控制？", "deepseek", 5)

    assert result.related_entities[0]["name"] == "一号矿体"
    assert result.spatial_locations[0]["longitude"] == 114.9
    assert result.sources[0]["documentName"] == "调查报告"


def test_graph_contract_rejects_unsupported_node_type():
    with pytest.raises(ValidationError):
        GraphNode(entity_id=1,document_id=1,name="花岗岩",node_type="LITHOLOGY",source_text="花岗岩出露。",page=1)


class FakeResult:
    def consume(self):
        return None


class FakeSession:
    def __init__(self, calls):
        self.calls = calls

    def __enter__(self):
        return self

    def __exit__(self, *args):
        return None

    def run(self, query, **parameters):
        self.calls.append((query, parameters))
        return FakeResult()


class FakeDriver:
    def __init__(self):
        self.calls = []

    def session(self, database):
        assert database == "neo4j"
        return FakeSession(self.calls)


def test_neo4j_sync_uses_whitelisted_relationship_type():
    driver = FakeDriver()
    node_a = GraphNode(entity_id=1,document_id=2,name="北东向断裂",node_type="STRUCTURE",source_text="断裂控制矿体。",page=3)
    node_b = GraphNode(entity_id=2,document_id=2,name="一号矿体",node_type="ORE_BODY",source_text="断裂控制矿体。",page=3)
    relation = GraphRelation(source_entity_id=1,target_entity_id=2,relation_type="CONTROLS",confidence=.95,source_text="断裂控制矿体。",page=3)

    counts = Neo4jGraphStore(Settings(), driver).sync(2, [node_a, node_b], [relation])

    assert counts == (2, 1)
    assert any("MERGE (s)-[r:CONTROLS" in query for query, _ in driver.calls)
