# Phase C5: Web, vỏ chọn app và giao diện Lab

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | Frontend (web), dùng chung |
| Ước lượng | 4-5 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C2 (`GET /api/v1/app`), C4 (API Lab), C3 (client `dxos-web`) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề thi HaUI mục 9 | Giao diện rõ ràng, tiếng Việt, dashboard thể hiện trạng thái | Nghiêm trọng | Web |
| Đề thi HaUI mục 3, 4.3 | Giao diện thuộc tầng 2 và 3, tách khỏi core | Nghiêm trọng | Web |
| RD2, RD5 | Chọn app bằng `DXOS_APP`; Lab web trước | Nghiêm trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: web chỉ có giao diện Farm; `App.tsx`, `auth.ts`, `labels.ts` và menu viết cứng theo vai và trang của Farm.
- Vì sao làm ở phase này: đổi `DXOS_APP` mà web không đổi theo thì không "chuyển app" được.

## 3. Tổng quan sửa đổi

Web đọc `GET /api/v1/app` lúc khởi động và nạp module giao diện tương ứng (một image web dùng cho mọi app, không build lại khi đổi app).
Chia thành vỏ dùng chung (đăng nhập, bố cục, menu từ manifest, hộp duyệt, chi tiết case, workspace, tri thức, thông báo) và module giao diện theo app (`farm`, `lab`).
Lab có giao diện cho bốn khu vực: ticket (báo sự cố có gợi ý, trao đổi, SLA, đánh giá, hàng chờ kỹ thuật viên), thiết bị và mượn trả, đặt phòng với lịch tuần, tổng quan và thống kê; kèm hỏi đáp quy chế.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | |
| `dx-core` (cài đặt dùng chung) | Không | |
| `app-farm` | Không | |
| `app-lab` | Không (ngoại lệ: thêm trường manifest nếu thiếu) | |
| `dx-host` (chạy, chọn app) | Không | |
| Frontend (web) | Có | Vỏ, module Farm, module Lab |
| Mobile | Không | Để sau (RD5) |
| AI | Không | |
| Data / Seed / Migration | Không | |
| Infra / Deploy / CI | Có | Build arg của web, CI frontend |
| Docs / PoF | Có | `frontend/README` (nếu có), ảnh chụp giao diện |

## 4. Phạm vi

- Trong phạm vi: vỏ web, tách giao diện Farm vào module riêng không đổi hành vi, giao diện Lab.
- Ngoài phạm vi: mobile cho Lab; thay đổi API (C2, C4).
- Giả định: manifest có id, tên, vai, menu; client web là `dxos-web`.
- Ranh giới Core/App: vỏ không import module app; mỗi module app chỉ dùng thành phần của vỏ qua một cửa chung.

## 5. Ưu điểm và hạn chế

- Ưu điểm: đổi `DXOS_APP` và restart backend là web đổi theo; thành phần dùng chung tái dùng cho Lab.
- Hạn chế: bundle chứa mã của cả hai app (tải lười theo manifest); trang dùng chung phải chịu trạng thái và nhãn không biết trước.
- Trade-off lớn: nhỏ, ghi ở đây: chọn nạp lúc chạy theo manifest thay cho build riêng từng app, để chuyển app không cần build lại web.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: với `DXOS_APP=farm` web như trước; đổi `DXOS_APP=lab`, restart backend, tải lại trang: web hiện menu và trang của Lab; từng vai Lab đi hết kịch bản của mình (báo sự cố, xử lý, đặt phòng, mượn thiết bị, xem thống kê).
- Sản phẩm bàn giao: vỏ web, module Farm, module Lab, nhãn tiếng Việt.
- Tiêu chí đo được:
  - `npx tsc --noEmit` đạt.
  - Vỏ không import từ thư mục module app.
  - Cùng một image web chạy cả hai app.
  - Menu và vai hiển thị lấy từ manifest, không viết cứng.
- Bằng chứng: ảnh chụp hai chế độ, kết quả `tsc`.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: manifest (C2), API Lab (C4), client `dxos-web` (C3).
- Phase sau cần từ phase này: C6 (demo hai app).

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: tính thân thiện và giá trị sử dụng (10 điểm); phong cách trình diễn (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 3 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Cách nạp module app | A: động theo manifest (tải lười). B: build riêng mỗi app bằng build arg | Đầu phase | Chốt A (09/10/2026, theo đề xuất) đề xuất A |
| Q2 | Thư viện giao diện | Giữ bộ style hiện có hay thêm thư viện | Đầu phase | Chốt giữ bộ style hiện có (09/10/2026) đề xuất giữ |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Hướng dẫn thêm module giao diện cho app mới (nháp cho C7).
