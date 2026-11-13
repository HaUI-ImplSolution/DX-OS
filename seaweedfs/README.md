# Lưu tệp (SeaweedFS)

Kho object tương thích S3 giữ tệp đính kèm của hồ sơ; phía Backend là `core.storage` (`StorageService`).
Service `seaweedfs` khai báo trong [`deploy/docker-compose.yml`](../deploy/docker-compose.yml), dùng image `chrislusf/seaweedfs` ghim theo tag và digest.

## Cách hoạt động

- `seaweedfs` chạy `weed server` với S3 gateway; master, volume và filer nằm trong cùng tiến trình, filer không mở HTTP nên object chỉ truy cập được qua API S3 với khóa `S3_ACCESS_KEY` / `S3_SECRET_KEY`.
- gRPC của filer (quản lý user, policy và STS của S3) chỉ nhận lời gọi ký bằng `S3_SECRET_KEY` (`WEED_JWT_FILER_SIGNING_KEY`), nên container khác trong mạng compose không tự tạo được user S3.
- Log của `seaweedfs` chỉ gồm cảnh báo và lỗi (`-v=-1` trong compose); đổi thành `-v=0` để xem log thông tin.
- Bucket `S3_BUCKET` (mặc định `dxos-uploads`) riêng tư, Backend tự tạo lúc khởi động.
- Khóa object là `<uuid>.<đuôi tệp>`, metadata `uploaded-by` (`sub` của người tải lên) và `source`.
- Backend quét mã độc tệp bằng ClamAV (`STORAGE_SCAN_HOST`, xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md)) trước khi xử lý; tệp nhiễm bị từ chối và không chạm kho.
- Backend kiểm tên và loại tệp (`engine.files.allowed-extensions`) rồi mới lưu; client không ghi thẳng vào kho.
- Client nhận link tải có hạn (mặc định 15 phút, `storage.presign-ttl`), ký theo địa chỉ client dùng gọi API nên tải được qua mọi địa chỉ của gateway.
- Cổng S3 không publish ra host: link đi qua gateway (route `/<bucket>/*`, chỉ `GET` và `HEAD`, giữ nguyên đường dẫn vì chữ ký phủ cả đường dẫn).

## Biến môi trường (`deploy/.env`)

| Biến | Ý nghĩa |
|---|---|
| `S3_ENDPOINT` | Địa chỉ nội bộ, mặc định `http://seaweedfs:8333` |
| `S3_ACCESS_KEY`, `S3_SECRET_KEY` | Khóa S3 của Backend; secret tối thiểu 16 ký tự vì cũng là khóa ký gRPC của filer |
| `S3_BUCKET` | Tên bucket; route tải tệp ở `deploy/apisix/common.yaml` trỏ `/dxos-uploads/*`, đổi tên thì sửa route theo |
| `STORAGE_PRESIGN_TTL` | Thời hạn của link tải, mặc định `15m` |
| `GATEWAY_PUBLIC_URL` | Địa chỉ gateway mà trình duyệt truy cập, dùng ký link khi không có request |

Dữ liệu ở `deploy/data/seaweedfs` (bind mount), mất khi chạy `deploy/reset.sh`.
`deploy/backup.sh` sao lưu thư mục này cùng cơ sở dữ liệu, xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md).

## Giới hạn

- Backend dùng chung một khóa S3, chưa có tài khoản dịch vụ quyền hẹp.
- Tệp dữ liệu được sao lưu khi `seaweedfs` đang chạy; cần bản chắc chắn nhất quán thì dừng `seaweedfs` trong lúc chạy `backup.sh`.
