# Lưu ảnh (MinIO)

Thư mục riêng ngang hàng `backend/`, `sso/`, `bi/`, `chat/`, `portal/` — chứa cách dựng MinIO và script chuyển ảnh cũ, không có mã nguồn ứng dụng tự viết.
MinIO là kho object tương thích S3 giữ ảnh báo cáo sâu bệnh; code phía Backend nằm ở package `core.storage` (`StorageService`).
Service `minio` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), lên cùng lệnh `docker compose up -d --build`.

## Nội dung thư mục

| File | Vai trò |
|---|---|
| `Dockerfile` | Build MinIO từ source tag `RELEASE.2025-10-15T17-29-55Z` (multi-stage: Go build, rồi image Alpine nhỏ) |
| `migrate-uploads.sh` | Chuyển ảnh cũ trong thư mục `deploy/data/uploads` vào bucket, giữ nguyên tên file làm khóa object |
| `README.md` | Tài liệu này |

## Vì sao build từ source

Image chính thức của MinIO không còn được phát hành (Docker Hub, quay.io) và repo upstream đã archived.
Thay vì dùng image đóng gói lại của bên thứ ba không rõ nguồn, `Dockerfile` build đúng tag chính thức của upstream.
License AGPL-3.0 (OSI-approved), chạy nguyên bản không sửa mã nguồn; xem [`LICENSES.md`](../LICENSES.md).
Lần dựng đầu mất khoảng 2 phút, các lần sau dùng cache của Docker.

## Cách hoạt động

- Bucket `MINIO_BUCKET` (mặc định `dxos-uploads`) riêng tư, Backend tự tạo lúc khởi động.
- Khóa object là `<uuid>.jpg` hoặc `<uuid>.png`; metadata đi kèm gồm `uploaded-by` (claim `sub` của người upload) và `source`.
- Backend làm sạch ảnh (giải mã lại, bỏ EXIF/GPS) rồi mới lưu; client không bao giờ ghi thẳng vào MinIO.
- Client nhận link có hạn (`imageUrl`, mặc định 15 phút, cấu hình ở `storage.presign-ttl` trong `application.yaml`) trong response báo cáo.
  Link ký theo địa chỉ mà client dùng để gọi API, nên web (`localhost`) và điện thoại (IP LAN) đều tải được.
- Cổng của MinIO không publish ra host.
  Link công khai đi qua gateway APISIX (route `/<bucket>/*`, chỉ `GET` và `HEAD`, đường dẫn giữ nguyên vì chữ ký phủ cả đường dẫn).
  AI Service nhận link nội bộ `http://minio:9000/...` hiệu lực 10 phút.

## Giao diện quản trị (Console)

- Mở `http://localhost:9082` (`APISIX_MINIO_HOST_PORT`), cũng có link trong portal.
  Đây là listener thứ ba của gateway APISIX, chuyển tới cổng console `9001` của MinIO; console không publish cổng ra host.
- Đăng nhập bằng tài khoản gốc `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`.
  Console không dùng SSO Keycloak: MinIO cần đọc cấu hình OIDC qua một địa chỉ Keycloak cố định, trong khi Keycloak của repo chủ ý không cố định hostname để web (`localhost`) và điện thoại (IP LAN) đăng nhập cùng lúc.
- Bản community của MinIO chỉ còn phần xem bucket và object; các trang quản lý người dùng, policy không có trong bản này.
- Engineer và admin của ứng dụng xem ảnh báo cáo qua app chính; không cần vào console.

## Biến môi trường (`deploy/.env`)

| Biến | Ý nghĩa |
|---|---|
| `MINIO_ENDPOINT` | Địa chỉ nội bộ của MinIO, mặc định `http://minio:9000` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Tài khoản gốc của MinIO, Backend dùng chung; bắt buộc, secret tối thiểu 8 ký tự |
| `MINIO_BUCKET` | Tên bucket; route ảnh ở `deploy/apisix/common.yaml` đang trỏ `/dxos-uploads/*`, đổi tên bucket thì sửa route theo |
| `APISIX_MINIO_HOST_PORT` | Cổng của listener console trên gateway, mặc định `9082` |
| `GATEWAY_PUBLIC_URL` | Địa chỉ gateway mà trình duyệt truy cập, dùng ký link khi không có request (thông báo chat) |

Dữ liệu lưu ở `deploy/data/minio` (bind mount), mất khi chạy `deploy/reset.sh`.

## Chuyển ảnh cũ vào bucket

```bash
./migrate-uploads.sh [thư-mục-ảnh]     # mặc định: deploy/data/uploads
```

Cần stack đang chạy (`docker compose up -d minio`).
Script copy bằng AWS CLI chạy trong container, rồi kiểm từng file đã có trong bucket; có thể chạy lại nhiều lần.
Script không xoá thư mục cũ, bạn tự xoá sau khi kiểm xong và ảnh mở được trong app.

## Giới hạn hiện tại

- Backend dùng chung tài khoản gốc của MinIO, chưa có tài khoản dịch vụ quyền hẹp.
- Bản MinIO đã dừng phát hành nên không có bản vá lỗi; muốn đổi sang kho S3 khác chỉ cần đổi service và biến `MINIO_*`, code Backend dùng S3 API.
