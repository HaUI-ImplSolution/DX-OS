# DX-Farm

MVP dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA).
Bài toán: **Agri-Carbon DX-OS**, hệ điều hành chuyển đổi số nội bộ cho một Hợp tác xã Nông nghiệp Số chuyên canh lúa nước (ĐBSCL), theo kiến trúc 4 không gian **H-P-D-I** (Human, Process, Data, Intelligence).

Luồng xương sống: nông dân báo bệnh bằng ảnh, AI chẩn đoán, hệ thống tra quy tắc xử lý xác định, kỹ sư duyệt, ghi nhận phun thuốc và khóa thu hoạch theo thời gian cách ly (PHI).

Lịch sử thay đổi theo từng module ở [`CHANGELOG.md`](./CHANGELOG.md); cách đóng góp ở [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Kiến trúc

Ba tầng Open-Core.
Sơ đồ đầy đủ và luồng request ở [`development/docs/submission/ARCHITECTURE_OPEN_CORE.md`](./development/docs/submission/ARCHITECTURE_OPEN_CORE.md).

| Tầng | Nội dung |
|---|---|
| Ứng dụng DX-Farm | Thửa, vụ mùa, báo cáo sâu bệnh, chẩn đoán, khai phun thuốc, thu hoạch; web (kỹ sư, admin) và mobile (nông dân) |
| Ứng dụng DX-Lab | Yêu cầu hỗ trợ CNTT có SLA, thiết bị và mượn trả, đặt phòng máy chống trùng giờ, tri thức và hỏi đáp, thống kê ([`backend/app-lab/README.md`](./backend/app-lab/README.md)) |
| H-P-D-I | [H] Keycloak, Mattermost, nông hộ; [P] workflow duyệt, vòng đời vụ mùa, thông báo; [D] PostgreSQL, MinIO, Superset, thời tiết; [I] CV, RAG, LangGraph |
| Core dùng chung | `core.identity` (workspace, thành viên), `core.workflow` (loại case cấu hình được, hộp duyệt), `core.notification`, `core.storage`, `core.ai`, `core.knowledge`, `core.security` của Backend; cổng API APISIX |

Mọi client chỉ gọi gateway APISIX (cổng `9080`).
Gateway kiểm JWT Keycloak, giới hạn tốc độ các thao tác tốn tài nguyên và chuyển tới Backend, Keycloak, MinIO, web và portal.
Backend, Keycloak, AI, MinIO không publish cổng ra host; Backend tự kiểm JWT lần hai.

### Thành phần

- **Backend (Java, Spring Boot)** sở hữu dữ liệu nghiệp vụ trong PostgreSQL, state machine duyệt, poka-yoke (khóa PHI, ràng buộc vụ mùa), phân quyền theo role và nông hộ.
  Schema do Flyway quản lý.
- **Workflow dùng chung** ghi mỗi quyết định thành một `workflow_event` chỉ thêm, mỗi yêu cầu có người phụ trách; danh sách việc chờ ở `GET /api/v1/workflow-cases`.
- **AI Service (Python, FastAPI)** gồm CV (YOLOv8n), RAG trên pgvector, LangGraph và Qwen2.5-1.5B qua Ollama.
  AI không có quyền vào database của Backend; gọi API nội bộ của Backend để lấy dữ kiện có cấu trúc.
- **Web (React, Vite)**: một image phục vụ cả hai app, đọc `GET /api/v1/app` rồi nạp giao diện của app đang chạy. Farm dành cho kỹ sư và admin (hàng chờ duyệt, chi tiết yêu cầu, nông hộ, tài liệu); Lab dành cho sinh viên, giảng viên, kỹ thuật viên và quản trị (yêu cầu hỗ trợ, mượn thiết bị, đặt phòng, thống kê, hỏi đáp).
- **Mobile (Expo)** dành cho nông dân: đăng nhập Keycloak (PKCE), tạo thửa và vụ, chụp và gửi ảnh, hộp thông báo, hàng đợi gửi lại khi mất mạng.
- **BI (Superset)** chỉ đọc 6 view `vw_*` của bản ghi đã duyệt; dashboard 7 chart ([`bi/README.md`](./bi/README.md)).
- **Lưu ảnh (MinIO)** bucket riêng tư, client nhận link có hạn (mặc định 15 phút).
- **Chat (Mattermost)** nhận thông báo một chiều từ Backend qua Incoming Webhook ([`chat/README.md`](./chat/README.md)).
- **Portal** trang liên kết tới các ứng dụng và hiện trạng thái từng dịch vụ ([`portal/README.md`](./portal/README.md)).

### Nguyên tắc

1. Deterministic-first: thuốc, liều, PHI luôn lấy từ dữ liệu có cấu trúc, LLM không tự sinh con số.
2. Human-in-the-loop: khuyến nghị có thuốc phải qua kỹ sư duyệt.
3. PostgreSQL là nguồn sự thật; RAG chỉ để giải thích.
4. Poka-yoke chặn ở Backend, không chỉ ẩn nút ở giao diện.

## Tính năng và mức hoàn thiện

Ba mức: có trong code, đã kiểm thử, có dữ liệu demo.

| Tính năng | Trong code | Kiểm thử | Dữ liệu demo |
|---|---|---|---|
| Đăng nhập SSO, phân quyền role, phạm vi dữ liệu theo nông hộ | Có | Test ma trận quyền, E2E | Có (`farmer1`, `engineer1`, `admin1`) |
| Admin tạo nông hộ kèm tài khoản | Có | Test tích hợp | Có |
| Nông dân tạo thửa và vụ (GPS, mã tự sinh), admin duyệt qua workflow | Có | Test workflow, E2E | Có |
| Báo cáo sâu bệnh: kiểm tra chất lượng ảnh, idempotency, chụp lại | Có | Test tích hợp, E2E | Có |
| Chẩn đoán AI, nhập triệu chứng, chẩn đoán lại, kỹ sư duyệt | Có | Test tích hợp, pytest | Có (chẩn đoán thủ công; AI khi có ảnh thật) |
| Khai phun thuốc, xác nhận, khóa thu hoạch theo PHI | Có | Test đơn vị và tích hợp, E2E | Có cho đạo ôn (rule được chuyên gia duyệt) |
| Thu hoạch (sớm cần xác nhận), nhắc hạn, hộp thông báo | Có | Test đơn vị | Có |
| Dashboard BI trên view đã duyệt | Có | Kiểm tay | Một phần: chart "Tỷ lệ AI đúng/sai" cần chẩn đoán AI bằng ảnh thật |
| Thời tiết theo thửa (Open-Meteo, snapshot dự phòng) | Có | Chưa qua giao diện | Có |
| Hỏi đáp tài liệu có trích nguồn | Có | Kiểm tay | Có (6 tài liệu bệnh lúa) |
| Portal hiện trạng dịch vụ, lỗi hiển thị tiếng Việt | Có | Kiểm tay | Không áp dụng |

`data.sql` seed thuốc `NEWTEC_300SC` và hai treatment rule đã được chuyên gia duyệt cho đạo ôn (`Rice___Blast`) và khô vằn (`Rice___Sheath_blight`).
Chẩn đoán trên bệnh chưa có rule (ví dụ đốm nâu) ở trạng thái chỉ xem xét (`review_required`), không có liều hay PHI.
Khi rule mới được duyệt và thêm vào `data/structured/`, chạy lại `python3 data/scripts/generate_seed_sql.py` để mở luồng phun thuốc và khóa thu hoạch cho bệnh đó.

## Cài đặt và chạy

Yêu cầu: Docker và Docker Compose.
Lần dựng đầu tải và build nhiều thứ: image AI khoảng 3 GB, Ollama khoảng 9 GB cùng model Qwen2.5.

```bash
cp deploy/.env.example deploy/.env      # điền mọi biến đang để trống
python3 -m pip install pillow           # cho script dữ liệu demo
./deploy/start.sh                       # dựng, chạy ứng dụng trong DXOS_APP và nạp dữ liệu demo
```

Thiếu biến nào trong `.env`, Compose dừng và báo tên biến đó.
Lần dựng đầu build MinIO từ mã nguồn Go (khoảng 2 phút).
`start.sh` chạy được cả khi stack đang tắt lẫn đang chạy; dữ liệu demo nạp lại không tạo trùng.

### Chọn và đổi ứng dụng

Mỗi lần khởi động chạy một ứng dụng trên cùng core, chọn bằng `DXOS_APP` (`farm` hoặc `lab`) trong `deploy/.env`.
Đổi ứng dụng bằng một lệnh; web đổi theo mà không build lại.

```bash
./deploy/start.sh farm                  # chạy Farm, nạp dữ liệu demo của Farm
./deploy/start.sh lab                   # đổi sang Lab, nạp dữ liệu demo của Lab (khoảng 1 phút)
./deploy/start.sh farm --reset --yes    # xóa deploy/data và dựng lại sạch với Farm
```

`start.sh` gọi `switch-app.sh <app> --seed`: ghi `DXOS_APP`, dựng lại gateway, backend, job nạp tri thức và Superset, khởi động phần còn lại, đợi `GET /api/v1/system/status` báo đúng app rồi nạp dữ liệu demo.
Bảng của ứng dụng trước nằm lại trong database, không dùng tới; `--reset` dọn sạch.
Gateway chỉ có route và giới hạn tốc độ của ứng dụng đang chạy (`deploy/apisix/common.yaml` cùng `deploy/apisix/apps/<app>.yaml`).

### Dữ liệu demo

`start.sh` đã nạp dữ liệu demo; chạy riêng khi cần nạp lại hoặc dùng ảnh thật:

```bash
python3 -m pip install pillow
python3 deploy/demo/seed_demo.py                 # ứng dụng đang chạy, Farm dùng ảnh tổng hợp
python3 deploy/demo/seed_demo.py --images DIR    # Farm: ảnh lá lúa thật (.jpg), chẩn đoán qua AI
python3 deploy/demo/seed_demo.py --app lab       # chọn ứng dụng rõ ràng
```

- Farm: ba tài khoản demo và vai trong Keycloak, nông hộ (gắn `farmer1`), thửa có tọa độ ĐBSCL, vụ ở nhiều trạng thái, báo cáo kèm ảnh, một thửa chờ duyệt.
- Lab: sáu tài khoản demo, lớp và bộ môn, phòng và thiết bị, yêu cầu hỗ trợ ở mọi trạng thái, phiếu mượn và lịch đặt phòng.

Chạy lại không tạo trùng.
Chi tiết ở [`deploy/demo/README.md`](./deploy/demo/README.md).

### Kiểm thử

- Backend: `./mvnw verify` trong `backend` (cần Docker cho Testcontainers).
- AI: `pytest` trong `ai/`.
- Frontend và mobile: `npx tsc --noEmit`.
- E2E trên stack sạch: `python3 deploy/demo/e2e.py` cho ứng dụng đang chạy (`--app farm|lab`); workflow `E2E` chạy cả hai ứng dụng.

### Địa chỉ

| Dịch vụ | Địa chỉ | Đăng nhập |
|---|---|---|
| Portal | http://localhost:9080/portal/ | Không cần |
| Ứng dụng web | http://localhost:9080 | SSO Keycloak, `engineer1` hoặc `admin1` |
| Swagger | http://localhost:9080/swagger-ui.html | Access token Keycloak, bấm Authorize |
| Keycloak Admin | http://localhost:9080/admin | `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` |
| BI (Superset) | http://localhost:9081 | SSO Keycloak; `admin1` vào trang quản trị |
| Quản trị lưu ảnh (MinIO) | http://localhost:9082 | `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY` |
| Chat (Mattermost) | http://localhost:8065 | `MATTERMOST_ADMIN_USERNAME` / `MATTERMOST_ADMIN_PASSWORD` |

### Tài khoản demo

`seed_demo.py` tạo các tài khoản này trong realm `dxos` chỉ để demo và chấm thi.
Đổi mật khẩu hoặc xóa trước khi dùng cho dữ liệu thật.

| Ứng dụng | Username | Mật khẩu | Role |
|---|---|---|---|
| Farm | `farmer1` | `farmer123` | FARMER |
| Farm | `engineer1` | `engineer123` | ENGINEER |
| Farm, Lab | `admin1` | `admin123` | ADMIN |
| Lab | `student1`, `student2` | `student123` | STUDENT |
| Lab | `lecturer1` | `lecturer123` | LECTURER |
| Lab | `tech1`, `tech2` | `tech123` | TECHNICIAN |

Nông dân chỉ dùng app mobile; web và Superset từ chối tài khoản chỉ có role `FARMER`.
`seed_demo.py` gắn `farmer1` vào một nông hộ; không chạy script thì admin gắn qua `PATCH /api/v1/households/{code}/account` hoặc tạo hộ mới ở `POST /api/v1/households`.

### API

Tài liệu tương tác ở `/swagger-ui.html`, sinh từ code.
Mọi response bọc trong `ApiResponse` (`success`, `message`, `data`, `timestamp`); response lỗi có thêm `errorCode` ổn định để client hiển thị tiếng Việt.
Nhóm endpoint chính: `parcels`, `crop-seasons`, `disease-reports`, `disease-reports/{id}/diagnosis`, `workflow-cases`, `approval-requests`, `households`, `workspaces`, `notifications`, `knowledge/ask`, `system/status`, `app`.

### Dừng và dựng lại

```bash
docker compose down        # dừng, giữ dữ liệu trong deploy/data
./reset.sh                 # xóa toàn bộ deploy/data và dựng lại (hỏi xác nhận, --yes để bỏ qua)
```

`reset.sh` giữ volume model Ollama; `--keep-images` bỏ bước build lại image.
Dựng một phần: `./reset.sh --yes postgres backend keycloak`.

## Dữ liệu

Hai database trên cùng một Postgres:

- `dxos`: dữ liệu nghiệp vụ và tri thức có cấu trúc (Disease, Pesticide, TreatmentRule, DiseaseManagementPolicy).
- `dx_farm_ai_vector_db`: `documents` và `document_chunks` cho RAG; AI dùng role chỉ đọc, job ingest dùng role ghi riêng.

Seed nghiệp vụ của Farm nằm ở `backend/app-farm/src/main/resources/farm/data.sql`, sinh từ `data/structured/*.json` bằng `data/scripts/generate_seed_sql.py`; chỉ record được duyệt (`eligible_for_seed=true`) mới được seed.
Không sửa tay `data.sql`; khi thay đổi dữ liệu, cần commit đồng thời dữ liệu nguồn và file đã sinh.
CI chạy `generate_seed_sql.py --check` cùng unit test của generator để phát hiện dữ liệu nguồn và `data.sql` bị lệch.
Hạ tầng Postgres (role, database, extension) do các `*Seeder` trong `dx-core`.

## Giấy phép và hồ sơ PoF

- Mã nguồn tự viết: Apache-2.0, SPDX header ở đầu file Java của Backend và file Python của AI; toàn văn ở [`LICENSE`](./LICENSE).
- Rà soát license từng thành phần (kể cả AGPL-3.0 của Ultralytics, PyMuPDF, MinIO và checkpoint CV): [`LICENSES.md`](./LICENSES.md).
- SBOM CycloneDX của backend, AI, frontend, mobile: [`sbom/`](./sbom/README.md); phiên bản AI khóa ở `ai/requirements.lock`.
- Nguồn tài liệu gốc: `development/docs/legacy_docs/`, đề thi `development/docs/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`, kế hoạch cải thiện `development/docs/improvement_docs/`.
- Chủ đề chính thức: https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html
- Giáo trình DX-OS: https://opendigitransform.gitbook.io/dx-os

## Tài liệu theo module

[`mobile/README.md`](./mobile/README.md), [`ai/README.md`](./ai/README.md), [`sso/README.md`](./sso/README.md), [`bi/README.md`](./bi/README.md), [`minio/README.md`](./minio/README.md), [`chat/README.md`](./chat/README.md), [`portal/README.md`](./portal/README.md), [`data/README.md`](./data/README.md), [`development/docs/legacy_docs/PARCEL_SEASON_CODES.md`](./development/docs/legacy_docs/PARCEL_SEASON_CODES.md).
