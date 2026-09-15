"""Optional Ollama wording with an extractive evidence gate."""

from httpx import HTTPError
from langchain_ollama import ChatOllama
from ollama import ResponseError

from app.core.config import get_settings
from app.graph.prompts.recommendation_prompt import build_grounded_excerpt_prompt


class GroundedRecommendationWriter:
    def __init__(self) -> None:
        settings = get_settings()
        self._llm = ChatOllama(
            model=settings.ollama_model,
            base_url=settings.ollama_base_url,
            temperature=0,
            client_kwargs={"timeout": settings.llm_timeout_seconds},
        )

    def write(self, evidence: str) -> tuple[str, str]:
        """Return only a verbatim evidence excerpt; otherwise deterministic fallback."""

        if not evidence.strip():
            return "", "no_evidence"
        prompt = build_grounded_excerpt_prompt(evidence)
        try:
            draft = self._llm.invoke(prompt).content
            if isinstance(draft, str) and draft.strip() and draft.strip() in evidence:
                return draft.strip(), "ollama_grounded_excerpt"
        except (HTTPError, ResponseError, TimeoutError, ConnectionError):
            pass
        return evidence, "evidence_fallback"
