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

### Model nhận diện bệnh lá lúa

`ai/weights/best.pt` được nhóm đồng ý phát hành công khai. Đây là model
`YOLOv8n-RiceLeafBD`, phiên bản `3.0.0`, do
[`@nddKhanh`](https://github.com/nddKhanh) fine-tune từ pretrained
`yolov8n.pt` bằng Ultralytics YOLO. Nguồn quá trình train được người train xác nhận là
notebook Kaggle [`baodark/dx-farm-8169e1`](https://www.kaggle.com/code/baodark/dx-farm-8169e1/notebook).
Checkpoint phát hành có SHA-256
`573db0871f39396f2bbec5e6aa5988d36b79b13e01cb00f03eee29b32a12c60b`.
Thông tin kỹ thuật và giới hạn được mô tả trong [`ai/MODEL_CARD.md`](./ai/MODEL_CARD.md);
toàn văn giấy phép checkpoint nằm tại [`ai/MODEL_LICENSE`](./ai/MODEL_LICENSE).

Checkpoint chứa metadata `license=AGPL-3.0` và được tạo bằng Ultralytics `8.4.165`.
Theo [chính sách license của Ultralytics](https://www.ultralytics.com/license), model được
train hoặc fine-tune bằng Ultralytics mặc định thuộc AGPL-3.0 nếu không có commercial
license khác. Nhóm không khai báo Enterprise/commercial license, vì vậy checkpoint này
được phân phối theo **AGPL-3.0**. Apache-2.0 tại `LICENSE` ở gốc repo không thay thế license
riêng của checkpoint hoặc nghĩa vụ phát sinh từ Ultralytics.

### Dataset dùng để train

Model sử dụng **RiceLeafDiseaseBD: A Field-Based Annotated Smartphone Image Dataset of
Healthy and Diseased Rice Leaves from Bangladesh**, Version 3,
[DOI `10.17632/86s4jzj2m4.3`](https://doi.org/10.17632/86s4jzj2m4.3), theo
[CC BY 4.0](https://creativecommons.org/licenses/by/4.0/).

Tác giả dataset: Mohammad Mehedi Hasan Munna, Toriqul Islam, Smita Saha, Iqbal Hossain
Safy, Saiful Bari, Jasmin Akter, Md Momtajur Rahman, Riya Akter, Nahid Hossain Antu,
Acramul Haque Kabir, Reefka Fabliha Tulona, Ashraful Islam và M Ashraful Amin.

Thay đổi khi sử dụng: quá trình train lấy 5 lớp bệnh `Blast`, `Brown Spot`, `Leaf Smut`,
`Rice Tungro` và `Sheath Blight`; lớp `Healthy` của dataset gốc không phải class detection
trong checkpoint. Ở runtime, `Rice___healthy` chỉ là fallback khi không có detection đạt
ngưỡng. Dữ liệu được resize về `640` và áp dụng augmentation theo cấu hình train.

| Thành phần | Phiên bản | License | OSI-approved | Ghi chú |
|---|---|---|---|---|
| Mã nguồn AI do DX-Farm tự viết | Theo repo | Apache-2.0 | ✅ | Vẫn phải tuân thủ các nghĩa vụ AGPL-3.0 khi kết hợp/phân phối cùng Ultralytics và checkpoint |
| Ultralytics (training) | 8.4.165 | AGPL-3.0 | ✅ | Phiên bản lấy từ metadata của checkpoint |
| Ultralytics (runtime) | 8.3.40 | AGPL-3.0 | ✅ | Khóa trong `ai/requirements.txt` |
| `YOLOv8n-RiceLeafBD` / `best.pt` | 3.0.0 | AGPL-3.0 | ✅ | Fine-tune từ pretrained `yolov8n.pt`; checksum ghi ở trên |
| RiceLeafDiseaseBD | Version 3 | CC BY 4.0 | N/A | License nội dung/dữ liệu, không phải software license do OSI phê duyệt |

### Dependency Python trực tiếp

Bảng này đối chiếu khai báo trực tiếp trong `ai/requirements.txt` với metadata của môi
trường kiểm tra ngày 2026-10-02. Phần lớn dependency dùng khoảng phiên bản thay vì khóa một
phiên bản duy nhất; phải rà lại khi dependency được nâng cấp hoặc file lock được tạo.

| Dependency đã kiểm tra | Phiên bản môi trường | License metadata | OSI-approved |
|---|---:|---|---|
| FastAPI | 0.141.1 | MIT | ✅ |
| Uvicorn | 0.52.4 | BSD-3-Clause | ✅ |
| python-multipart | 0.0.32 | Apache-2.0 | ✅ |
| HTTPX | 0.28.1 | BSD-3-Clause | ✅ |
| Pydantic / pydantic-settings | 2.13.5 / 2.15.0 | MIT | ✅ |
| python-dotenv | 1.2.3 | BSD-3-Clause | ✅ |
| SQLAlchemy | 2.0.52 | MIT | ✅ |
| psycopg / psycopg-binary | 3.3.5 | LGPL-3.0-only | ✅ |
| pgvector | 0.3.6 | MIT | ✅ |
| LangGraph | 1.2.11 | MIT | ✅ |
| langchain-core / community / postgres / ollama | 1.6.2 / 0.4.2 / 0.0.17 / 0.3.10 | MIT | ✅ |
| sentence-transformers | 5.7.0 | Apache-2.0 | ✅ |
| pypdf | 6.18.0 | BSD-3-Clause | ✅ |
| PyMuPDF | 1.28.2 | AGPL-3.0 hoặc Artifex commercial | ✅ với nhánh AGPL-3.0 |
| python-docx | 1.2.0 | MIT | ✅ |
| PyTorch | 2.14.0 | Apache-2.0 và các license third-party kèm wheel | ⚠️ Cần chốt inventory bundled của image |
| Ultralytics | 8.3.40 | AGPL-3.0 | ✅ |
| NumPy | 2.5.3 | BSD-3-Clause và notices third-party kèm wheel | ⚠️ Cần chốt inventory bundled của image |
| Pillow | 12.3.0 | MIT-CMU | ✅ |
| Ruff (development) | 0.16.6 | MIT | ✅ |

Hai dependency trực tiếp có strong copyleft là **Ultralytics** và **PyMuPDF**. Apache-2.0
tương thích với AGPLv3 theo hướng kết hợp, nhưng khi tạo/phân phối một chương trình kết hợp
thì các nghĩa vụ AGPL-3.0 vẫn áp dụng cho chương trình kết hợp; mã nguồn Apache-2.0 ban đầu
vẫn giữ thông báo và license của nó. Không được dùng `LICENSE` ở gốc repo để tuyên bố
Ultralytics, PyMuPDF hoặc checkpoint là Apache-2.0.

Kiểm kê trên bao phủ dependency khai báo trực tiếp, không phải toàn bộ dependency bắc cầu và
mọi thành phần được bundle trong wheel. Khi chốt release cần lưu inventory/SBOM từ đúng
Docker image phát hành và giữ các license/notice do package cung cấp.

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
