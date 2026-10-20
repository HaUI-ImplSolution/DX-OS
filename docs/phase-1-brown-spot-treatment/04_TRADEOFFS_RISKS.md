# Trade-off và rủi ro: Phase 1

## 1. Trade-off

| # | Quyết định | Phương án chọn trong kế hoạch | Được | Hạn chế | Đảo ngược |
|---|---|---|---|---|---|
| TO1 | Phạm vi kích hoạt | Chỉ Help 400SC/đốm nâu sau hồ sơ duyệt | Ít tác động, tận dụng lookup hiện tại | Còn bệnh chưa có phác đồ | Đổi eligibility, sinh lại seed |
| TO2 | Nguồn tiếng Việt | Link và metadata, không viết lại corpus | Không kéo theo đổi truy xuất hoặc UI | Chẩn đoán chưa tự trích nội dung từ các link mới | Bỏ metadata mới |
| TO3 | Bốn thông số | Giữ nước/thời điểm trong notes hiện có | Không đổi API/schema/giao diện | Chưa có bốn cột riêng trên app | Tính năng hiển thị xử lý ở yêu cầu khác |

## 2. Rủi ro

| # | Rủi ro | Ảnh hưởng | Giảm thiểu | Dự phòng |
|---|---|---|---|---|
| R1 | Đồng nhất nguồn có uy tín với chuyên gia đã duyệt | Mở thuốc thiếu căn cứ | Ghi người/vai trò/ngày/tham chiếu thật; kiểm tra đăng ký hiện hành | Giữ rule bị chặn |
| R2 | URL còn trả 200 nhưng nội dung lỗi hoặc đã thay | Không truy vết được thông số | Đọc đúng phần hướng dẫn, ghi ngày đối chiếu | Giữ hồ sơ chưa duyệt hoặc tìm nguồn chính thức khác |
| R3 | Tài liệu địa phương bị dùng làm chỉ định chung | Sai đối tượng/thời điểm | Ghi phạm vi; nguồn mới không sửa rule hoặc tự sinh khuyến cáo | Không chọn phần thiếu căn cứ |
| R4 | Source registry còn IN_REVIEW | Hiểu nhầm trạng thái nguồn/rule | Giữ trạng thái thật và phân biệt duyệt nguồn với duyệt phác đồ | Không đổi hàng loạt trạng thái |
| R5 | Có phác đồ mới nhưng chẩn đoán cũ không có rule | Kỳ vọng hồi tố sai | Kiểm tra bằng chẩn đoán mới; giữ lịch sử cũ | Dùng luồng tạo chẩn đoán mới hiện có |
| R6 | Rollback làm mất lịch sử phun/cách ly | Ảnh hưởng dữ liệu đã dùng | Dùng cơ chế ngừng active và giữ record có tham chiếu | Không xóa DB/volume hoặc rút PHI snapshot |

## 3. Giới hạn chấp nhận

Không giải quyết thiếu hồ sơ duyệt của hai rule cũ, sửa policy cũ, cải tiến RAG hay hiển thị bốn cột trong đợt này.
Các vấn đề đó chỉ được ghi nhận; không tự mở rộng phạm vi.
Không lưu toàn văn trang/PDF chưa rõ quyền tái phân phối và không tạo checksum công khai mới cho tài liệu.

## 4. Điều kiện dừng và rollback

Chưa xác minh đủ đăng ký, thông số hoặc quyết định duyệt thì chưa mở eligibility.
Nếu cần thay hợp đồng, kiến trúc, thuật toán hoặc hạ tầng để đạt yêu cầu thì cập nhật kế hoạch để người dùng xem trước, không sửa kèm.
Nếu phải thu hồi rule đã mở: đổi eligibility, sinh lại SQL, nạp theo cơ chế hiện có và kiểm tra giữ lịch sử/PHI.

## 5. Tác động ngoài phase

Không có thay đổi dự kiến cho Core, DX-Lab, model, giao diện, Docker/env hoặc CI.
Kết quả chỉ bổ sung một phác đồ Farm và danh mục đường dẫn tham khảo đúng phạm vi.
