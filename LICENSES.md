# Thành phần và giấy phép

Bảng các thành phần đang dùng trong DX-OS: giấy phép và vai trò.
Danh mục đầy đủ theo phiên bản ở [`sbom/`](./sbom/README.md) (CycloneDX); `ai/requirements.lock` khóa phiên bản gói Python.

## Giấy phép của mã nguồn

| Phần | Giấy phép | Ghi chú |
|---|---|---|
| `backend`, `frontend`, `mobile`, `deploy`, `bi`, `portal`... | Apache-2.0 ([`LICENSE`](./LICENSE)) | SPDX header ở file Java và Python |
| Mã nguồn `ai/` | AGPL-3.0 ([`ai/LICENSE`](./ai/LICENSE)) | Giữ giấy phép mã nguồn AI; giấy phép trọng số liệt kê riêng bên dưới |

AI Service là tiến trình riêng, chỉ giao tiếp với Backend qua HTTP nên Backend giữ Apache-2.0.

## Backend (Maven)

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| Spring Boot, Spring Framework, Spring Security | Apache-2.0 | Khung ứng dụng, xác thực token Keycloak |
| springdoc-openapi | Apache-2.0 | Swagger UI và OpenAPI |
| Thymeleaf (cùng attoparser, unbescape) | Apache-2.0 | Khung trang của màn hình giám sát |
| Spring Security OAuth2 Client, Nimbus OAuth 2.0 SDK (cùng json-smart, accessors-smart, content-type, lang-tag, jcip-annotations) | Apache-2.0 | Đăng nhập Keycloak của màn hình giám sát |
| ASM | BSD-3-Clause | Phụ thuộc của json-smart |
| Flyway | Apache-2.0 | Migration schema |
| MinIO Java SDK | Apache-2.0 | Lưu ảnh và ký link có hạn |
| Hibernate Validator | Apache-2.0 | Kiểm dữ liệu request |
| Resilience4j | Apache-2.0 | Circuit breaker khi gọi AI Service |
| AspectJ | EPL-2.0 | Method security |
| PostgreSQL JDBC Driver | BSD-2-Clause | Kết nối cơ sở dữ liệu |
| Lombok | MIT | Sinh mã lúc build, không vào runtime |
| Testcontainers, JUnit 5, Mockito, AssertJ | MIT, EPL-2.0, Apache-2.0 | Chỉ khi chạy test |
| formatter-maven-plugin | Apache-2.0 | Chỉ khi build, kiểm định dạng mã |
| Eclipse Temurin (OpenJDK 21) | GPL-2.0 with Classpath Exception | Base image build và runtime, chạy nguyên bản |

## Web và mobile (npm)

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| React, React DOM, react-router-dom, Vite, `@vitejs/plugin-react` | MIT | Web |
| oidc-client-ts | MIT | Đăng nhập OIDC cho web |
| TypeScript | Apache-2.0 | Web và mobile |
| Expo và các module `expo-*`, React Native | MIT | Mobile |
| `@react-native-async-storage/async-storage`, `@react-native-community/netinfo`, `react-native-screens`, `react-native-safe-area-context` | MIT | Mobile: hàng đợi offline, điều hướng |
| nginx | BSD-2-Clause | Image phục vụ web và portal |

