# Plan thực hiện: Phase 6

Thứ tự làm: Spec → Swagger → Code.
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WP6-00 → WP6-01 → WP6-02 → WP6-03 → WP6-04 → WP6-05 → WP6-06.
WP6-00 không chặn các WP khác, nhưng làm trước vì kết quả có thể đổi thiết kế.

## WP6-00: Thử công cụ cho Phase 7

- Mục tiêu: biết phần đổi mô hình tự làm bao nhiêu, dùng công cụ nào.
- Ước lượng: S.
- Hướng làm: thử trên PostgreSQL tạm, ngoài mã nguồn của sản phẩm.
  - `ProcessMigrationService`: chuyển hồ sơ đang chạy từ mẫu cũ sang mẫu mới, gồm kiểm tra trước.
  - pgschema và pgroll: thêm cột, đổi tên cột khi có dữ liệu, thêm `NOT NULL` khi dữ liệu vi phạm.
- Kết quả đầu ra: bảng kết luận ở [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).

## WP6-01: Mẫu quy trình khai báo trạng thái và hành động

- Mục tiêu: trạng thái và hành động do mẫu quy trình quyết định, không viết cứng ở nhiều nơi.
- Ước lượng: M.

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Sinh cột trạng thái | Ba trạng thái viết cứng | Lấy từ mẫu quy trình |
| Thực hiện quyết định | `POST .../decision` | `POST .../actions/{action}` |
| Cán bộ thiếu thông tin | Chỉ duyệt hoặc từ chối | `return` kèm ghi chú bắt buộc, trạng thái `returned` |
| Người nộp sửa xong | Không có | Sửa rồi `resubmit`, trạng thái về `submitted` |
| Sửa hồ sơ đã kết thúc | 409 | 409, theo cờ kết thúc của trạng thái |
| Giao diện | Nút và nhãn viết cứng | Đọc trạng thái và hành động từ metadata |

- Hướng làm: `ProcessTemplate` ở `dx-model` khai báo trạng thái, hành động, người được làm; `approval.bpmn20.xml` thêm nhánh trả lại.

## WP6-02: Đơn vị và quyền theo dòng

- Mục tiêu: cán bộ chỉ thấy và xử lý hồ sơ của đơn vị mình hoặc được giao.
- Ước lượng: M.

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Khai báo đơn vị | Không có | `units` trong mô hình, kiểu trường `unit`, áp dụng thì tạo nhóm Keycloak |
| Quyền `unit` | Không có | Thấy và sửa dòng có đơn vị thuộc nhóm trong token |
| Quyền `assigned` | Không có | Thấy và sửa dòng giao cho mình |
| Một người nhiều vai | Hợp `all` và `own` | Hợp mọi phạm vi bằng `OR` |
| Thực thể con | `own` qua cha | Mọi phạm vi đều xét qua cha |
| Tạo hoặc chuyển hồ sơ bằng quyền `unit` | Không có | Chỉ trong đơn vị của mình |

- Hướng làm: `UserContext.groups()` đọc claim `groups`; thêm mapper nhóm vào client web trong realm.
- Bộ kiểm tra từ chối `unit` khi thực thể không có trường đơn vị, `assigned` khi không có quy trình.

## WP6-03: Giao việc và hộp việc

- Mục tiêu: mỗi cán bộ biết việc của mình và hồ sơ không bị xử lý chồng chéo.
- Ước lượng: M.

| Tình huống | Hành vi sau thay đổi |
|---|---|
| `POST .../{id}/assign` | Vai xử lý hoặc vai điều phối (quyền ghi `all`, `unit`) giao cho một người đủ điều kiện, cho mình, hoặc bỏ giao |
| `GET .../{id}/handlers` | Danh sách người giao được: có vai xử lý, đúng đơn vị, không phải người nộp |
| Hồ sơ đã giao | Chỉ người được giao thực hiện được hành động |
| `GET /api/v1/inbox` | Việc người gọi làm được ngay, trên mọi mô hình, gồm cả hồ sơ bị trả lại của người nộp |

## WP6-04: Dòng lịch sử

- Mục tiêu: trả lời được ai làm gì, khi nào, ngay cả khi bản ghi đã bị xóa.
- Ước lượng: S.
- Hướng làm: bảng `engine.record_event` ghi trong cùng giao dịch; `GET .../{id}/events` theo quyền đọc của bản ghi.
- Chỉ lưu tên trường đã sửa, không lưu giá trị, để không nhân bản dữ liệu cá nhân.
- Bảng này là nền cho trục sự kiện ở Phase 8.

## WP6-05: Giao diện

- Ước lượng: M.
- Hộp việc là trang đầu; màn chi tiết có nút hành động, ô ghi chú, nhận và giao việc, dòng lịch sử.
- Không có tên thực thể hay hành động nào viết cứng.

## WP6-06: Mô hình mẫu và kiểm chứng

- Ước lượng: S.
- `models/onestop.yaml` cho Một cửa sinh viên; `deploy/dev/check_case.py` kiểm phần mới; `check_api.py` chuyển sang API hành động.
- Kết quả ghi ở [`03_TEST_PLAN.md`](./03_TEST_PLAN.md) và `docs/TESTING.md`.

## Thay đổi hợp đồng

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /api/v1/data/{model}/{entity}/{id}/decision` | Thay bằng `.../actions/{action}` | Có, chưa phát hành |
| Endpoint | `.../{id}/assign`, `.../{id}/handlers`, `.../{id}/events`, `/api/v1/inbox` | Mới | Không |
| Metadata | Trạng thái, hành động, đơn vị, `canHandle` | Mới, thay `canDecide` | Có, chưa phát hành |
| Tệp mô hình | `units`, kiểu `unit`, phạm vi `unit` và `assigned`; thêm tên dành riêng `assignee` | Mới, vẫn `format: 0` | Không |
| Bảng DB | Cột `assignee`; trạng thái `returned`; `engine.record_event` | Mới | Dựng lại từ rỗng |
| Realm | Mapper `groups` | Mới | Dựng lại realm hoặc thêm mapper |

## Dữ liệu và rollback

- Chưa có dữ liệu thật trên bản mới, nên dựng lại cơ sở dữ liệu dev từ rỗng.
- Rollback: quay về `Khuong/main`, chạy `deploy/reset.sh`.

## Quyết định thiết kế

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Hồ sơ là thực thể có `process`, không thêm loại mới | Giữ tệp mô hình đơn giản; thực thể khác vẫn là dữ liệu phụ trợ |
| D2 | Đơn vị khai báo trong mô hình, áp dụng thành nhóm Keycloak | Tệp mô hình là nguồn sự thật; Keycloak giữ thành viên và cấp token |
| D3 | Nhãn trạng thái và hành động nằm ở mẫu quy trình | Giao diện không biết trước quy trình nào |
| D4 | Mở lại toàn bộ trường khi hồ sơ bị trả lại; hạn tính lại khi làm mẫu có hạn | Đơn giản, đủ cho các tình huống đã biết |
| D5 | Giao việc do vai điều phối hoặc tự nhận, chưa tự động | Tự động cần luật phân công, để sau khi có nhu cầu thật; vai `assigned` cần người khác giao vì không thấy hồ sơ chưa giao |
| D6 | Không lưu giá trị trường trong dòng lịch sử | Tránh nhân bản dữ liệu cá nhân |
