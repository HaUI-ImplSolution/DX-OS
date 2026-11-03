# Trade-off và rủi ro: Phase 10

## 1. Trade-off

| # | Quyết định | Được | Mất |
|---|---|---|---|
| TO1 | Gỡ hẳn dữ liệu và trọng số của bản cũ khỏi cây mã | Kho mã hoàn toàn mở, nhẹ hơn khoảng 230 MB | Chạy lại AI của bản cũ phải checkout tag `v1.1.1` |
| TO2 | Cắt `ai/` còn RAG chung | Giữ phần RAG làm nền cho phần AI sau | Mất chẩn đoán bệnh lúa và các luật kiểm câu trả lời theo mã bệnh |
| TO3 | Số phiên bản `2.0.0` | Báo rõ đây là kiến trúc khác, không nâng cấp dữ liệu từ `1.1.x` | Người dùng của `1.1.x` phải dựng lại |

## 2. Rủi ro

| # | Rủi ro | Giảm thiểu | Dự phòng |
|---|---|---|---|
| R1 | Cắt `ai/` làm test và luồng RAG còn lại hỏng | Chạy `pytest` sau từng bước và thử nạp, tìm kiếm thật | Quay về commit trước của phase |
| R2 | Gỡ `data/` làm workflow hoặc script còn trỏ vào | Tìm mọi tham chiếu, chạy lại hai script kiểm tĩnh và đọc workflow | Khôi phục từ `v1.1.1` |
| R3 | Một thành phần còn sót giấy phép không mở | Danh mục khai báo và `check_licenses.py` chặn khi thiếu | Gỡ hoặc đánh dấu tùy chọn kèm lý do |
| R4 | Bản phát hành chạy trên clone sạch lại thiếu tệp | Kiểm trên clone sạch, không trên thư mục làm việc | Sửa rồi kiểm lại |

## 3. Điều kiện dừng

Cắt `ai/` đòi viết lại quá nửa mã RAG thì dừng và hỏi người dùng có gỡ hẳn `ai/` không.

## 4. Nợ kỹ thuật chấp nhận

- Phần AI (hỏi đáp có nguồn, tác tử) làm lại sau theo cơ chế provider.
- Triển khai VPS và trang docs chưa cập nhật.
