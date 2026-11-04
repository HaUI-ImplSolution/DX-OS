# SPDX-License-Identifier: AGPL-3.0-only
import json
import os
import tempfile
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from unittest.mock import patch

import pytest
from app.assistant.body_limit import AssistantBodyLimit
from app.assistant.pipeline import AssistantPipeline
from app.assistant.router import capacity, router
from app.assistant.schemas import (
    ContextRequest,
    PlanDraft,
    PlanRequest,
    SummaryDraft,
)
from app.core.config import Settings, get_settings
from fastapi import FastAPI
from fastapi.testclient import TestClient
from haystack.dataclasses import ChatMessage
from pydantic import ValidationError


class Embedder:
    def embed_text(self, text):
        return [1.0, 0.0] if "printer" in text.lower() else [0.0, 1.0]

    def embed_texts(self, texts):
        return [self.embed_text(text) for text in texts]


def pipeline(generate=False):
    service = AssistantPipeline(Embedder())
    service.settings = Settings(
        assistant_generation_enabled=generate, assistant_model="test", _env_file=None
    )
    return service


def context(prefix="record"):
    return ContextRequest(
        query="printer",
        documents=[
            {"id": f"{prefix}:1", "text": "printer offline"},
            {"id": f"{prefix}:2", "text": "room reservation"},
        ],
    )


def test_hybrid_only_returns_supported_candidates():
    items = pipeline().rank(context())
    assert [item.id for item in items] == ["record:1"]
    assert items[0].score == pytest.approx(1.0)


def test_concurrent_contexts_do_not_share_documents():
    with ThreadPoolExecutor(max_workers=2) as executor:
        results = list(
            executor.map(
                lambda prefix: pipeline().rank(context(prefix)), ["first", "second"]
            )
        )
    assert [[item.id for item in result] for result in results] == [
        ["first:1"],
        ["second:1"],
    ]


def test_store_is_cleared_on_pipeline_failure():
    from haystack.document_stores.in_memory import InMemoryDocumentStore

    store = InMemoryDocumentStore(shared=False, embedding_similarity_function="cosine")
    with (
        patch("app.assistant.pipeline.InMemoryDocumentStore", return_value=store),
        patch(
            "app.assistant.pipeline.Pipeline.run", side_effect=RuntimeError("failed")
        ),
        pytest.raises(RuntimeError),
    ):
        pipeline().rank(context())
    assert store.count_documents() == 0


def test_duplicate_or_oversized_context_is_rejected():
    with pytest.raises(ValidationError, match="Duplicate"):
        ContextRequest(
            documents=[{"id": "same", "text": "a"}, {"id": "same", "text": "b"}]
        )
    with pytest.raises(ValidationError, match="128 KiB"):
        ContextRequest(
            documents=[{"id": str(n), "text": "á" * 4000} for n in range(20)]
        )


def test_context_cannot_supply_tools_or_business_credentials():
    with pytest.raises(ValidationError):
        ContextRequest(documents=[], token="secret", tools=["write"])


def test_generation_disabled_does_not_call_model():
    with patch("app.assistant.pipeline.OllamaChatGenerator") as generator:
        result = pipeline().summarize(context())
    assert result.status == "DISABLED"
    generator.assert_not_called()


def test_generation_cannot_start_without_an_explicit_model():
    with pytest.raises(ValidationError, match="ASSISTANT_MODEL is required"):
        Settings(assistant_generation_enabled=True, assistant_model=" ", _env_file=None)


def test_generation_context_is_not_silently_truncated():
    request = ContextRequest(
        documents=[{"id": str(n), "text": "text " * 400} for n in range(5)]
    )
    with patch("app.assistant.pipeline.OllamaChatGenerator") as generator:
        result = pipeline(True).summarize(request)
    assert result.status == "CONTEXT_LIMIT"
    generator.assert_not_called()


def test_foreign_evidence_is_not_published():
    draft = SummaryDraft(summary=[{"text": "Unrelated", "evidenceIds": ["outside:9"]}])
    service = pipeline(True)
    with patch.object(service, "_generate", return_value=draft):
        result = service.summarize(context())
    assert result.status == "INVALID"
    assert result.summary == []


def test_summary_keeps_source_identifiers():
    draft = SummaryDraft(
        summary=[{"text": "Máy in chưa kết nối.", "evidenceIds": ["record:1"]}]
    )
    service = pipeline(True)
    with patch.object(service, "_generate", return_value=draft):
        result = service.summarize(context())
    assert result.status == "GENERATED"
    assert result.summary[0].evidence_ids == ["record:1"]


