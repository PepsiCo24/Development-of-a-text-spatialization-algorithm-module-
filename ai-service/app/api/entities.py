from fastapi import APIRouter, HTTPException, status

from app.models.entity import EntityExtractionRequest, EntityExtractionResponse
from app.services.llm_entities import GeologicalEntityExtractor, LlmConfigurationError, LlmExtractionError

router = APIRouter(prefix="/entities", tags=["geological entity extraction"])


@router.post("/extract", response_model=EntityExtractionResponse, summary="使用 DeepSeek 或 Qwen 识别地质实体")
def extract_entities(request: EntityExtractionRequest) -> EntityExtractionResponse:
    try:
        provider, entities = GeologicalEntityExtractor().extract(request.chunks, request.provider)
        return EntityExtractionResponse(provider=provider.name, model=provider.model, entities=entities)
    except LlmConfigurationError as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception
    except LlmExtractionError as exception:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exception)) from exception
