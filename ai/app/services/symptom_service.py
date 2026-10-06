"""Rank only canonical diseases using evidence from the existing retriever."""

from app.cv.contract import class_contract
from app.rag.retriever import RAGRetriever


def suggest_diseases(
    db, symptom_text: str, top_k: int = 5, retriever=None
) -> list[dict]:
    if not symptom_text.strip():
        raise ValueError("symptom_text must not be blank")
    contract = class_contract()
    catalog = {
        entry["disease_code"]: entry["disease_name"]
        for entry in [*contract["classes"], contract["fallback"]]
    }
    rows = (retriever or RAGRetriever()).retrieve(db, symptom_text, top_k=20)
    candidates = {}
    for row in rows:
        code = row.get("disease_code")
        if code not in catalog:
            continue
        score = float(row["similarity"])
        if code not in candidates or score > candidates[code]["score"]:
            candidates[code] = {
                "disease_code": code,
                "disease_name": catalog[code],
                "score": score,
                "evidence": row["content"],
                "source_ids": row.get("source_ids", []),
            }
    return sorted(
        candidates.values(), key=lambda row: (-row["score"], row["disease_code"])
    )[:top_k]
