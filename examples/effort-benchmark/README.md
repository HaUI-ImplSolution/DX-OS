# So sánh luồng ticket cơ bản (#228)

Với nhóm chức năng đang dùng, các hàm API và xử lý tương ứng của Lab có **347 dòng Java**; Core dùng **một YAML 28 dòng**.
Trong phạm vi đếm này, phần mã/khai báo riêng giảm **319 dòng, tương đương 91,93%**; không cần viết thêm controller, service hay giao diện nghiệp vụ.
Luồng có tệp đính kèm đã đạt **33 kiểm tra API và 9 bước trên giao diện**.

## Nhóm chức năng được so sánh

| Chức năng | Lab viết tay | Core hiện tại |
|---|---|---|
| Tạo/sửa phiếu | `create`, `update` ở controller/service | Khai báo các trường trong YAML, dùng API và form chung |
| Danh sách, chi tiết và quyền xem | `list`, `detail`, `findVisible` | `REQUESTER: own`, `AGENT: all` |
| Giao việc, bắt đầu | `assign`, `accept`, `start` | Giao việc chung và họ `work_item` |
| Nhập kết quả, hoàn tất | `resolve` kiểm cách xử lý và nguyên nhân | Hai trường text, `resolve.require` |
| Đánh giá, xác nhận đóng | `confirm` kiểm người nộp và điểm 1–5 | Trường `rating`, `confirm.require` và hành động của chủ phiếu |
| Lịch sử | `detailOf`, `timeline` | Lịch sử xử lý có sẵn |
| Tệp đính kèm | `addAttachment`, `removeAttachment`, `store` | Trường `file`, API và giao diện tệp có sẵn |

Nhóm này không gồm AI, trao đổi nội bộ, chờ bổ sung, giới hạn mở lại, SLA theo ưu tiên hoặc các phần nghiệp vụ khác của Lab.
Core dùng các trạng thái và hợp đồng API chung; bảng đối chiếu theo việc người dùng thực hiện, không yêu cầu giữ nguyên API cũ.

## Cách đếm và kết quả

[measure.py](./measure.py) đọc đúng commit Lab, lấy nguyên các hàm phục vụ bảng trên và các hàm kiểm quyền/trạng thái mà chúng dùng.
Đếm từ dòng khai báo hàm đến dấu đóng hàm, gồm dòng trống và chú thích bên trong; mỗi dòng chỉ tính một lần, toàn bộ YAML được đếm cùng cách.
Các hàm được đếm nguyên vẹn, gồm kiểm tra đi kèm trong bản cũ; không cộng import, annotation ngoài hàm, entity, DTO, repository, frontend cũ hoặc mã nền tảng.
Danh sách hàm cố định trong script; `--details` xuất từng hàm và khoảng dòng để kiểm lại.

| Phần được đếm | Lab | Core |
|---|---:|---:|
| Các hàm API trong `TicketController` | 62 dòng / 10 hàm | Dùng API chung |
| Các hàm xử lý trong `TicketService` | 285 dòng / 24 hàm | Dùng xử lý chung theo mô hình |
| Tổng mã/khai báo riêng trong phép so sánh | **347 dòng Java** | **28 dòng YAML** |
| Java/TypeScript nghiệp vụ phải thêm | Có mã viết tay | **0 file** |

**Mức giảm số dòng: `(347 − 28) / 347 × 100 = 91,93%`.**
Đây là tỷ lệ giảm lượng mã/khai báo trong phạm vi đã nêu, không phải tỷ lệ giảm số giờ lao động.
Core tái sử dụng mã nền tảng và giao diện sinh từ metadata; công cụ đo và bản sửa nền tảng #268 không được tính là mã riêng của ứng dụng ticket.
Theo [quy tắc đếm của dự án](../../docs/TARGETS.md), mẫu này thuộc mức 2, chỉ có khai báo mô hình.

## Kiểm chức năng và thời gian áp dụng

Kiểm cùng [support.yaml](./support.yaml) trên Core đã tích hợp [PR #268](https://github.com/HaUI-ImplSolution/DX-OS/pull/268): tạo phiếu → tải tệp → giao việc → nhập kết quả → người nộp đánh giá và đóng → xem lịch sử.
Cả **33 kiểm API và 9 bước UI đạt**, gồm chặn xem phiếu người khác, chặn xử lý sai người được giao, yêu cầu đủ kết quả/đánh giá và giữ tệp sau khi đóng.

| Thao tác trên nền tảng đã sẵn sàng | Thời gian đo |
|---|---:|
| Xem kế hoạch lần đầu | 0,675 giây |
| Áp dụng lần đầu | 0,752 giây |
| Áp dụng lại cùng YAML, 3 lần | 0,261 / 0,226 / 0,246 giây; đều `unchanged` |

Môi trường: Linux, 8 CPU logic, 7,59 GiB RAM; backend và Keycloak mỗi dịch vụ giới hạn 2 CPU, 1 GiB, có stack khác đang chạy.
Các số trên là thời gian thực thi lệnh trên máy đã có image; thời gian viết ứng dụng Lab lịch sử không được đo trong phép so sánh này.

## Nguồn và chạy lại

- Lab: `v1.1.1`, commit `90d62e5f6ace55e6c3ae8bdc9d27a674cf55f657`, hai file `TicketController.java` và `TicketService.java` trong `backend/app-lab/src/main/java/HaUI/ImplSolution/dxos/lab/`.
- Core: `bab0367f869c0c6fb13b413c4c3048375405fc79` trên `Khuong/main`, sau #268; backend/frontend dựng từ đúng source, SSO/monitor/portal dùng image có mã chạy không đổi.
- [results.json](./results.json): số đếm từng hàm, công thức, YAML, phiên bản và kết quả kiểm.

Từ gốc repo:

```bash
git fetch origin main Khuong/main --tags
python3 examples/effort-benchmark/measure.py
python3 examples/effort-benchmark/measure.py --details
```

Đầu ra `--details` phải khớp trường `measurement` trong dữ liệu nếu YAML và hai mốc nguồn không đổi.
Ref, file hoặc hàm thiếu làm công cụ thoát 1; chọn `--baseline` hoặc `--core` khác là một phép so sánh mới.
Để kiểm lại luồng, dựng stack riêng theo [TESTING](../../docs/TESTING.md#3-dựng-stack), áp dụng YAML, tạo tài khoản REQUESTER/AGENT rồi thực hiện các việc trong bảng chức năng.
