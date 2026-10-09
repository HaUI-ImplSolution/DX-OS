# Rà soát license thành phần (PoF — tiêu chí loại trừ)

Tiêu chí PoF của OLP yêu cầu **mọi thành phần trong stack phải dùng license OSI-approved** (xem [danh sách OSI](https://opensource.org/licenses)).
Bảng dưới liệt kê từng thành phần đang thực sự chạy trong repo (không tính thành phần đã comment-out/chưa dùng), license, và tình trạng OSI-approved.

## Backend (`backend/DX-Farm`, Maven)

| Thành phần | License | OSI-approved | Ghi chú |
|---|---|---|---|
| Spring Boot / Spring Framework / Spring Security (`org.springframework*`) | Apache-2.0 | ✅ | Bao gồm `spring-boot-starter-oauth2-resource-server` (xác thực token Keycloak) |
| PostgreSQL JDBC Driver (`org.postgresql:postgresql`) | BSD-2-Clause (PostgreSQL License) | ✅ | |
| springdoc-openapi | Apache-2.0 | ✅ | |
| Lombok | MIT | ✅ | `optional=true`, không đóng gói vào runtime |
| PostgreSQL (server) và pgvector (`pgvector/pgvector:pg16` image) | PostgreSQL License | ✅ | Một instance chứa cả database nghiệp vụ lẫn AI Vector DB |
| Flyway (`org.flywaydb:flyway-core`, `flyway-database-postgresql`) | Apache-2.0 | ✅ | Migration schema (`db/migration`), chạy lúc backend khởi động |
| Testcontainers (`org.testcontainers`, scope `test`) | MIT | ✅ | Chỉ dùng khi chạy test, khởi động PostgreSQL thật; không đóng gói vào runtime |
| JUnit 5, Mockito, AssertJ (qua `spring-boot-starter-test`, scope `test`) | EPL-2.0 / MIT / Apache-2.0 | ✅ | Chỉ dùng khi chạy test |
| MinIO Java SDK (`io.minio:minio`) | Apache-2.0 | ✅ | Client S3 để lưu ảnh và ký link có hạn (`core.storage`) |
| Hibernate Validator (`spring-boot-starter-validation`) | Apache-2.0 | ✅ | Kiểm tra dữ liệu request |
| Resilience4j (`resilience4j-spring-boot3`) | Apache-2.0 | ✅ | Circuit breaker khi gọi AI Service |
| AspectJ (`spring-boot-starter-aspectj`) | EPL-2.0 | ✅ | Hỗ trợ method security và circuit breaker |
| formatter-maven-plugin (Revelc) | Apache-2.0 | ✅ | Chỉ dùng khi build, kiểm định dạng mã |
| Eclipse Temurin (OpenJDK 21, base image build và runtime) | GPL-2.0 with Classpath Exception | ✅ | Chạy nguyên bản, không sửa mã nguồn |

Các dependency đã bị comment-out trong `pom.xml` (Redis, Mail, AMQP, Apache POI, totp, owasp-java-html-sanitizer) **chưa thực sự dùng** — không tính vào rà soát cho tới khi được bật lại.
Khi bật lại cần rà license tương ứng: POI Apache-2.0 OK, các cái còn lại cũng Apache-2.0 hoặc MIT, đều OSI-approved.

## Frontend (`frontend`, npm)

| Thành phần | License | OSI-approved |
|---|---|---|
| React / React DOM | MIT | ✅ |
| Vite | MIT | ✅ |
| TypeScript | Apache-2.0 | ✅ |
| @vitejs/plugin-react | MIT | ✅ |
| oidc-client-ts | MIT | ✅ |
| react-router-dom | MIT | ✅ |
| nginx (ảnh serve production, `frontend/Dockerfile`) | BSD-2-Clause (nginx license) | ✅ |

## Mobile (`mobile`, npm)

| Thành phần | License | OSI-approved |
|---|---|---|
| Expo (`expo`, `expo-router`, `expo-auth-session`, `expo-secure-store`, `expo-image-picker`, `expo-image-manipulator`, `expo-web-browser`, `expo-build-properties`, `expo-constants`, `expo-linking`, `expo-crypto`, `expo-file-system`, `expo-location`, `expo-status-bar`) | MIT | ✅ |
| React / React DOM / React Native | MIT | ✅ |
| @react-native-async-storage/async-storage / @react-native-community/netinfo | MIT | ✅ |
| react-native-screens / react-native-safe-area-context | MIT | ✅ |
| TypeScript | Apache-2.0 | ✅ |

## Portal (`portal`)

| Thành phần | License | OSI-approved |
|---|---|---|
| nginx (ảnh serve trang tĩnh, `portal/Dockerfile`) | BSD-2-Clause (nginx license) | ✅ |

## AI Service (`ai`)

| Thành phần | License | OSI-approved | Ghi chú |
|---|---|---|---|
| Mã nguồn AI do DX-Farm tự viết | Apache-2.0 | ✅ | Theo `LICENSE` ở gốc repo |
| FastAPI, Pydantic, SQLAlchemy, pgvector, LangGraph, LangChain, python-docx, Ruff | MIT | ✅ | Dependency trực tiếp; Ruff chỉ dùng khi phát triển |
| Uvicorn, HTTPX, python-dotenv, pypdf | BSD-3-Clause | ✅ | Dependency trực tiếp |
| python-multipart, sentence-transformers | Apache-2.0 | ✅ | Dependency trực tiếp |
| psycopg / psycopg-binary | LGPL-3.0-only | ✅ | Dependency trực tiếp |
| PyMuPDF | AGPL-3.0 hoặc Artifex commercial | ✅ với AGPL-3.0 | Repo dùng nhánh open-source AGPL-3.0 |
| PyTorch | Apache-2.0 và các license nguồn mở đi kèm | ✅ | Dependency trực tiếp |
| [ONNX Runtime](https://github.com/microsoft/onnxruntime/blob/main/LICENSE) | MIT | ✅ | Crop classifier CPU 1.30.0; FlatBuffers Apache-2.0, Protobuf BSD-3-Clause |
| [Crop classifier YOLO26s-cls](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier) | AGPL-3.0 (metadata artifact) | ✅ | Artifact `ai/weights/crop_classifier.onnx` được quản lý bằng Git, từ revision `c339988a6eceb5e22943ee27e4731a1a1dc9160a`; model card repository ghi Apache-2.0, giữ điều kiện AGPL của artifact |
| NumPy | BSD-3-Clause và các license nguồn mở đi kèm | ✅ | Dependency trực tiếp |
| Pillow | MIT-CMU | ✅ | Dependency trực tiếp |
| [OpenCV](https://opencv.org/license/) | Apache-2.0 | ✅ | Dependency runtime do Ultralytics kéo theo; áp dụng cho OpenCV 4.5.0 trở lên |
| [Ultralytics](https://www.ultralytics.com/license) | AGPL-3.0 | ✅ | Runtime `8.3.40`; checkpoint ghi phiên bản train `8.4.165` |
| [Ollama](https://github.com/ollama/ollama) (`ollama/ollama:0.34.2`) | MIT | ✅ | Runtime phục vụ LLM trong Compose |
| [Qwen2.5 1.5B](https://ollama.com/library/qwen2.5) | Apache-2.0 | ✅ | Model mặc định `qwen2.5:1.5b` |
| [`paraphrase-multilingual-MiniLM-L12-v2`](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2) | Apache-2.0 | ✅ | Model embedding mặc định |
| `YOLOv8n-RiceLeafBD` / `ai/weights/best.pt` | AGPL-3.0 | ✅ | Phiên bản `3.1.0`, fine-tune từ pretrained `yolov8n.pt` |
| RiceLeafDiseaseBD | CC BY 4.0 | N/A | License dữ liệu, không phải software license do OSI phê duyệt |

**Checkpoint gốc:** `YOLOv8n-RiceLeafBD` phiên bản `3.0.0`, do
[`@nddKhanh`](https://github.com/nddKhanh) fine-tune trong
[notebook Kaggle](https://www.kaggle.com/code/baodark/dx-farm-8169e1/notebook), được nhóm
đồng ý công khai theo AGPL-3.0. Bản thay thế `3.1.0` do người dùng cung cấp trong nhánh này; giữ license model hiện có. Checkpoint nằm tại `ai/weights/best.pt`; toàn văn license
nằm tại [`ai/MODEL_LICENSE`](./ai/MODEL_LICENSE).

**Dataset:** [RiceLeafDiseaseBD Version 3](https://doi.org/10.17632/86s4jzj2m4.3), theo
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/), của Mohammad Mehedi Hasan Munna,
Toriqul Islam, Smita Saha, Iqbal Hossain Safy, Saiful Bari, Jasmin Akter, Md Momtajur
Rahman, Riya Akter, Nahid Hossain Antu, Acramul Haque Kabir, Reefka Fabliha Tulona,
Ashraful Islam và M Ashraful Amin. Thay đổi: chỉ dùng 5 lớp bệnh, resize ảnh về `640` và
áp dụng augmentation; lớp `Healthy` không được dùng làm class detection, còn
`Rice___healthy` chỉ là fallback runtime.

Ultralytics và PyMuPDF áp dụng AGPL-3.0 cho phần tương ứng; mã nguồn DX-Farm tự viết vẫn
giữ Apache-2.0.

## Deploy (`deploy`)

| Thành phần | License | OSI-approved | Ghi chú |
|---|---|---|---|
| Apache Superset (`apache/superset` image) | Apache-2.0 | ✅ | BI dashboard — xem `bi/` |
| Authlib (pip, cài lúc container Superset khởi động cho SSO OAuth) | BSD-3-Clause | ✅ | |
| Python (`python:3.11-slim`, base image của AI Service) | PSF-2.0 | ✅ | |
| Node.js (`node:20-alpine`, chỉ ở bước build frontend) | MIT | ✅ | |
| CycloneDX tooling (`cyclonedx-bom`, `@cyclonedx/cyclonedx-npm`, `cyclonedx-maven-plugin`) | Apache-2.0 | ✅ | Chỉ dùng để sinh SBOM ở `sbom/`, không đóng gói vào sản phẩm |
| Mattermost (`mattermost/mattermost-team-edition` image) | AGPLv3 (server core) | ✅ | Xem mục riêng "Rà soát license Mattermost" dưới đây — không đơn giản như một dòng |
| Keycloak (`quay.io/keycloak/keycloak` image) | Apache-2.0 | ✅ | Identity Provider cho SSO thật — xem `sso/README.md` |
| Apache APISIX (`apache/apisix` image, chế độ standalone) | Apache-2.0 | ✅ | Gateway: route, kiểm JWT, giới hạn tốc độ — xem `deploy/apisix/` |
| MinIO server (build từ source tag `RELEASE.2025-10-15T17-29-55Z`, `minio/Dockerfile`) | AGPL-3.0 | ✅ | Lưu ảnh báo cáo. Image chính thức không còn phát hành nên build từ source; repo upstream đã archived. Chạy nguyên bản, không sửa mã nguồn |
| AWS CLI (`amazon/aws-cli` image, chỉ dùng trong `minio/migrate-uploads.sh`) | Apache-2.0 | ✅ | Công cụ chuyển ảnh cũ vào bucket, không chạy trong stack |
| Docker / Docker Compose (tooling, không đóng gói vào sản phẩm) | Apache-2.0 | ✅ | |
| gitleaks (`ghcr.io/gitleaks/gitleaks`, quét secret trong CI) | MIT | ✅ | Công cụ CI, không đóng gói vào sản phẩm; cấu hình ở `.gitleaks.toml` |
| pytest (`ai/requirements-dev.txt`) | MIT | ✅ | Chỉ dùng để chạy test AI, không nằm trong image runtime |

## Rà soát license Mattermost (kênh chat nội bộ)

Không phải một license duy nhất như các thành phần khác — chia theo thư mục trong repo nguồn Mattermost:

- Server core (toàn bộ trừ `server/enterprise/`): **AGPLv3**, OSI-approved.
- `server/enterprise/` (tính năng trả phí — SSO nâng cao, compliance, LDAP...): **Mattermost Source Available License**, **không OSI-approved**.
- Bản Linux binary Team Edition mà chính Mattermost, Inc. compile sẵn: họ cấp phép lại dưới **MIT** — chỉ áp dụng cho đúng binary đó, không phải toàn bộ mã nguồn.

Quyết định: dùng thẳng image chính thức `mattermost/mattermost-team-edition` (không build lại từ source), không bật/không đụng `server/enterprise/`.
Chi tiết đầy đủ ở [`chat/README.md`](./chat/README.md).

## Quyết định về n8n

Không dùng n8n (Sustainable Use License, không OSI-approved) — chưa từng tích hợp vào code.
LangGraph (Apache-2.0) đảm nhận vai trò orchestrator cho luồng vision → lookup → LLM diễn đạt.

## SBOM và phiên bản

- SBOM định dạng CycloneDX của backend, AI, frontend và mobile nằm ở [`sbom/`](./sbom/README.md).
- `ai/requirements.lock` khóa phiên bản chính xác mọi gói Python của AI Service; Dockerfile cài `requirements.txt` với file này làm constraints.

## Việc còn thiếu

- SPDX header đã có ở file Java của Backend và file Python; file TypeScript chưa có.
- Chưa có inventory thành phần bundle trong từng Docker image phát hành (chỉ có dependency khai báo ở trên).
