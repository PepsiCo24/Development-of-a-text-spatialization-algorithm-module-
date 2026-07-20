from pydantic import BaseModel, Field


class DocumentChunk(BaseModel):
    chunk_index: int = Field(ge=0)
    chapter_title: str | None = None
    content: str = Field(min_length=1)
    page_start: int = Field(ge=1)
    page_end: int = Field(ge=1)
    char_count: int = Field(ge=1)


class ParseResponse(BaseModel):
    filename: str
    document_type: str
    page_count: int = Field(ge=1)
    chunks: list[DocumentChunk]
    warnings: list[str] = Field(default_factory=list)
