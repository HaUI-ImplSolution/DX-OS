# Phase 5: Vòng hẹp của lõi dynamic

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | Toàn bộ backend, frontend, deploy |
| Ước lượng | 14 ngày, chốt lại khi phân công |
| Người phụ trách | Chưa phân công |
| Đóng phase | 02/11/2026 |
| Phụ thuộc | Không |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục "Thứ tự công việc" | Bước 1: vòng hẹp một tệp mô hình qua API chung, PostgreSQL, Keycloak, Flowable | Nghiêm trọng | Toàn bộ |
| Cùng tài liệu, mục "Thế nào là đạt" | Mục 1 (một lệnh, có hệ thống đăng nhập được), mục 2 (sinh viên chỉ thấy hồ sơ của mình), mục 4 (gọi thẳng API đổi trạng thái thì bị chặn) | Nghiêm trọng | Toàn bộ ba mục |
| Cùng tài liệu, mục "Chưa xác minh" | Trạng thái không lệch, hiệu năng API chung, quyền theo dòng qua quan hệ, react-admin dựng từ metadata | Quan trọng | Kiểm chứng và ghi kết quả |
| Đề thi, yêu cầu lõi | Định danh và SSO, cổng API, dữ liệu có cấu trúc, động cơ luồng công việc | Nghiêm trọng | Bốn dịch vụ đều chạy trong vòng hẹp |

## 2. Lý do

- Mọi phase sau đều dựng trên một đường đi thông suốt từ tệp mô hình đến API có quyền và quy trình; chưa có đường này thì chưa có lõi.
- Các giả định rủi ro nhất của thiết kế nằm ở đường này, nên kiểm chứng ngay khi dựng sẽ rẻ hơn nhiều so với phát hiện muộn.
- Hệ thống được xây lại từ lõi lên; mã cũ nằm ở tag `v1.1.1` nên gỡ ngay được, không cần giữ hai nhánh mã cùng lúc.

## 3. Tổng quan sửa đổi

Phase dọn nền rồi dựng một lát cắt dọc hẹp nhưng đầy đủ.
Thứ tự: dọn nền, định dạng mô hình và bộ kiểm tra, áp dụng mô hình từ trạng thái rỗng, API chung, Flowable với mẫu phê duyệt một cấp, giao diện react-admin, rồi chạy trên stack và ghi kết quả kiểm chứng.
Giao diện và phần backend làm song song sau khi chốt hợp đồng metadata.

