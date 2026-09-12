"""Optional Ollama wording with an extractive evidence gate."""

import httpx

from src.core.config import get_settings
from src.graph.prompts.recommendation_prompt import build_grounded_excerpt_prompt


class GroundedRecommendationWriter:
    def __init__(self) -> None:
        settings = get_settings()
        self._model = settings.ollama_model
        self._base_url = settings.ollama_base_url.rstrip("/")
        self._timeout = getattr(settings, "llm_timeout_seconds", 30)

    def write(self, evidence: str) -> tuple[str, str]:
        """Return only a verbatim evidence excerpt; otherwise deterministic fallback."""

        if not evidence.strip():
            return "", "no_evidence"
        prompt = build_grounded_excerpt_prompt(evidence)
        try:
            resp = httpx.post(
                f"{self._base_url}/api/generate",
                json={"model": self._model, "prompt": prompt, "stream": False},
                timeout=float(self._timeout),
            )
            if resp.status_code == 200:
                draft = resp.json().get("response", "")
                if isinstance(draft, str) and draft.strip() and draft.strip() in evidence:
                    return draft.strip(), "ollama_grounded_excerpt"
        except Exception:
            pass
        return evidence, "evidence_fallback"
