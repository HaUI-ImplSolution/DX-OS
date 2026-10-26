# Hướng dẫn kiểm thử

Dành cho người kiểm thử: kiểm tĩnh, dựng stack, chạy từng ứng dụng, đổi ứng dụng và kiểm tra theo thứ tự.
Mọi lệnh chạy được ở repo hiện tại; lệnh dựng và nạp dữ liệu chi tiết ở [`README.md`](../README.md) và [`deploy/demo/README.md`](../deploy/demo/README.md).

## 1. Mức hoàn thiện theo tính năng

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
| Thu hoạch (sớm cần xác nhận), nhắc hạn (dừng sau 14 ngày), hộp thông báo | Có | Test đơn vị; test tích hợp thông báo duyệt, từ chối, xác nhận phun và nhắc hạn; kiểm tay nhắc hạn | Có |
| Nhắc kênh kỹ sư về bản khai phun chờ xác nhận quá 24 giờ | Có | Kiểm tay | Có |
| Dashboard BI trên view đã duyệt (11 chart, `query_context` sinh lại sau mỗi lần import) | Có | Test bundle và `query_context`, kiểm tay | Một phần: chart "Tỷ lệ AI đúng/sai" cần chẩn đoán AI bằng ảnh thật |
| Thời tiết theo thửa (Open-Meteo, snapshot dự phòng), panel trên web và thẻ trên mobile | Có | Kiểm tay trên web và mobile | Có |
| Báo cáo chưa chẩn đoán trong hàng đợi kỹ sư, mở báo cáo bằng `/?report=<id>` | Có | Kiểm tay | Có |
| Thông báo Mattermost cho kết quả duyệt và phun thuốc, tiêu đề là liên kết mở báo cáo | Có | Kiểm tay | Có (webhook tự tạo khi dựng stack) |
| Hỏi đáp tài liệu có trích nguồn, trên web và mobile | Có | Kiểm tay | Có (6 tài liệu bệnh lúa) |
| Portal hiện trạng dịch vụ, lỗi hiển thị tiếng Việt | Có | Kiểm tay | Không áp dụng |

### DX-Lab

| Tính năng | Trong code | Kiểm thử | Dữ liệu demo |
|---|---|---|---|
| Vai STUDENT, LECTURER, TECHNICIAN, ADMIN; sinh viên chỉ thấy yêu cầu của mình | Có | E2E Lab, kiểm tay | Có (`student1`, `lecturer1`, `tech1`, `admin1`) |
| Yêu cầu hỗ trợ: gửi kèm ảnh, trao đổi công khai và nội bộ, nhận, xử lý, xác nhận và đánh giá, mở lại | Có | E2E Lab, kiểm tay | Có (15 yêu cầu ở mọi trạng thái) |
| SLA theo ưu tiên, cảnh báo ở 80% và đánh dấu quá hạn theo lịch | Có | Kiểm tay với SLA rút ngắn | Có |
| Cảnh báo kênh kỹ thuật viên khi một thiết bị có 3 ticket trong 30 ngày | Có | Kiểm tay | Có |
| Hai kỹ thuật viên nhận cùng yêu cầu: một bên 409 | Có | E2E Lab | Không áp dụng |
| Gợi ý loại và ưu tiên bằng luật, bài tri thức liên quan | Có | E2E Lab, kiểm tay | Có (12 bài trích văn bản của HaUI) |
| Mượn thiết bị có hạn, duyệt theo vai, trả hỏng mở nhật ký sửa chữa | Có | E2E Lab, kiểm tay | Có (7 thiết bị, 5 phiếu, 1 phiếu quá hạn) |
| Đặt phòng chống trùng giờ ở cơ sở dữ liệu, lịch tuần; luật độ dài, bước giờ, sức chứa, người duyệt | Có | Test tích hợp và phân quyền, E2E Lab, kiểm tay | Có (5 phòng, 5 lịch) |
| Hỏi đáp quy chế trích văn bản của HaUI, nguồn mở trang gốc và bản PDF | Có | E2E Lab (`--require-knowledge`) | Có |
| Thống kê SLA, tải kỹ thuật viên, mức dùng phòng; view `vw_lab_*` cho BI | Có | E2E Lab, kiểm tay | Có |

### Dùng chung: gateway, log và audit

| Tính năng | Trong code | Kiểm thử | Dữ liệu demo |
|---|---|---|---|
| Log truy cập JSON kèm `X-Request-Id`, IP thật từ `CF-Connecting-IP` | Có | Kiểm tay trên stack local (giả lập header) | Không áp dụng |
| Sự kiện đăng nhập và khóa tạm sau 5 lần sai của Keycloak | Có | Kiểm tay, Chrome | Không áp dụng |
| Giới hạn đăng nhập (120 mỗi phút), `/api/*` (600 mỗi phút) và route riêng của từng app theo IP | Có | Kiểm tay, E2E | Không áp dụng |
| `audit_log` chỉ thêm, `GET /api/v1/audit-logs` cho ADMIN, cảnh báo Mattermost | Có | Kiểm tay, E2E Farm và Lab | Không áp dụng |
| Ghi `SERVER_ERROR` và `ADMIN_ACTION_FAILED` vào `audit_log` | Có | Kiểm tay (M1, M2) | Không áp dụng |
| Màn hình giám sát `/monitor/`: sự kiện gateway, dò dịch vụ, truy vết, cảnh báo | Có | Kiểm tay, Chrome (M1 đến M14) | Không áp dụng |

