# Kế Hoạch Chi Tiết: Tách Database & Biến AI Thành Dịch Vụ Suy Luận Thuần Túy (Stateless Inference Service)

> **Dự án**: DX-Farm (Hệ thống Quản lý Nông nghiệp Thông minh)  
> **Tài liệu**: Kế hoạch Tái cấu trúc Kiến trúc Backend & AI  
> **Ngày cập nhật**: 15/09/2026  

---

## 🎯 Mục Tiêu Cốt Lõi

1. **Loại bỏ 100% tác vụ GHI (Write/CRUD)** dữ liệu nghiệp vụ nông nghiệp ở phía AI.
2. **Phân định rõ trách nhiệm (Database-per-Service)**:
   - **Backend DB**: Quản lý toàn bộ dữ liệu nghiệp vụ vận hành nông trại (Operational DB / RDBMS).
   - **AI DB**: Chỉ quản lý kho tri thức Vector RAG (PostgreSQL + pgvector).
3. **Biến AI Service thành Dịch vụ Suy luận Thuần túy (Stateless Inference Service)**:
   - AI nhận Request Payload -> Chẩn đoán hình ảnh (CV) + Tra cứu RAG -> Trả về kết quả JSON cho Backend.
   - AI **không kết nối trực tiếp** và **không lưu trữ** vào Database của Backend.
   - AI chỉ trả `disease_code`, confidence và RAG evidence; Backend là nơi duy nhất tra cứu và quyết định dữ liệu thuốc, liều lượng, PHI, policy và trạng thái nghiệp vụ.

---

## 📊 1. Hiện Trạng DB & Sự Trùng Lặp Giữa AI và Backend

### 1.1 Danh sách bảng trong DB hiện tại

| Phân loại | Bảng thuộc Backend (Java/Spring) | Bảng thuộc AI (Python/FastAPI) |
| :--- | :--- | :--- |
| **Thực địa & Mùa vụ** | `parcels`, `crop_seasons` | `parcels`, `crop_seasons` |
| **Danh mục Nông nghiệp** | `diseases`, `pesticides`, `treatment_rules` | `diseases`, `pesticides`, `treatment_rules`, `disease_management_policies` |
| **Nghiệp vụ Chẩn đoán** | `disease_reports`, `diagnoses`, `treatment_applications` | `disease_reports`, `diagnoses`, `treatment_applications`, `workflow_cases` |
| **Tri thức Vector RAG** | *Không có* | `documents`, `document_chunks` |

### 1.2 Chi tiết 8 bảng đang bị TRÙNG LẶP hoàn toàn

Cả Backend và AI đều đang khai báo các bảng này trong DB của mình, gây ra xung đột kiểu dữ liệu (Backend dùng `Long`, AI dùng `UUID`) và rủi ro bất đồng bộ dữ liệu:

1. `parcels`: Quản lý thông tin thửa đất.
2. `crop_seasons`: Quản lý mùa vụ trồng trọt.
3. `diseases`: Danh mục các loại bệnh hại.
4. `pesticides`: Danh mục các loại thuốc bảo vệ thực vật.
5. `treatment_rules`: Quy tắc phác đồ điều trị (liều lượng min/max, ngày PHI).
6. `disease_reports`: Báo cáo phát hiện bệnh từ nông dân.
7. `diagnoses`: Kết quả chẩn đoán và đề xuất xử lý.
8. `treatment_applications`: Nhật ký ghi nhận phun thuốc thực tế.

---

## 🔄 2. Đối Chiếu Luồng Nghiệp Vụ & Loại Bỏ Phần Trùng Lặp

| Luồng Nghiệp Vụ | Phía Backend thực hiện | Phía AI thực hiện | Hướng xử lý |
| :--- | :--- | :--- | :--- |
| **Tạo báo cáo dịch bệnh** | Nhận ảnh từ App -> Ghi `disease_reports` | Nhận request -> Ghi `disease_reports` | ❌ **LOẠI BỎ KHỎI AI**. Chỉ Backend quản lý upload & tạo báo cáo. |
| **Lưu kết quả chẩn đoán** | Ghi `diagnoses` (kèm trạng thái duyệt `PENDING`/`APPROVED`) | Ghi `diagnoses` thẳng vào DB | ❌ **LOẠI BỎ KHỎI AI**. AI chỉ trả kết quả JSON, Backend tự lưu `diagnoses`. |
| **Ghi nhận phun thuốc** | Ghi `treatment_applications` + tính ngày khóa thu hoạch PHI | Ghi `treatment_applications` | ❌ **LOẠI BỎ KHỎI AI**. Chỉ Backend quản lý lịch sử phun thuốc. |
| **Đọc thông tin thực địa** | Truy vấn `parcels`, `crop_seasons` | Truy vấn `parcels`, `crop_seasons` | ❌ **LOẠI BỎ TRUY VẤN Ở AI**. Backend đọc DB và đóng gói gửi qua REST Payload. |
| **Tra cứu dữ liệu quyết định** | Dựa vào `disease_code` để lấy disease, treatment rule, policy, PHI và lịch sử phun | Không truy vấn các bảng này | ✅ **CHỈ BACKEND**. RAG/LLM không được tạo hoặc xác nhận dữ liệu quyết định. |
| **Tra cứu tri thức RAG** | *Không thực hiện* | Đọc `documents`, `document_chunks` | ✅ **GIỮ LẠI CHO AI**. Đây là nhiệm vụ cốt lõi của AI. |

