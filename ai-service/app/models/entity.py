from typing import Literal

from pydantic import BaseModel, Field, model_validator


EntityType = Literal[
    "STRATUM", "LITHOLOGY", "ROCK_BODY", "FAULT", "MINERAL", "ORE_BODY",
    "MINERALIZATION_ZONE", "GEOLOGICAL_AGE", "PLACE", "COORDINATE", "GRADE",
    "THICKNESS", "DIP_DIRECTION", "DIP_ANGLE",
]


class EntityChunk(BaseModel):
    chunk_id: int
    content: str = Field(min_length=1, max_length=20000)
    page_start: int = Field(ge=1)
    page_end: int = Field(ge=1)


class EntityExtractionRequest(BaseModel):
    document_id: int
    provider: Literal["deepseek", "qwen"] | None = None
    chunks: list[EntityChunk] = Field(min_length=1, max_length=200)


class ExtractedEntity(BaseModel):
    entity_name: str = Field(min_length=1, max_length=255)
    entity_type: EntityType
    confidence: float = Field(ge=0, le=1)
    source_text: str = Field(min_length=1, max_length=2000)
    page: int = Field(ge=1)
    chunk_id: int
    source_start: int | None = Field(default=None, ge=0)
    source_end: int | None = Field(default=None, ge=0)

    @model_validator(mode="after")
    def validate_offsets(self):
        if self.source_start is not None and self.source_end is not None and self.source_end <= self.source_start:
            raise ValueError("source_end must be greater than source_start")
        return self


class EntityExtractionResponse(BaseModel):
    provider: str
    model: str
    entities: list[ExtractedEntity]
