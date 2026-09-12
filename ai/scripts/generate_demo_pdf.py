# SPDX-License-Identifier: Apache-2.0
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

AI_DIR = Path(__file__).resolve().parents[1]
if str(AI_DIR) not in sys.path:
    sys.path.insert(0, str(AI_DIR))

from unittest.mock import MagicMock

from fastapi import FastAPI
from fastapi.testclient import TestClient

import src.api.router as router_module
from src.api.router import router
from src.db.session import get_db
from tests.cv.test_stage6_contract import Model

AI_DIR = Path(__file__).resolve().parents[1]
IMAGE_PATH = AI_DIR / "tests" / "data" / "images" / "tomato_leaf.jpg"
OUTPUT_PDF_PATH = AI_DIR / "diagnosis_report.pdf"


def run_pdf_generation_flow():
    print(f"1. Đang đọc ảnh đầu vào: {IMAGE_PATH}")
    image_bytes = IMAGE_PATH.read_bytes()

    # Giả lập RAG & Structured Knowledge lookup
    mock_rag_chunks = [
        {
            "chunk_id": "doc-001-chunk-1",
            "document_title": "Quy trình phòng trừ Bệnh đốm lá Cà chua (Tomato Leaf Mold)",
            "content": (
                "Bệnh đốm lá cà chua do nấm Passalora fulva gây ra. Triệu chứng ban đầu là các vệt đốm vàng "
                "ở mặt trên của lá, mặt dưới xuất hiện lớp nấm dại màu nâu ô-liu. Bệnh phát triển mạnh trong điều kiện "
                "ẩm độ không khí cao (>85%) và nhiệt độ từ 20-25°C."
            ),
            "source_ids": ["QCVN-01-2024", "VietGAP-Tomato-P2"],
            "score": 0.92,
        }
    ]

    mock_treatment_knowledge = {
        "path": "treatment",
        "disease_code": "Tomato___Leaf_Mold",
        "rules": [
            {
                "pesticide": "Amistar Top 325SC",
                "active_ingredient": "Azoxystrobin + Difenoconazole",
                "dose_min": 0.3,
                "dose_max": 0.5,
                "dose_unit": "L/ha",
                "phi_days": 7,
            }
        ],
        "policy": None,
    }

    # Setup FastAPI app với dependency overrides
    app = FastAPI(title="DX-Farm AI Test")
    app.dependency_overrides[get_db] = lambda: MagicMock()
    app.include_router(router, prefix="/api/v1")

    # Giả lập mô hình CV chẩn đoán Tomato___Leaf_Mold (index = 32 trong 38 lớp)
    app.state.plant_model = Model(index=32)

    client = TestClient(app)

    print("2. Đang gửi request tới API POST /api/v1/disease/diagnose-and-retrieve/pdf...")
    with monkeypatch_context(router_module, mock_rag_chunks, mock_treatment_knowledge):
        response = client.post(
            "/api/v1/disease/diagnose-and-retrieve/pdf",
            files={"image": ("tomato_leaf.jpg", image_bytes, "image/jpeg")},
            data={"query": "Cách xử lý bệnh đốm lá cà chua và thời gian PHI", "top_k": 1},
        )

    assert response.status_code == 200, f"Lỗi API: {response.text}"
    assert response.headers["content-type"] == "application/pdf"
    
    pdf_data = response.content
    print(f"3. Tạo file PDF thành công ({len(pdf_data)} bytes). Đang ghi vào: {OUTPUT_PDF_PATH}")
    OUTPUT_PDF_PATH.write_bytes(pdf_data)
    print("✓ ĐÃ LƯU FILE PDF THÀNH CÔNG!")


class monkeypatch_context:
    def __init__(self, target_module, rag_data, treatment_data):
        self.target_module = target_module
        self.rag_data = rag_data
        self.treatment_data = treatment_data
        self.orig_retrieve = None
        self.orig_lookup = None

    def __enter__(self):
        self.orig_retrieve = getattr(self.target_module.RAGRetriever, "retrieve", None)
        self.orig_lookup = getattr(self.target_module, "lookup_structured_knowledge", None)
        
        self.target_module.RAGRetriever.retrieve = lambda self_instance, db, query, top_k, disease_code: self.rag_data
        self.target_module.lookup_structured_knowledge = lambda db, disease_code: self.treatment_data

    def __exit__(self, exc_type, exc_val, exc_tb):
        if self.orig_retrieve:
            self.target_module.RAGRetriever.retrieve = self.orig_retrieve
        if self.orig_lookup:
            self.target_module.lookup_structured_knowledge = self.orig_lookup


if __name__ == "__main__":
    run_pdf_generation_flow()
