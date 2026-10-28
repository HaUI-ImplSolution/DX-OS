# Lưu ảnh (MinIO)

Kho object tương thích S3 giữ ảnh báo cáo và tệp đính kèm; phía Backend là `core.storage` (`StorageService`).
Service `minio` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml).

| File | Vai trò |
|---|---|
| `Dockerfile` | Build MinIO từ source tag `RELEASE.2025-10-15T17-29-55Z` (Go build, rồi image Alpine nhỏ) |
| `migrate-uploads.sh` | Chuyển ảnh cũ trong `deploy/data/uploads` vào bucket, giữ tên file làm khóa object |

## Build từ source

Image chính thức của MinIO không còn phát hành và repo upstream đã archived, nên `Dockerfile` build đúng tag chính thức của upstream thay vì dùng image đóng gói lại của bên thứ ba.
AGPL-3.0, chạy nguyên bản không sửa mã; lần dựng đầu khoảng 2 phút, các lần sau dùng cache Docker.
Trên máy chủ công khai, MinIO Console (`files.<tên miền>`) nằm sau Cloudflare Access, xem [`docs/CLOUDFLARE.md`](../docs/CLOUDFLARE.md).

## Cách hoạt động

- Bucket `MINIO_BUCKET` (mặc định `dxos-uploads`) riêng tư, Backend tự tạo lúc khởi động.
- Khóa object `<uuid>.jpg` hoặc `<uuid>.png`, metadata `uploaded-by` (`sub` của người upload) và `source`.
- Khi bật ClamAV (`STORAGE_SCAN_HOST`, xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md)), Backend quét mã độc tệp trước khi xử lý; tệp nhiễm bị từ chối và không chạm kho.
- Backend làm sạch tệp qua `FileProcessor` của app (ảnh: giải mã lại, bỏ EXIF/GPS) rồi mới lưu; client không ghi thẳng vào MinIO.
- Client nhận link có hạn (`imageUrl`, mặc định 15 phút, `storage.presign-ttl`), ký theo địa chỉ client dùng gọi API nên web (`localhost`) và điện thoại (IP LAN) đều tải được.
- Cổng MinIO không publish ra host: link đi qua gateway (route `/<bucket>/*`, chỉ `GET` và `HEAD`, giữ nguyên đường dẫn vì chữ ký phủ cả đường dẫn).
  AI Service nhận link nội bộ `http://minio:9000/...` hiệu lực 10 phút.
- Console ở `http://localhost:9082`, mặc định chỉ mở trên chính máy chủ (`APISIX_MINIO_HOST_PORT=127.0.0.1:9082`; đặt `9082` để mở ra mạng), đăng nhập `MINIO_ACCESS_KEY` / `MINIO_SECRET_KEY`; không dùng SSO vì Keycloak cố ý không cố định hostname.
  Bản community chỉ còn xem bucket và object.

## Biến môi trường (`deploy/.env`)

| Biến | Ý nghĩa |
|---|---|
| `MINIO_ENDPOINT` | Địa chỉ nội bộ, mặc định `http://minio:9000` |
| `MINIO_ACCESS_KEY`, `MINIO_SECRET_KEY` | Tài khoản gốc, Backend dùng chung; secret tối thiểu 8 ký tự |
| `MINIO_BUCKET` | Tên bucket; route ảnh ở `deploy/apisix/common.yaml` trỏ `/dxos-uploads/*`, đổi tên thì sửa route theo |
| `APISIX_MINIO_HOST_PORT` | Cổng console trên gateway, mặc định `127.0.0.1:9082` |
| `GATEWAY_PUBLIC_URL` | Địa chỉ gateway mà trình duyệt truy cập, dùng ký link khi không có request (thông báo chat) |

Dữ liệu ở `deploy/data/minio` (bind mount), mất khi chạy `deploy/reset.sh`.
`deploy/backup.sh` sao lưu thư mục này cùng cơ sở dữ liệu, xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md).

## Chuyển ảnh cũ

```bash
./migrate-uploads.sh [thư-mục-ảnh]     # mặc định deploy/data/uploads; cần minio đang chạy
```

Script copy bằng AWS CLI trong container và kiểm từng file đã có trong bucket, chạy lại được, không xóa thư mục cũ.

## Giới hạn

- Backend dùng chung tài khoản gốc, chưa có tài khoản dịch vụ quyền hẹp.
- Bản MinIO đã dừng phát hành nên không có bản vá; đổi sang kho S3 khác chỉ cần đổi service và biến `MINIO_*`.
