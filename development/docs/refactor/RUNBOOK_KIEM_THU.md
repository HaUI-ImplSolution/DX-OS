# Hướng dẫn dựng và chạy app để kiểm thử sau refactor

Tài liệu cho người kiểm thử: dựng stack, chạy từng app, chuyển app và kiểm tra theo thứ tự.
Lệnh đánh dấu **(thiết kế)** chưa tồn tại trong repo; chúng được làm ở phase ghi kèm và có thể khác tên khi cài đặt xong, khi đó cập nhật tài liệu này.
Các lệnh còn lại đang chạy được ở repo hiện tại hoặc là dạng đã dùng từ trước.

## 1. Chuẩn bị

| Cần có | Ghi chú |
|---|---|
| Docker và Docker Compose | Backend `verify` dùng Testcontainers nên cần Docker chạy |
| JDK 21 | Dùng Maven wrapper trong repo |
| Node 20 trở lên | Cho `frontend` và `mobile` |
| Python 3 và `pillow` | Cho `seed_demo.py` (`python3 -m pip install pillow`) |
| Trọng số AI trong `ai/weights/` | `best.pt` và `crop_classifier.onnx` có sẵn trong Git sau checkout |
| Dung lượng và RAM | Lần dựng đầu tải khoảng 3 GB (image AI) và 9 GB (Ollama và model); cần máy đủ RAM để chạy cả stack |

Lấy đúng nhánh, rồi kiểm tra không còn thay đổi dở:

```bash
git switch <nhánh cần kiểm thử>
git status          # sạch
```

## 2. Kiểm tra tĩnh (không cần dựng stack)

| Bước | Lệnh | Kết quả mong đợi | Phase |
|---|---|---|---|
| Backend biên dịch toàn bộ module | `cd backend && ./mvnw compile` | Cả bốn module đạt (sau C4 thêm `app-lab`) | C1 trở đi |
| Web | `cd frontend && npx tsc --noEmit` | Không lỗi | C2, C5 |
| Mobile | `cd mobile && npx tsc --noEmit` | Không lỗi | C2, C3 |
| Ranh giới app | Tìm `core.` hoặc `dx-core` trong `app-farm` và `app-lab` | Không có (chỉ `dx-core-api`) | C1, C2, C4 |
| Ranh giới core | Tìm `Household`, `Parcel`, tên view `vw_` trong `dx-core-api`, `dx-core` | Không có | C2 |
| Tên cũ | Tìm `dxfarm` ngoài tài liệu lịch sử, scheme và bundle mobile | Không có | C3 |
| Đường dẫn cũ | Tìm `backend/DX-Farm`, `docs/ARCHITECTURE_OPEN_CORE`, `improvement_docs/_template` | Không có | C1, C7 |
| Backend đầy đủ (có thể bỏ qua nếu chỉ kiểm nhanh) | `cd backend && ./mvnw verify` | Số test không ít hơn baseline (79), không bỏ qua | C1, C2, C4 |
| AI | `cd ai && pytest` | Số test không ít hơn baseline (64) | C2, C4 |

## 3. Dựng stack

### 3.1 Cấu hình

```bash
cd deploy
cp .env.example .env     # điền mọi biến đang để trống (mật khẩu, khóa nội bộ)
```

Đặt app muốn chạy trong `.env` (mặc định `farm`):

```bash
DXOS_APP=farm            # hoặc lab
```

Thiếu biến nào, Compose dừng và báo tên biến đó.

### 3.2 Dựng từ đầu

```bash
./reset.sh --yes         # xóa deploy/data, dựng lại toàn bộ; giữ volume model Ollama
```

Đợi đến khi script báo mọi service sẵn sàng.
Dùng `./reset.sh --yes --keep-images` khi chỉ đổi dữ liệu, không đổi code.

Bắt buộc dựng lại sạch ở lần đầu sau refactor: migration được đánh số lại, tên realm, database và bucket đã đổi.

### 3.3 Kiểm tra stack lên đủ

| Việc | Cách | Kết quả |
|---|---|---|
| Dịch vụ healthy | `docker compose ps` | Mọi service healthy hoặc job đã thoát mã 0 |
| Backend | `curl -s http://localhost:9080/api/v1/system/status` | Có `backend`, `ai`, `storage` ở trạng thái UP; có tên app đang chạy **(thiết kế, C6)** |
| Manifest app | Với token hợp lệ: `GET /api/v1/app` | `id` đúng `farm` hoặc `lab` **(thiết kế, C2)** |
| Portal | http://localhost:9080/portal/ | Hiện hạ tầng và app đang chạy |

### 3.4 Nạp dữ liệu demo

```bash
python3 deploy/demo/seed_demo.py [--app farm|lab]
```