## 2. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo |
| Node 20 trở lên | Cho `frontend` và `mobile` |
| Python 3 và `pillow` | Cho `seed_demo.py` (`python3 -m pip install pillow`) |
| Trọng số AI trong `ai/weights/` | Ba phần EfficientNetV2-M và `crop_classifier.onnx` có sẵn trong Git; Docker build ghép model bệnh |
| Dung lượng và RAM | Lần dựng đầu tải khoảng 3 GB (image AI) và 9 GB (Ollama và model); cần máy đủ RAM để chạy cả stack |

## 3. Kiểm tra tĩnh (không cần dựng stack)

| Bước | Lệnh | Kết quả mong đợi |
|---|---|---|
| Backend biên dịch toàn bộ module | `cd backend && ./mvnw compile` | Cả sáu module đạt |
| Web | `cd frontend && npx tsc --noEmit` | Không lỗi |
| Mobile | `cd mobile && npx tsc --noEmit` | Không lỗi |
| Ranh giới app | Tìm `core.` hoặc `dx-core` trong `app-farm` và `app-lab` | Không có (chỉ `dx-core-api`) |
| Ranh giới core | Tìm `Household`, `Parcel`, tên view `vw_` trong `dx-core-api`, `dx-core` | Không có |
| Backend đầy đủ | `cd backend && ./mvnw verify` | Mọi test đạt, không bỏ qua |
| AI | `cd ai && pytest` | Mọi test đạt |
| Dashboard BI | `cd bi && python -m unittest test_chart_query_context` | Đạt; CI chạy ở job `dashboard` của `ci-infra.yml` |
| Seed Farm nhất quán | `python data/scripts/generate_seed_sql.py --check` và `python -m unittest data.scripts.test_generate_seed_sql` | Đạt; CI chạy lại ở `ci-ai.yml` |

## 4. Dựng stack

### 4.1 Cấu hình

```bash
cd deploy
cp .env.example .env     # điền mọi biến đang để trống (mật khẩu, khóa nội bộ)
```

Đặt app muốn chạy trong `.env` (mặc định `farm`): `DXOS_APP=farm` hoặc `lab`.
Thiếu biến nào, Compose dừng và báo tên biến đó.

### 4.2 Dựng từ đầu

```bash
./reset.sh --yes         # xóa deploy/data, dựng lại toàn bộ; giữ volume model Ollama
```

Lệnh này dựng hạ tầng, migration, vai và tri thức của app đang chọn (job `rag-ingest` nạp corpus theo `DXOS_APP`).
Dùng `./reset.sh --yes --keep-images` khi chỉ đổi dữ liệu, không đổi code.

### 4.3 Kiểm tra stack lên đủ

| Việc | Cách | Kết quả |
|---|---|---|
| Dịch vụ healthy | `docker compose ps` | Mọi service healthy hoặc job đã thoát mã 0 |
| Backend | `curl -s http://localhost:9080/api/v1/system/status` | `backend`, `ai`, `storage` ở trạng thái UP; `app.id` và `app.name` là app đang chạy |
| Manifest app | Với token hợp lệ: `GET /api/v1/app` | `id` đúng `farm` hoặc `lab`; kèm vai, menu và loại workspace |
| Portal | http://localhost:9080/portal/ | Hiện hạ tầng và app đang chạy |

### 4.4 Nạp dữ liệu demo

```bash
python3 deploy/demo/seed_demo.py [--app farm|lab]
```

Không truyền `--app` thì script đọc app đang chạy từ `GET /api/v1/system/status` và dừng khi `--app` khác app đó.
Seed tạo tài khoản mẫu qua Admin API rồi nạp dữ liệu qua API nên đi qua kiểm tra nghiệp vụ; chạy lại không tạo trùng.

## 5. Kiểm thử Farm (`DXOS_APP=farm`)

Tài khoản: `farmer1` (chỉ mobile), `engineer1`, `admin1`; địa chỉ và mật khẩu ở [`README.md`](../README.md) mục "Chạy thử".

### 5.1 E2E tự động

```bash
python3 deploy/demo/e2e.py [--require-rule]
```

Kết quả mong đợi: 10/10 bước đạt.
Treatment rule của đạo ôn, khô vằn và đốm nâu có trong seed Backend, nên `--require-rule` dùng được sau khi seed.

### 5.2 Kiểm tay

| # | Việc | Kết quả mong đợi |
|---|---|---|
| F1 | Đăng nhập web bằng `engineer1`, đi qua các trang | Vào được hàng chờ duyệt, nông hộ, tài liệu |
| F2 | Đăng nhập web bằng `farmer1` | Bị từ chối (403) |
| F3 | Đăng nhập mobile bằng `farmer1`, gửi báo cáo kèm ảnh | Gửi được; ảnh không phải lúa bị chặn |
| F4 | `admin1` duyệt thửa chờ duyệt (An Giang) | Duyệt được; `farmer1` có thông báo |
| F5 | `engineer1` chạy chẩn đoán, duyệt | Chẩn đoán chuyển trạng thái, timeline ghi đủ bước |
| F6 | Khai phun thuốc rồi xác nhận trên chẩn đoán đạo ôn, khô vằn hoặc đốm nâu | Vụ vào cách ly; thu hoạch bị chặn tới hết hạn |
| F6b | Chẩn đoán bệnh chưa có rule (than lá) | Chỉ xem xét (`review_required`), không có liều và PHI |
| F7 | Hỏi đáp tri thức | Đoạn trích có nguồn |
| F8 | Mở Superset bằng `admin1` | Dashboard của Farm hiển thị 11 chart, mỗi chart một ô; chart "Thửa đang cách ly" và hai chart thu hoạch có dữ liệu sau khi seed; hai chart thời tiết có điểm sau khi Backend chạy khoảng một phút; chart "Tỷ lệ AI đúng/sai" trống nếu chưa chẩn đoán AI bằng ảnh thật |
| F9 | Hai người cùng quyết định một case | Một bên 409 |
| F10 | Chọn một thửa ở "Theo dõi đồng ruộng" | Panel Thời tiết hiện nhiệt độ, độ ẩm, dự báo 5 ngày và nguồn |
| F11 | Nông dân gửi báo cáo mới; `engineer1` mở Hàng đợi → "Chưa chẩn đoán" → "Mở để chẩn đoán" | Báo cáo có trong danh sách; liên kết mở đúng thửa, vụ và báo cáo |
| F12 | Duyệt hoặc từ chối chẩn đoán, xác nhận hoặc từ chối khai phun (đặt `MATTERMOST_APP_BASE_URL` để tiêu đề có liên kết) | Kênh "Kỹ sư nông nghiệp" có tin kết quả; tiêu đề mở được báo cáo; `farmer1` có thông báo |
| F13 | `farmer1` nộp bản khai phun, để chờ; chạy backend với `TREATMENT_CONFIRMATION_WAIT_HOURS=0` và `TREATMENT_CONFIRMATION_REMINDER_CRON="0 * * * * *"` | Kênh "Kỹ sư nông nghiệp" có tin "Bản khai phun thuốc chờ xác nhận quá lâu" mỗi phút tới khi kỹ sư quyết định |
| F14 | Đặt `expected_harvest_date` của một vụ đã duyệt lùi 20 ngày và một vụ lùi 5 ngày; chạy backend với `HARVEST_REMINDER_CRON="0 * * * * *"` | Chỉ vụ lùi 5 ngày có thông báo "Đến hạn thu hoạch" |

