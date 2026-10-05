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
| PostgreSQL (server, `postgres:15-alpine` image) | PostgreSQL License | ✅ | |

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
| nginx (ảnh serve production, `frontend/Dockerfile`) | BSD-2-Clause (nginx license) | ✅ |

## Mobile (`mobile`, npm)

| Thành phần | License | OSI-approved |
|---|---|---|
| Expo (`expo`, `expo-router`, `expo-auth-session`, `expo-secure-store`, `expo-image-picker`, `expo-image-manipulator`, `expo-web-browser`, `expo-build-properties`, `expo-constants`, `expo-linking`, `expo-crypto`, `expo-file-system`, `expo-status-bar`) | MIT | ✅ |
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
| NumPy | BSD-3-Clause và các license nguồn mở đi kèm | ✅ | Dependency trực tiếp |
| Pillow | MIT-CMU | ✅ | Dependency trực tiếp |
| [OpenCV](https://opencv.org/license/) | Apache-2.0 | ✅ | Dependency runtime do Ultralytics kéo theo; áp dụng cho OpenCV 4.5.0 trở lên |
| [Ultralytics](https://www.ultralytics.com/license) | AGPL-3.0 | ✅ | Runtime `8.3.40`; checkpoint ghi phiên bản train `8.4.165` |
| [Ollama](https://github.com/ollama/ollama) (`ollama/ollama:0.34.2`) | MIT | ✅ | Runtime phục vụ LLM trong Compose |
| [Qwen2.5 1.5B](https://ollama.com/library/qwen2.5) | Apache-2.0 | ✅ | Model mặc định `qwen2.5:1.5b` |
| [`paraphrase-multilingual-MiniLM-L12-v2`](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2) | Apache-2.0 | ✅ | Model embedding mặc định |
| `YOLOv8n-RiceLeafBD` / `ai/weights/best.pt` | AGPL-3.0 | ✅ | Phiên bản `3.0.0`, fine-tune từ pretrained `yolov8n.pt` |
| RiceLeafDiseaseBD | CC BY 4.0 | N/A | License dữ liệu, không phải software license do OSI phê duyệt |

**Checkpoint:** `YOLOv8n-RiceLeafBD` phiên bản `3.0.0`, do
[`@nddKhanh`](https://github.com/nddKhanh) fine-tune trong
[notebook Kaggle](https://www.kaggle.com/code/baodark/dx-farm-8169e1/notebook), được nhóm
đồng ý công khai theo AGPL-3.0. File `ai/weights/best.pt` có SHA-256
`573db0871f39396f2bbec5e6aa5988d36b79b13e01cb00f03eee29b32a12c60b`; toàn văn license
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
| Apache Superset (`apache/superset` image) | Apache-2.0 | ✅ | BI dashboard, thêm ở lượt này — xem `bi/` |
| Authlib (pip, cài lúc container Superset khởi động cho SSO OAuth) | BSD-3-Clause | ✅ | |
| Mattermost (`mattermost/mattermost-team-edition` image) | AGPLv3 (server core) | ✅ | Xem mục riêng "Rà soát license Mattermost" dưới đây — không đơn giản như một dòng |
| Keycloak (`quay.io/keycloak/keycloak` image) | Apache-2.0 | ✅ | Identity Provider cho SSO thật — xem `sso/README.md` |
| Docker / Docker Compose (tooling, không đóng gói vào sản phẩm) | Apache-2.0 | ✅ | |

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

## Việc còn thiếu (ngoài phạm vi lượt rà soát này)

- **SPDX header**: `LICENSE` (Apache-2.0) đã có ở gốc repo.
  SPDX header (`// SPDX-License-Identifier: Apache-2.0`) đầu mỗi file mã nguồn tự viết **chưa có** — cần một lượt riêng để chèn hàng loạt theo từng loại file (`.java`/`.ts`/`.tsx`).
- AI Service: dependency trực tiếp đã được rà từ môi trường hiện tại; còn thiếu inventory
  dependency bắc cầu và thành phần bundle từ đúng Docker image phát hành.