---

## 🏛️ 3. Kiến Trúc Mới Sau Khi Tách Riêng 2 DB

### 3.1 Cấu trúc 2 Database độc lập

```
                      ┌─────────────────────────────────────────┐
                      │            HỆ THỐNG DX-FARM             │
                      └────────────────────┬────────────────────┘
                                           │
             ┌─────────────────────────────┴─────────────────────────────┐
             ▼                                                           ▼
┌──────────────────────────┐                               ┌──────────────────────────┐
│   BACKEND DATABASE       │                               │   AI VECTOR DATABASE     │
│   (dx_farm_db)           │                               │   (dx_farm_ai_vector_db) │
├──────────────────────────┤                               ├──────────────────────────┤
│ - parcels                │                               │ - documents              │
│ - crop_seasons           │                               │ - document_chunks        │
│ - diseases               │                               │   (với extension pgvector│
│ - pesticides             │                               │    lưu vector 384d)      │
│ - treatment_rules        │                               └──────────────────────────┘
│ - management policies    │                                            ▲
│ - disease_reports        │                                            ▲
│ - diagnoses              │                                            │ Read-Only (RAG)
│ - treatment_applications │                                            │
└────────────▲─────────────┘                               ┌────────────┴─────────────┐
             │ Read/Write                                  │    AI INFERENCE SERVICE   │
             │                                             │    (Python FastAPI)      │
┌────────────┴─────────────┐         REST API (JSON)       │  - CV Model Inference    │
│     BACKEND SERVICE      ├──────────────────────────────►│  - RAG Vector Search     │
│    (Java Spring Boot)    │◄──────────────────────────────┤  - Evidence-only wording │
└──────────────────────────┘                               │  - STATELESS (NO WRITES) │
                                                           └──────────────────────────┘
```

### 3.2 Bảng dữ liệu AI truy vấn sau khi loại bỏ trùng
Sau khi tách DB, **AI DB chỉ chứa duy nhất 2 bảng tri thức Vector**:
1. `documents`: Lưu tiêu đề, mã bệnh (`disease_code`), nguồn tài liệu.
2. `document_chunks`: Lưu nội dung chia nhỏ (`content`) và chuỗi Vector 384 chiều (`embedding`) để thực hiện **Cosine Distance Vector Search**.

`disease_code` là contract chung giữa hai database: giá trị AI trả về phải khớp chính xác với `Backend.diseases.code` và `documents.disease_code`. Không dùng tên bệnh hoặc fuzzy matching để nối dữ liệu giữa hai hệ.

---

## ⚙️ 4. Workflow Chi Tiết Của AI & Backend

### 4.1 Sơ đồ luồng xử lý (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Farmer as Nông dân (App)
    participant BE as Backend Service (Java)
    participant BEDB as Backend DB (Read/Write)
    participant AI as AI Inference Service (Python)
    participant AIDB as AI Vector DB (Read-Only)

    Farmer->>BE: 1. Đăng tải ảnh lá bệnh + parcel_code
    BE->>BEDB: 2. Lưu ảnh -> Tạo disease_report (PENDING)
    BE->>BEDB: 3. Đọc context thực địa không quyết định
    BE->>AI: 4. POST /api/v1/diagnose (image + request_id + context)

    activate AI
    AI->>AI: 5. CV nhận diện và tạo disease_code
    AI->>AIDB: 6. Vector search theo disease_code
    AIDB-->>AI: 7. Trả evidence, similarity, source IDs
    AI-->>BE: 8. Trả CV result + RAG evidence
    deactivate AI

    BE->>BEDB: 9. Tra disease, rule, policy, PHI, lịch sử phun theo disease_code
    BEDB-->>BE: 10. Trả dữ liệu nghiệp vụ chính xác
    BE->>BEDB: 11. Ghi diagnosis/workflow PENDING
    BE-->>Farmer: 12. Trả recommendation đã được Backend kiểm tra
```

### 4.2 Ranh giới giữa RAG evidence và dữ liệu quyết định

Sau khi CV có `disease_code`, hai nhánh dữ liệu chạy độc lập:

```text
AI → AI Vector DB (pgvector)
   → đoạn kiến thức liên quan, similarity, source IDs
   → dùng để giải thích, không quyết định thuốc hoặc PHI

