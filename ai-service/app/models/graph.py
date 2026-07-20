from typing import Literal

from pydantic import BaseModel, Field

NodeType = Literal["STRATUM", "ROCK_BODY", "STRUCTURE", "ORE_BODY", "MINERAL", "REGION"]
RelationType = Literal["LOCATED_IN", "CONTAINS", "CONTROLS", "INTRUDES"]


class GraphNode(BaseModel):
    entity_id: int
    document_id: int
    name: str = Field(min_length=1, max_length=255)
    node_type: NodeType
    source_text: str = Field(min_length=1, max_length=2000)
    page: int = Field(ge=1)
    longitude: float | None = Field(default=None, ge=-180, le=180)
    latitude: float | None = Field(default=None, ge=-90, le=90)


class GraphRelation(BaseModel):
    source_entity_id: int
    target_entity_id: int
    relation_type: RelationType
    confidence: float = Field(ge=0, le=1)
    source_text: str = Field(min_length=1, max_length=2000)
    page: int = Field(ge=1)


class VectorChunk(BaseModel):
    chunk_id: int
    document_id: int
    document_name: str
    content: str = Field(min_length=1, max_length=20000)
    page_start: int = Field(ge=1)
    page_end: int = Field(ge=1)


class GraphSyncRequest(BaseModel):
    document_id: int
    nodes: list[GraphNode]
    relations: list[GraphRelation]
    chunks: list[VectorChunk]


class GraphSyncResponse(BaseModel):
    node_count: int
    relation_count: int
    vector_count: int


class GraphView(BaseModel):
    nodes: list[dict]
    links: list[dict]


class QuestionRequest(BaseModel):
    question: str = Field(min_length=2, max_length=1000)
    provider: Literal["deepseek", "qwen"] | None = None
    limit: int = Field(default=5, ge=1, le=12)


class QuestionResponse(BaseModel):
    answer: str
    related_entities: list[dict]
    spatial_locations: list[dict]
    sources: list[dict]
    provider: str
    model: str
