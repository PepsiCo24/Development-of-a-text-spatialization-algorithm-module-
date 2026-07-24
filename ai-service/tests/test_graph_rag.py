import json

import httpx
import pytest
from pydantic import ValidationError

from app.core.config import Settings
from app.models.graph import GraphNode, GraphRelation, VectorChunk
from app.services.graph_store import Neo4jGraphStore
from app.services.llm_entities import GeologicalEntityExtractor
from app.services.rag import GeologicalRagService
from app.services.runtime_config import RuntimeProvider, set_runtime_provider
from app.services.vector_store import EmbeddingService, QdrantVectorStore


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


def test_runtime_provider_configuration_overrides_environment():
    set_runtime_provider("qwen", RuntimeProvider("https://runtime.test/v1", "runtime-key", "qwen-custom", 0.4, "严格引用证据"))
    provider = GeologicalEntityExtractor(Settings()).resolve_provider("qwen")
    assert provider.base_url == "https://runtime.test/v1"
    assert provider.model == "qwen-custom"
    assert provider.temperature == 0.4
    assert provider.prompt_template == "严格引用证据"


def test_dependency_free_embedding_is_deterministic_and_normalized():
    embedder = EmbeddingService(Settings())
    embedder._model = False
    first, second, different = embedder.encode(["大冶矿区钻孔", "大冶矿区钻孔", "铜绿山矿段"])

    assert len(first) == 1024
    assert first == second
    assert first != different
    assert sum(value * value for value in first) == pytest.approx(1.0)


def test_rag_uses_source_evidence_when_remote_model_is_unavailable():
    def handler(request: httpx.Request) -> httpx.Response:
        raise httpx.ConnectError("model temporarily unavailable", request=request)

    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://llm.test/v1")
    client = httpx.Client(transport=httpx.MockTransport(handler))
    result = GeologicalRagService(settings, client, StubVectors(), StubGraph()).ask("矿体受什么控制？", "deepseek", 5)

    assert result.answer.startswith("根据《")
    assert result.sources
    assert result.related_entities


def test_rag_stream_emits_status_metadata_and_model_deltas():
    def handler(request: httpx.Request) -> httpx.Response:
        body = json.loads(request.content)
        assert body["stream"] is True
        assert body["max_tokens"] == 1024
        stream = (
            'data: {"choices":[{"delta":{"content":"一号矿体"}}]}\n\n'
            'data: {"choices":[{"delta":{"content":"受断裂控制。"}}]}\n\n'
            'data: [DONE]\n\n'
        )
        return httpx.Response(200, text=stream, headers={"content-type": "text/event-stream"})

    settings = Settings(deepseek_api_key="test-key", deepseek_base_url="https://llm.test/v1")
    client = httpx.Client(transport=httpx.MockTransport(handler))
    events = list(GeologicalRagService(settings, client, StubVectors(), StubGraph()).stream(
        "矿体受什么控制？", "deepseek", 5,
    ))

    assert [event for event, _ in events] == ["status", "metadata", "status", "draft", "reset", "delta", "delta", "complete"]
    assert "".join(payload["content"] for event, payload in events if event == "delta") == "一号矿体受断裂控制。"
    metadata = next(payload for event, payload in events if event == "metadata")
    assert metadata["sources"][0]["documentName"] == "调查报告"


def test_rag_retrieval_removes_duplicate_chunks_and_entities():
    class DuplicateVectors:
        def search(self, question, limit):
            row = StubVectors().search(question, limit)[0]
            return [row, {**row, "score": 0.8}]

    class DuplicateGraph:
        def context_for_documents(self, document_ids):
            row = StubGraph().context_for_documents(document_ids)[0]
            return [row, dict(row)]

    service = GeologicalRagService(
        Settings(deepseek_api_key="test-key"),
        httpx.Client(transport=httpx.MockTransport(lambda request: httpx.Response(200))),
        DuplicateVectors(),
        DuplicateGraph(),
    )

    sources, entities = service._retrieve("矿体受什么控制？", 5)

    assert len(sources) == 1
    assert len(entities) == 1
