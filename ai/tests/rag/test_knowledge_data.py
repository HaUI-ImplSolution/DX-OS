import json
from pathlib import Path

from app.rag.chunker import chunk_text
from app.rag.ingestion_service import load_disease_knowledge
from app.rag.prompt_builder import build_rag_prompt
from app.rag.rag_service import build_citations
from app.rag.source_registry import load_source_registry

AI_ROOT = Path(__file__).resolve().parents[2]


def test_stage1_knowledge_has_valid_sources_and_unique_codes():
    registry = load_source_registry()
    diseases = load_disease_knowledge()

    assert len(registry) == 41
    assert len(diseases) == 38

    codes = [item["disease_code"] for item in diseases]
    assert len(codes) == len(set(codes))

    referenced_sources = {
        source_id for item in diseases for source_id in item["provenance"]["source_ids"]
    }
    assert referenced_sources <= registry.keys()


def test_knowledge_chunks_fit_the_embedding_budget():
    diseases = load_disease_knowledge()
    chunks = [chunk for item in diseases for chunk in chunk_text(item["content"])]

    assert len(chunks) > len(diseases)
    assert max(map(len, chunks)) <= 350


def test_treatment_candidates_are_not_seedable():
    path = AI_ROOT / "data" / "structured" / "treatment_rule_candidates.json"
    candidates = json.loads(path.read_text(encoding="utf-8"))

    assert len(candidates) == 26
    assert all(
        item["verification"]["eligible_for_seed"] is False for item in candidates
    )
    assert not any(item["disease_code"].endswith("___healthy") for item in candidates)


def test_prompt_and_citations_keep_source_ids():
    contexts = [
        {
            "content": "Triệu chứng tham khảo.",
            "source_ids": ["S05", "S03"],
            "document_id": "document-1",
            "chunk_id": "chunk-1",
            "similarity": 0.9,
        },
        {
            "content": "Biện pháp phòng ngừa.",
            "source_ids": ["S03"],
            "document_id": "document-2",
            "chunk_id": "chunk-2",
            "similarity": 0.8,
        },
    ]

    prompt = build_rag_prompt("Cần làm gì?", contexts)
    citations = build_citations(contexts)

    assert "[Nguồn S05, S03]" in prompt
    assert [item["source_id"] for item in citations] == ["S05", "S03"]
    assert all(item["url"].startswith("http") for item in citations)
