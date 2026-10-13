# Dữ liệu demo và kiểm thử đầu-cuối

Các script dùng Python 3 (thư viện chuẩn, thêm `pillow` để tạo ảnh tổng hợp) và gọi API qua gateway `http://localhost:9080`.
Đăng nhập bằng tài khoản demo do `seed_demo.py` tạo, qua luồng Authorization Code + PKCE của Keycloak, nên token mang đúng client như app thật (`farmer1` đi qua client mobile).
Mỗi script làm việc với app đang chạy; `--app farm|lab` chọn rõ ràng và dừng nếu backend chạy app khác.
Không có `--app` thì app lấy từ `app.id` của `GET /api/v1/system/status`.

| File | Việc làm |
|---|---|
| `seed_demo.py` | Chọn app, nạp dữ liệu demo |
| `seed_farm.py`, `seed_lab.py` | Tài khoản và dữ liệu của từng app |
| `e2e.py` | Chọn app, chạy kịch bản đầu-cuối, thoát mã 0 khi mọi bước đạt |
| `e2e_farm.py`, `e2e_lab.py` | Kịch bản của từng app |
| `dxos_client.py` | Đăng nhập, gọi API, Keycloak Admin API và bộ chạy bước dùng chung |
| `docker-compose.e2e.yml` | Stack rút gọn cho CI: không BI, chat, nạp RAG, Ollama |
| `ci_fixture_rule.sql` | Một rule thử nghiệm riêng cho CI của Farm |
| `make_ci_env.py` | Sinh `deploy/.env` cho stack CI (`--app farm\|lab`) |

## Nạp dữ liệu demo

```bash
python3 -m pip install pillow
python3 deploy/demo/seed_demo.py [--app farm|lab] [--base-url URL] [--images DIR]
```

Chạy trên stack mới dựng (`deploy/reset.sh`, hoặc `deploy/switch-app.sh <app>`, đợi đến khi báo mọi service sẵn sàng) và cần `deploy/.env`.
Script đọc `.env` (tài khoản quản trị Keycloak và Postgres) và gọi `docker compose exec postgres` khi cần, nên chạy trên máy đang chạy stack.
`e2e.py` dùng các tài khoản do seed tạo, nên chạy `seed_demo.py` trước.
Chạy lại khi đã có dữ liệu demo thì không tạo thêm.

## Farm

### Dữ liệu được tạo

**Tài khoản.** `farmer1`, `engineer1`, `admin1` được tạo qua Keycloak Admin API cùng vai `FARMER`, `ENGINEER`, `ADMIN`; tài khoản và vai đã có thì giữ nguyên.
Script tạo thêm `demo.hogd2` và `demo.hogd3`; mật khẩu tạm in ra một lần ở cuối lúc chạy.

**Nông hộ.**

| Hộ | Tài khoản | Cách tạo |
|---|---|---|
| Nguyễn Văn Hậu (Cần Thơ) | `farmer1` | Thêm một hàng `workspaces` (loại `HOUSEHOLD`) bằng `psql`, rồi gắn tài khoản qua API |
| Trần Thị Mai (Tháp Mười, Đồng Tháp) | `demo.hogd2` | Admin tạo |
| Lê Văn Phước (Vĩnh Thuận, Kiên Giang) | `demo.hogd3` | Admin tạo |

**Thửa và vụ mùa.**

| Thửa | Hộ | Diện tích | Trạng thái | Vụ |
|---|---|---|---|---|
| Phong Điền, Cần Thơ | `farmer1` | 1,20 ha | Đã duyệt | Đang canh tác (bắt đầu 45 ngày trước, Thu Đông) |
| Mỹ Xuyên, Sóc Trăng | `farmer1` | 0,85 ha | Đã duyệt | Đã thu hoạch, 7.200 kg |
| Châu Phú, An Giang | `farmer1` | 2,00 ha | Chờ duyệt | Không có |
| Tháp Mười, Đồng Tháp | `demo.hogd2` | 1,50 ha | Đã duyệt | Đang canh tác |
| Vĩnh Thuận, Kiên Giang | `demo.hogd3` | 1,10 ha | Đã duyệt | Đang canh tác |

- Thửa của `farmer1` đi qua luồng nông dân gửi và admin duyệt, kèm GPS có sai số và `Idempotency-Key`.
  Thửa và vụ của hai hộ còn lại do admin tạo nên được duyệt ngay.
