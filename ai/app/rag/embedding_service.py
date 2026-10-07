# SPDX-License-Identifier: Apache-2.0
from functools import lru_cache

from sentence_transformers import SentenceTransformer

from app.core.config import get_settings


@lru_cache(maxsize=2)
def _load_model(name: str):
    return SentenceTransformer(name)


class EmbeddingService:
    def __init__(self, model_name: str | None = None):
        settings = get_settings()
        self.model = _load_model(
            model_name or settings.embedding_model,
        )

    def embed_text(self, text: str) -> list[float]:
        vector = self.model.encode(
            text,
            normalize_embeddings=True,
        )
        return vector.tolist()

    def embed_texts(
        self,
        texts: list[str],
    ) -> list[list[float]]:
        vectors = self.model.encode(
            texts,
            normalize_embeddings=True,
        )
        return vectors.tolist()
