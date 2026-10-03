# Dữ liệu demo và kiểm thử đầu-cuối

Các script Python 3 (thư viện chuẩn, thêm `pillow` cho ảnh tổng hợp) gọi API qua gateway, địa chỉ đặt bằng `--base-url` (ví dụ `https://khuongdev.io.vn`).
Đăng nhập bằng tài khoản demo qua Authorization Code + PKCE của Keycloak nên token mang đúng client như app thật (`farmer1` đi qua client mobile).
Mỗi script làm việc với app đang chạy; `--app farm|lab` chọn rõ ràng và dừng nếu backend chạy app khác.

| File | Việc làm |
|---|---|
| `seed_demo.py` | Chọn app, nạp dữ liệu demo (`seed_farm.py`, `seed_lab.py`) |
| `e2e.py` | Chọn app, chạy kịch bản đầu-cuối (`e2e_farm.py`, `e2e_lab.py`), thoát mã 0 khi mọi bước đạt |
| `dxos_client.py` | Đăng nhập, gọi API, Keycloak Admin API, bộ chạy bước dùng chung |
| `check_gateway_routes.py` | Kiểm trong CI: mọi route của gateway không có kiểm token đều nằm trong danh sách công khai có ghi lý do |
| `docker-compose.e2e.yml`, `make_ci_env.py` | Stack rút gọn cho CI (không BI, chat, nạp RAG, Ollama; tắt giới hạn tốc độ theo người dùng vì seed và E2E tạo dữ liệu theo đợt); `make_ci_env.py` cũng sinh `deploy/.env` với mật khẩu ngẫu nhiên cho lần cài đầu, giữ nguyên tệp đã có trừ khi có `--force` |
| `ci_fixture_rule.sql` | Rule thử nghiệm riêng cho CI của Farm |

## Nạp dữ liệu

```bash
python3 -m pip install pillow
python3 deploy/demo/seed_demo.py [--app farm|lab] [--base-url URL] [--images DIR]
```

`deploy/start.sh [app]` đã gọi script này sau khi dựng stack; chạy riêng khi cần nạp lại hoặc dùng `--images`.
Script đọc `deploy/.env` và gọi `docker compose exec postgres`, nên chạy trên máy đang chạy stack; chạy lại không tạo trùng.
Seed Farm upload ảnh nên chờ tối đa 300 giây khi dịch vụ AI chưa sẵn sàng.
Seed bị dừng trước bước báo cáo thì lần chạy sau tạo tiếp báo cáo và nông hộ còn thiếu; thửa hoặc vụ dở dang thì script dừng và báo dùng `deploy/reset.sh`.
`e2e.py` dùng tài khoản do seed tạo nên phải seed trước.

## Farm

**Tài khoản.** `farmer1`, `engineer1`, `admin1` (vai `FARMER`, `ENGINEER`, `ADMIN`) cùng `demo.hogd2`, `demo.hogd3`; mật khẩu tạm của hai tài khoản sau in ra một lần.

| Thửa | Hộ | Diện tích | Trạng thái | Vụ |
|---|---|---|---|---|
| Phong Điền, Cần Thơ | `farmer1` | 1,20 ha | Đã duyệt | Đang canh tác (Thu Đông) |
| Mỹ Xuyên, Sóc Trăng | `farmer1` | 0,85 ha | Đã duyệt | Đã thu hoạch, 7.200 kg |
| Châu Phú, An Giang | `farmer1` | 2,00 ha | Chờ duyệt | Không có |
| Tháp Mười, Đồng Tháp | `demo.hogd2` | 1,50 ha | Đã duyệt | Đang canh tác |
| Vĩnh Thuận, Kiên Giang | `demo.hogd3` | 1,10 ha | Đã duyệt | Đang canh tác |

- Thửa của `farmer1` đi qua luồng nông dân gửi và admin duyệt (GPS có sai số, `Idempotency-Key`); thửa của hai hộ còn lại do admin tạo nên duyệt ngay.
- Ba báo cáo sâu bệnh trên vụ đang canh tác của `farmer1`: đã chẩn đoán và duyệt, chờ kỹ sư duyệt, kỹ sư yêu cầu chụp lại.
- Hàng chờ có hai việc (thửa An Giang cho admin, chẩn đoán số 2 cho kỹ sư); `farmer1` có thông báo từ các lần duyệt.
- Thuốc, treatment rule, tài liệu RAG và danh mục giống đến từ seed của Backend và job `rag-ingest`, script không tạo.
- Bệnh có rule được duyệt (đạo ôn, khô vằn, đốm nâu) thì script ghi thêm bản khai phun và xác nhận để vụ vào cách ly; bệnh chưa có rule chỉ ở trạng thái xem xét, không có liều và PHI.
- **Ảnh:** không có `--images` thì dùng ảnh tổng hợp và chẩn đoán nhập thủ công (`Rice___Blast`, `manual-input-v1`); có `--images DIR` (`.jpg` lá lúa thật) thì gọi chẩn đoán AI. Chart "Tỷ lệ AI đúng/sai" trống tới khi có chẩn đoán AI.

### E2E Farm

```bash
python3 deploy/demo/e2e.py [--app farm] [--require-rule]
```

