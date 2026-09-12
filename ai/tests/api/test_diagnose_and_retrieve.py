# SPDX-License-Identifier: Apache-2.0
from unittest.mock import MagicMock

from fastapi import FastAPI
from fastapi.testclient import TestClient

import src.api.router as router_module
from src.api.router import router
from src.db.session import get_db
from tests.cv.test_stage6_contract import Model, image_bytes


def test_diagnose_and_retrieve_api(monkeypatch):
    mock_retrieve = MagicMock(return_value=[{"chunk_id": "1", "content": "RAG test chunk"}])
    monkeypatch.setattr(router_module.RAGRetriever, "retrieve", lambda self, db, query, top_k, disease_code: mock_retrieve(query, top_k, disease_code))
    
    mock_treatment = {"path": "treatment", "disease_code": "Corn_(maize)__Common_rust", "rules": [], "policy": None}
    monkeypatch.setattr(router_module, "lookup_structured_knowledge", lambda db, disease_code: mock_treatment)

    app = FastAPI()
    app.dependency_overrides[get_db] = lambda: MagicMock()
    app.include_router(router, prefix="/api/v1")
    client = TestClient(app)

    # 1. When model is not loaded, return 503
    resp_503 = client.post(
        "/api/v1/disease/diagnose-and-retrieve",
        files={"image": ("leaf.png", image_bytes(), "image/png")},
    )
    assert resp_503.status_code == 503

    # 2. When model is loaded, return 200 with cv_result, treatment_knowledge, rag_knowledge
    app.state.plant_model = Model(8)  # Corn_(maize)__Common_rust
    response = client.post(
        "/api/v1/disease/diagnose-and-retrieve",
        files={"image": ("leaf.png", image_bytes(), "image/png")},
    )
    assert response.status_code == 200, response.text
    data = response.json()
    assert "cv_result" in data
    assert data["cv_result"]["disease_code"] == "Corn_(maize)__Common_rust"
    assert "treatment_knowledge" in data
    assert "rag_knowledge" in data
    assert len(data["rag_knowledge"]) == 1
    mock_retrieve.assert_called_with("Explain disease Corn_(maize)__Common_rust in detail", 1, "Corn_(maize)__Common_rust")

    # 3. Test with custom query and top_k
    response_custom = client.post(
        "/api/v1/disease/diagnose-and-retrieve",
        files={"image": ("leaf.png", image_bytes(), "image/png")},
        data={"query": "Thuốc đặc trị rỉ sắt", "top_k": 2},
    )
    assert response_custom.status_code == 200
    mock_retrieve.assert_called_with("Thuốc đặc trị rỉ sắt", 2, "Corn_(maize)__Common_rust")

    # 4. Test PDF report endpoint
    resp_pdf = client.post(
        "/api/v1/disease/diagnose-and-retrieve/pdf",
        files={"image": ("leaf.png", image_bytes(), "image/png")},
    )
    assert resp_pdf.status_code == 200
    assert resp_pdf.headers["content-type"] == "application/pdf"
    assert resp_pdf.content.startswith(b"%PDF")


