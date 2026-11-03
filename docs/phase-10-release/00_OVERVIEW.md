# Phase 10: Bản phát hành 2.0.0

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong |
| Mức độ | Nghiêm trọng |
| Ứng dụng và module chính | `ai`, `data`, `.github`, `sbom`, `backend`, `frontend`, tài liệu |
| Phụ thuộc | Phase 9 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/README.md`](../dynamic-core/README.md), mục "Cam kết nguồn mở" | Mọi thành phần của bản phát hành phải mở; các mục "Tạm gác" bắt buộc xử lý trước khi phát hành | Kho quy chế HaUI, Redis và Langfuse, mô hình AI, kiểm tự động, `COPYRIGHT.txt` và SBOM |
| Cùng tài liệu, mục "Thứ tự công việc", bước 7 | Bản phát hành, giấy phép, SBOM, tài liệu | Toàn bộ phần nằm trong repository |
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md) | Việc phải xử lý trước khi phát hành | Toàn bộ, trừ triển khai công khai |

## 2. Lý do

Kho mã được chấm điểm, và cam kết của đội là mọi thành phần của bản phát hành đều mở.
Cây mã còn các phần của bản hai ứng dụng cũ: kho văn bản của HaUI không có giấy phép mở, bộ đánh giá RAG dùng Redis và Langfuse, trọng số nhận diện bệnh lúa và dữ liệu Farm.
Lõi dynamic không dùng chúng, và phiên bản số trong `pom.xml` và `package.json` vẫn là của bản cũ.

## 3. Phạm vi

- Trong phạm vi:
  - Gỡ khỏi cây mã `data/`, `ai/weights`, `ai/evaluation`, các script đánh giá và nhận diện, mã CV và luồng chẩn đoán; bản cũ còn ở tag `v1.1.1`.
  - Cắt `ai/` còn phần RAG chung, không còn gì của Farm hay Lab và không kéo mô hình ngoài tiêu chuẩn mở.
  - Phiên bản `2.0.0` ở mọi nơi.
  - Sinh lại SBOM, cập nhật `COPYRIGHT.txt` và `NOTICE`, mở rộng `check_licenses.py` cho image, mô hình và dữ liệu theo một danh mục khai báo.
  - Cập nhật README, `README_EN.md`, `ARCHITECTURE.md`, hướng dẫn triển khai và các README của module.
  - Đường phát hành và CI khớp cây mã mới.
  - Kiểm trên một bản clone sạch.
- Ngoài phạm vi:
  - Triển khai lên VPS và cập nhật trang `DX-OS-docs`: người dùng làm sau.
  - Đẩy tag, tạo GitHub Release và gửi thư dự thi: người dùng làm.
  - Dựng lại phần AI, thông báo chat, báo cáo Superset.
  - Cập nhật `deploy/.env` của máy dev.
- Ranh giới: không thêm tính năng cho lõi; tag `v2.0.0` chỉ tạo sau khi người dùng báo đã merge.

## 4. Kết quả đầu ra mong đợi (Definition of Done)

- Cây mã không còn tệp có bản quyền không mở, trọng số hay dữ liệu của bản cũ, và không còn nhắc tới Redis hay Langfuse như thành phần của sản phẩm.
- `ai/` chạy được như một dịch vụ RAG chung, test của nó đạt, và mô hình mặc định đều đạt tiêu chuẩn mở hoặc tắt sẵn.
- Phiên bản của backend, web, SBOM và CHANGELOG đều là `2.0.0`.
- `COPYRIGHT.txt`, `NOTICE` và SBOM khớp thành phần thật; `check_licenses.py` kiểm cả image, mô hình và dữ liệu.
- `./mvnw clean verify`, `npx tsc --noEmit`, test của `ai/`, `check_licenses.py`, `check_routes.py` đạt.
- Trên một clone sạch, stack dựng được và chuỗi `check_*.py` đạt.
- Tài liệu mô tả đúng bản `2.0.0`.

## 5. Quyết định đã chốt

| Quyết định | Chọn |
|---|---|
| Phần chưa mở và của bản cũ | Gỡ khỏi cây mã |
| Dịch vụ `ai/` | Cắt còn RAG chung |
| Số phiên bản | `2.0.0` |
| VPS và trang docs | Để sau |