### 5.3 Mobile

Đặt `mobile/.env`:

```bash
EXPO_PUBLIC_API_BASE_URL=http://<ip-máy-chạy-stack>:9080
EXPO_PUBLIC_KEYCLOAK_URL=http://<ip-máy-chạy-stack>:9080/realms/dxos
EXPO_PUBLIC_KEYCLOAK_CLIENT_ID=farm-mobile
```

Máy chủ công khai dùng `https://<tên-miền>` và `https://<tên-miền>/realms/dxos`, không có số cổng.

```bash
cd mobile && npx expo start      # Expo Go, hoặc build APK bằng EAS
```

Chi tiết ở [`mobile/README.md`](../mobile/README.md).

## 6. Kiểm thử Lab (`DXOS_APP=lab`)

```bash
./deploy/start.sh lab                 # đổi biến, dựng lại gateway, backend, nạp tri thức, Superset và nạp dữ liệu demo
./deploy/start.sh lab --reset --yes   # kèm dựng lại sạch, dùng khi đổi schema lớn
python3 deploy/demo/e2e.py --app lab --require-knowledge
```

Web không cần build lại: web đọc `GET /api/v1/app` rồi nạp giao diện Lab; tải lại trang sau khi backend đã lên.

### 6.1 Tài khoản

| Username | Vai | Dùng để |
|---|---|---|
| `student1`, `student2` | STUDENT | Báo sự cố, mượn thiết bị, hỏi đáp |
| `lecturer1` | LECTURER | Duyệt phiếu mượn của sinh viên, đặt phòng |
| `tech1`, `tech2` | TECHNICIAN | Nhận và xử lý ticket, quản lý thiết bị, duyệt đặt phòng |
| `admin1` | ADMIN | Quản trị, gán việc, xem thống kê |

### 6.2 Kiểm tay theo vai

| # | Vai | Việc | Kết quả mong đợi |
|---|---|---|---|
| L1 | `student1` | Mô tả "máy chiếu phòng A2 không lên hình" | Gợi ý loại thiết bị kèm lý do và bài tri thức liên quan |
| L2 | `student1` | Gửi ticket kèm ảnh | Mã `TK-{năm}-{số}`, trạng thái mới, có hạn phản hồi và hạn xử lý |
| L3 | `tech1` | Nhận ticket, bắt đầu, trả lời công khai, thêm bình luận nội bộ | `student1` thấy câu trả lời, không thấy bình luận nội bộ |
| L4 | `tech1`, `tech2` | Cùng nhận một ticket | Một bên 409 |
| L5 | `tech1` | Chuyển chờ người dùng; `student1` trả lời | Tự về đang xử lý |
| L6 | `tech1` | Hoàn tất không ghi chú, rồi có ghi chú và nguyên nhân gốc | Lần đầu 422; lần sau thành công |
| L7 | `student1` | Xác nhận kèm 4 sao | Ticket đóng, điểm được ghi |
| L8 | `student1` | Mượn thiết bị đã có người mượn | 422 |
| L9 | `student1`, `lecturer1` | Mượn thiết bị trống, duyệt, trả báo hỏng | Thiết bị sang đang sửa, có nhật ký sửa chữa |
| L10 | `lecturer1` | Đặt phòng máy 2 giờ; `tech1` duyệt | Lịch tuần hiện đúng |
| L11 | `lecturer1` | Đặt chồng giờ cùng phòng, quá 4 giờ, vượt sức chứa | Bị từ chối kèm lý do bằng tiếng Việt |
| L12 | Hai phiên | Đặt cùng phòng cùng giờ gần như đồng thời | Chỉ một lịch hiệu lực |
| L13 | `student1` | Đặt phòng | Bị chặn |
| L14 | Bất kỳ | Hỏi đáp và gợi ý khi báo sự cố theo bảng ở mục 6.4 | Đúng bài, nguồn mở trang của HaUI và bản PDF gốc |
| L15 | `admin1` | Mở trang thống kê | Số liệu khớp dữ liệu mẫu |
| L16 | `student1` | Mở ticket của người khác bằng đường dẫn trực tiếp | Bị chặn |
| L17 | `tech1` | Rút ngắn SLA khi thử để ticket quá hạn | Cảnh báo, rồi đánh dấu quá hạn; thông báo không gửi trùng |
| L18 | `student1` | Gửi 4 ticket nêu cùng một `deviceId` | Kênh "Kỹ thuật viên" có đúng một tin "Thiết bị hỏng lặp lại" ở ticket thứ 3, ticket thứ 4 không đăng thêm |

