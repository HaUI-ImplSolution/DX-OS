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
| Thu hoạch (sớm cần xác nhận), nhắc hạn, hộp thông báo | Có | Test đơn vị; test tích hợp thông báo duyệt, từ chối, xác nhận phun và nhắc hạn | Có |
| Dashboard BI trên view đã duyệt (9 chart, `query_context` sinh lại sau mỗi lần import) | Có | Test bundle và `query_context`, kiểm tay | Một phần: chart "Tỷ lệ AI đúng/sai" cần chẩn đoán AI bằng ảnh thật |
| Thời tiết theo thửa (Open-Meteo, snapshot dự phòng), panel trên web và thẻ trên mobile | Có | Kiểm tay trên web; mobile chưa | Có |
| Báo cáo chưa chẩn đoán trong hàng đợi kỹ sư, mở báo cáo bằng `/?report=<id>` | Có | Kiểm tay | Có |
| Thông báo Mattermost cho kết quả duyệt và phun thuốc, tiêu đề là liên kết mở báo cáo | Có | Kiểm tay | Có (webhook tự tạo khi dựng stack) |
| Hỏi đáp tài liệu có trích nguồn | Có | Kiểm tay | Có (6 tài liệu bệnh lúa) |
| Portal hiện trạng dịch vụ, lỗi hiển thị tiếng Việt | Có | Kiểm tay | Không áp dụng |

### DX-Lab

| Tính năng | Trong code | Kiểm thử | Dữ liệu demo |
|---|---|---|---|
| Vai STUDENT, LECTURER, TECHNICIAN, ADMIN; sinh viên chỉ thấy yêu cầu của mình | Có | E2E Lab, kiểm tay | Có (`student1`, `lecturer1`, `tech1`, `admin1`) |
| Yêu cầu hỗ trợ: gửi kèm ảnh, trao đổi công khai và nội bộ, nhận, xử lý, xác nhận và đánh giá, mở lại | Có | E2E Lab, kiểm tay | Có (12 yêu cầu ở mọi trạng thái) |
| SLA theo ưu tiên, cảnh báo ở 80% và đánh dấu quá hạn theo lịch | Có | Kiểm tay với SLA rút ngắn | Có |
| Hai kỹ thuật viên nhận cùng yêu cầu: một bên 409 | Có | E2E Lab | Không áp dụng |
| Gợi ý loại và ưu tiên bằng luật, bài tri thức liên quan | Có | E2E Lab, kiểm tay | Có (10 bài quy chế và hướng dẫn) |
| Mượn thiết bị có hạn, duyệt theo vai, trả hỏng mở nhật ký sửa chữa | Có | E2E Lab, kiểm tay | Có (7 thiết bị, 5 phiếu) |
| Đặt phòng chống trùng giờ ở cơ sở dữ liệu, lịch tuần; luật độ dài, bước giờ, sức chứa, người duyệt | Có | Test tích hợp và phân quyền, E2E Lab, kiểm tay | Có (5 phòng, 5 lịch) |
| Hỏi đáp quy chế có trích nguồn và giấy phép | Có | E2E Lab (`--require-knowledge`) | Có |
| Thống kê SLA, tải kỹ thuật viên, mức dùng phòng; view `vw_lab_*` cho BI | Có | E2E Lab, kiểm tay | Có |

## 2. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo |
| Node 20 trở lên | Cho `frontend` và `mobile` |
| Python 3 và `pillow` | Cho `seed_demo.py` (`python3 -m pip install pillow`) |
| Trọng số AI trong `ai/weights/` | `best.pt` và `crop_classifier.onnx` có sẵn trong Git sau checkout |
| Dung lượng và RAM | Lần dựng đầu tải khoảng 3 GB (image AI) và 9 GB (Ollama và model); cần máy đủ RAM để chạy cả stack |

## 3. Kiểm tra tĩnh (không cần dựng stack)

| Bước | Lệnh | Kết quả mong đợi |
|---|---|---|
| Backend biên dịch toàn bộ module | `cd backend && ./mvnw compile` | Cả năm module đạt |
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
Treatment rule của đạo ôn và khô vằn có trong seed Backend, nên `--require-rule` dùng được sau khi seed.

### 5.2 Kiểm tay

