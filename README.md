# DX-OS: DX-Farm và DX-Lab

MVP dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA).
Sản phẩm là một nền tảng **Open-Core** dùng chung (định danh và SSO, API Gateway, dữ liệu, workflow, thông báo, cổng AI) theo kiến trúc 4 không gian **H-P-D-I** (Human, Process, Data, Intelligence), trên đó chạy hai ứng dụng nghiệp vụ cắm vào qua interface:

- **DX-Farm**: hệ điều hành chuyển đổi số cho Hợp tác xã Nông nghiệp Số chuyên canh lúa nước (ĐBSCL).
  Luồng xương sống: nông dân báo bệnh bằng ảnh, AI chẩn đoán, hệ thống tra quy tắc xử lý xác định, kỹ sư duyệt, ghi nhận phun thuốc và khóa thu hoạch theo thời gian cách ly (PHI).
- **DX-Lab**: trạm vận hành CNTT của khoa.
  Luồng xương sống: sinh viên báo sự cố có gợi ý, kỹ thuật viên xử lý theo SLA, mượn thiết bị và đặt phòng máy có duyệt, hỏi đáp quy chế, quản trị xem thống kê.

Mỗi lần khởi động chạy một ứng dụng, chọn bằng biến `DXOS_APP` (`farm` hoặc `lab`); core giữ nguyên khi đổi ứng dụng.
Repository giữ tên `DX---Farm`.

Lịch sử thay đổi theo từng module ở [`CHANGELOG.md`](./CHANGELOG.md); cách đóng góp và thêm ứng dụng mới ở [`CONTRIBUTING.md`](./CONTRIBUTING.md).

## Kiến trúc

Ba tầng Open-Core.
Sơ đồ đầy đủ, ranh giới biên dịch và luồng request ở [`development/docs/submission/ARCHITECTURE_OPEN_CORE.md`](./development/docs/submission/ARCHITECTURE_OPEN_CORE.md).

| Tầng | Nội dung |
|---|---|
| 1. Open-Core (headless, không có giao diện người dùng) | `dx-core` của Backend: `core.identity` (workspace, thành viên), `core.workflow` (loại case cấu hình được, hộp duyệt), `core.notification`, `core.storage`, `core.ai`, `core.knowledge`, `core.security`; Keycloak (SSO), cổng API APISIX, PostgreSQL, MinIO |
| 2. H-P-D-I | [H] Keycloak, workspace, vai, Mattermost; [P] workflow duyệt, vòng đời, thông báo; [D] PostgreSQL, MinIO, view BI, Superset; [I] CV, RAG, LangGraph, luật phân loại; giao diện web và mobile |
| 3. Ứng dụng DX-Farm | Thửa, vụ mùa, báo cáo sâu bệnh, chẩn đoán, khai phun thuốc, thu hoạch; web (kỹ sư, admin) và mobile (nông dân) ([`backend/app-farm/README.md`](./backend/app-farm/README.md)) |
| 3. Ứng dụng DX-Lab | Yêu cầu hỗ trợ CNTT có SLA, thiết bị và mượn trả, đặt phòng máy chống trùng giờ, tri thức và hỏi đáp, thống kê; web ([`backend/app-lab/README.md`](./backend/app-lab/README.md)) |

Backend gồm năm module Maven ([`backend/README.md`](./backend/README.md)): `dx-core-api` (interface), `dx-core`, `app-farm`, `app-lab` và `dx-host` (chạy được).
Ứng dụng chỉ phụ thuộc `dx-core-api`, nên compiler chặn việc ứng dụng import ruột core.

Mọi client chỉ gọi gateway APISIX (cổng `9080`).
Gateway kiểm JWT Keycloak, giới hạn tốc độ các thao tác tốn tài nguyên của ứng dụng đang chạy và chuyển tới Backend, Keycloak, MinIO, web và portal.
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

### DX-Farm

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

### DX-Lab

