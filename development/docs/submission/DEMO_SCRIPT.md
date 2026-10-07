# Kịch bản demo 10 phút

Chuỗi trình bày: người dùng, Open-Core, quy trình, dữ liệu, trí tuệ, kết quả.
Chuẩn bị trước: stack đã dựng sạch, `deploy/demo/seed_demo.py` đã chạy (kèm `--images` nếu có ảnh lá lúa thật), mở sẵn các tab ở bảng dưới.

| Tab | Địa chỉ |
|---|---|
| Portal | http://localhost:9080/portal/ |
| Web kỹ sư (`engineer1`) | http://localhost:9080 |
| Web admin (`admin1`) | cửa sổ ẩn danh |
| Superset | http://localhost:9081 |
| Mattermost | http://localhost:8065 |
| Sơ đồ | `development/docs/ARCHITECTURE_OPEN_CORE.md` |

Mobile của nông dân (`farmer1`) chạy trên điện thoại hoặc emulator.

## Các bước

| Phút | Việc làm | Nói và chỉ vào |
|---|---|---|
| 0:00-1:00 | Mở sơ đồ 3 tầng, rồi portal | "Đây là Tầng 1 Core, đây là bốn không gian H-P-D-I, đây là Tầng 3 ứng dụng." Portal hiện trạng thái từng dịch vụ đang hoạt động |
| 1:00-2:30 | Mobile: đăng nhập `farmer1`, xem thửa và vụ, hộp thông báo | [H] một lần đăng nhập Keycloak, nông dân chỉ thấy thửa của hộ mình, web và Superset từ chối tài khoản này |
| 2:30-4:00 | Mobile: tạo thửa mới (GPS), chụp ảnh sâu bệnh và gửi | Ảnh được kiểm tra chất lượng trước khi gửi và kiểm lại ở server; gửi lại tự động khi mất mạng |
| 4:00-5:30 | Web admin: hàng chờ duyệt, duyệt thửa vừa tạo, mở timeline | [P] workflow dùng chung: mỗi quyết định là một sự kiện chỉ thêm, có người phụ trách; Mattermost nhận thông báo |
| 5:30-7:00 | Web kỹ sư: mở báo cáo, chạy chẩn đoán AI, xem kết quả, duyệt | [I] CV, độ tin cậy, ngưỡng 0.70, nguồn RAG; kỹ sư là người chốt; liều và PHI chỉ lấy từ dữ liệu có cấu trúc |
| 7:00-8:00 | Phun thuốc và khóa thu hoạch | Có rule được duyệt: nông dân khai phun, kỹ sư xác nhận, thu hoạch bị chặn `POKA_YOKE_VIETGAP_VIOLATION`. Chưa có rule: nói rõ chẩn đoán ở trạng thái `review_required`, không có liều hay PHI, và chỉ ra vì sao không bịa số liệu |
| 8:00-9:00 | Superset: dashboard 7 chart | [D] chỉ đọc view của bản ghi đã duyệt, không có dữ liệu cá nhân; thời tiết có snapshot dự phòng |
| 9:00-9:30 | Web: trang Tài liệu, hỏi một câu | Trả lời trích nguyên văn kèm nguồn và giấy phép |
| 9:30-10:00 | Tổng kết | Kết quả: chuỗi báo bệnh, chẩn đoán, duyệt, phun, khóa thu hoạch chạy trên stack mở; kiểm chứng bằng E2E trong CI |

## Phương án dự phòng

| Sự cố | Xử lý |
|---|---|
| AI hoặc Ollama chưa sẵn sàng | Portal hiện AI ngừng; chuyển sang chẩn đoán nhập thủ công (`POST .../diagnosis`) và nói rõ đây là đường dự phòng |
| Mạng không có | Thời tiết trả snapshot (`isSnapshot=true`); mobile gửi lại khi có mạng |
| Điện thoại không kết nối | Dùng emulator hoặc quay sẵn video đoạn mobile |
| Máy yếu không chạy Ollama | Hỏi đáp và diễn đạt dùng nhánh `evidence_fallback` của AI |
| Superset trống | Chạy lại `seed_demo.py`; chart "Thửa đang cách ly" và "Tỷ lệ AI đúng/sai" cần rule được duyệt và chẩn đoán AI |

## Bằng chứng đi kèm

- Log workflow `E2E` trên GitHub.
- Kết quả `./mvnw verify` và `pytest`.
- Video quay thử toàn bộ kịch bản.