def test_plan_does_not_accept_unknown_fields():
    service = pipeline(True)
    request = PlanRequest(
        query="printer", fields=[{"name": "title", "label": "Tiêu đề", "type": "text"}]
    )
    bad = PlanDraft(
        status="SUPPORTED",
        filters=[{"field": "private_role", "operator": "eq", "value": "ADMIN"}],
    )
    with patch.object(service, "_generate", return_value=bad):
        assert service.plan(request).status == "NEEDS_CLARIFICATION"


def test_plan_preserves_explicit_operators_and_choice_values():
    service = pipeline(True)
    request = PlanRequest(
        query="Đã đóng",
        fields=[
            {
                "name": "status",
                "label": "Trạng thái",
                "type": "choice",
                "options": ["closed"],
            }
        ],
    )
    draft = PlanDraft(
        status="SUPPORTED",
        filters=[{"field": "status", "operator": "eq", "value": "closed"}],
    )
    with patch.object(service, "_generate", return_value=draft):
        assert service.plan(request).filters == {"status.eq": "closed"}
        draft.filters[0].value = "invented"
        assert service.plan(request).status == "NEEDS_CLARIFICATION"


def test_plan_conflicting_conditions_are_not_silently_overwritten():
    service = pipeline(True)
    request = PlanRequest(
        query="Tiêu đề", fields=[{"name": "title", "label": "Tiêu đề", "type": "text"}]
    )
    draft = PlanDraft(
        status="SUPPORTED",
        filters=[
            {"field": "title", "operator": "eq", "value": "first"},
            {"field": "title", "operator": "eq", "value": "second"},
        ],
    )
    with patch.object(service, "_generate", return_value=draft):
        assert service.plan(request).status == "NEEDS_CLARIFICATION"


def test_empty_plan_cannot_turn_a_question_into_an_unfiltered_listing():
    service = pipeline(True)
    request = PlanRequest(
        query="Tìm những hồ sơ đã đóng",
        fields=[
            {
                "name": "status",
                "label": "Trạng thái",
                "type": "choice",
                "options": ["closed"],
            }
        ],
    )
    with patch.object(service, "_generate", return_value=PlanDraft(status="SUPPORTED")):
        assert service.plan(request).status == "NEEDS_CLARIFICATION"


def test_plan_does_not_turn_a_literal_search_into_sql_wildcards():
    service = pipeline(True)
    request = PlanRequest(
        query="Tìm máy in",
        fields=[{"name": "title", "label": "Tiêu đề", "type": "text"}],
    )
    draft = PlanDraft(
        status="SUPPORTED",
        filters=[{"field": "title", "operator": "like", "value": "%máy in%"}],
    )
    with patch.object(service, "_generate", return_value=draft):
        assert service.plan(request).status == "NEEDS_CLARIFICATION"
        draft.filters[0].value = "máy in"
        assert service.plan(request).filters == {"title.like": "máy in"}
        draft.filters[0].value = " máy in "
        assert service.plan(request).filters == {"title.like": "máy in"}


def test_model_invalid_json_is_not_retried_or_exposed():
    service = pipeline(True)
    with patch("app.assistant.pipeline.OllamaChatGenerator") as generator:
        generator.return_value.run.return_value = {
            "replies": [ChatMessage.from_assistant("invalid")]
        }
        result = service.summarize(context())
    assert result.status == "INVALID"
    assert generator.call_args.kwargs["max_retries"] == 0
    assert "tools" not in generator.call_args.kwargs
    generator.return_value.run.assert_called_once()


def client():
    app = FastAPI()
    app.include_router(router, prefix="/api/v1")
    app.add_middleware(AssistantBodyLimit)
    app.dependency_overrides[get_settings] = lambda: Settings(
        ai_service_api_key="test", _env_file=None
    )
    return TestClient(app)


def test_internal_routes_require_service_authentication():
    response = client().post("/api/v1/assistant/rank", json=context().model_dump())
    assert response.status_code == 401


def test_busy_assistant_rejects_without_waiting():
    assert capacity.acquire(blocking=False)
    try:
        response = client().post(
            "/api/v1/assistant/rank",
            json=context().model_dump(),
            headers={"X-Service-Api-Key": "test"},
        )
    finally:
        capacity.release()
    assert response.status_code == 503


def test_missing_ollama_model_returns_unavailable_and_releases_capacity():
    from ollama import ResponseError

    with patch.object(
        AssistantPipeline, "summarize", side_effect=ResponseError("not found", 404)
    ):
        for _ in range(2):
            response = client().post(
                "/api/v1/assistant/summarize",
                json=context().model_dump(),
                headers={"X-Service-Api-Key": "test"},
            )
            assert response.status_code == 200
            assert response.json() == {"status": "UNAVAILABLE", "summary": []}