## AI Service (Python)

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| FastAPI, Pydantic, SQLAlchemy, pgvector, LangGraph, LangChain, python-docx | MIT | API, RAG, luồng suy luận |
| Uvicorn, HTTPX, python-dotenv, pypdf | BSD-3-Clause | Máy chủ, HTTP client, nạp tài liệu |
| rank-bm25 0.2.2 | Apache-2.0 | BM25 cho truy xuất chatbot |
| python-multipart, sentence-transformers | Apache-2.0 | Nhận upload, embedding |
| ir-measures 0.3.3 | Apache-2.0 | Chấm Recall và nDCG trong môi trường đánh giá riêng |
| pytrec-eval-terrier 0.5.11 | MIT | Provider tính metric của ir-measures, chỉ dùng để đánh giá |
| langfuse Python SDK 5.0.4 | MIT | Publish dataset, experiment và score trong môi trường đánh giá riêng |
| Grafana k6 2.3.0 | AGPL-3.0 | Binary thử tải trong image công cụ đánh giá riêng; không đóng gói trong runtime/CI ([license](https://github.com/grafana/k6/blob/v2.3.0/LICENSE.md)) |
| psycopg | LGPL-3.0-only | Kết nối PostgreSQL |
| PyTorch, Torchvision | BSD-3-Clause | Chạy mô hình EfficientNetV2-M |
| ONNX Runtime 1.30.0 | MIT | Chạy crop classifier trên CPU |
| NumPy | BSD-3-Clause | Tính toán |
| Pillow | MIT-CMU | Xử lý ảnh |
| Ruff, pytest | MIT | Chỉ khi phát triển và test |

## Mô hình và dữ liệu

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| DX-Farm EfficientNetV2-M 4.0.0 (`ai/weights/dx-farm-efficientnetv2m/`) | Apache-2.0 cho phần fine-tune của nhóm ([`LICENSE`](./LICENSE)) | Healthy và 5 bệnh; trọng số khởi tạo giữ [điều khoản pretrained của Torchvision](https://github.com/pytorch/vision#pre-trained-model-license) |
| [Crop classifier YOLO26s-cls](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier) (`ai/weights/crop_classifier.onnx`) | AGPL-3.0 | Kiểm ảnh là lúa trước model bệnh |
| [Qwen2.5 1.5B](https://ollama.com/library/qwen2.5) | Apache-2.0 | LLM diễn đạt, chạy qua Ollama |
| [`paraphrase-multilingual-MiniLM-L12-v2`](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2) | Apache-2.0 | Embedding cho RAG |
| [mMARCO multilingual MiniLM reranker](https://huggingface.co/cross-encoder/mmarco-mMiniLMv2-L12-H384-v1) | Apache-2.0 | Xếp hạng lại evidence chatbot, tách khỏi chẩn đoán |
| [RiceLeafDiseaseBD v3](https://doi.org/10.17632/86s4jzj2m4.3) | CC BY 4.0 | Dữ liệu huấn luyện model bệnh |
| Tri thức DX-Lab (`data/rag/lab/`) | © Đại học Công nghiệp Hà Nội | Trích nội quy, quy định và hướng dẫn công khai trên các trang haui.edu.vn, kèm bản PDF gốc |
| [Open-Meteo](https://open-meteo.com) | CC BY 4.0 | Thời tiết theo thửa, snapshot dự phòng và chart thời tiết của Superset |
| [OpenFreeMap](https://openfreemap.org) vector tile | ODbL (dữ liệu OpenStreetMap), CC BY 4.0 (OpenMapTiles) | Nền bản đồ của dashboard Superset |

Nguồn dữ liệu: RiceLeafDiseaseBD Version 3 của Mohammad Mehedi Hasan Munna, Toriqul Islam, Smita Saha, Iqbal Hossain Safy, Saiful Bari, Jasmin Akter, Md Momtajur Rahman, Riya Akter, Nahid Hossain Antu, Acramul Haque Kabir, Reefka Fabliha Tulona, Ashraful Islam và M Ashraful Amin, theo [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Thay đổi: EfficientNetV2-M dùng 6 lớp, resize ảnh 480×480 và augmentation.
Checkpoint do người huấn luyện của nhóm cung cấp, khởi tạo từ `EfficientNet_V2_M_Weights`; phần fine-tune theo Apache-2.0, giữ điều khoản pretrained Torchvision.
Dữ liệu mẫu của Lab (phòng, thiết bị, yêu cầu) là dữ liệu giả lập, không chứa người thật.

## Hạ tầng và công cụ

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| PostgreSQL và pgvector (`pgvector/pgvector:pg16`) | PostgreSQL License | Dữ liệu nghiệp vụ và vector RAG |
| Keycloak | Apache-2.0 | Định danh, SSO |
| Apache APISIX (standalone) | Apache-2.0 | API Gateway: route, kiểm JWT, giới hạn tốc độ |
| MinIO (build từ source tag `RELEASE.2025-10-15T17-29-55Z`) | AGPL-3.0 | Lưu ảnh và tệp đính kèm; chạy nguyên bản, không sửa mã |
| Apache Superset | Apache-2.0 | BI dashboard |
| Authlib | BSD-3-Clause | SSO OAuth cho Superset |
| Mattermost Team Edition | AGPL-3.0 | Kênh chat nhận thông báo; chạy nguyên bản, không dùng `server/enterprise/` |
| Ollama 0.34.2 | MIT | Phục vụ LLM |
| cloudflared (`cloudflare/cloudflared`) | Apache-2.0 | Tunnel ra Cloudflare, chỉ khi dùng overlay `docker-compose.cloudflared.yml` |
| Python 3.11 (`python:3.11-slim`) | PSF-2.0 | Base image AI Service |
| Node.js 20 (`node:20-alpine`) | MIT | Chỉ khi build web |
| AWS CLI | Apache-2.0 | Chỉ dùng trong `minio/migrate-uploads.sh` |
| Langfuse server/worker 4.70.1 | MIT cho phần core; phần EE theo điều khoản riêng | Giao diện đánh giá local, không bật tính năng Enterprise ([license](https://github.com/langfuse/langfuse/blob/main/LICENSE)) |
| ClickHouse 25.12 | Apache-2.0 | Lưu kết quả Langfuse trong môi trường đánh giá ([license](https://github.com/ClickHouse/ClickHouse/blob/master/LICENSE)) |
| Redis 7.x (`redis:7`) | RSALv2 hoặc SSPLv1 (từ 7.4) | Queue/cache Langfuse local ([license](https://redis.io/legal/licenses/)) |
| Docker CLI (Moby) | Apache-2.0 | Chỉ công cụ thử tải lấy mẫu container do bộ chạy tạo ([license](https://github.com/docker/cli/blob/master/LICENSE)) |
| gitleaks | MIT | Quét secret trong CI |
| Trivy 0.58.1 | Apache-2.0 | Quét lỗ hổng phụ thuộc trong CI, chạy trong container |
| ClamAV 1.5.4 (`clamav/clamav`) | GPL-2.0 | Quét mã độc tệp tải lên; dịch vụ riêng ở profile `av`, backend nói chuyện qua mạng, không liên kết mã |
| restic 0.17.3 | BSD-2-Clause | Sao lưu mã hóa của `deploy/backup.sh`, chạy trong container, không đóng gói trong runtime ([license](https://github.com/restic/restic/blob/master/LICENSE)) |
| CycloneDX (`cyclonedx-bom`, `@cyclonedx/cyclonedx-npm`, `cyclonedx-maven-plugin`) | Apache-2.0 | Sinh SBOM |