`--app` **(thiết kế, C6)**; nếu không truyền, script đọc `DXOS_APP`.
Seed tạo tài khoản mẫu qua Admin API (realm không còn chứa sẵn), rồi nạp dữ liệu qua API nên đi qua kiểm tra nghiệp vụ.
Chạy lại lần hai không tạo trùng.

## 4. Chạy và kiểm thử Farm (`DXOS_APP=farm`)

### 4.1 Địa chỉ

| Dịch vụ | Địa chỉ |
|---|---|
| Ứng dụng web | http://localhost:9080 |
| Swagger | http://localhost:9080/swagger-ui.html |
| Keycloak Admin | http://localhost:9080/admin |
| Superset | http://localhost:9081 |
| Console MinIO | http://localhost:9082 |
| Mattermost | http://localhost:8065 |

### 4.2 Tài khoản

| Username | Mật khẩu | Vai | Dùng ở |
|---|---|---|---|
| `farmer1` | `farmer123` | FARMER | Chỉ mobile |
| `engineer1` | `engineer123` | ENGINEER | Web, Superset |
| `admin1` | `admin123` | ADMIN | Web, Superset, Keycloak |

### 4.3 E2E tự động

```bash
python3 deploy/demo/e2e.py [--require-rule]
```

Kết quả mong đợi: 10/10 bước đạt, giống baseline ở C0.

### 4.4 So với baseline

- Xuất `/v3/api-docs` rồi so với bản baseline ở C0: không có endpoint nào của Farm đổi, kể cả `/api/v1/households`.
- Chỉ được thêm `/api/v1/workspaces` và `/api/v1/app`.

### 4.5 Kiểm tay Farm

| # | Việc | Kết quả mong đợi |
|---|---|---|
| F1 | Đăng nhập web bằng `engineer1`, đi qua các trang | Đủ trang như trước refactor |
| F2 | Đăng nhập web bằng `farmer1` | Bị từ chối (403) |
| F3 | Đăng nhập mobile bằng `farmer1` (xem 4.6), gửi báo cáo kèm ảnh | Gửi được; ảnh không phải lúa bị chặn |
| F4 | `admin1` duyệt thửa chờ duyệt (An Giang) | Duyệt được; `farmer1` có thông báo |
| F5 | `engineer1` chạy chẩn đoán, duyệt | Trạng thái và lịch sử như trước |
| F6 | Khai phun thuốc rồi xác nhận (khi có rule được duyệt) | Vụ vào cách ly; thu hoạch bị chặn tới hết hạn |
| F7 | Hỏi đáp tri thức | Đoạn trích có nguồn |
| F8 | Mở Superset bằng `admin1` | Dashboard của Farm hiển thị |
| F9 | Hai người cùng quyết định một case | Một bên 409 |

### 4.6 Mobile

Mobile đã đổi client Keycloak nên phải chạy bản mới.
Đặt `mobile/.env`:

```bash
EXPO_PUBLIC_KEYCLOAK_URL=http://<ip-máy-chạy-stack>:9080/realms/dxos
EXPO_PUBLIC_KEYCLOAK_CLIENT_ID=farm-mobile
```

```bash
cd mobile && npx expo start      # Expo Go, hoặc build APK bằng EAS
```

## 5. Chuyển sang Lab (`DXOS_APP=lab`)

```bash
./deploy/switch-app.sh lab            # đổi biến và restart backend (thiết kế, C6)
./deploy/switch-app.sh lab --reset    # kèm dựng lại sạch, dùng khi đổi schema lớn (thiết kế, C6)
python3 deploy/demo/seed_demo.py --app lab
python3 deploy/demo/e2e.py --app lab
```

Không cần build lại image web: web đọc `GET /api/v1/app` rồi nạp giao diện Lab.
Tải lại trang sau khi backend đã lên.

### 5.1 Tài khoản Lab

| Username | Vai | Dùng để |
|---|---|---|
| `student1`, `student2` | STUDENT | Báo sự cố, mượn thiết bị, hỏi đáp |
| `lecturer1` | LECTURER | Duyệt phiếu mượn của sinh viên, đặt phòng |
| `tech1`, `tech2` | TECHNICIAN | Nhận và xử lý ticket, quản lý thiết bị, duyệt đặt phòng |
| `admin1` | ADMIN | Quản trị, gán việc, xem thống kê |

Mật khẩu do script seed in ra hoặc ghi trong `deploy/demo/README.md` sau C6.

### 5.2 Kiểm tay Lab theo vai

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

## 6. Chuyển ngược và kiểm tra ranh giới

