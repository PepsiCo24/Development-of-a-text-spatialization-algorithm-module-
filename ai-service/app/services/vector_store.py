from functools import lru_cache
from typing import Any

import httpx

from app.core.config import Settings, get_settings
from app.models.graph import VectorChunk


class EmbeddingService:
    def __init__(self, settings: Settings | None = None) -> None:
        self.settings = settings or get_settings()
        self._model = None

    def encode(self, texts: list[str]) -> list[list[float]]:
        if self._model is None:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self.settings.embedding_model)
        return self._model.encode(texts, normalize_embeddings=True).tolist()


class QdrantVectorStore:
    def __init__(self, settings: Settings | None = None, client: httpx.Client | None = None, embedder: EmbeddingService | None = None) -> None:
        self.settings = settings or get_settings()
        headers = {"api-key": self.settings.qdrant_api_key} if self.settings.qdrant_api_key else None
        self.client = client or httpx.Client(base_url=self.settings.qdrant_url, headers=headers, timeout=60)
        self.embedder = embedder or get_embedder()

    def index(self, document_id: int, chunks: list[VectorChunk]) -> int:
        if not chunks:
            return 0
        vectors = self.embedder.encode([chunk.content for chunk in chunks])
        self._ensure_collection(len(vectors[0]))
        self.client.post(f"/collections/{self.settings.qdrant_collection}/points/delete", json={"filter":{"must":[{"key":"document_id","match":{"value":document_id}}]}}).raise_for_status()
        points = [{"id": chunk.chunk_id, "vector": vector, "payload": chunk.model_dump()} for chunk, vector in zip(chunks, vectors)]
        self.client.put(f"/collections/{self.settings.qdrant_collection}/points", params={"wait":"true"}, json={"points":points}).raise_for_status()
        return len(points)

    def search(self, question: str, limit: int) -> list[dict[str, Any]]:
        vector = self.embedder.encode([question])[0]
        response = self.client.post(f"/collections/{self.settings.qdrant_collection}/points/search", json={"vector":vector,"limit":limit,"with_payload":True})
        response.raise_for_status()
        return [{**row.get("payload", {}), "score": row.get("score", 0)} for row in response.json().get("result", [])]

    def _ensure_collection(self, vector_size: int) -> None:
        response = self.client.get(f"/collections/{self.settings.qdrant_collection}")
        if response.status_code == 404:
            self.client.put(f"/collections/{self.settings.qdrant_collection}", json={"vectors":{"size":vector_size,"distance":"Cosine"}}).raise_for_status()
        else:
            response.raise_for_status()


@lru_cache
def get_embedder() -> EmbeddingService:
    return EmbeddingService()