| # | Việc | Kết quả mong đợi |
|---|---|---|
| F1 | Đăng nhập web bằng `engineer1`, đi qua các trang | Vào được hàng chờ duyệt, nông hộ, tài liệu |
| F2 | Đăng nhập web bằng `farmer1` | Bị từ chối (403) |
| F3 | Đăng nhập mobile bằng `farmer1`, gửi báo cáo kèm ảnh | Gửi được; ảnh không phải lúa bị chặn |
| F4 | `admin1` duyệt thửa chờ duyệt (An Giang) | Duyệt được; `farmer1` có thông báo |
| F5 | `engineer1` chạy chẩn đoán, duyệt | Chẩn đoán chuyển trạng thái, timeline ghi đủ bước |
| F6 | Khai phun thuốc rồi xác nhận trên chẩn đoán đạo ôn hoặc khô vằn | Vụ vào cách ly; thu hoạch bị chặn tới hết hạn |
| F6b | Chẩn đoán bệnh chưa có rule (đốm nâu) | Chỉ xem xét (`review_required`), không có liều và PHI |
| F7 | Hỏi đáp tri thức | Đoạn trích có nguồn |
| F8 | Mở Superset bằng `admin1` | Dashboard của Farm hiển thị 9 chart, mỗi chart một ô; chart "Thửa đang cách ly" và hai chart thu hoạch có dữ liệu sau khi seed; chart "Tỷ lệ AI đúng/sai" trống nếu chưa chẩn đoán AI bằng ảnh thật |
| F9 | Hai người cùng quyết định một case | Một bên 409 |
| F10 | Chọn một thửa ở "Luồng demo" | Panel Thời tiết hiện nhiệt độ, độ ẩm, dự báo 5 ngày và nguồn |
| F11 | Nông dân gửi báo cáo mới; `engineer1` mở Hàng đợi → "Chưa chẩn đoán" → "Mở để chẩn đoán" | Báo cáo có trong danh sách; liên kết mở đúng thửa, vụ và báo cáo |
| F12 | Duyệt hoặc từ chối chẩn đoán, xác nhận hoặc từ chối khai phun (đặt `MATTERMOST_APP_BASE_URL` để tiêu đề có liên kết) | Kênh "Kỹ sư nông nghiệp" có tin kết quả; tiêu đề mở được báo cáo; `farmer1` có thông báo |

### 5.3 Mobile

Đặt `mobile/.env`:

```bash
EXPO_PUBLIC_KEYCLOAK_URL=http://<ip-máy-chạy-stack>:9080/realms/dxos
EXPO_PUBLIC_KEYCLOAK_CLIENT_ID=farm-mobile
```

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
| L14 | Bất kỳ | Hỏi đáp "quy chế sử dụng phòng máy" | Đoạn trích kèm nguồn và giấy phép |
| L15 | `admin1` | Mở trang thống kê | Số liệu khớp dữ liệu mẫu |
| L16 | `student1` | Mở ticket của người khác bằng đường dẫn trực tiếp | Bị chặn |
| L17 | `tech1` | Rút ngắn SLA khi thử để ticket quá hạn | Cảnh báo, rồi đánh dấu quá hạn; thông báo không gửi trùng |

### 6.3 Kiểm qua API

Dùng Swagger (`http://localhost:9080/swagger-ui.html`) hoặc client trong `deploy/demo/dxos_client.py`, sau khi `reset.sh` và `seed_demo.py --app lab` đã chạy xong.

| Bước | Gọi | Kiểm |
|---|---|---|
| 1 | `GET /api/v1/app` | `id` là `lab`, có 4 vai và menu |
| 2 | `tech1` `POST /rooms` hai lần cùng `code`; `student1` `POST /devices` | Lần hai 409; `student1` 403 |
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
| 16 | Quá 4 giờ, quá khứ, vượt sức chứa, phòng `MAINTENANCE`; `student1` đặt | 422 hoặc 403 |
| 17 | `PUT /sla-policies/NORMAL` `{"responseMinutes":1,"resolutionMinutes":2}`, tạo ticket, đợi 5 phút | Cảnh báo ở 80%, rồi `overdue=true` và thông báo, không gửi trùng |
| 18 | `POST /tickets/suggest` với "máy chiếu phòng A2 không lên hình" và "cả phòng máy mất mạng giữa giờ thi" | Loại thiết bị; ưu tiên khẩn; có từ khóa, lý do, bài liên quan |
| 19 | `POST /knowledge/ask` "Quy định khi sử dụng phòng máy" | Đoạn trích kèm nguồn và `CC BY 4.0` |
| 20 | `GET /lab-stats/summary`, đối chiếu `SELECT` ở DB | Số khớp |
| 21 | `GET /approval-requests` bằng `tech1` | Có ticket, phiếu mượn, đặt phòng |
| 22 | Đăng nhập role đọc BI, `SELECT` từ `vw_lab_tickets` và từ `tickets` | View đọc được, bảng bị từ chối |

## 7. Đổi ứng dụng và ranh giới

| # | Việc | Kết quả mong đợi |
|---|---|---|
| B1 | `./deploy/switch-app.sh farm` rồi tải lại web | Web về giao diện Farm; chạy lại `e2e.py` được 10/10 |
| B2 | Đặt `DXOS_APP=abc` rồi khởi động backend | Dừng sớm, thông báo nêu các giá trị hợp lệ |
| B3 | Khi chạy Lab, gọi một route của Farm (ví dụ `/api/v1/parcels`) | Không tồn tại (404) |
| B4 | Khi chạy Farm, gọi một route của Lab (ví dụ `/api/v1/tickets`) | Không tồn tại (404) |
| B5 | Xem bảng trong DB sau B1 | Bảng Lab còn nhưng không dùng; Farm chạy bình thường |
| B6 | Đổi app không dùng `--reset` | Không lỗi migration |

