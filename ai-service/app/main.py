from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.health import router as health_router
from app.api.documents import router as documents_router
from app.api.entities import router as entities_router
from app.api.knowledge import router as knowledge_router
from app.api.spatial import router as spatial_router
from app.core.config import get_settings

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version=settings.app_version,
    description="地质文档解析、OCR、知识抽取与向量检索服务",
    docs_url="/docs",
    redoc_url="/redoc",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=settings.backend_cors_origins,
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(health_router, prefix="/api/v1")
app.include_router(documents_router, prefix="/api/v1")
app.include_router(entities_router, prefix="/api/v1")
app.include_router(knowledge_router, prefix="/api/v1")
app.include_router(spatial_router, prefix="/api/v1")


@app.get("/", include_in_schema=False)
def root() -> dict[str, str]:
    return {"service": settings.app_name, "docs": "/docs"}
