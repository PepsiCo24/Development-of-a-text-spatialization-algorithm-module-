from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel, Field, HttpUrl

from app.services.runtime_config import RuntimeProvider, set_runtime_provider

router = APIRouter(prefix="/config", tags=["runtime LLM configuration"])


class ProviderUpdate(BaseModel):
    provider: Literal["deepseek", "qwen"]
    base_url: HttpUrl
    api_key: str = Field(min_length=1)
    model: str = Field(min_length=1, max_length=128)
    temperature: float = Field(ge=0, le=2)
    prompt_template: str | None = Field(default=None, max_length=8000)


@router.put("/provider")
def update_provider(request: ProviderUpdate):
    set_runtime_provider(request.provider, RuntimeProvider(str(request.base_url).rstrip("/"), request.api_key, request.model, request.temperature, request.prompt_template))
    return {"provider":request.provider,"model":request.model,"applied":True}
