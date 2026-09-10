# Rà soát license thành phần (PoF — tiêu chí loại trừ)

Tiêu chí PoF của OLP yêu cầu **mọi thành phần trong stack phải dùng license OSI-approved** (xem [danh sách OSI](https://opensource.org/licenses)).
Bảng dưới liệt kê từng thành phần đang thực sự chạy trong repo (không tính thành phần đã comment-out/chưa dùng), license, và tình trạng OSI-approved.

## Backend (`backend/DX-Farm`, Maven)

| Thành phần | License | OSI-approved | Ghi chú |
|---|---|---|---|
| Spring Boot / Spring Framework / Spring Security (`org.springframework*`) | Apache-2.0 | ✅ | |
| PostgreSQL JDBC Driver (`org.postgresql:postgresql`) | BSD-2-Clause (PostgreSQL License) | ✅ | |
| jjwt (`io.jsonwebtoken:jjwt-*`) | Apache-2.0 | ✅ | |
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
| nginx (ảnh serve production, `frontend/Dockerfile`) | BSD-2-Clause (nginx license) | ✅ |

## AI Service (`ai`, ngoài phạm vi lượt rà soát này)

Chưa rà — sẽ làm khi tích hợp AI Service thật vào backend (xem `CHANGELOG.md`).

## Deploy (`deploy`)

| Thành phần | License | OSI-approved | Ghi chú |
|---|---|---|---|
| Apache Superset (`apache/superset` image) | Apache-2.0 | ✅ | BI dashboard, thêm ở lượt này — xem `bi/` |
| Docker / Docker Compose (tooling, không đóng gói vào sản phẩm) | Apache-2.0 | ✅ | |

## Quyết định về n8n

Không dùng n8n (Sustainable Use License, không OSI-approved) — chưa từng tích hợp vào code.
LangGraph (Apache-2.0) đảm nhận vai trò orchestrator cho luồng vision → lookup → LLM diễn đạt.

## Việc còn thiếu (ngoài phạm vi lượt rà soát này)

- **SPDX header**: `LICENSE` (Apache-2.0) đã có ở gốc repo.
  SPDX header (`// SPDX-License-Identifier: Apache-2.0`) đầu mỗi file mã nguồn tự viết **chưa có** — cần một lượt riêng để chèn hàng loạt theo từng loại file (`.java`/`.ts`/`.tsx`).
- AI Service (Python, `ai/requirements.txt`: FastAPI, PyTorch, v.v.) chưa rà.
  Cần làm trước khi khoá kiến trúc, cùng lúc với license model checkpoint EfficientNet-B4 đang dùng.
