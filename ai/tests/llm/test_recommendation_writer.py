from types import SimpleNamespace

from app.llm.recommendation_writer import GroundedRecommendationWriter


def writer_with(draft):
    writer = object.__new__(GroundedRecommendationWriter)
    writer._llm = SimpleNamespace(invoke=lambda prompt: SimpleNamespace(content=draft))
    return writer


def test_writer_accepts_only_verbatim_evidence():
    evidence = "Remove affected leaves and clean tools."
    assert writer_with(evidence).write(evidence) == (
        evidence,
        "ollama_grounded_excerpt",
    )
    text, mode = writer_with("Use INVENTED CHEMICAL 3 L/ha").write(evidence)
    assert text == evidence
    assert mode == "evidence_fallback"


def test_writer_does_not_call_llm_without_evidence():
    writer = object.__new__(GroundedRecommendationWriter)
    writer._llm = SimpleNamespace(
        invoke=lambda prompt: (_ for _ in ()).throw(AssertionError)
    )
    assert writer.write("") == ("", "no_evidence")
