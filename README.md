# DX-OS: DX-Farm và DX-Lab (Bài dự thi Phần Mềm Nguồn Mở 2026)

**Tiếng Việt** · [English](./README_EN.md)

[![License](https://img.shields.io/badge/License-Apache_2.0-blue.svg)](./LICENSE)
[![AI License](https://img.shields.io/badge/ai%2F-AGPL--3.0-orange.svg)](./ai/LICENSE)

**Đội:** HaUI-ImplSolution · **Trường:** Trường Công nghệ Thông tin và Truyền thông, Đại học Công nghiệp Hà Nội

**Dự án đang chạy:** [khuongdev.io.vn/portal](https://khuongdev.io.vn/portal/)

**Tài liệu:** theo dõi tại [trang docs](https://haui-implsolution.github.io/DX-OS-docs/).

Nền tảng mã nguồn mở dùng chung cho chuyển đổi số, dự thi **OLP Phần mềm nguồn mở 2026** (khối thi DX-OS, VFOSSA).
Một lõi dùng chung lo đăng nhập, dữ liệu, quy trình, thông báo và AI; hai ứng dụng nghiệp vụ được xây trên lõi đó.

## 💡 Giới thiệu

| Ứng dụng | Bài toán | Luồng chính |
|---|---|---|
| **DX-Farm** | Chuyển đổi số cho Hợp tác xã Nông nghiệp Số chuyên canh lúa nước (ĐBSCL) | Nông dân chụp ảnh báo bệnh, AI chẩn đoán, kỹ sư duyệt và hướng dẫn xử lý, ghi nhận phun thuốc, hệ thống chặn thu hoạch khi chưa hết thời gian cách ly |
| **DX-Lab** | Trạm vận hành CNTT của khoa | Sinh viên báo sự cố, kỹ thuật viên xử lý đúng hạn, mượn thiết bị và đặt phòng máy có duyệt, hỏi đáp quy chế, quản trị xem thống kê |

## ✨ Điểm nổi bật

- **Một lõi, nhiều ứng dụng:** Farm và Lab dùng chung một lõi, thêm ứng dụng không phải sửa core.
- **An toàn nghiệp vụ:** các quy tắc quan trọng được kiểm ngay ở Backend và cơ sở dữ liệu, không chỉ ở giao diện.
- **Truy vết:** mọi lượt truy cập, lần đăng nhập, lỗi và thao tác quản trị đều được ghi lại; màn hình giám sát dành cho quản trị viên gom các nguồn này, theo dõi tình trạng dịch vụ và báo sự cố qua kênh chat.
- **Bảo mật:** nhân sự có thể bật xác thực hai bước, tệp tải lên có thể được quét mã độc, thông tin cá nhân được che trước khi gửi sang kênh chat và AI, số lần gọi API bị giới hạn theo địa chỉ ở gateway và theo người dùng ở backend.
- **Sao lưu và khôi phục:** dữ liệu và tệp được sao lưu mã hóa, việc khôi phục được kiểm tra tự động.
- **AI có kiểm soát:** AI chỉ hỗ trợ, trả lời kèm nguồn và từ chối khi câu hỏi ngoài phạm vi hoặc thiếu căn cứ; thông tin thuốc và liều lấy từ dữ liệu chuyên gia đã duyệt, kỹ sư là người quyết định.
- **Chạy một lệnh:** dựng toàn bộ hệ thống kèm dữ liệu demo, có kiểm thử tự động trên môi trường sạch.
- **Nguồn mở minh bạch:** công khai giấy phép và danh sách thành phần phần mềm; mọi tệp mã có header giấy phép, CI kiểm giấy phép của từng thư viện, tự động quét lộ mật khẩu, lỗ hổng của thư viện (kể cả danh mục đã phân giải) và image của bên thứ ba; Dependabot đề xuất cập nhật.
- **Phát hành:** mỗi bản `vX.Y.Z` có gói mã nguồn `.tar.gz`, tệp `.sha256` và SBOM trên trang Releases; image dựng sẵn ở GHCR, cài ra thư mục khác bằng `deploy/install.sh`.

## 🗺️ Kiến trúc tổng thể

![Sơ đồ kiến trúc tổng thể](docs/images/tong_quan.gif)

Người dùng, web hay mobile, chỉ đi qua một Gateway (APISIX) để vào mạng nội bộ.
Backend xử lý nghiệp vụ, lưu ảnh ở SeaweedFS và dữ liệu ở PostgreSQL, nhờ AI Service chẩn đoán và hỏi đáp, gửi thông báo sang Mattermost; Superset đọc dữ liệu để làm báo cáo.
Dịch vụ giám sát chạy riêng, theo dõi lượt truy cập bất thường và tình trạng từng dịch vụ, báo sự cố cho quản trị viên qua Mattermost.
Vai trò từng dịch vụ ở [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## 🧱 Kiến trúc 3 tầng

![Sơ đồ kiến trúc 3 tầng](docs/images/so_do_3_tang.png)

| Tầng | Thành phần |
|---|---|
| 1. Open-Core (không có giao diện người dùng) | Identity (SSO), Workflow, Notification, Storage, AI và Knowledge, Gateway, Security |
| 2. H-P-D-I | Bốn không gian Human, Process, Data, Intelligence dựng từ các module Core, kèm AI, báo cáo và chat |
| 3. Ứng dụng | Farm và Lab |

| Không gian | Farm | Lab |
|---|---|---|
| **H** Human | Tài khoản nông hộ; vai nông dân, kỹ sư, quản trị | Tài khoản lớp và bộ môn; vai sinh viên, giảng viên, kỹ thuật viên, quản trị |
| **P** Process | Duyệt thửa, vụ mùa, chẩn đoán và khai phun; chặn thu hoạch khi chưa hết thời gian cách ly | Yêu cầu hỗ trợ có hạn xử lý, mượn thiết bị có duyệt, đặt phòng chống trùng giờ |
| **D** Data | Cơ sở dữ liệu, kho ảnh, báo cáo tổng hợp, thời tiết | Cơ sở dữ liệu, kho ảnh, thống kê hạn xử lý và mức dùng phòng |
| **I** Intelligence | Nhận diện bệnh từ ảnh, hỏi đáp tài liệu | Gợi ý phân loại có giải thích, hỏi đáp quy chế |

Mọi ứng dụng chỉ đi qua một Gateway duy nhất để kiểm tra đăng nhập, Backend kiểm tra lại lần nữa.
Sơ đồ đầy đủ và luồng xử lý ở [`ARCHITECTURE.md`](./ARCHITECTURE.md).

## 🧩 Tính năng

**DX-Farm:** quản lý nông hộ và thửa ruộng có vị trí, vụ mùa, báo cáo sâu bệnh kèm kiểm tra chất lượng ảnh, chẩn đoán AI và kỹ sư duyệt, khai báo phun thuốc, chặn thu hoạch khi chưa hết thời gian cách ly, nhắc hạn, hộp thông báo, thông báo chat kèm liên kết mở thẳng báo cáo, thời tiết theo thửa, hỏi đáp tài liệu có trích nguồn, báo cáo tổng hợp; ứng dụng di động cho nông dân dùng được cả khi mất mạng.

**DX-Lab:** yêu cầu hỗ trợ có hạn xử lý và trao đổi nội bộ, gợi ý loại và mức ưu tiên, mượn trả thiết bị và nhật ký sửa chữa, đặt phòng máy không trùng giờ, hỏi đáp quy chế trích từ văn bản chính thức của trường, bấm nguồn là mở trang gốc, thống kê.

Mức hoàn thiện và độ phủ kiểm thử từng tính năng ở [`docs/TESTING.md`](./docs/TESTING.md); việc chưa làm theo dõi ở [trang docs](https://haui-implsolution.github.io/DX-OS-docs/).

## 🧰 Công nghệ và giấy phép

| Thành phần | Công nghệ | Giấy phép |
|---|---|---|
| Backend | Java 21, Spring Boot, Flyway | Apache-2.0 |
| AI Service | Python, FastAPI, LangGraph, pgvector, Ollama (Qwen2.5-1.5B) | AGPL-3.0 (mã `ai/`); thư viện MIT, Apache-2.0 |
| CV | EfficientNetV2-M (Torchvision) và crop classifier ONNX | BSD-3-Clause, Apache-2.0, AGPL-3.0 |
| Web, Mobile | React, Vite, Expo | MIT |
| SSO, Gateway | Keycloak, Apache APISIX | Apache-2.0 |
| Dữ liệu, BI | PostgreSQL, SeaweedFS, Apache Superset | PostgreSQL License, Apache-2.0, Apache-2.0 |
| Chat | Mattermost Team Edition (chạy nguyên bản) | AGPL-3.0 |

Mã tự viết theo Apache-2.0 ([`LICENSE`](./LICENSE)), riêng mã nguồn `ai/` theo AGPL-3.0 ([`ai/LICENSE`](./ai/LICENSE)).
Bảng dependency và giấy phép đầy đủ ở [`COPYRIGHT.txt`](./COPYRIGHT.txt); SBOM CycloneDX ở [`sbom/`](./sbom/README.md).

## 🚀 Cài đặt và chạy

### 💻 Yêu cầu hệ thống

| Hạng mục | Tối thiểu | Ghi chú |
|---|---|---|
| Phần mềm | Docker và Docker Compose v2, Git | Không cần cài JDK, Node, Python để chạy (chỉ cần `pillow` cho script demo) |
| RAM | 8 GB | Khuyến nghị 16 GB nếu chạy LLM và build cùng lúc |
| CPU | 4 vCPU | Không cần GPU; Qwen cần CPU có AVX2, máy không có thì đặt `AI_ANSWER_GENERATION=false` |
| Ổ đĩa | 40 GB trống | Image AI khoảng 3 GB, Ollama khoảng 9 GB cùng model Qwen2.5 |
| Cổng | `9080`, `9081`, `8065`; `9082` và `5432` chỉ mở trên chính máy chủ | Đổi bằng biến `*_HOST_PORT` |

### 📝 Các bước

```bash
git clone https://github.com/HaUI-ImplSolution/DX-OS.git && cd DX-OS
python3 deploy/demo/make_ci_env.py      # sinh deploy/.env, mật khẩu và khóa để trống nhận giá trị ngẫu nhiên
python3 -m pip install pillow           # cho script dữ liệu demo
./deploy/doctor.sh                      # kiểm Docker, RAM, ổ đĩa, biến trong deploy/.env và các cổng
./deploy/start.sh                       # dựng, chạy ứng dụng trong DXOS_APP và nạp dữ liệu demo
```

Lần dựng đầu tải và build nhiều thứ, Superset khởi tạo metadata mất thêm vài phút.
Lệnh chờ đến khi các dịch vụ sẵn sàng; nạp dữ liệu demo của Farm chờ dịch vụ AI tối đa 5 phút.
Đổi ứng dụng bằng một lệnh, web đổi theo mà không build lại:

```bash
./deploy/start.sh farm                  # chạy Farm, nạp dữ liệu demo của Farm
./deploy/start.sh lab                   # đổi sang Lab, nạp dữ liệu demo của Lab (khoảng 1 phút)
./deploy/start.sh farm --reset --yes    # xóa deploy/data và dựng lại sạch
docker compose down                     # dừng, giữ dữ liệu trong deploy/data (chạy trong deploy/)
```

Chạy lại `start.sh` không tạo dữ liệu trùng.
Dữ liệu demo nạp riêng xem chi tiết ở [`deploy/demo/README.md`](./deploy/demo/README.md).

### 📦 Chạy ngoài thư mục mã nguồn

```bash
./deploy/install.sh /opt/dx-os          # chép file chạy sang /opt/dx-os, sinh deploy/.env; thêm tag image nếu cần, mặc định latest
cd /opt/dx-os/deploy
docker compose pull                     # lấy image dựng sẵn ở ghcr.io/haui-implsolution/dxos-*, không build
./start.sh
```

Sau khi cài có thể xóa thư mục mã nguồn; cập nhật bằng `./update.sh [tag]` trong `/opt/dx-os/deploy`.
Image do workflow `Release` đẩy lên GHCR; muốn dựng image từ mã nguồn thì dùng các bước ở mục trên.

### 🛠️ Build từ mã nguồn không qua Docker

Mỗi thành phần build và kiểm tra riêng được; dịch vụ chạy cần PostgreSQL, Keycloak và gateway của stack (`./deploy/start.sh`).

| Thành phần | Công cụ | Lệnh |
|---|---|---|
| Backend | JDK 21 (kèm Maven Wrapper) | `cd backend && ./mvnw verify` ra `dx-host/target/dx-host.jar` và `dx-monitor/target/dx-monitor.jar`; test tích hợp cần Docker cho Testcontainers |
| Web | Node 20 | `cd frontend && npm ci && npm run build` ra `dist/` |
| Mobile | Node 20 | `cd mobile && npm ci && npx tsc --noEmit`; chạy và đóng gói ở [`mobile/README.md`](./mobile/README.md) |
| AI Service | Python 3.11 | `cd ai && python -m venv .venv && . .venv/bin/activate && pip install --extra-index-url https://download.pytorch.org/whl/cpu -r requirements-dev.txt && python -m pytest tests` |

Toàn bộ công cụ trên là phần mềm nguồn mở; chi tiết từng thành phần ở README trong thư mục của nó.

### 🔧 Cấu hình

Mọi cấu hình nằm ở `deploy/.env` (mẫu [`deploy/.env.example`](./deploy/.env.example)); thiếu biến nào, Compose dừng và báo tên biến đó.

| Biến | Ý nghĩa |
|---|---|
| `DXOS_APP` | Ứng dụng chạy: `farm` (mặc định) hoặc `lab` |
| `POSTGRES_*`, `S3_*`, `KEYCLOAK_*`, `SUPERSET_*`, `MATTERMOST_*` | Tài khoản và mật khẩu từng dịch vụ, để trống trong mẫu, không commit |
| `BACKEND_TO_AI_API_KEY`, `AI_TO_BACKEND_API_KEY` | Khóa dịch vụ hai chiều giữa Backend và AI |
| `PUBLIC_HOST` | Địa chỉ trình duyệt dùng để vào hệ thống: `localhost` khi dev, IP hoặc tên miền của server khi triển khai; `GATEWAY_PUBLIC_URL`, `SUPERSET_PUBLIC_URL` và các địa chỉ trong thông báo chat tự theo biến này |
| `MATTERMOST_WEBHOOK_URL_*` | Ghi đè webhook từng kênh chat; để trống thì dùng webhook tự tạo khi dựng stack |
| `OLLAMA_MODEL` | Model LLM, mặc định `qwen2.5:1.5b` |

Cấu hình riêng của từng ứng dụng ở [`backend/app-farm/README.md`](./backend/app-farm/README.md) và [`backend/app-lab/README.md`](./backend/app-lab/README.md).

## 🌐 Chạy thử

| Dịch vụ | Địa chỉ | Đăng nhập |
|---|---|---|
| Ứng dụng web | https://khuongdev.io.vn | SSO Keycloak, tài khoản bên dưới |
| Portal (trạng thái dịch vụ) | https://khuongdev.io.vn/portal/ | Không cần |
| Giám sát và truy vết | https://khuongdev.io.vn/monitor/ | SSO Keycloak, chỉ vai ADMIN (`admin1`) |
| Swagger | https://khuongdev.io.vn/swagger-ui.html | Access token Keycloak, bấm Authorize |
| Keycloak Admin | https://khuongdev.io.vn/admin | `KEYCLOAK_ADMIN_USERNAME` / `KEYCLOAK_ADMIN_PASSWORD` |
| BI (Superset) | https://bi.khuongdev.io.vn | Cloudflare Access, rồi SSO Keycloak; `admin1` vào trang quản trị |
| Quản trị lưu ảnh (SeaweedFS) | https://files.khuongdev.io.vn | Cloudflare Access, rồi `S3_ACCESS_KEY` / `S3_SECRET_KEY` |
| Chat (Mattermost) | https://chat.khuongdev.io.vn | `MATTERMOST_ADMIN_USERNAME` / `MATTERMOST_ADMIN_PASSWORD` |

### 🌐 Máy chủ công khai

Máy chủ công khai chạy sau Cloudflare Tunnel, không mở cổng vào.
Bật bằng overlay [`deploy/docker-compose.cloudflared.yml`](./deploy/docker-compose.cloudflared.yml) (hướng dẫn ở đầu file).
Khái niệm và cấu hình Cloudflare theo dõi ở [trang docs](https://haui-implsolution.github.io/DX-OS-docs/); xem log, giới hạn tốc độ, audit, sao lưu và khôi phục ở [`deploy/OPERATIONS.md`](./deploy/OPERATIONS.md).
Script [`deploy/online-check.sh`](./deploy/online-check.sh) gọi các đường công khai, chạy bằng cron trên máy chủ và báo sang Mattermost khi không truy cập được.

### 🔑 Tài khoản demo

`seed_demo.py` tạo các tài khoản này trong realm `dxos` chỉ để demo và chấm thi; đổi mật khẩu hoặc xóa trước khi dùng cho dữ liệu thật.

| Ứng dụng | Username | Mật khẩu | Vai |
|---|---|---|---|
| Farm | `farmer1` | `farmer123` | FARMER (chỉ app mobile) |
| Farm | `engineer1` | `engineer123` | ENGINEER |
| Farm, Lab | `admin1` | `admin123` | ADMIN |
| Lab | `student1`, `student2` | `student123` | STUDENT |
| Lab | `lecturer1` | `lecturer123` | LECTURER |
| Lab | `tech1`, `tech2` | `tech1234` | TECHNICIAN |

## 🔌 API

- Swagger UI ở `/swagger-ui.html`, OpenAPI JSON ở `/v3/api-docs`; tài liệu sinh từ annotation springdoc và chỉ có API của ứng dụng đang chạy (`DXOS_APP`).
- Mọi lời gọi đi qua gateway `https://khuongdev.io.vn/api/v1/...` kèm `Authorization: Bearer <access token Keycloak>`; chỉ `GET /api/v1/system/status` và danh mục công khai của ứng dụng không cần token.
- Response bọc trong `ApiResponse`; lỗi có `errorCode` ổn định, lỗi nghiệp vụ trả `422` và xung đột đồng thời trả `409`.
- Gọi quá giới hạn số lần trả `429` kèm `Retry-After`; các lệnh tạo mới nhận header `Idempotency-Key`.
- Để thử: bấm **Authorize** trong Swagger với access token, hoặc dùng `deploy/demo/dxos_client.py` (đăng nhập Authorization Code + PKCE như app thật).

| Nhóm | Đường dẫn chính |
|---|---|
| Core dùng chung | `/api/v1/system/status`, `/api/v1/app` (manifest: vai, menu), `/api/v1/workspaces`, `/api/v1/workflow-cases` (`assign`, `approve`, `reject`, `events`), `/api/v1/notifications`, `/api/v1/audit-logs` (ADMIN) |
| Farm | `/api/v1/households`, `/api/v1/parcels`, `/api/v1/crop-seasons`, `/api/v1/disease-reports`, `/api/v1/treatment-rules`, `/api/v1/knowledge/ask`; xem [`backend/app-farm/README.md`](./backend/app-farm/README.md#api) |
| Lab | `/api/v1/tickets`, `/api/v1/device-loans`, `/api/v1/room-bookings`, `/api/v1/lab-stats/summary`, `/api/v1/knowledge/ask`; xem [`backend/app-lab/README.md`](./backend/app-lab/README.md#api) |
| AI Service | Chỉ Backend gọi được trong mạng nội bộ, không có route ở gateway; xem [`ai/README.md`](./ai/README.md#api) |

## 🧪 Kiểm thử

- Backend: `./mvnw verify` trong `backend` (cần Docker cho Testcontainers, gồm luật phân lớp `ArchitectureTest`); AI: `pytest` trong `ai/`.
- Web và mobile: `npx tsc --noEmit`.
- Giấy phép: `python3 .github/scripts/check_licenses.py` kiểm header của mọi tệp mã và giấy phép của từng thư viện trong `sbom/`.
- E2E trên stack sạch: `python3 deploy/demo/e2e.py [--app farm|lab]`; CI chạy workflow `E2E` cho cả hai ứng dụng.
- Đánh giá truy xuất RAG và thử tải retrieval: `./ai/evaluate.sh quality|performance|all`, chạy riêng với stack Farm/Lab; xem [`ai/README.md`](./ai/README.md#đánh-giá-truy-xuất-rag).

Hướng dẫn kiểm thử đầy đủ ở [`docs/TESTING.md`](./docs/TESTING.md).

## 🐛 Xử lý sự cố

| Dấu hiệu | Cách xử lý |
|---|---|
| Compose báo thiếu biến | Chạy `./deploy/doctor.sh` để liệt kê mọi biến thiếu hoặc để trống, rồi điền vào `deploy/.env` |
| Web hoặc API trả 401 sau khi dựng lại | Đăng xuất, đăng nhập lại (trình duyệt giữ token của lần dựng trước) |
| Web hiện giao diện app cũ sau khi đổi ứng dụng | Tải lại cứng, đợi `/api/v1/system/status` báo đúng app |
| Hỏi đáp không có kết quả | Kiểm `docker compose logs rag-ingest`; job nạp tri thức chưa xong hoặc lỗi |
| Hết dung lượng khi dựng | `docker system df`, dọn image và cache không dùng |

## 📁 Cấu trúc repository

| Thư mục | Nội dung |
|---|---|
| [`backend/`](./backend/README.md) | `dx-core-api`, `dx-core`, `app-farm`, `app-lab`, `dx-host`, `dx-monitor` |
| [`frontend/`](./frontend/README.md) · [`mobile/`](./mobile/README.md) | Web React (hai ứng dụng) · Mobile Expo cho nông dân |
| [`ai/`](./ai/README.md) | AI Service: CV, RAG, LangGraph |
| [`data/`](./data/README.md) | Dữ liệu có cấu trúc, corpus RAG, bộ đề đánh giá RAG, script sinh seed |
| [`deploy/`](./deploy) | Docker Compose, gateway, `start.sh`, `doctor.sh`, dữ liệu demo và E2E; xem log truy cập ở [`OPERATIONS.md`](./deploy/OPERATIONS.md) |
| [`sso/`](./sso/README.md) · [`bi/`](./bi/README.md) · [`chat/`](./chat/README.md) · [`seaweedfs/`](./seaweedfs/README.md) · [`portal/`](./portal/README.md) | Keycloak, Superset, Mattermost, SeaweedFS, portal |
| [`docs/`](./docs) | Hướng dẫn kiểm thử và ảnh sơ đồ; các tài liệu còn lại ở [trang docs](https://haui-implsolution.github.io/DX-OS-docs/) |

Tài liệu gốc: [`ARCHITECTURE.md`](./ARCHITECTURE.md) · [`CHANGELOG.md`](./CHANGELOG.md) · [`COPYRIGHT.txt`](./COPYRIGHT.txt) · [Trang docs](https://haui-implsolution.github.io/DX-OS-docs/).

## 🤝 Đóng góp

- **Báo lỗi:** [tạo bug report](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=bug_report.yml).
- **Đề xuất tính năng:** [tạo feature request](https://github.com/HaUI-ImplSolution/DX-OS/issues/new?template=feature_request.yml).
- **Gửi mã:** đọc [`CONTRIBUTING.md`](./CONTRIBUTING.md) và tuân theo [`CODE_OF_CONDUCT.md`](./CODE_OF_CONDUCT.md).

## 📞 Liên hệ

| Vai trò | Tên | Liên hệ |
|---|---|---|
| Người phát triển | Phạm Minh Khương | pkhuong535@gmail.com |
| Người phát triển | Nguyễn Đình Duy Khánh | dinhkhanh19042006@gmail.com |
| Người phát triển | Trần Quang Tùng | tung51652@gmail.com |

## 📜 Giấy phép

Apache License 2.0, xem [`LICENSE`](./LICENSE); thư mục `ai/` theo AGPL-3.0, xem [`ai/LICENSE`](./ai/LICENSE) và [`COPYRIGHT.txt`](./COPYRIGHT.txt).

Tham khảo: [chủ đề cuộc thi](https://vfossa.vn/tin-tuc/gioi-thieu-mo-hinh-dx-os-va-chu-de-cuoc-thi-phan-mem-nguon-mo-olp-2026-761.html) · [giáo trình DX-OS](https://opendigitransform.gitbook.io/dx-os)