### 6.3 Kiểm qua API

Dùng Swagger (`http://localhost:9080/swagger-ui.html`) hoặc client trong `deploy/demo/dxos_client.py`, sau khi `reset.sh` và `seed_demo.py --app lab` đã chạy xong.

| Bước | Gọi | Kiểm |
|---|---|---|
| 1 | `GET /api/v1/app` | `id` là `lab`, có 4 vai và menu |
| 2 | `tech1` `POST /rooms` hai lần cùng `code`; `student1` `POST /devices` | Lần hai 422 `ROOM_CODE_TAKEN`; `student1` 403 |
| 3 | `student1` `POST /tickets` (multipart `ticket` + `files` là ảnh PNG) | Mã `TK-<năm>-<số>`, `NEW`, hạn theo ưu tiên |
| 4 | `tech1` `/assign`, `/start`, comment công khai; `student1` comment | `firstRespondedAt` có giá trị |
| 5 | `student1` `GET /tickets/{id}` và ticket của người khác | Không thấy ghi chú nội bộ; ticket người khác 404 |
| 6 | `tech1` `/wait` có `message`; `student1` comment | Về `IN_PROGRESS` |
| 7 | `/resolve` thiếu `rootCause` | 422 `ROOT_CAUSE_REQUIRED` |
| 8 | `/resolve` đủ, `student1` `/confirm` `{"rating":4}` | `CLOSED`, `timeline` đủ sự kiện |
| 9 | `/reopen` lần 4 trên ticket đã mở lại 3 lần | 422 `TICKET_REOPEN_LIMIT` |
| 10 | `tech1` và `tech2` cùng `/assign` một ticket `NEW` (hai luồng) | Một bên 409 |
| 11 | `student1` `POST /device-loans`, `lecturer1` `/approve`, `tech1` `/return` | Thiết bị `AVAILABLE` → `ON_LOAN` → `AVAILABLE` |
| 12 | Mượn thiết bị `ON_LOAN`; `dueDate` quá 7 ngày; phiếu thứ ba | 422 |
| 13 | `/return` với `DAMAGED` | Thiết bị `UNDER_REPAIR`, có mục ở `/devices/{id}/maintenance` |
| 14 | `lecturer1` `POST /room-bookings`, `tech1` `/approve`, `GET /rooms/{id}/schedule` | Lịch có khung giờ |
| 15 | Đặt chồng giờ; hai luồng cùng phòng cùng giờ | 422 `ROOM_SLOT_TAKEN`; DB chỉ một bản ghi `active` |
| 16 | Quá 4 giờ, quá khứ, vượt sức chứa, phòng `MAINTENANCE`; `student1` hoặc `tech1` đặt; `admin1` duyệt lịch của chính mình | 422 hoặc 403 |
| 17 | `PUT /sla-policies/NORMAL` `{"responseMinutes":1,"resolutionMinutes":2}`, tạo ticket, đợi 5 phút | Cảnh báo ở 80%, rồi `overdue=true` và thông báo, không gửi trùng |
| 18 | `POST /tickets/suggest` với "máy chiếu phòng A2 không lên hình" và "cả phòng máy mất mạng giữa giờ thi" | Loại thiết bị; ưu tiên khẩn; có từ khóa, lý do, bài liên quan |
| 19 | `POST /knowledge/ask` "Quy định sử dụng máy tính trong thư viện" | Đoạn trích Nội quy sử dụng thư viện, nguồn có `url` trang lic.haui.edu.vn và `documentUrl` bản PDF |
| 20 | `GET /lab-stats/summary`, đối chiếu `SELECT` ở DB | Số khớp |
| 21 | `GET /approval-requests` bằng `tech1` | Có ticket, phiếu mượn, đặt phòng |
| 22 | Đăng nhập role đọc BI, `SELECT` từ `vw_lab_tickets` và từ `tickets` | View đọc được, bảng bị từ chối |

### 6.4 Hỏi đáp và nguồn tri thức

Mỗi bài trong kho Lab trích từ một văn bản công khai của HaUI; danh sách văn bản, trang gốc và link PDF ở `data/rag/lab/source_registry.json`.
Đăng nhập `student1`, gõ ở trang Tri thức (hỏi đáp) hoặc ở form Báo sự cố mới (gợi ý), rồi đối chiếu cột cuối.
Độ liên quan đo trên stack ngày 19/10/2026; chênh vài phần trăm sau khi nạp lại vẫn là bình thường.