| Tính năng | Trong code | Kiểm thử | Dữ liệu demo |
|---|---|---|---|
| Vai STUDENT, LECTURER, TECHNICIAN, ADMIN; sinh viên chỉ thấy yêu cầu của mình | Có | E2E Lab, kiểm tay | Có (`student1`, `lecturer1`, `tech1`, `admin1`) |
| Yêu cầu hỗ trợ: gửi kèm ảnh, trao đổi công khai và nội bộ, nhận, xử lý, xác nhận và đánh giá, mở lại | Có | E2E Lab, kiểm tay | Có (12 yêu cầu ở mọi trạng thái) |
| SLA theo ưu tiên, cảnh báo ở 80% và đánh dấu quá hạn theo lịch | Có | Kiểm tay với SLA rút ngắn | Có |
| Hai kỹ thuật viên nhận cùng yêu cầu: một bên 409 | Có | E2E Lab | Không áp dụng |
| Gợi ý loại và ưu tiên bằng luật, bài tri thức liên quan | Có | E2E Lab, kiểm tay | Có (10 bài quy chế và hướng dẫn) |
| Mượn thiết bị có hạn, duyệt theo vai, trả hỏng mở nhật ký sửa chữa | Có | E2E Lab, kiểm tay | Có (7 thiết bị, 5 phiếu) |
| Đặt phòng chống trùng giờ ở cơ sở dữ liệu, lịch tuần | Có | E2E Lab, kiểm tay | Có (5 phòng, 5 lịch) |
| Hỏi đáp quy chế có trích nguồn và giấy phép | Có | E2E Lab (`--require-knowledge`) | Có |
| Thống kê SLA, tải kỹ thuật viên, mức dùng phòng; view `vw_lab_*` cho BI | Có | E2E Lab, kiểm tay | Có |

Lab chưa có test tự động riêng ngoài kịch bản E2E; dashboard Superset của Lab và mobile cho Lab nằm ở backlog.

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
| Ứng dụng web | http://localhost:9080 | SSO Keycloak: Farm `engineer1` hoặc `admin1`; Lab `student1`, `lecturer1`, `tech1` hoặc `admin1` |
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
DX-Lab thêm `tickets`, `device-loans`, `devices`, `rooms`, `room-bookings`, `ticket-categories`, `sla-policies`, `lab-stats/summary`.
Một ứng dụng chỉ nạp controller của nó: route của ứng dụng kia trả 404.

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
DX-Lab không có script seed SQL: phòng, thiết bị, yêu cầu, phiếu và lịch mẫu nạp qua API bằng `deploy/demo/seed_demo.py`; tri thức quy chế ở `data/rag/lab/`.
Hạ tầng Postgres (role, database, extension) do các `*Seeder` trong `dx-core`.

## Giấy phép và hồ sơ PoF

- Mã nguồn tự viết: Apache-2.0 (SPDX header ở đầu file Java và Python); toàn văn ở [`LICENSE`](./LICENSE). Riêng thư mục `ai/` phân phối theo AGPL-3.0 vì import Ultralytics, toàn văn ở [`ai/LICENSE`](./ai/LICENSE).
- Rà soát license từng thành phần (kể cả AGPL-3.0 của Ultralytics, MinIO và checkpoint CV): [`LICENSES.md`](./LICENSES.md).
- SBOM CycloneDX của backend, AI, frontend, mobile: [`sbom/`](./sbom/README.md); phiên bản AI khóa ở `ai/requirements.lock`.
- Nguồn tài liệu gốc: `development/docs/legacy_docs/`, đề thi `development/docs/De_thi_PMNM_HaUI_2026_BAN_CHINH_THUC.pdf`, kế hoạch cải thiện `development/docs/improvement_docs/`.
- Chủ đề chính thức: https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html
- Giáo trình DX-OS: https://opendigitransform.gitbook.io/dx-os

## Tài liệu theo module

[`backend/README.md`](./backend/README.md), [`backend/app-farm/README.md`](./backend/app-farm/README.md), [`backend/app-lab/README.md`](./backend/app-lab/README.md), [`mobile/README.md`](./mobile/README.md), [`ai/README.md`](./ai/README.md), [`sso/README.md`](./sso/README.md), [`bi/README.md`](./bi/README.md), [`minio/README.md`](./minio/README.md), [`chat/README.md`](./chat/README.md), [`portal/README.md`](./portal/README.md), [`data/README.md`](./data/README.md), [`development/docs/legacy_docs/PARCEL_SEASON_CODES.md`](./development/docs/legacy_docs/PARCEL_SEASON_CODES.md).
