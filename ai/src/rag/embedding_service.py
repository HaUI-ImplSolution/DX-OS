from __future__ import annotations
from functools import lru_cache

from src.core.config import get_settings


@lru_cache(maxsize=2)
def _load_model(name: str):
    try:
        from sentence_transformers import SentenceTransformer
        return SentenceTransformer(name)
    except Exception:
        return None


class EmbeddingService:
    def __init__(self, model_name: str | None = None):
        settings = get_settings()
        self.model = _load_model(
            model_name or getattr(settings, "embedding_model", "all-MiniLM-L6-v2"),
        )

    def embed_text(self, text: str) -> list[float]:
        if self.model is None:
            return [0.0] * 384
        vector = self.model.encode(
            text,
            normalize_embeddings=True,
        )
        return vector.tolist()

    def embed_texts(
        self,
        texts: list[str],
    ) -> list[list[float]]:
        if self.model is None:
            return [[0.0] * 384 for _ in texts]
        vectors = self.model.encode(
            texts,
            normalize_embeddings=True,
        )
        return vectors.tolist()