| # | Gõ ở | Câu hỏi hoặc tiêu đề và mô tả | Bài đứng đầu (độ liên quan) |
|---|---|---|---|
| K1 | Hỏi đáp | Quy định sử dụng máy tính trong thư viện là gì? | Sử dụng máy tính và tài sản tại Thư viện (85%) |
| K2 | Hỏi đáp | Sinh viên được mượn tối đa bao nhiêu cuốn sách và trong bao lâu? | Mượn trả tài liệu tại Thư viện (86%) |
| K3 | Hỏi đáp | Muốn dùng phòng thảo luận nhóm thì đăng ký thế nào? | Đăng ký sử dụng phòng thảo luận nhóm (78%) |
| K4 | Hỏi đáp | Có sóng wifi nhưng không vào được mạng thì làm sao? | Kết nối Wi-Fi HaUI-Staffs (74%) |
| K5 | Hỏi đáp | Làm sao đổi mật khẩu email trường? | Đăng nhập và đổi mật khẩu email của trường (72%) |
| K6 | Hỏi đáp | Tắt máy chiếu đúng cách như thế nào? | Sử dụng máy chiếu trong phòng học (59%) |
| K7 | Hỏi đáp | Cách mã hóa ổ đĩa bằng BitLocker? | An toàn thông tin trên máy tính cá nhân (69%) |
| K8 | Hỏi đáp | Thiết bị tin học hỏng đã hết bảo hành thì xử lý thế nào? | Quy trình sửa chữa, bảo trì thiết bị tin học (60%) |
| K9 | Hỏi đáp | Giảng viên chặn USB trên máy sinh viên bằng cách nào? | Phần mềm quản lý phòng máy dùng chung Faronics Insight (70%) |
| K10 | Hỏi đáp | Quên mật khẩu LMS thì liên hệ ai? | Đăng nhập LMS và thi trắc nghiệm trên máy trạm (69%) |
| K11 | Hỏi đáp | Được mang những gì vào phòng thi? | Quy định khi vào phòng thi và thi trên máy tính (51%) |
| K12 | Hỏi đáp | Phúc khảo bài thi trong bao nhiêu ngày? | Hoãn thi, thi bổ sung, phúc khảo và xử lý vi phạm thi (64%) |
| K13 | Báo sự cố | Không đăng nhập được email trường / Đăng nhập mail.haui.edu.vn báo sai mật khẩu, cần đổi mật khẩu email. | Đăng nhập và đổi mật khẩu email của trường (76%) |
| K14 | Báo sự cố | Máy phòng A102 nhiễm virus, tường lửa bị tắt / Cần bật lại tường lửa Windows và quét virus cho các máy dãy đầu. | An toàn thông tin trên máy tính cá nhân (53%) |
| K15 | Báo sự cố | Không khóa được màn hình sinh viên bằng Faronics / Faronics Insight trên máy giảng viên phòng A101 không hiện máy sinh viên nên không dùng được Blank Screen. | Phần mềm quản lý phòng máy dùng chung Faronics Insight (76%) |
| K16 | Báo sự cố | Máy chiếu phòng A201 không lên hình / Máy chiếu bật đèn nhưng không có tín hiệu từ laptop, đã thử đổi cáp HDMI. | Sử dụng máy chiếu trong phòng học (52%) |
| K17 | Hỏi đáp hoặc báo sự cố | Máy in kẹt giấy thì làm sao? | Không có văn bản nào về máy in; đoạn hiện ra (quy định phòng thi) không trả lời câu hỏi |

Với mọi dòng có kết quả, kiểm thêm:

- Dưới đoạn trích có "Nguồn:" gồm tên văn bản, đơn vị ban hành, nhãn "© Đại học Công nghiệp Hà Nội" và "Văn bản chính thức".
- Bấm tên văn bản mở trang của đơn vị trên `lic.haui.edu.vn`, `itc.haui.edu.vn` hoặc `tac.haui.edu.vn`, không mở GitHub.
- Bấm "[Bản PDF gốc]" mở file PDF trên máy chủ của trường; file trùng SHA-256 với bản lưu ở `data/rag/lab/raw/` khi văn bản chưa được trường thay.

## 7. Đổi ứng dụng và ranh giới

| # | Việc | Kết quả mong đợi |
|---|---|---|
| B1 | `./deploy/switch-app.sh farm` rồi tải lại web | Web về giao diện Farm; chạy lại `e2e.py` được 10/10 |
| B2 | Đặt `DXOS_APP=abc` rồi khởi động backend | Dừng sớm, thông báo nêu các giá trị hợp lệ |
| B3 | Khi chạy Lab, gọi một route của Farm (ví dụ `/api/v1/parcels`) | Không tồn tại (404) |
| B4 | Khi chạy Farm, gọi một route của Lab (ví dụ `/api/v1/tickets`) | Không tồn tại (404) |
| B5 | Xem bảng trong DB sau B1 | Bảng Lab còn nhưng không dùng; Farm chạy bình thường |
| B6 | Đổi app không dùng `--reset` | Không lỗi migration |

## 8. Gateway, log và audit

Cần stack đang chạy. Cách đọc log và audit ở [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md).

| # | Việc | Kết quả mong đợi |
|---|---|---|
| G1 | Gọi `GET /api/v1/system/status` kèm header `CF-Connecting-IP: 203.0.113.9` từ máy chạy stack | Dòng log gateway có `remote_addr` là `203.0.113.9` và `request_id` bằng header `X-Request-Id` của response |
| G2 | Cùng việc G1 từ máy khác trong LAN, đặt `CF-Connecting-IP: 1.2.3.4` | `remote_addr` là IP LAN của máy đó |
| G3 | Gọi `/realms/dxos/protocol/openid-connect/auth` kèm `state` và `code` trong query | `uri` trong log không có query string |
| G4 | Đăng nhập sai ba lần rồi đúng, mỗi lần cách nhau hơn một giây | Admin Console → Events có ba `LOGIN_ERROR` và một `LOGIN` |
| G5 | Đăng nhập sai năm lần rồi nhập đúng mật khẩu | Bị từ chối trong thời gian chờ, vào lại được sau khoảng một phút |
| G6 | Gửi 130 yêu cầu `POST` vào token endpoint từ một IP | 120 yêu cầu đầu qua, các yêu cầu sau nhận 429 kèm `X-RateLimit-*`; IP khác không bị ảnh hưởng |
| G7 | Người dùng không phải ADMIN gọi `GET /api/v1/audit-logs` | 403 và một bản ghi `ACCESS_DENIED` có người dùng, vai, IP, đường dẫn, `request_id` |
| G8 | ADMIN tạo workspace, thêm thành viên, xóa mềm một thửa (Farm) hoặc đổi SLA (Lab) | Mỗi thao tác một bản ghi `SUCCESS` ở `GET /api/v1/audit-logs` |
| G9 | `UPDATE` hoặc `DELETE` một dòng của `audit_log` bằng `psql` | Database báo `audit_log is append-only` |
| G10 | Tạo từ 10 bản ghi bị từ chối từ một IP trong 10 phút | Kênh Mattermost "Ban quản trị" nhận một tin cảnh báo trong 5 phút |

