# Plan thực hiện: Phase 9

Thứ tự làm: Spec → Swagger → Code.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WP9-01 → WP9-02 → WP9-03 → WP9-04 → WP9-05; WP9-06 sau WP9-01; WP9-07 sau WP9-03; WP9-08 cuối.

## WP9-01: Họ chiếm dụng tài nguyên theo thời gian

- Mục tiêu: một họ mẫu cho mọi miền chiếm dụng tài nguyên theo khung giờ.
- Hướng làm:
  - Họ `reservation`: yêu cầu, xác nhận, sử dụng, hoàn tất; từ chối, vắng mặt, rút lại.
  - Khối `booking:` trong `process:` nêu trường tài nguyên (tham chiếu), trường bắt đầu, trường kết thúc và sức chứa.
  - Kiểm tra khi tạo và khi sửa: kết thúc sau bắt đầu; số lượt đang chiếm trùng khung giờ chưa đạt sức chứa.
  - Khóa dòng tài nguyên trong giao dịch để hai yêu cầu đồng thời không cùng lọt qua.
  - Lượt đã vào trạng thái cuối không còn chiếm tài nguyên.

## WP9-02: API cho bảng điều khiển

- Mục tiêu: bảng điều khiển đọc được mọi thứ nó cần, không giữ trạng thái riêng.
- Hướng làm (ADMIN):
  - `GET /api/v1/models/schema`: JSON Schema của tệp mô hình.
  - `GET /api/v1/models/families`: họ mẫu với trạng thái, hành động và tham số.
  - `GET /api/v1/models/templates`: thư viện mẫu.
  - `GET /api/v1/models/{model}/versions` và `.../versions/{version}`: phiên bản đã áp dụng và nội dung.

## WP9-03: Mẫu mô hình

- Mục tiêu: mỗi họ mẫu có ít nhất một mẫu dùng được ngay.
- Hướng làm: mẫu nằm ở `backend/dx-engine/src/main/resources/templates/` kèm `index.json`; `models/invalid/` giữ các tệp sai dùng cho kiểm thử.

## WP9-04: Bảng điều khiển, khung và soạn mô hình

- Mục tiêu: người triển khai tạo và sửa mô hình mà không cần biết cú pháp.
- Hướng làm:
  - Tuyến `/console` trong ứng dụng web, nạp chậm, chỉ vai ADMIN.
  - Thư viện mẫu; trình soạn Monaco với monaco-yaml theo JSON Schema; form dựng bằng react-jsonschema-form từ cùng schema.
  - Hai chế độ cùng sửa một tệp YAML, đọc và ghi bằng thư viện `yaml` để giữ chú thích.

## WP9-05: Bảng điều khiển, kế hoạch và lịch sử

- Mục tiêu: áp dụng an toàn ngay trên giao diện.
- Hướng làm:
  - Màn hình kế hoạch: thay đổi theo mức, dòng vi phạm, câu hỏi đổi tên, số hồ sơ đang chạy, câu lệnh SQL; xác nhận theo mức.
  - Lịch sử áp dụng và quay về phiên bản trước.
  - Sơ đồ quy trình bằng React Flow từ trạng thái và hành động của họ mẫu.

## WP9-06: Chế độ xem lịch

- Mục tiêu: xem lượt đặt theo lịch.
- Hướng làm: FullCalendar bản MIT trong ứng dụng; đọc danh sách theo khoảng thời gian bằng bộ lọc sẵn có.

## WP9-07: Một cửa sinh viên và E2E

- Mục tiêu: kịch bản trình diễn.
- Hướng làm: mẫu Một cửa sinh viên có tệp minh chứng và hạn; `deploy/dev/check_demo.py` kiểm tám tiêu chí, kèm kiểm giao diện bằng Chrome.

## WP9-08: Kiểm chứng

- Mục tiêu: bằng chứng cho từng tiêu chí ở `00_OVERVIEW.md` mục 4.

## Thay đổi hợp đồng

| Thay đổi | Trước | Sau |
|---|---|---|
| Khối `process:` | `family`, `handler`, `allow_self_decision`, `deadline`, `after` | thêm `booking` cho họ `reservation` |
| Họ mẫu | `approval`, `work_item` | thêm `reservation` |
| API | `/api/v1/models` có danh sách, kế hoạch, áp dụng, quay về, lịch sử | thêm `schema`, `families`, `templates`, `versions` |
| Metadata của thực thể | hành động có `from`, `by`, `needsNote`; `process` không có `booking` | hành động có thêm `to`; `process.booking` cho họ `reservation` |

## Quyết định thiết kế

- Bảng điều khiển nằm trong ứng dụng web hiện có và nạp chậm, nên người dùng cuối không tải trình soạn.
- Mẫu mô hình đi cùng dịch vụ lõi vì ngữ cảnh build của backend chỉ gồm thư mục `backend/`.
- Mỗi hành động của họ mẫu có trạng thái đích `to`, nên sơ đồ quy trình đọc thẳng từ API.
- Thư viện của bảng điều khiển ghim `dompurify` từ 3.4.17 bằng `overrides` vì Monaco kéo theo bản có lỗi.
- Trình soạn trỏ các đường dẫn cũ của `monaco-yaml` sang tệp của Monaco 0.57 bằng `resolve.alias` trong `vite.config.ts`.