| # | Việc | Kết quả mong đợi |
|---|---|---|
| B1 | `./deploy/switch-app.sh farm` rồi tải lại web | Web về giao diện Farm; chạy lại `e2e.py` được 10/10 |
| B2 | Đặt `DXOS_APP=abc` rồi khởi động backend | Dừng sớm, thông báo nêu các giá trị hợp lệ |
| B3 | Khi chạy Lab, gọi một route của Farm (ví dụ `/api/v1/parcels`) | Không tồn tại (404) |
| B4 | Khi chạy Farm, gọi một route của Lab (ví dụ `/api/v1/tickets`) | Không tồn tại (404) |
| B5 | Xem bảng trong DB sau B1 | Bảng Lab còn nhưng không dùng; Farm chạy bình thường |
| B6 | Đổi app không dùng `--reset` | Không lỗi migration |

## 7. Điểm dừng kiểm thử theo phase

Khi muốn kiểm giữa chừng, chỉ cần chạy tới đây là đủ.

| Sau phase | Chạy | Chấp nhận khi |
|---|---|---|
| C1 | Mục 2, mục 3 (Farm), mục 4.3 và 4.4 | `compile` đạt; ranh giới sạch; E2E 10/10; OpenAPI không đổi |
| C2 | Mục 2, mục 3 (Farm), mục 4 đầy đủ | Như C1, thêm F1 đến F9 đạt; `/api/v1/app` trả `farm` |
| C3 | Dựng sạch, seed, mục 4.5 và 4.6 | Đăng nhập web, mobile, Superset đều được; token audience cũ bị 401 |
| C4 | Mục 3 với `DXOS_APP=lab`, gọi API qua Swagger | L2 đến L14 đạt qua API; chạy `farm` vẫn đạt |
| C5 | Mục 5 đến hết L16 | Web Lab đi hết kịch bản từng vai; web Farm không đổi |
| C6 | Mục 5, mục 6 và E2E hai app | `switch-app.sh` hoạt động; E2E hai app đạt; CI xanh |
| C7 | Làm theo README trên clone sạch | Không còn link gãy; làm được theo hướng dẫn thêm app |

## 8. Sự cố thường gặp

| Dấu hiệu | Nguyên nhân thường gặp | Cách xử lý |
|---|---|---|
| Web hoặc API trả 401 sau khi đổi tên | Token hoặc phiên cũ có audience `dxfarm-api` | Đăng xuất, xóa dữ liệu trình duyệt, đăng nhập lại |
| Mobile báo `invalid_client` | Còn dùng client id cũ | Đặt `farm-mobile` trong `mobile/.env`, build và cài lại |
| Realm Keycloak không đổi sau khi sửa file | Realm chỉ được import ở lần khởi động đầu | `./reset.sh --yes` |
| Lỗi `relation ... does not exist` khi khởi động | DB cũ dùng migration trước refactor | `./reset.sh --yes` |
| Backend dừng với thông báo `DXOS_APP` | Giá trị biến sai chính tả | Dùng `farm` hoặc `lab` |
| Web hiện giao diện Farm khi backend chạy Lab | Cache trình duyệt, hoặc backend chưa lên khi tải trang | Tải lại cứng, đợi `/api/v1/system/status` UP |
| Route 404 hoặc không bị giới hạn tốc độ | Gateway mount sai file cấu hình theo app | Kiểm `DXOS_APP` và file gateway mà compose mount |
| Không tạo được extension khi khởi động Lab | Vai của DB không đủ quyền với `btree_gist` | Dùng image PostgreSQL có extension; cấp quyền tạo extension cho vai chủ DB |
| Hỏi đáp Lab không có kết quả | Chưa nạp tri thức namespace `lab` | Chạy lại job `rag-ingest` |
| Ảnh không hiện | Route ảnh hoặc tên bucket cũ | Kiểm `MINIO_BUCKET` và route `/dxos-uploads/*`; `./reset.sh --yes` |
| Thiếu `best.pt` hoặc `crop_classifier.onnx` | Checkout không đủ trọng số | `git status`, `git pull`; xem `ai/weights/` |
| Dựng lâu hoặc hết dung lượng | Lần đầu tải image lớn | Đợi, hoặc dọn Docker (`docker system df`) |

## 9. Ghi kết quả

| Mục | Người chạy | Ngày | Kết quả (Đạt hoặc `Fail #issue`) | Ghi chú |
|---|---|---|---|---|
| Mục 2: kiểm tra tĩnh | | | | |
| Mục 4.3: E2E Farm | | | | |
| Mục 4.4: OpenAPI so baseline | | | | |
| Mục 4.5: F1 đến F9 | | | | |
| Mục 5: E2E Lab | | | | |
| Mục 5.2: L1 đến L17 | | | | |
| Mục 6: B1 đến B6 | | | | |