## 8. Sự cố thường gặp

| Dấu hiệu | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Web hoặc API trả 401 sau khi dựng lại | Trình duyệt giữ token hoặc phiên của lần dựng trước | Đăng xuất, xóa dữ liệu trình duyệt, đăng nhập lại; Superset bấm "Sign in with Keycloak" lại |
| Mobile báo `invalid_client` | Sai client id | Đặt `farm-mobile` trong `mobile/.env`, build và cài lại |
| Realm Keycloak không đổi sau khi sửa file | Realm chỉ được import ở lần khởi động đầu | `./reset.sh --yes` |
| Lỗi `relation ... does not exist` khi khởi động | Database dựng bằng bản schema khác | `./reset.sh --yes` |
| Backend dừng với thông báo `DXOS_APP` | Giá trị biến sai chính tả | Dùng `farm` hoặc `lab` |
| Web hiện giao diện Farm khi backend chạy Lab | Cache trình duyệt, hoặc backend chưa lên khi tải trang | Tải lại cứng, đợi `/api/v1/system/status` UP |
| Route 404 hoặc không bị giới hạn tốc độ | Gateway mount sai file cấu hình theo app | Kiểm `DXOS_APP` và file gateway mà compose mount |
| Tạo ticket hoặc upload nhận 429 | Giới hạn 30 lần mỗi phút theo địa chỉ | Đợi một phút rồi chạy lại |
| Không tạo được extension khi khởi động Lab | Vai của DB không đủ quyền với `btree_gist` | Dùng image PostgreSQL có extension; cấp quyền tạo extension cho vai chủ DB |
| Hỏi đáp Lab không có kết quả | Job `rag-ingest` chưa xong hoặc lỗi, hoặc `DXOS_APP` không phải `lab` lúc dựng | `docker compose logs rag-ingest`; chạy lại bằng `docker compose up -d --force-recreate rag-ingest` |
| Ảnh không hiện | Route ảnh hoặc tên bucket sai | Kiểm `MINIO_BUCKET` và route `/dxos-uploads/*`; `./reset.sh --yes` |
| Thiếu `best.pt` hoặc `crop_classifier.onnx` | Checkout không đủ trọng số | `git status`, `git pull`; xem `ai/weights/` |
| Dựng lâu hoặc hết dung lượng | Lần đầu tải image lớn | Đợi, hoặc dọn Docker (`docker system df`) |

## 9. Kết quả kiểm gần nhất

| Mục | Kết quả | Ghi chú |
|---|---|---|
| Backend `./mvnw clean test` | Đạt | 148 test: dx-core 14, app-farm 23, app-lab 9, dx-host 102 |
| BI `python -m unittest test_chart_query_context` trong `bi/` | Đạt | 22 test |
| AI `pytest` trong `ai/` | Đạt | 79 test |
| Web và mobile `tsc` | Đạt | Không lỗi |
| Ranh giới app và core, seed Farm, nguồn tri thức khớp registry | Đạt | Không có kết quả vi phạm |
| E2E Farm | Đạt | 10/10 bước với `--require-rule` |
| E2E Lab | Đạt | 16/16 bước với `--require-knowledge`, kể cả sau khi đổi app không `--reset` |
| Đổi ứng dụng B1 đến B6 | Đạt | B2 dừng với thông báo nêu `[farm, lab]`; B3, B4 route app khác 404; B6 không lỗi migration |
| Kiểm tay Farm | Đạt | F1, F2, F4 đến F12 (kỹ sư cũng thấy dashboard Superset); hộp duyệt chỉ có loại case của Farm; F3 (mobile) chưa chạy |
| Kiểm tay Lab | Đạt | L1 đến L17, API bước 1 đến 22, các màn hình web; hộp duyệt chỉ có loại case của Lab |
| API và lỗi request | Đạt | 415, 405, 400; `settings` của `GET /api/v1/app`; `GET /api/v1/crop-seasons/isolated` |
| Thời tiết | Đạt | Open-Meteo trả dữ liệu thật; địa chỉ không tới được trả snapshot kèm ghi chú |
| Dựng từ clone sạch theo README | Đạt | `start.sh farm` 113 giây, `start.sh lab` 56 giây, mọi dịch vụ healthy |
| Thêm ứng dụng mẫu rỗng theo CONTRIBUTING | Đạt | Backend lên với `DXOS_APP=sample`, `system/status` trả id `sample`, không sửa `dx-core` |
| gitleaks | Đạt | 358 commit, không có phát hiện |

Mục kiểm tay chưa chạy là F3 và các màn mới của mobile (icon, splash, hỏi đáp tài liệu, thẻ thời tiết), theo dõi ở [`BACKLOG.md`](./BACKLOG.md).