### 8.1 Màn hình giám sát

Cần stack đang chạy với `monitor` healthy.
Cách dùng và ý nghĩa từng khối ở [`backend/dx-monitor/README.md`](../backend/dx-monitor/README.md).

| # | Việc | Kết quả mong đợi |
|---|---|---|
| M1 | Dừng MinIO, tải ảnh báo cáo bằng token hợp lệ; `GET /api/v1/audit-logs?action=SERVER_ERROR` | Response `5xx`; một bản ghi `FAILED` có `request_id` bằng header `X-Request-Id`, `metadata` chỉ có mã và tên lớp ngoại lệ |
| M2 | ADMIN gắn tài khoản chủ cho workspace đã có chủ (409) | Một bản ghi `ADMIN_ACTION_FAILED`; FARMER gây lỗi tương tự thì không có bản ghi |
| M3 | Chưa đăng nhập mở `/monitor/`, rồi đăng nhập `admin1` | Chuyển sang Keycloak, đăng nhập xong vào trang; đã đăng nhập web app thì vào thẳng |
| M4 | `engineer1` và tài khoản FARMER mở `/monitor/` và `/monitor/api/summary` | Trang 403 tiếng Việt cho trang, 403 cho API; không có dữ liệu |
| M5 | `psql` bằng `monitor_app` đọc `audit_log`, bảng `monitor.*`, `workspaces`; bằng `keycloak_ro` đọc `vw_auth_events`, `credential`, `user_entity` | Hai nhóm đầu đọc được, `workspaces`, `credential`, `user_entity` bị `permission denied` |
| M6 | Từ một IP xả 700 request tới `/api/*` trong một phút | Dòng `RATE_LIMITED` ở `monitor.gateway_event`; không quá 100 dòng mỗi IP mỗi phút; tạo lại container `apisix` thì dữ liệu còn |
| M7 | Gọi `POST /monitor/ingest/gateway` không khóa, khóa sai, và từ ngoài qua gateway công khai | Không khóa và khóa sai bị `401`; qua gateway công khai `404` |
| M8 | Dừng `backend` một phút rồi bật lại | `/monitor/` vẫn mở được, dịch vụ `backend` hiện `DOWN` rồi `UP` với thời gian ngừng; kênh Mattermost nhận tin ngừng và tin phục hồi |
| M9 | Gọi API sai quyền 15 lần, xả 700 request, đăng nhập sai 6 lần; mở màn hình | Các con số, biểu đồ theo thời gian và thanh nguồn đáng ngờ khớp kịch bản trong 30 giây |
| M10 | Mở mục Tra cứu chi tiết, lấy `X-Request-Id` của một request bị `403`, dán vào ô mã yêu cầu; rồi tra theo IP | Có dòng của gateway và `ACCESS_DENIED` của backend cùng `request_id`; tra theo IP thêm dòng đăng nhập của Keycloak |
| M11 | Gửi request tới đường dẫn chứa `<script>alert(1)</script>`, mở màn hình | Chuỗi hiện nguyên văn, không có script chạy |
| M12 | Gây 5 lỗi `5xx` trong 5 phút, xả request cho `429` | Kênh "Ban quản trị" nhận một tin mỗi loại, không lặp trong 60 phút |
| M13 | Kéo chuột ngang trên biểu đồ "Sự việc bất thường theo thời gian", rồi bấm "Xem lại toàn bộ" | Hai biểu đồ theo thời gian cùng phóng vào khoảng chọn với cột mịn hơn, có dòng "Đang phóng to"; bấm nút thì trở về cột theo giờ |
| M14 | Mở mục Tra cứu chi tiết, để trống hai ô; bấm một tên nguồn trong biểu đồ "Nguồn truy cập gây nhiều sự việc nhất" | Để trống: hiện các bản ghi mới nhất của cả ba nguồn, tối đa 200, số trang ở góc dưới bên phải; bấm tên nguồn thì tra theo IP đó |

## 9. Sự cố thường gặp

