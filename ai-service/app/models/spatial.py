from typing import Any, Literal

from pydantic import BaseModel, Field, model_validator


class SpatialEntity(BaseModel):
    entity_id: int
    entity_name: str
    entity_type: str


class SpatialChunk(BaseModel):
    chunk_id: int
    content: str = Field(min_length=1, max_length=20000)
    page_start: int = Field(ge=1)
    page_end: int = Field(ge=1)
    entities: list[SpatialEntity]


class SpatialExtractionRequest(BaseModel):
    document_id: int
    provider: Literal["deepseek", "qwen"] | None = None
    region_hint: str | None = Field(default=None, max_length=255)
    chunks: list[SpatialChunk] = Field(min_length=1, max_length=200)


class GeoJsonGeometry(BaseModel):
    type: Literal["Point", "LineString", "Polygon"]
    coordinates: Any

    @model_validator(mode="after")
    def validate_coordinates(self):
        def point(value: Any) -> bool:
            return isinstance(value, list) and len(value) >= 2 and all(isinstance(v, (int, float)) for v in value[:2]) and -180 <= value[0] <= 180 and -90 <= value[1] <= 90
        valid = False
        if self.type == "Point":
            valid = point(self.coordinates)
        elif self.type == "LineString":
            valid = isinstance(self.coordinates, list) and len(self.coordinates) >= 2 and all(point(v) for v in self.coordinates)
        elif self.type == "Polygon":
            valid = isinstance(self.coordinates, list) and bool(self.coordinates) and all(
                isinstance(ring, list) and len(ring) >= 4 and all(point(v) for v in ring) and ring[0][:2] == ring[-1][:2]
                for ring in self.coordinates
            )
        if not valid:
            raise ValueError(f"invalid {self.type} coordinates")
        return self


class SpatialObject(BaseModel):
    name: str = Field(min_length=1, max_length=255)
    object_type: Literal["PLACE", "COORDINATE", "MINERAL_POINT", "BOREHOLE", "FAULT", "SURVEY_AREA"]
    entity_id: int | None = None
    chunk_id: int
    geometry: GeoJsonGeometry
    confidence: float = Field(ge=0, le=1)
    source_text: str = Field(min_length=1, max_length=2000)
    page: int = Field(ge=1)
    geocoding_source: str | None = None


class SpatialExtractionResponse(BaseModel):
    provider: str
    model: str
    objects: list[SpatialObject]
    warnings: list[str] = Field(default_factory=list)
