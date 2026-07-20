from datetime import UTC, datetime

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(tags=["system"])


class HealthResponse(BaseModel):
    service: str
    status: str
    phase: int
    timestamp: datetime


@router.get("/health", response_model=HealthResponse, summary="AI 服务健康检查")
def health() -> HealthResponse:
    return HealthResponse(
        service="geotext-ai-service",
        status="UP",
        phase=1,
        timestamp=datetime.now(UTC),
    )