| Dấu hiệu | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Web hoặc API trả 401 sau khi dựng lại | Trình duyệt giữ token hoặc phiên của lần dựng trước | Đăng xuất, xóa dữ liệu trình duyệt, đăng nhập lại; Superset bấm "Sign in with Keycloak" lại |
| Mobile báo `invalid_client` | Sai client id | Đặt `farm-mobile` trong `mobile/.env`, build và cài lại |
| Realm Keycloak không đổi sau khi sửa file | Realm chỉ được import ở lần khởi động đầu | `./reset.sh --yes` |
| Lỗi `relation ... does not exist` khi khởi động | Database dựng bằng bản schema khác | `./reset.sh --yes` |
| Backend dừng với thông báo `DXOS_APP` | Giá trị biến sai chính tả | Dùng `farm` hoặc `lab` |
| Web hiện giao diện Farm khi backend chạy Lab | Cache trình duyệt, hoặc backend chưa lên khi tải trang | Tải lại cứng, đợi `/api/v1/system/status` UP |
| Route 404 hoặc không bị giới hạn tốc độ | Gateway mount sai file cấu hình theo app | Kiểm `DXOS_APP` và file gateway mà compose mount |
| Tạo ticket, upload ảnh, chẩn đoán AI, đặt phòng hoặc đính kèm nhận 429 | Giới hạn mỗi phút theo địa chỉ: 60 lần tạo ticket, 30 lần các thao tác còn lại | Đợi một phút rồi chạy lại |
| Đăng nhập bị từ chối dù đúng mật khẩu | Keycloak khóa tạm tài khoản sau 5 lần đăng nhập sai | Đợi hết thời gian chờ (tối đa 15 phút) hoặc tắt **Temporarily locked** của người dùng trong Admin Console |
| Màn hình giám sát bắt đăng nhập lại hoặc tải lại trang | `monitor` vừa khởi động lại, phiên đăng nhập nằm trong bộ nhớ | Đăng nhập lại; đây là hành vi bình thường |
| Màn hình giám sát không có sự kiện gateway | `MONITOR_INGEST_KEY` lệch giữa `monitor` và `apisix.yaml`, hoặc `monitor` chưa lên khi gateway gửi | `./switch-app.sh <app>` để gateway nhận khóa mới; `docker compose logs apisix \| grep "http logger"` |
| Log gateway có `remote_addr` là IP container | Dải mạng Docker nằm ngoài `real_ip_from` của `apisix/config.yaml`, hoặc request không đi qua Cloudflare | Kiểm `docker network inspect`, bổ sung dải vào `real_ip_from`; xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md) |
| Không tạo được extension khi khởi động Lab | Vai của DB không đủ quyền với `btree_gist` | Dùng image PostgreSQL có extension; cấp quyền tạo extension cho vai chủ DB |
| Hỏi đáp Lab không có kết quả | Job `rag-ingest` chưa xong hoặc lỗi, hoặc `DXOS_APP` không phải `lab` lúc dựng | `docker compose logs rag-ingest`; chạy lại bằng `docker compose up -d --force-recreate rag-ingest` |
| Ảnh không hiện | Route ảnh hoặc tên bucket sai | Kiểm `MINIO_BUCKET` và route `/dxos-uploads/*`; `./reset.sh --yes` |
| Thiếu phần trọng số bệnh hoặc `crop_classifier.onnx` | Checkout không đủ trọng số | `git status`, `git pull`; xem `ai/weights/` |
| Dựng lâu hoặc hết dung lượng | Lần đầu tải image lớn | Đợi, hoặc dọn Docker (`docker system df`) |

## 10. Kết quả kiểm gần nhất