Kịch bản (10 bước): trạng thái hệ thống công khai, engineer bị chặn tạo thửa, GPS giả lập bị từ chối, thửa chờ duyệt chưa tạo được vụ, chỉ admin duyệt, thu hoạch trước ngày dự kiến bị chặn, upload idempotent, ảnh trắng không qua kiểm tra chất lượng, chẩn đoán chờ rồi được duyệt.
Khi có treatment rule: bản khai phun chặn thu hoạch tới khi kỹ sư xác nhận, rồi khóa cách ly chặn tới `harvestLockedUntil`.
`--require-rule` coi việc thiếu rule là lỗi.

## Lab

**Tài khoản** (vai do backend tạo lúc khởi động):

| Username | Mật khẩu | Vai | Workspace |
|---|---|---|---|
| `student1`, `student2` | `student123` | STUDENT | Lớp CNTT K15A |
| `lecturer1` | `lecturer123` | LECTURER | Bộ môn Công nghệ phần mềm |
| `tech1`, `tech2` | `tech1234` | TECHNICIAN | Không |
| `admin1` | `admin123` | ADMIN | Không |

- **Phòng, thiết bị:** 5 phòng (`A101`, `A102` phòng máy 40 chỗ, `A103` đang bảo trì, `A201`, `B301`); 7 thiết bị (3 laptop, 2 máy chiếu cho mượn, 1 thiết bị mạng, 1 máy in).
- **Yêu cầu hỗ trợ (15)** ở mọi trạng thái: 4 mới, 1 đã giao (khẩn), 2 đang xử lý, 1 chờ người dùng, 2 đã giải quyết, 3 đã đóng (có đánh giá, một đã mở lại, một có ảnh), 1 từ chối, 1 đã hủy.
- **Phiếu mượn (5):** đang mượn, đã trả tốt, trả hỏng (máy chiếu sang "Đang sửa" kèm nhật ký), bị từ chối, chờ duyệt.
- **Đặt phòng (5)** của `lecturer1`: 2 đã duyệt ở `A101`, 1 chờ duyệt ở `A102`, 1 bị từ chối ở `B301`, 1 đã hủy ở `A201`.
- Mọi bản ghi mang `Idempotency-Key` cố định nên chạy lại không tạo trùng; bài quy chế do `rag-ingest` nạp lúc dựng stack với `DXOS_APP=lab`, script không nạp.

### E2E Lab

```bash
python3 deploy/demo/e2e.py --app lab [--require-knowledge]
```

Mỗi lần chạy tạo phòng, thiết bị, yêu cầu riêng (mã có hậu tố ngẫu nhiên) nên chạy lại được trên cùng stack.
16 bước, gom theo nhóm:

1. Trạng thái hệ thống nêu app `lab`; route của app khác 404.
2. Gợi ý loại và ưu tiên kèm từ khóa, lý do.
3. Sinh viên báo sự cố kèm ảnh: mã `TK-<năm>-<số>`, hạn phản hồi và hạn xử lý.
4. Hai kỹ thuật viên nhận cùng lúc: 200 và 409.
5. Trả lời công khai và ghi chú nội bộ: sinh viên chỉ thấy câu trả lời; sinh viên ghi chú nội bộ bị 403.
6. Hoàn tất thiếu nguyên nhân gốc bị 422 `ROOT_CAUSE_REQUIRED`, rồi đủ; sinh viên xác nhận kèm đánh giá, ticket đóng, timeline đủ bước.
7. Sinh viên khác không mở được ticket (404); sinh viên không xem được thống kê (403).
8. Mượn thiết bị được giảng viên duyệt; mượn lại bị 422 `DEVICE_NOT_AVAILABLE`.
9. Trả hỏng thiếu ghi chú bị 422 `RETURN_NOTE_REQUIRED`; có ghi chú thì thiết bị sang "Đang sửa" kèm nhật ký.
10. Giảng viên đặt phòng, kỹ thuật viên duyệt, lịch tuần có khung giờ.
11. Đặt chồng giờ `ROOM_SLOT_TAKEN`, quá 4 giờ `BOOKING_TOO_LONG`, vượt sức chứa `ROOM_CAPACITY_EXCEEDED`; sinh viên đặt phòng bị 403.
12. Hỏi đáp quy chế; `--require-knowledge` đòi mỗi đoạn trích có nguồn và giấy phép (cần corpus Lab đã nạp, stack CI rút gọn không có).
13. Thống kê khớp số ticket tạo trong kỳ, có điểm đánh giá; hộp duyệt của kỹ thuật viên trả được.

## CI

Workflow `E2E` chạy ma trận `farm` và `lab`, mỗi app một stack rút gọn: sinh `.env` (`make_ci_env.py --app`), dựng stack, kiểm file gateway chỉ có route của app, `seed_demo.py` hai lần, rồi `e2e.py`.
Farm thêm `ci_fixture_rule.sql` trước `e2e.py --require-rule`; rule này nằm trong database tạm của CI, không thuộc dữ liệu demo hay seed.
Ba phần trọng số bệnh và `crop_classifier.onnx` lấy từ checkout; Docker build ghép checkpoint bệnh, không có bước download model bệnh.