Backend sau phase gồm bốn module: `dx-model` (định dạng và kiểm tra mô hình), `dx-core` (thư viện dùng chung đã rút gọn), `dx-engine` (dịch vụ chạy được) và `dx-monitor`.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-model` (mới) | Có | Định dạng mô hình, JSON Schema, bộ kiểm tra |
| `dx-engine` (mới) | Có | Phần điều khiển, API chung, Flowable nhúng; là dịch vụ chạy được của backend |
| `dx-core` | Có | Rút còn bảo mật, audit, lưu trữ, trạng thái hệ thống, client Keycloak |
| `dx-core-api`, `dx-host`, `app-farm`, `app-lab` | Có | Gỡ |
| Frontend (web) | Có | Gỡ ứng dụng Farm và Lab, thêm ứng dụng react-admin dựng từ metadata |
| Mobile | Có | Gỡ |
| Data / Seed / Migration | Có | Migration của schema `engine`, mô hình mẫu |
| Infra / Deploy / CI | Có | Bỏ `DXOS_APP` và job của phần đã gỡ; compose chạy dịch vụ lõi, route APISIX, vai cơ sở dữ liệu |
| Docs | Có | `docs/dynamic-core/README.md`, `ARCHITECTURE.md`, các README |

## 4. Phạm vi

- Trong phạm vi:
  - Dọn nền: gỡ `app-farm`, `app-lab`, `dx-core-api`, `dx-host`, mobile, ứng dụng Farm và Lab ở frontend, `DXOS_APP`; rút `dx-core`; sửa CI cho khớp.
  - Định dạng mô hình phiên bản 0 và bộ kiểm tra có báo lỗi đúng dòng.
  - Áp dụng mô hình từ trạng thái rỗng: sinh schema, bảng, ràng buộc, vai Keycloak, ghi phiên bản và lịch sử.
  - API chung cho đọc, tạo, sửa, xóa mọi thực thể, kèm API metadata.
  - Quyền theo vai và theo dòng (`all` và `own`), quy tắc bắt buộc và duy nhất.
  - Flowable nhúng với mẫu phê duyệt một cấp; chặn ghi trực tiếp cột trạng thái.
  - Giao diện react-admin: danh sách, form, chi tiết, nút quyết định.
  - Chạy được trên máy dev bằng compose.
- Ngoài phạm vi:
  - Đổi mô hình khi đã có dữ liệu, so sánh phiên bản, đổi tên, báo dữ liệu vi phạm: Phase 6.
  - Tệp đính kèm, quét mã độc, mẫu xử lý có hạn, trục sự kiện: Phase 7.
  - Màn hình xem kế hoạch và lịch sử áp dụng, ứng dụng nghiệp vụ hoàn chỉnh, mô hình thứ hai đầy đủ: các phase sau.
  - Dọn `ai/` (mã nhận diện ảnh, trọng số) và `data/` (dữ liệu Farm, Lab): làm ở phase AI.
  - Dựng lại `bi/` (Superset, bộ dashboard theo mô hình) và `chat/` (Mattermost, thông báo qua trục sự kiện): làm ở phase của chúng; hai dịch vụ này, cùng AI, tạm ra khỏi compose.
  - CLI `dxos`, cắt bớt service bằng compose profile, AI, thông báo chat.
- Giả định:
  - Mô hình mẫu có một thực thể chính có phê duyệt, hai vai, một thực thể con tham chiếu về thực thể chính.
  - Một mô hình hiện diện trong một lần áp dụng; dữ liệu thử có thể xóa và dựng lại.
  - Bản công khai trên VPS vẫn chạy `v1.1.1` cho đến khi lõi mới đạt và được triển khai lại.
- Ranh giới:
  - `dx-model` không phụ thuộc module nào khác trong backend.
  - `dx-engine` phụ thuộc `dx-model` và `dx-core`.
  - `dx-core` là thư viện, không có khái niệm ứng dụng hay nghiệp vụ.
  - Mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực.

## 5. Ưu điểm và hạn chế

- Ưu điểm:
  - Kiểm chứng sớm các giả định rủi ro nhất.
  - Chỉ còn một nhánh mã, cấu trúc đổi tự do theo nhu cầu của lõi.
  - Có sẵn một lát cắt để demo và để các phase sau mở rộng.
- Hạn chế:
  - Nhánh làm việc không có bản chạy được cho đến khi xong phần dọn nền và WP5-06; đường lùi là tag `v1.1.1`.
  - Mô hình mẫu hẹp nên chưa chứng minh được độ rộng của định dạng.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được:
  - Trên stack sạch, nạp tệp mô hình mẫu bằng một lệnh gọi API; hệ thống có schema, vai Keycloak và API.
  - Người dùng vai `REQUESTER` đăng nhập, tạo hồ sơ và chỉ thấy hồ sơ của mình.
  - Người dùng vai `APPROVER` thấy mọi hồ sơ và duyệt hoặc từ chối.
  - Gọi thẳng API để sửa trạng thái thì bị chặn.
  - Nạp thêm một mô hình nhỏ thứ hai: giao diện hiện thêm màn hình mới, không build lại.
- Sản phẩm bàn giao: hai module mới, ứng dụng giao diện, mô hình mẫu, compose chạy dịch vụ lõi, hướng dẫn kiểm thử thủ công, kết quả kiểm chứng ghi vào `docs/dynamic-core/README.md`.
- Tiêu chí đo được:
  - Tài khoản `REQUESTER` gọi `GET` danh sách chỉ nhận hồ sơ của mình; gọi `POST` quyết định bị 403.
  - `PATCH` cột trạng thái bằng bất kỳ vai nào bị 403.
  - Lỗi xảy ra sau khi đổi trạng thái làm cả bản ghi và quy trình quay về như cũ.
  - Tên trường chứa ký tự ngoài `[a-z][a-z0-9_]*` bị bộ kiểm tra từ chối; tham số lọc chứa SQL không có tác dụng.
  - Danh sách 10.000 dòng có bộ lọc trả về dưới 500 ms ở phân vị 95; số đo được ghi lại.
  - Vai kết nối báo cáo không ghi được vào bảng nghiệp vụ.
  - Mỗi quyết định lưu người quyết định và thời điểm; client không gửi được hai giá trị này.
  - Lần áp dụng dở chỉ hoàn tất được bằng đúng tệp đã bắt đầu; mỗi mô hình chỉ có một lần áp dụng chạy tại một thời điểm.
  - Sáu kiểu trường (văn bản, số, ngày giờ, lựa chọn, tham chiếu, người dùng) dựng được form và danh sách từ metadata.
- Không còn tham chiếu tới `DXOS_APP`, `app-farm`, `app-lab` trong mã, cấu hình và CI, ngoài `ai/`, `data/`, `bi/`, `chat/`; `./mvnw compile`, `npx tsc --noEmit` và CI đạt; stack lên đủ dịch vụ healthy.
- Bằng chứng: kết quả từng case ở `03_TEST_PLAN.md`, số đo hiệu năng và mục 8 của [`docs/TESTING.md`](../TESTING.md).

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: không.
- Phase sau cần từ phase này:
  - Phase 6 cần phiên bản mô hình đã lưu, lịch sử áp dụng, định dạng mô hình có số phiên bản.
  - Phase 7 cần cột trạng thái và Flowable chạy ổn để thêm mẫu xử lý có hạn và trục sự kiện.
  - Các phase giao diện cần hợp đồng metadata đã chốt.

## 8. Ảnh hưởng đến thi

- Điểm rubric liên quan: tính nguyên gốc của giải pháp (10), mức độ hoàn thiện (10), kho mã và bản phát hành (50, phần mã chạy được từ nguồn).
- Dùng làm bằng chứng cho: mục 1, 2 và 4 của kịch bản demo 15 phút, và phần kiến trúc trong báo cáo.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Cấu trúc `dx-core` | A: rút gọn `dx-core` thành thư viện dùng lại; B: viết lại từng phần cần thiết | 02/11/2026 | A: xây lại tự do, giữ phần dùng lại được, bỏ phần gắn với app |
| Q2 | Vị trí hai module mới | A: trong reactor `backend/`; B: thư mục riêng ngoài `backend/` | 02/11/2026 | A |
| Q3 | Ứng dụng giao diện nằm ở đâu | A: ứng dụng duy nhất trong shell hiện có; B: gói riêng | 02/11/2026 | A |
| Q4 | Cách chạy trên máy dev | A: compose riêng dùng chung hạ tầng; B: sửa compose chính | 02/11/2026 | B: không còn bản cũ cần giữ |
| Q5 | Mô hình mẫu cho vòng hẹp | A: mượn thiết bị (có phê duyệt); B: yêu cầu xin giấy xác nhận | 02/11/2026 | A |
| Q6 | Cú pháp trường ở phiên bản 0 | A: dạng khai báo đầy đủ (`type`, `options`, `required`); B: dạng gọn | 02/11/2026 | A, dạng gọn thêm sau như cú pháp rút gọn |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Swagger (springdoc) cho các endpoint của `dx-engine`.
- [x] `ARCHITECTURE.md`: thêm phần lõi dynamic và sơ đồ.
- [x] `docs/dynamic-core/README.md`: ghi kết quả kiểm chứng, bỏ các mục đã xác minh khỏi "Chưa xác minh".
- [x] `COPYRIGHT.txt`, `.env.example` nếu có thêm thành phần hoặc biến môi trường.
- [x] README gốc, `README_EN.md`, README các module và `CONTRIBUTING.md`: bỏ nội dung về Farm, Lab, `DXOS_APP`; trỏ tới tag `v1.1.1` cho bản cũ.
- [x] [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md): thêm việc phát sinh.