- Mọi vụ dùng giống đầu tiên trong danh mục giống; mã thửa và mã vụ do backend sinh.

**Báo cáo sâu bệnh** trên vụ đang canh tác của `farmer1`:

1. Đã chẩn đoán và kỹ sư đã duyệt.
2. Đã chẩn đoán, chờ kỹ sư duyệt.
3. Kỹ sư yêu cầu chụp lại, lý do "Ảnh chưa thấy rõ vết bệnh, hãy chụp lại gần lá hơn."

**Kết quả trên giao diện.**

- Hàng chờ có hai việc: thửa An Giang (admin) và chẩn đoán số 2 (kỹ sư).
- `farmer1` có thông báo từ các lần duyệt thửa, vụ, chẩn đoán và yêu cầu chụp lại.
- Superset có dữ liệu cho 4 thửa đã duyệt, 4 vụ và 1 chẩn đoán đã duyệt.
  Chart "Tỷ lệ AI đúng/sai" trống cho tới khi có chẩn đoán AI; chart "Thửa đang cách ly" lấy dữ liệu từ bản khai phun theo rule đạo ôn.

Script không tạo thuốc, treatment rule, tài liệu RAG hay danh mục giống; các dữ liệu này đến từ seed của Backend và job `rag-ingest` lúc dựng stack.

**Ảnh.** Không có `--images`, script dùng ảnh tổng hợp: đủ cho các màn hình và kiểm tra chất lượng ảnh, không đủ cho chẩn đoán AI có ý nghĩa; chẩn đoán khi đó nhập thủ công (`Rice___Blast`, `manual-input-v1`).
Có `--images DIR` (các file `.jpg` ảnh lá lúa thật), script gọi chẩn đoán AI cho báo cáo.

**Treatment rule.** Thuốc và rule đến từ seed của Backend, script không tạo thêm.
Bệnh có rule được duyệt (đạo ôn, khô vằn) thì script ghi thêm bản khai phun thuốc và xác nhận, vụ vào trạng thái cách ly.
Bệnh chưa có rule thì chẩn đoán ở trạng thái chỉ xem xét, không có liều và PHI.

### Kiểm thử đầu-cuối

```bash
python3 deploy/demo/e2e.py [--app farm] [--base-url URL] [--require-rule]
```

Các bước: trạng thái hệ thống công khai, engineer bị chặn tạo thửa, GPS giả lập bị từ chối, thửa chờ duyệt và chưa tạo được vụ, chỉ admin duyệt, thu hoạch trước ngày dự kiến bị chặn, upload idempotent, ảnh trắng không qua kiểm tra chất lượng, chẩn đoán chờ rồi được duyệt.
Khi có treatment rule: bản khai phun chặn thu hoạch tới khi kỹ sư xác nhận, sau đó khóa cách ly chặn thu hoạch tới `harvestLockedUntil`.
Không có rule, kịch bản kiểm chẩn đoán ở trạng thái chỉ xem xét; `--require-rule` coi việc thiếu rule là lỗi.

## Lab

### Dữ liệu được tạo

**Tài khoản** (qua Keycloak Admin API, vai do backend tạo lúc khởi động):

| Username | Mật khẩu | Vai | Workspace |
|---|---|---|---|
| `student1`, `student2` | `student123` | STUDENT | Lớp CNTT K15A |
| `lecturer1` | `lecturer123` | LECTURER | Bộ môn Công nghệ phần mềm |
| `tech1`, `tech2` | `tech123` | TECHNICIAN | Không |
| `admin1` | `admin123` | ADMIN | Không |

**Phòng và thiết bị.**

- Năm phòng: `A101`, `A102` (phòng máy 40 chỗ), `A103` (phòng máy đang bảo trì), `A201` (phòng học), `B301` (phòng họp).
- Bảy thiết bị: ba laptop, hai máy chiếu (cho mượn), một thiết bị mạng ở `A101` (có nhật ký kiểm tra) và một máy in ở `A201`.

**Yêu cầu hỗ trợ** (12, tạo bằng tài khoản người yêu cầu, xử lý bằng `tech1` và `tech2`):

| Trạng thái | Số lượng | Ghi chú |
|---|---|---|
| Mới | 3 | Chưa ai nhận |
| Đã giao | 1 | Ưu tiên khẩn, mất mạng giữa giờ thi |
| Đang xử lý | 1 | |
| Chờ người dùng | 1 | Kỹ thuật viên hỏi thêm thông tin |
| Đã giải quyết | 1 | Chờ người dùng xác nhận |
| Đã đóng | 3 | Có đánh giá 3 đến 5 sao; một yêu cầu đã mở lại một lần; một có ảnh đính kèm |
| Từ chối | 1 | Admin từ chối |
| Đã hủy | 1 | Người yêu cầu hủy |

