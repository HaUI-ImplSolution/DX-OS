# Thành phần và giấy phép

Bảng các thành phần đang dùng trong DX-OS: giấy phép và vai trò.
Danh mục đầy đủ theo phiên bản ở [`sbom/`](./sbom/README.md) (CycloneDX); `ai/requirements.lock` khóa phiên bản gói Python.

## Giấy phép của mã nguồn

| Phần | Giấy phép | Ghi chú |
|---|---|---|
| `backend`, `frontend`, `mobile`, `deploy`, `bi`, `portal`... | Apache-2.0 ([`LICENSE`](./LICENSE)) | SPDX header ở file Java và Python |
| `ai/`, trọng số `ai/weights/*` | AGPL-3.0 ([`ai/LICENSE`](./ai/LICENSE)) | Mã `ai/` import Ultralytics (AGPL-3.0) nên phân phối theo AGPL-3.0 |

AI Service là tiến trình riêng, chỉ giao tiếp với Backend qua HTTP nên Backend giữ Apache-2.0.

## Backend (Maven)

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| Spring Boot, Spring Framework, Spring Security | Apache-2.0 | Khung ứng dụng, xác thực token Keycloak |
| springdoc-openapi | Apache-2.0 | Swagger UI và OpenAPI |
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
| python-multipart, sentence-transformers | Apache-2.0 | Nhận upload, embedding |
| psycopg | LGPL-3.0-only | Kết nối PostgreSQL |
| PyTorch | Apache-2.0 | Chạy mô hình |
| ONNX Runtime 1.30.0 | MIT | Chạy crop classifier trên CPU |
| NumPy | BSD-3-Clause | Tính toán |
| Pillow | MIT-CMU | Xử lý ảnh |
| OpenCV | Apache-2.0 | Do Ultralytics kéo theo |
| Ultralytics 8.3.40 | AGPL-3.0 | Detector bệnh lá lúa |
| Ruff, pytest | MIT | Chỉ khi phát triển và test |

## Mô hình và dữ liệu

| Thành phần | Giấy phép | Vai trò |
|---|---|---|
| `YOLOv8n-RiceLeafBD` 3.1.0 (`ai/weights/best.pt`) | AGPL-3.0 | Detector 5 bệnh lá lúa, fine-tune từ `yolov8n.pt` |
| [Crop classifier YOLO26s-cls](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier) (`ai/weights/crop_classifier.onnx`) | AGPL-3.0 | Kiểm ảnh là lúa trước detector |
| [Qwen2.5 1.5B](https://ollama.com/library/qwen2.5) | Apache-2.0 | LLM diễn đạt, chạy qua Ollama |
| [`paraphrase-multilingual-MiniLM-L12-v2`](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2) | Apache-2.0 | Embedding cho RAG |
| [RiceLeafDiseaseBD v3](https://doi.org/10.17632/86s4jzj2m4.3) | CC BY 4.0 | Dữ liệu huấn luyện detector |
| Tri thức DX-Lab (`data/rag/lab/`) | CC BY 4.0 | 10 bài quy chế và hướng dẫn do nhóm tự soạn |

Nguồn dữ liệu: RiceLeafDiseaseBD Version 3 của Mohammad Mehedi Hasan Munna, Toriqul Islam, Smita Saha, Iqbal Hossain Safy, Saiful Bari, Jasmin Akter, Md Momtajur Rahman, Riya Akter, Nahid Hossain Antu, Acramul Haque Kabir, Reefka Fabliha Tulona, Ashraful Islam và M Ashraful Amin, theo [CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).
Thay đổi: chỉ dùng 5 lớp bệnh, resize ảnh về 640 và áp dụng augmentation.
Checkpoint `YOLOv8n-RiceLeafBD` do [`@nddKhanh`](https://github.com/nddKhanh) fine-tune ([notebook Kaggle](https://www.kaggle.com/code/baodark/dx-farm-8169e1/notebook)) và được công khai theo AGPL-3.0.
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
| Python 3.11 (`python:3.11-slim`) | PSF-2.0 | Base image AI Service |
| Node.js 20 (`node:20-alpine`) | MIT | Chỉ khi build web |
| AWS CLI | Apache-2.0 | Chỉ dùng trong `minio/migrate-uploads.sh` |
| gitleaks | MIT | Quét secret trong CI |
| CycloneDX (`cyclonedx-bom`, `@cyclonedx/cyclonedx-npm`, `cyclonedx-maven-plugin`) | Apache-2.0 | Sinh SBOM |
