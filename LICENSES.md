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

## Portal (`portal`)

| Thành phần | License | OSI-approved |
|---|---|---|
| nginx (ảnh serve trang tĩnh, `portal/Dockerfile`) | BSD-2-Clause (nginx license) | ✅ |

## AI Service (`ai`, ngoài phạm vi lượt rà soát này)

Chưa rà — sẽ làm khi tích hợp AI Service thật vào backend (xem `CHANGELOG.md`).

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
- AI Service (Python, `ai/requirements.txt`: FastAPI, PyTorch, v.v.) chưa rà.
  Cần làm trước khi khoá kiến trúc, cùng lúc với license model checkpoint EfficientNet-B4 đang dùng.
