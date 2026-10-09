# Phase C6: Chuyển app, triển khai, CI và dữ liệu demo

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | `dx-host`, hạ tầng, demo |
| Ước lượng | 3 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C3, C4, C5 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| RD2 ở `refactor_docs/README.md` | Đổi `DXOS_APP` rồi restart backend là chuyển app | Nghiêm trọng | Toàn bộ |
| Đề thi HaUI mục 5, 7, 9 | Build và chạy từ source, dữ liệu mẫu, kịch bản demo đầu-cuối | Nghiêm trọng | Toàn bộ |
| Đề thi HaUI mục 12 | Demo không chạy bị trừ điểm | Nghiêm trọng | CI hai app |

## 2. Lý do

- Vấn đề đang gặp: compose, gateway, seed, E2E, dashboard Superset và CI đều giả định chỉ có Farm.
  Gateway có route và giới hạn tốc độ riêng của Farm (`disease-reports`, `diagnosis/ai`).
  Script seed và E2E tạo thửa, vụ, báo cáo.
- Vì sao làm ở phase này: sau C4 và C5 hệ thống chạy được hai app nhưng chưa dựng, kiểm và demo được theo từng app.

## 3. Tổng quan sửa đổi

Truyền `DXOS_APP` xuyên suốt compose.
Tách cấu hình gateway thành phần chung và phần theo app.
Tách seed và E2E thành phần chung và phần theo app.
Superset nạp dashboard theo app.
CI chạy E2E cho cả hai app.
Thêm script `deploy/switch-app.sh` và kịch bản demo hai app.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | |
| `dx-core` (cài đặt dùng chung) | Không | |
| `app-farm` | Không | |
| `app-lab` | Không | |
| `dx-host` (chạy, chọn app) | Có | Mặc định theo `DXOS_APP`, nhãn trạng thái hệ thống |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Có | Seed theo app |
| Infra / Deploy / CI | Có | Compose, APISIX, Superset, CI, script chuyển app |
| Docs / PoF | Có | `deploy/demo/README.md`, kịch bản demo |

## 4. Phạm vi

- Trong phạm vi: truyền biến, cấu hình gateway theo app, seed và E2E theo app, dashboard theo app, CI matrix, script chuyển app.
- Ngoài phạm vi: chạy hai app đồng thời; mobile cho Lab.
- Giả định: C3 đã đổi tên; C4, C5 chạy được từng app độc lập.
- Ranh giới Core/App: phần chung của gateway, seed, E2E không nhắc tên app; phần theo app nằm ở thư mục riêng.

## 5. Ưu điểm và hạn chế

- Ưu điểm: một lệnh đổi app, một CI kiểm cả hai app.
- Hạn chế: thời gian E2E của CI gấp đôi; đổi app trên cùng DB để lại bảng của app cũ.
- Trade-off lớn: nhỏ, ghi ở đây: đổi app trên cùng DB không dọn dữ liệu app cũ (RD9); muốn sạch thì `deploy/reset.sh`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: `./deploy/switch-app.sh lab` rồi mở web thấy Lab với dữ liệu mẫu; `./deploy/switch-app.sh farm` thấy Farm như trước.
- Sản phẩm bàn giao: script chuyển app, cấu hình gateway theo app, seed và E2E theo app, workflow CI.
- Tiêu chí đo được:
  - CI chạy E2E hai lần (`farm`, `lab`) và đạt.
  - Không có route hay giới hạn của app khác trong cấu hình gateway đang chạy.
  - Superset: Farm có dashboard như trước; Lab đọc được view `vw_lab_*`.
  - Đổi app mà không sửa code hay build lại image web.
- Bằng chứng: log CI, ảnh chụp hai chế độ, kết quả chạy script chuyển app.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: C3 (tên), C4 (API Lab), C5 (web Lab).
- Phase sau cần từ phase này: C7 (tài liệu, ảnh chụp, kịch bản demo).

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: build và chạy từ source (10 điểm); mức độ hoàn thiện và ổn định khi demo (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 3 và 8 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Cấu hình gateway theo app | A: hai file đầy đủ sinh từ phần chung và phần app. B: một file chung cho mọi route | Đầu phase | Chốt A (09/10/2026, theo đề xuất) đề xuất A |
| Q2 | Script chuyển app có reset DB không | A: chỉ đổi biến và restart. B: tùy chọn `--reset` | Đầu phase | Chốt B: tùy chọn `--reset` (09/10/2026) đề xuất B |
| Q3 | Số bước E2E của Lab | Phủ ba luồng chính (ticket, mượn thiết bị, đặt phòng) cùng hỏi đáp và thống kê, tối thiểu 10 bước như Farm | Đầu phase | Chốt theo đề xuất (09/10/2026) |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] `deploy/demo/README.md`, `CONTRIBUTING.md` mục chạy và kiểm.
- [ ] `.env.example` (kể cả `DXOS_APP`).
