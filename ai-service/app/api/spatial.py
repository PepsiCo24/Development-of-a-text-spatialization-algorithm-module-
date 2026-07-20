from fastapi import APIRouter,HTTPException,status
from app.models.spatial import SpatialExtractionRequest,SpatialExtractionResponse
from app.services.llm_entities import LlmConfigurationError,LlmExtractionError
from app.services.llm_spatial import GeologicalSpatialExtractor
router=APIRouter(prefix="/spatial",tags=["text spatialization"])
@router.post("/extract",response_model=SpatialExtractionResponse,summary="抽取并转换地质空间对象")
def extract_spatial(request:SpatialExtractionRequest)->SpatialExtractionResponse:
    try:
        provider,objects,warnings=GeologicalSpatialExtractor().extract(request.chunks,request.provider,request.region_hint)
        return SpatialExtractionResponse(provider=provider.name,model=provider.model,objects=objects,warnings=warnings)
    except LlmConfigurationError as exc:raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE,detail=str(exc))from exc
    except LlmExtractionError as exc:raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY,detail=str(exc))from exc
