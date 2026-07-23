from fastapi import APIRouter, HTTPException, Query, status

from app.models.graph import GraphSyncRequest, GraphSyncResponse, GraphView, QuestionRequest, QuestionResponse
from app.services.graph_store import Neo4jGraphStore
from app.services.llm_entities import LlmConfigurationError, LlmExtractionError
from app.services.rag import GeologicalRagService
from app.services.vector_store import QdrantVectorStore

router = APIRouter(tags=["knowledge graph and RAG"])


@router.post("/graph/sync", response_model=GraphSyncResponse)
def sync_graph(request: GraphSyncRequest) -> GraphSyncResponse:
    try:
        node_count, relation_count = Neo4jGraphStore().sync(request.document_id, request.nodes, request.relations)
        vector_count = QdrantVectorStore().index(request.document_id, request.chunks)
        return GraphSyncResponse(node_count=node_count, relation_count=relation_count, vector_count=vector_count)
    except Exception as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=f"图谱或向量服务不可用: {exception}") from exception


@router.get("/graph/nodes", response_model=GraphView)
def graph_nodes(query: str | None = None, limit: int = Query(default=100, ge=1, le=300), document_id: int | None = None):
    try:
        return Neo4jGraphStore().nodes(query, limit, document_id)
    except Exception as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=f"图谱服务不可用: {exception}") from exception


@router.get("/graph/expand/{entity_id}", response_model=GraphView)
def expand_graph(entity_id: int, depth: int = Query(default=1, ge=1, le=3)):
    try:
        return Neo4jGraphStore().expand(entity_id, depth)
    except Exception as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=f"图谱服务不可用: {exception}") from exception


@router.get("/graph/path", response_model=GraphView)
def graph_path(source_id: int, target_id: int):
    try:
        return Neo4jGraphStore().path(source_id, target_id)
    except Exception as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=f"图谱服务不可用: {exception}") from exception


@router.post("/qa/ask", response_model=QuestionResponse)
def ask_question(request: QuestionRequest):
    try:
        return GeologicalRagService().ask(request.question, request.provider, request.limit)
    except LlmConfigurationError as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception
    except LlmExtractionError as exception:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exception)) from exception
    except Exception as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=f"检索服务不可用: {exception}") from exception
