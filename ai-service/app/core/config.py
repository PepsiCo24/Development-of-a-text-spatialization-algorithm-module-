from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "基于填图对象智能识别的文本空间化算法模块 AI 服务"
    app_version: str = "1.0.0"
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
    geocoding_enabled: bool = True
    geocoding_base_url: str = "https://nominatim.openstreetmap.org"
    geocoding_user_agent: str = "GeoText-Spatialization/0.6"
    geocoding_timeout_seconds: float = 20.0
    geocoding_min_interval_seconds: float = 1.0
    embedding_model: str = "BAAI/bge-m3"
    qdrant_url: str = "http://localhost:6333"
    qdrant_api_key: str | None = None
    qdrant_collection: str = "geotext_chunks"
    neo4j_uri: str = "bolt://localhost:7687"
    neo4j_username: str = "neo4j"
    neo4j_password: str = "change-me"
    neo4j_database: str = "neo4j"

    model_config = SettingsConfigDict(env_file=".env", env_file_encoding="utf-8", extra="ignore")


@lru_cache
def get_settings() -> Settings:
    return Settings()
