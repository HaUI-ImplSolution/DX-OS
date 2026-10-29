# Lưu ảnh (SeaweedFS)

Kho object tương thích S3 giữ ảnh báo cáo và tệp đính kèm; phía Backend là `core.storage` (`StorageService`).
Hai service `seaweedfs` và `seaweedfs-admin` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), dùng image `chrislusf/seaweedfs` ghim theo tag và digest.

| File | Vai trò |
|---|---|
| `migrate-uploads.sh` | Chuyển ảnh trong một thư mục (mặc định `deploy/data/uploads`) vào bucket, giữ tên file làm khóa object |

## Cách hoạt động

- `seaweedfs` chạy `weed server` với S3 gateway; master, volume và filer nằm trong cùng tiến trình, filer không mở HTTP nên object chỉ truy cập được qua API S3 với khóa `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- gRPC của filer (quản lý user, policy và STS của S3) chỉ nhận lời gọi ký bằng `S3_SECRET_KEY` (`WEED_JWT_FILER_SIGNING_KEY`), nên container khác trong mạng compose không tự tạo được user S3.
- Log của `seaweedfs` chỉ gồm cảnh báo và lỗi (`-v=-1` trong compose); đổi thành `-v=0` để xem log thông tin.
- Bucket `S3_BUCKET` (mặc định `dxos-uploads`) riêng tư, Backend tự tạo lúc khởi động.
- Khóa object `<uuid>.jpg` hoặc `<uuid>.png`, metadata `uploaded-by` (`sub` của người upload) và `source`.
- Khi bật ClamAV (`STORAGE_SCAN_HOST`, xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md)), Backend quét mã độc tệp trước khi xử lý; tệp nhiễm bị từ chối và không chạm kho.
- Backend làm sạch tệp qua `FileProcessor` của app (ảnh: giải mã lại, bỏ EXIF/GPS) rồi mới lưu; client không ghi thẳng vào kho.
- Client nhận link có hạn (`imageUrl`, mặc định 15 phút, `storage.presign-ttl`), ký theo địa chỉ client dùng gọi API nên web (`localhost`) và điện thoại (IP LAN) đều tải được.
- Cổng S3 không publish ra host: link đi qua gateway (route `/<bucket>/*`, chỉ `GET` và `HEAD`, giữ nguyên đường dẫn vì chữ ký phủ cả đường dẫn).
  AI Service nhận link nội bộ `http://seaweedfs:8333/...` hiệu lực 10 phút.
- Trang quản trị `seaweedfs-admin` ở `http://localhost:9082`, mặc định chỉ mở trên chính máy chủ (`APISIX_STORAGE_ADMIN_HOST_PORT=127.0.0.1:9082`; đặt `9082` để mở ra mạng), đăng nhập `S3_ACCESS_KEY` / `S3_SECRET_KEY`; không dùng SSO vì Keycloak cố ý không cố định hostname.
- Trên máy chủ công khai, trang quản trị (`files.<tên miền>`) nằm sau Cloudflare Access, xem [`docs/CLOUDFLARE.md`](../docs/CLOUDFLARE.md).

## Biến môi trường (`deploy/.env`)

| Biến | Ý nghĩa |
|---|---|
| `S3_ENDPOINT` | Địa chỉ nội bộ, mặc định `http://seaweedfs:8333` |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | Khóa S3 của Backend, cũng là tài khoản đăng nhập trang quản trị; secret tối thiểu 16 ký tự vì cũng là khóa ký gRPC của filer |
| `S3_BUCKET` | Tên bucket; route ảnh ở `deploy/apisix/common.yaml` trỏ `/dxos-uploads/*`, đổi tên thì sửa route theo |
| `APISIX_STORAGE_ADMIN_HOST_PORT` | Cổng trang quản trị trên gateway, mặc định `127.0.0.1:9082` |
| `PORTAL_STORAGE_URL` | Link trang quản trị trên portal khi nó có địa chỉ riêng |
| `GATEWAY_PUBLIC_URL` | Địa chỉ gateway mà trình duyệt truy cập, dùng ký link khi không có request (thông báo chat) |

Dữ liệu ở `deploy/data/seaweedfs` (bind mount), mất khi chạy `deploy/reset.sh`.
`deploy/backup.sh` sao lưu thư mục này cùng cơ sở dữ liệu, xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md).

## Chuyển ảnh có sẵn

```bash
./migrate-uploads.sh [thư-mục-ảnh]     # mặc định deploy/data/uploads; cần seaweedfs đang chạy
```

Script copy bằng curl có ký S3 trong container `curlimages/curl` (chạy được trên CPU không có x86-64-v2) và kiểm từng file đã có trong bucket, chạy lại được, không xóa thư mục cũ.
Ảnh đang nằm trong một kho S3 khác thì tải về một thư mục trước, rồi chạy script trên với thư mục đó.

## Giới hạn

- Backend dùng chung một khóa S3, chưa có tài khoản dịch vụ quyền hẹp.
- Tệp dữ liệu được sao lưu khi `seaweedfs` đang chạy; cần bản chắc chắn nhất quán thì dừng `seaweedfs` trong lúc chạy `backup.sh`.
