from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "GeoText AI Service"
    app_version: str = "0.4.0"
    app_env: str = "development"
    ai_service_host: str = "0.0.0.0"
    ai_service_port: int = 8000
    backend_cors_origins: list[str] = ["http://localhost:5173"]
    ocr_language: str = "ch"
    ocr_device: str = "cpu"
    parse_chunk_size: int = 1200
    llm_default_provider: str = "deepseek"
    deepseek_api_key: str | None = None
    deepseek_base_url: str = "https://api.deepseek.com/v1"
    deepseek_model: str = "deepseek-chat"
    qwen_api_key: str | None = None
    qwen_base_url: str = "https://dashscope.aliyuncs.com/compatible-mode/v1"
    qwen_model: str = "qwen-plus"
    llm_temperature: float = 0.1
    llm_timeout_seconds: float = 120.0
    llm_max_tokens: int = 4096

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
