from typing import Literal

from pydantic import BaseModel, Field


AttributeType = Literal["AGE", "THICKNESS", "SCALE", "GRADE", "LITHOLOGY"]
RelationType = Literal["LOCATED_IN", "OCCURS_IN", "INTRUDES", "CONTACTS", "CONTROLS", "CONTAINS"]


class KnowledgeEntity(BaseModel):
    entity_id: int
    entity_name: str
    entity_type: str


class KnowledgeChunk(BaseModel):
    chunk_id: int
    content: str = Field(min_length=1, max_length=20000)
    page_start: int = Field(ge=1)
    page_end: int = Field(ge=1)
    entities: list[KnowledgeEntity]


class KnowledgeExtractionRequest(BaseModel):
    document_id: int
    provider: Literal["deepseek", "qwen"] | None = None
    chunks: list[KnowledgeChunk] = Field(min_length=1, max_length=200)


class ExtractedAttribute(BaseModel):
    entity_id: int
    attribute_type: AttributeType
    original_value: str = Field(min_length=1, max_length=500)
    confidence: float = Field(ge=0, le=1)
    source_text: str = Field(min_length=1, max_length=2000)
    page: int = Field(ge=1)


class ExtractedRelation(BaseModel):
    source_entity_id: int
    target_entity_id: int
    relation_type: RelationType
    confidence: float = Field(ge=0, le=1)
    source_text: str = Field(min_length=1, max_length=2000)
    page: int = Field(ge=1)


class KnowledgeExtractionResponse(BaseModel):
    provider: str
    model: str
    attributes: list[ExtractedAttribute]
    relations: list[ExtractedRelation]
