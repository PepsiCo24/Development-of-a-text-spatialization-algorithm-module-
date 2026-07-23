from functools import lru_cache
import hashlib
import math
import re
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
            try:
                from sentence_transformers import SentenceTransformer
                self._model = SentenceTransformer(self.settings.embedding_model)
            except (ImportError, OSError):
                self._model = False
        if self._model:
            return self._model.encode(texts, normalize_embeddings=True).tolist()
        return [self._hash_embedding(text) for text in texts]

    @staticmethod
    def _hash_embedding(text: str, dimensions: int = 1024) -> list[float]:
        """Dependency-free Chinese character/ngram embedding for local demo retrieval."""
        normalized = re.sub(r"\s+", "", text.lower())
        tokens = list(normalized) + [normalized[index:index + 2] for index in range(max(0, len(normalized) - 1))]
        vector = [0.0] * dimensions
        for token in tokens:
            digest = hashlib.blake2b(token.encode("utf-8"), digest_size=8).digest()
            value = int.from_bytes(digest, "big")
            index = value % dimensions
            sign = 1.0 if value & 1 else -1.0
            vector[index] += sign * (1.5 if len(token) > 1 else 1.0)
        norm = math.sqrt(sum(value * value for value in vector)) or 1.0
        return [value / norm for value in vector]


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