def test_body_limit_applies_before_parsing_large_json():
    response = client().post(
        "/api/v1/assistant/rank",
        content=b" " * (128 * 1024 + 1),
        headers={"X-Service-Api-Key": "test", "Content-Type": "application/json"},
    )
    assert response.status_code == 413


def test_model_preparation_reuses_complete_cache(tmp_path):
    from scripts import prepare_assistant_models as prepare

    (tmp_path / "model.safetensors").write_bytes(b"fixture")
    with patch.object(
        prepare, "snapshot_download", return_value=str(tmp_path)
    ) as download:
        prepare.main()
    download.assert_called_once_with(
        get_settings().embedding_model, local_files_only=True
    )


def test_model_preparation_downloads_only_when_weights_are_missing(tmp_path):
    from scripts import prepare_assistant_models as prepare

    with (
        patch.object(
            prepare, "snapshot_download", return_value=str(tmp_path)
        ) as download,
        patch("huggingface_hub.list_repo_files", return_value=["model.safetensors"]),
    ):
        prepare.main()
    assert download.call_count == 2
    assert download.call_args_list[0].kwargs == {"local_files_only": True}
    assert "model.safetensors" in download.call_args_list[1].kwargs["allow_patterns"]
    assert "*.onnx" not in download.call_args_list[1].kwargs["allow_patterns"]


@pytest.mark.skipif(
    os.environ.get("ASSISTANT_EVALUATE") != "true",
    reason="Explicit real-model evaluation",
)
def test_retrieval_pilot():
    import ir_measures
    from haystack import Document
    from haystack.components.retrievers.in_memory import InMemoryBM25Retriever
    from haystack.document_stores.in_memory import InMemoryDocumentStore
    from ir_measures import Recall, nDCG

    fixture = json.loads(
        (Path(__file__).parent / "fixtures/assistant_pilot.json").read_text()
    )
    service = AssistantPipeline()
    judgments, run, baseline, details = [], [], [], []
    for scenario in fixture["scenarios"]:
        for case in scenario["cases"]:
            if case["operation"] == "summarize":
                continue
            documents = [
                doc
                for doc in scenario["documents"]
                if doc["id"] != f"record:{case.get('recordId')}"
            ]
            request = ContextRequest(query=case["query"], documents=documents)
            items = service.rank(request)
            store = InMemoryDocumentStore(shared=False)
            try:
                store.write_documents(
                    [Document(id=doc["id"], content=doc["text"]) for doc in documents]
                )
                keyword_items = InMemoryBM25Retriever(store, top_k=4).run(
                    query=case["query"]
                )["documents"]
            finally:
                store.delete_all_documents()
                store.shutdown()
            judgments.extend(
                ir_measures.Qrel(case["id"], doc, 1) for doc in case["relevant"]
            )
            run.extend(
                ir_measures.ScoredDoc(case["id"], item.id, len(items) - rank)
                for rank, item in enumerate(items)
            )
            baseline.extend(
                ir_measures.ScoredDoc(case["id"], item.id, len(keyword_items) - rank)
                for rank, item in enumerate(keyword_items)
            )
            details.append(
                {
                    "query": case["id"],
                    "retrieved": [item.id for item in items],
                    "bm25": [item.id for item in keyword_items],
                }
            )
    scores = ir_measures.calc_aggregate([Recall @ 4, nDCG @ 4], judgments, run)
    keyword_scores = ir_measures.calc_aggregate(
        [Recall @ 4, nDCG @ 4], judgments, baseline
    )
    output = Path(
        os.environ.get(
            "ASSISTANT_EVAL_OUTPUT",
            str(Path(tempfile.gettempdir()) / "dx-assistant-evaluation.json"),
        )
    ).resolve()
    repository = next(
        (
            parent
            for parent in Path(__file__).resolve().parents
            if (parent / ".git").exists()
        ),
        None,
    )
    forbidden = (
        [repository] if repository else [Path(__file__).resolve().parent, Path.cwd()]
    )
    if any(output.is_relative_to(parent) for parent in forbidden):
        raise ValueError("Evaluation output must be outside the repository")
    result = {
        "labelStatus": fixture["labelStatus"],
        "queries": len(details),
        "metrics": {str(key): value for key, value in scores.items()},
        "bm25Metrics": {str(key): value for key, value in keyword_scores.items()},
        "qrels": [item._asdict() for item in judgments],
        "run": [item._asdict() for item in run],
        "bm25Run": [item._asdict() for item in baseline],
        "results": details,
    }
    output.write_text(json.dumps(result, ensure_ascii=False, indent=2) + "\n")
    print(
        json.dumps({key: result[key] for key in ["queries", "metrics", "bm25Metrics"]})
    )
    assert len(details) == 24