**Phiếu mượn** (5): một đang mượn, một đã trả tốt, một trả hỏng (máy chiếu sang "Đang sửa" kèm nhật ký sửa chữa), một bị từ chối, một chờ duyệt.

**Đặt phòng** (5, của `lecturer1`, tính từ ngày chạy script): hai lịch đã duyệt ở `A101`, một chờ duyệt ở `A102`, một bị từ chối ở `B301`, một đã hủy ở `A201`.

Yêu cầu, phiếu mượn và lịch đặt phòng mang `Idempotency-Key` cố định, và mỗi mục chỉ được đẩy tới trạng thái đích, nên chạy lại không tạo trùng.
Thời điểm tạo của yêu cầu là lúc chạy script: lịch sử luồng duyệt (`workflow_event`) chỉ thêm, không sửa.

Script không nạp tri thức: bài quy chế do job `rag-ingest` nạp lúc dựng stack với `DXOS_APP=lab`.

### Kiểm thử đầu-cuối

```bash
python3 deploy/demo/e2e.py --app lab [--base-url URL] [--require-knowledge]
```

Mỗi lần chạy tạo phòng, thiết bị và yêu cầu riêng (mã có hậu tố ngẫu nhiên) nên chạy lại được trên cùng stack.
16 bước:

1. Trạng thái hệ thống công khai, nêu app `lab`.
2. Route của app khác không tồn tại (404).
3. Gợi ý loại và ưu tiên kèm từ khóa và lý do (máy chiếu; mất mạng giữa giờ thi là khẩn).
4. Sinh viên báo sự cố kèm ảnh, có mã `TK-<năm>-<số>` và hạn phản hồi, hạn xử lý.
5. Hai kỹ thuật viên nhận cùng lúc: một bên 200, một bên 409.
6. Trả lời công khai và ghi chú nội bộ: sinh viên chỉ thấy câu trả lời; sinh viên ghi chú nội bộ bị 403.
7. Hoàn tất thiếu nguyên nhân gốc bị 422 `ROOT_CAUSE_REQUIRED`, rồi hoàn tất đủ.
8. Sinh viên xác nhận kèm đánh giá, yêu cầu đóng và timeline đủ bước.
9. Sinh viên khác không mở được yêu cầu (404); sinh viên không xem được thống kê (403).
10. Mượn thiết bị được giảng viên duyệt; mượn lại thiết bị đó bị 422 `DEVICE_NOT_AVAILABLE`.
11. Trả hỏng thiếu ghi chú bị 422 `RETURN_NOTE_REQUIRED`; có ghi chú thì thiết bị sang "Đang sửa" và có nhật ký.
12. Giảng viên đặt phòng, kỹ thuật viên duyệt, lịch tuần có khung giờ.
13. Đặt chồng giờ bị `ROOM_SLOT_TAKEN`, quá 4 giờ bị `BOOKING_TOO_LONG`, vượt sức chứa bị `ROOM_CAPACITY_EXCEEDED`.
14. Sinh viên đặt phòng bị 403.
15. Hỏi đáp quy chế: kiểm hình dạng câu trả lời; `--require-knowledge` đòi mỗi đoạn trích có nguồn và giấy phép (cần corpus Lab đã nạp, stack CI rút gọn không có).
16. Thống kê khớp: số yêu cầu theo trạng thái bằng tổng yêu cầu tạo trong kỳ, có điểm đánh giá; hộp duyệt của kỹ thuật viên trả được.

## CI

Workflow `E2E` trên GitHub chạy ma trận `farm` và `lab`, mỗi app một stack rút gọn:
checkout, sinh `.env` (`make_ci_env.py --app`), dựng stack, kiểm file gateway chỉ có route của app, `seed_demo.py` hai lần, rồi `e2e.py`.
Farm thêm một rule thử nghiệm của CI (`ci_fixture_rule.sql`) trước `e2e.py --require-rule`; rule này nằm trong database tạm của CI, không thuộc dữ liệu demo hay seed.

Checkout có sẵn `best.pt` và `crop_classifier.onnx` trong `ai/weights/`.
Stack mount read-only và nạp trực tiếp; E2E không có bước download classifier.