Backend → Backend DB
        → disease, treatment rules, management policy, lịch sử phun, PHI
        → nguồn sự thật duy nhất để tạo recommendation và approval
```

AI không gọi REST sang Backend để tra từng tên bệnh/thuốc. Backend tự tra Core DB sau khi nhận `disease_code`; hai bên chỉ trao đổi mã canonical và JSON contract.

---

## 📑 5. Chuẩn Hóa API Contract (REST DTO)

### 5.1 Request: `POST /api/v1/diagnose` (Backend -> AI)
```json
{
  "request_id": "req_123456789",
  "crop_season_id": "101",
  "image_url": "https://storage.dxfarm.com/reports/leaf_01.jpg",
  "field_context": {
    "parcel_code": "P-01",
    "crop_name": "Lúa mùa",
    "growth_stage": "Trỗ bông",
    "recent_treatments": [
      {
        "pesticide_name": "Anvil 5SC",
        "applied_at": "2026-09-01T08:00:00Z",
        "harvest_locked_until": "2026-09-15T08:00:00Z"
      }
    ]
  }
}
```

### 5.2 Response: `200 OK` (AI -> Backend)
```json
{
  "request_id": "req_123456789",
  "cv_result": {
    "disease_code": "<CANONICAL_DISEASE_CODE>",
    "disease_name": "Tên bệnh theo class contract CV",
    "confidence": 0.94,
    "model_name": "EfficientNet-B4",
    "model_version": "v1.2.0"
  },
  "rag_evidence": [
    {
      "content": "Đoạn kiến thức đã ingest.",
      "source_ids": ["DOC_DISEASE_GUIDE_V1"],
      "similarity": 0.87
    }
  ]
}
```

AI không trả `treatment_proposal`, liều lượng hoặc `phi_days_suggested`. Backend dùng `cv_result.disease_code` để lấy các giá trị này từ Core DB, kiểm tra policy và lịch sử phun trước khi lưu recommendation ở trạng thái `PENDING`.

---

## 🚀 6. Lộ Trình Triển Khai (Implementation Roadmap)

### Phase 1: Tái cấu trúc phía AI (Python/FastAPI)
- [ ] Xóa 10 ORM Models nghiệp vụ dư thừa (`disease_report.py`, `diagnosis.py`, `treatment_application.py`, `parcel.py`, `crop_season.py`, `disease.py`, `pesticide.py`, `treatment_rule.py`, `disease_management_policy.py`, `workflow_case.py`).
- [ ] Xóa repository/service CRUD nghiệp vụ và toàn bộ lệnh `db.add()`, `db.commit()` trong AI runtime.
- [ ] Đổi endpoint AI thành inference contract: nhận ảnh/request context, trả `cv_result` và `rag_evidence`; không trả rule, liều lượng hoặc PHI.
- [ ] Giữ `rag/retriever.py` và `rag/ingestion_service.py`; runtime chỉ đọc AI Vector DB, còn ingest là job quản trị có quyền ghi riêng.

### Phase 2: Tái cấu trúc phía Backend (Java/Spring Boot)
- [ ] Thêm `AiDiagnosisClient` (sử dụng `WebClient` hoặc `RestTemplate`).
- [ ] Cập nhật `DiseaseReportService`: Tự khởi tạo và lưu `DiseaseReport` vào Backend DB.
- [ ] Gọi `AiDiagnosisClient.diagnose(...)` gửi Payload sang AI Service.
- [ ] Nhận `cv_result`, xác thực `disease_code`, rồi tra disease, treatment rule, management policy, PHI và lịch sử phun trong Backend DB.
- [ ] Tự khởi tạo `Diagnosis`/workflow `PENDING` từ dữ liệu Backend đã kiểm tra; RAG evidence chỉ được lưu như nguồn giải thích.

### Phase 3: Khởi tạo Database & Deployment
- [ ] Khởi tạo DB riêng `dx_farm_ai_vector_db` chỉ chứa bảng `documents` và `document_chunks` (kèm pgvector extension).
- [ ] Cập nhật `deploy/docker-compose.yml` để tách biệt cấu hình biến môi trường kết nối DB của Backend và AI.
- [ ] Dùng user chỉ-đọc cho AI runtime trên Vector DB và user/job riêng cho ingest.

---

> 📌 **Kết Luận**: Kế hoạch này giúp chuyển đổi thành công AI Service thành **Dịch vụ suy luận phi trạng thái (Stateless Inference Service)**, giải quyết triệt me trùng lặp dữ liệu, đảm bảo tính bảo mật và tối ưu hiệu năng toàn hệ thống DX-Farm.
