from fastapi import APIRouter, HTTPException, status

from app.models.knowledge import KnowledgeExtractionRequest, KnowledgeExtractionResponse
from app.services.llm_entities import LlmConfigurationError, LlmExtractionError
from app.services.llm_knowledge import GeologicalKnowledgeExtractor

router = APIRouter(prefix="/knowledge", tags=["attribute and relation extraction"])


@router.post("/extract", response_model=KnowledgeExtractionResponse, summary="抽取地质属性与实体关系")
def extract_knowledge(request: KnowledgeExtractionRequest) -> KnowledgeExtractionResponse:
    try:
        provider, attributes, relations = GeologicalKnowledgeExtractor().extract(request.chunks, request.provider)
        return KnowledgeExtractionResponse(provider=provider.name, model=provider.model, attributes=attributes, relations=relations)
    except LlmConfigurationError as exception:
        raise HTTPException(status_code=status.HTTP_503_SERVICE_UNAVAILABLE, detail=str(exception)) from exception
    except LlmExtractionError as exception:
        raise HTTPException(status_code=status.HTTP_502_BAD_GATEWAY, detail=str(exception)) from exception
