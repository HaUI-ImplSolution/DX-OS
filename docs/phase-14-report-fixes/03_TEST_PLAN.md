# Kế hoạch kiểm thử: Phase 14

Mọi bài chạy trên stack rỗng theo thứ tự của `docs/TESTING.md`; bài làm hỏng dịch vụ chạy trên stack thử riêng.
Không viết test đơn vị mới; kiểm bằng script ở `deploy/dev` và bằng tay.

| Mã | Nội dung | Kết quả mong đợi | Script |
|---|---|---|---|
| T14-01 | Tạo hồ sơ liên tục trong lúc áp dụng một thay đổi bảng; tạo hồ sơ ngay trước khi áp dụng đổi luật quy trình | Không có 500; yêu cầu đến sau khi giữ nhận 409; hồ sơ tạo trước bị tính và lần áp dụng bị từ chối | Tay |
| T14-02 | Hủy bỏ lần áp dụng đầu | Client `dxos-m-<mô hình>` không còn; áp dụng lại thì người từng giữ vai không có quyền | Tay |
| T14-03 | Sao lưu, xóa một tệp ngay sau đó, `verify`; xóa bớt tệp trong bản chụp | Đủ tệp; `FAILED` kèm số tệp thiếu | `ci-infra.yml` job `backup-drill` |
| T14-04 | Job E2E | Đạt trên commit của phase | CI |
| T14-05 | `dxos plan`, `dxos apply` có và không có `--accept` | Mã thoát 0 khi xong, 1 khi bị từ chối; lịch sử ghi tài khoản dịch vụ | Tay |
| T14-06 | Bên nhận webhook luôn lỗi | Dừng sau `ENGINE_OUTBOX_MAX_ATTEMPTS` lần, trạng thái `stopped`; Thử lại cho thêm một lần | `check_events.py` |
| T14-07 | Bật quét, dừng `clamav`, tải tệp | 503 `ERR_SCAN_UNAVAILABLE`, không tệp nào được lưu | `check_demo.py` |
| T14-08 | Xóa container `backend`; khởi động lại Docker | Chạy lại trong một phút; mọi service tự lên | Bằng tay trên stack thử |
| T14-09 | Dừng `seaweedfs` lúc xóa tệp rồi bật lại | Thông điệp `file.delete` được thử lại tới khi đối tượng mất | `check_events.py` |
| T14-10 | Sửa, xóa, chèn một dòng `record_event` bằng SQL; tính lại cả chuỗi | `verify` chỉ ra dòng bị đổi; chuỗi tính lại lệch điểm neo | Tay |
| T14-11 | Thống kê với vai `all`, `own`, `unit` | Số khớp số dòng của danh sách cùng bộ lọc | Tay |
| T14-12 | Simulator gửi quan sát vượt ngưỡng, gửi lại cùng quan sát | Một hồ sơ; lần hai 400 | Simulator, tay |
| T14-13 | `dxos archive` rồi đọc bằng DuckDB | Số dòng và sha256 khớp manifest | Bằng tay |

Tiêu chí đạt: mọi bài trên đạt, chuỗi script cũ vẫn đạt, `./mvnw clean verify formatter:validate` và `npx tsc --noEmit` không lỗi.