| Mục | Kết quả | Ghi chú |
|---|---|---|
| Backend `./mvnw clean verify` | Đạt | 149 test: dx-core 14, app-farm 23, app-lab 9, dx-host 103 |
| BI `python -m unittest test_chart_query_context` trong `bi/` | Đạt | 22 test |
| AI `pytest` trong `ai/` | Đạt | 100 test |
| Web và mobile `tsc` | Đạt | Không lỗi |
| Ranh giới app và core, seed Farm, nguồn tri thức khớp registry | Đạt | Không có kết quả vi phạm |
| E2E Farm | Đạt | 10/10 bước với `--require-rule` |
| Tài liệu tham khảo trong chẩn đoán Farm | Đạt | Ngày 20/10/2026: lưu, tải lại, gộp các đoạn cùng tài liệu thành một mục (cả dữ liệu đã lưu trước đó), che theo vai FARMER và ẩn card khi rỗng; web ở khung 556 px không tràn; 5 ảnh lá lúa thật của bộ RiceLeafDiseaseBD (mỗi lớp bệnh một ảnh) qua AI thật đều ra đúng bệnh với độ tin cậy từ 0,94 đến 0,98, mỗi ảnh một khối tham khảo có các dòng đúng thứ tự trong tài liệu |
| Nhắc khai phun chờ lâu, nhắc thu hoạch có giới hạn, cảnh báo thiết bị hỏng lặp lại | Đạt | Ngày 21/10/2026 trên stack giữ nguyên dữ liệu: migration V13 chạy trên cơ sở dữ liệu có sẵn; E2E Farm 10/10 và `reported_at` được ghi khi nộp bản khai; F13 ra đúng một tin mỗi phút với chủ hộ, thửa, thuốc, giờ khai; F14 chỉ nhắc vụ lùi 5 ngày; L18 ra đúng một tin ở ticket thứ 3 (4 ticket) |
| Hỏi đáp gộp theo tài liệu (Farm và Lab) | Đạt | Ngày 20/10/2026: mỗi tài liệu một đoạn, các dòng theo thứ tự trong tài liệu, nguồn gộp; E2E Farm 10/10 và E2E Lab 16/16 với `--require-knowledge`; `./mvnw verify` đạt (dx-core 14, app-farm 23, app-lab 9, dx-host 102), 100 test AI đạt |
| E2E Lab | Đạt | 16/16 bước với `--require-knowledge`, kể cả sau khi đổi app không `--reset` |
| Đổi ứng dụng B1 đến B6 | Đạt | B2 dừng với thông báo nêu `[farm, lab]`; B3, B4 route app khác 404; B6 không lỗi migration |
| Kiểm tay Farm | Đạt | F1, F2, F4 đến F14 (kỹ sư cũng thấy dashboard Superset); hộp duyệt chỉ có loại case của Farm; trên mobile đạt thẻ thời tiết, màn hỏi đáp và danh sách thông báo hiện 30 mục mỗi trang (cuộn tải trang kế chưa thử trên máy); F3 chưa chạy |
| Tri thức Lab | Đạt, trừ K17 | Ngày 19/10/2026: K1 đến K16 đúng bài, nguồn mở trang của HaUI và bản PDF trên web và qua API; K17 (máy in) hiện đoạn không liên quan, xem `BACKLOG.md` |
| Kiểm tay Lab | Đạt | L1 đến L18, API bước 1 đến 22, các màn hình web; hộp duyệt chỉ có loại case của Lab |
| API và lỗi request | Đạt | 415, 405, 400; `settings` của `GET /api/v1/app`; `GET /api/v1/crop-seasons/isolated` |
| Thời tiết | Đạt | Open-Meteo trả dữ liệu thật; địa chỉ không tới được trả snapshot kèm ghi chú |
| Gateway, log và audit G1 đến G10 | Đạt | Ngày 23/10/2026 trên stack local và VPS (`remote_addr` thật `1.52.120.232` qua Cloudflare Tunnel, sự kiện Keycloak, khóa tạm, giới hạn tốc độ, `audit_log`, cảnh báo Mattermost) |
| Khởi động từ clone sạch Farm và Lab, không overlay | Đạt | Ngày 23/10/2026: migration core V1 đến V4, import realm, E2E Farm 10/10 và Lab 16/16; chưa thử `--build` toàn bộ image ngoài CI |
| Cấu hình Cloudflare (Bot fight mode, rate limiting, HTTPS và TLS, Access) | Đạt | Ngày 23/10/2026: `bi.`, `files.`, `admin/*` chuyển sang đăng nhập Access, vào được Superset sau khi qua Access; web app, API, `/realms/`, portal, chat vẫn mở; tải ảnh và chẩn đoán AI qua Cloudflare không bị chặn nhầm; 40 lượt gửi form đăng nhập trong 1,4 giây có 13 lượt bị Cloudflare chặn |
| Đăng nhập mobile qua địa chỉ công khai | Chưa kiểm | Cần điện thoại |
| Màn hình giám sát M1 đến M14 (stack local, Farm và Lab) | Đạt | Ngày 26/10/2026: đăng nhập `admin1` và từ chối `engineer1`, đăng xuất, sự kiện gateway nạp qua `http-logger` và còn sau khi tạo lại `apisix`, dừng backend và MinIO thì `monitor` vẫn mở và báo `DOWN` rồi phục hồi, cảnh báo Mattermost cho dịch vụ, `5xx` và `429`, truy vết theo IP, xóa theo thời hạn, `<script>` trong đường dẫn không chạy; E2E Farm 10/10 và Lab 16/16; `./mvnw verify` đạt 149 test |
| Dựng sạch có `monitor` (volume và `deploy/data` rỗng, `./start.sh farm --reset --yes --build`) | Đạt | Ngày 26/10/2026: realm import có client `dxos-monitor`, `monitor-init` xong, migration `V1` của monitor, 12 dịch vụ healthy, E2E Farm 10/10, đăng nhập `admin1` vào `/monitor/`; CI e2e dựng sạch cả Farm và Lab có `monitor` |
| Màn hình giám sát trên VPS qua Cloudflare Tunnel và Access | Đạt | Ngày 26/10/2026 qua `https://khuongdev.io.vn`: `admin1` đăng nhập và đọc đủ API, `engineer1` và `farmer1` nhận trang 403 và API 403, phiên web app vào thẳng `/monitor/`, `/monitor/ingest/gateway` từ ngoài 404; sự kiện gateway mang IP thật và tối đa 100 dòng mỗi IP mỗi phút; Keycloak dừng thì `/monitor/api/summary` vẫn trả dữ liệu; backend dừng thì báo `DOWN` sau 73 giây rồi `UP`; `monitor` dừng 30 giây thì gateway vẫn phục vụ; kênh "Ban quản trị" nhận tin backend ngừng, phục hồi và `429`; quyền `monitor_app`, `keycloak_ro` như M5; Superset từ chối `farmer1`, cấp `Gamma` cho `engineer1`, `Admin` cho `admin1`; Access chuyển mọi đường dẫn dưới `/monitor/` sang trang đăng nhập Access, web app, API và `/realms/*` vẫn mở; trên trình duyệt đăng nhập Access rồi Keycloak bằng `admin1` thì màn hình hiện đủ số liệu |
| Chart thời tiết trên Superset | Đạt | Một phút sau khi Backend lên, job ghi 4 dòng (4 vị trí thửa); `vw_weather_daily` có 4 dòng; `/api/v1/chart/{id}/data` trả dữ liệu cho cả 11 chart; chart "Nhiệt độ theo ngày" và "Lượng mưa theo ngày" hiển thị trên dashboard |
| Nền bản đồ Superset | Đạt | Hai chart bản đồ vẽ nền sáng OpenFreeMap với nhãn tiếng Việt và hiện attribution OpenFreeMap, OpenMapTiles, OpenStreetMap; không thấy nhãn đảo hay biên giới ở vùng ĐBSCL |
| Dựng từ clone sạch theo README | Đạt | `start.sh farm` 113 giây, `start.sh lab` 56 giây, mọi dịch vụ healthy |
| Dựng sạch hoàn toàn (volume và `deploy/data` rỗng) | Đạt | `start.sh farm` thoát mã 0 sau khoảng 5 phút, seed Farm đủ dữ liệu; sau khi khởi động lại AI, E2E Farm 10/10 (seed chờ AI); `start.sh lab` rồi E2E Lab 16/16 |
| Thêm ứng dụng mẫu rỗng theo CONTRIBUTING | Đạt | Backend lên với `DXOS_APP=sample`, `system/status` trả id `sample`, không sửa `dx-core` |
| gitleaks | Đạt | Ngày 23/10/2026: 434 commit, không có phát hiện |

Mục kiểm tay chưa chạy là F3 và icon, splash của mobile, theo dõi ở [`BACKLOG.md`](./BACKLOG.md).
