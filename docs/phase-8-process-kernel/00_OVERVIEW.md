# Phase 8: Lõi quy trình chung

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong |
| Mức độ | Nghiêm trọng |
| Ứng dụng và module chính | `dx-model`, `dx-engine`, `frontend`, `models`, `deploy/dev` |
| Phụ thuộc | Phase 7 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục "Quy trình chung" | Họ mẫu, khối dùng chung, bảy quy tắc bất biến, ba tầng quy tắc, quy trình trong kế hoạch áp dụng | Họ chuỗi duyệt và họ công việc cần xử lý; quy tắc bất biến; quy trình trong kế hoạch |
| Cùng tài liệu, mục "Thế nào là đạt" | Mục 6 (mô hình thuộc họ mẫu khác chạy không sửa mã lõi) | Toàn bộ |
| Cùng tài liệu, mục "Phạm vi bản dự thi" | Tệp đính kèm là dịch vụ bắt buộc; trục sự kiện và hành động hoàn tất | Toàn bộ |
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md), bảng giới hạn đã biết | Hành động hoàn tất sau khi duyệt; đổi mẫu cho hồ sơ đang chạy | Toàn bộ |

## 2. Lý do

Hướng đi là một quy trình chung phục vụ nhiều miền, nên lớp quy trình được dựng cùng lúc với họ mẫu thứ hai để họ mẫu thứ hai kiểm chứng phần trừu tượng.
Mọi việc còn lại của phase đều dựa trên lớp này.
Định dạng mô hình còn ở bản `format: 0`, nên khối `process:` đổi được trước khi phát hành.

## 3. Phạm vi

- Trong phạm vi:
  - Họ mẫu có tham số, thay cho mẫu viết cứng; khối `process:` dùng `family`, `handler`, `deadline`.
  - Lớp quy tắc bất biến trong engine: lý do khi từ chối và trả lại, rút lại thay cho xóa, người làm có vai và thấy được bản ghi.
  - Họ công việc cần xử lý có hạn.
  - Quy trình trong kế hoạch áp dụng: khác biệt tham số, họ mẫu đổi, số bản ghi đang chạy bị ảnh hưởng.
  - Trục sự kiện và hành động hoàn tất sau khi giao dịch xong.
  - Mô hình cấp phát dịch vụ IT chạy trên chuỗi duyệt.
  - Tệp đính kèm.
- Ngoài phạm vi:
  - Đổi họ mẫu của một thực thể đã có hồ sơ; việc này ở `docs/dynamic-core/BACKLOG.md`.
  - Họ chiếm dụng tài nguyên theo thời gian và chế độ xem lịch.
  - Bảng điều khiển và họ mẫu tùy biến.
  - Họ kiểm tra định kỳ, họ đánh giá và chọn lọc.
  - Tác tử AI.
- Ranh giới: mã lõi không chứa tên thực thể, vai hay luồng của một lĩnh vực; quy tắc bất biến nằm trong Java, không nằm trong tệp BPMN.

## 4. Kết quả đầu ra mong đợi (Definition of Done)

- Hai mô hình thuộc hai họ mẫu khác nhau chạy trên cùng lớp quy tắc bất biến mà không sửa mã lõi.
- Từ chối, trả lại và rút lại đều để lại một dòng lịch sử có lý do; bản ghi có quy trình không bị xóa.
- Họ công việc cần xử lý đánh dấu hồ sơ quá hạn, và hộp việc đưa hồ sơ quá hạn lên đầu.
- Kế hoạch áp dụng báo khác biệt của quy trình và số bản ghi đang chạy bị ảnh hưởng.
- Hành động hoàn tất chỉ chạy sau khi giao dịch xong, thử lại được và không chạy lặp.
- Mô hình cấp phát dịch vụ IT cấp quyền Keycloak thật sau khi được duyệt.
- Tệp tải lên được gắn vào hồ sơ và đi qua lớp lưu trữ có quét ClamAV của `dx-core`, nơi tệp nhiễm mã độc bị từ chối và không được lưu.
