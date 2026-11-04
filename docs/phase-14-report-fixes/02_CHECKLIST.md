# Checklist: Phase 14

Tick sau khi người dùng xem kết quả.
Mục kiểm bằng tay ghi kết quả ở `docs/TESTING.md`, mục "Kiểm ngày 04/11 và 05/11/2026".

## WP14-01: Khe hở khi bắt đầu áp dụng (acc1)

- [x] WP14-01-BE-01 Khóa đọc/ghi theo mô hình ở `ModelRegistry`; `beginApply` lấy khóa ghi
- [x] WP14-01-BE-02 Yêu cầu của `data/`, hành động quy trình, tệp đính kèm giữ khóa đọc tới khi xong
- [x] WP14-01-BE-03 Kiểm lại hồ sơ mở và luật quy trình sau `beginApply`
- [x] WP14-01-TEST-01 Kiểm tay: tạo hồ sơ liên tục trong lúc áp dụng một thay đổi bảng

## WP14-02: Hủy bỏ xóa client và vai (acc1, acc2)

- [x] WP14-02-BE-01 `KeycloakAdminClient.deleteClient` (acc2)
- [x] WP14-02-BE-02 Bước `DROP_CLIENT` trong `removeUnfinished` (acc1)
- [x] WP14-02-TEST-01 Kiểm tay: client `dxos-m-<mô hình>` không còn sau Hủy bỏ

## WP14-03: Sao lưu nhất quán (acc2)

- [x] WP14-03-INFRA-01 `backup.sh` cảnh báo khi lượt chạy lâu hơn `DXOS_FILE_DELETE_DELAY`
- [x] WP14-03-INFRA-02 `verify` khởi động SeaweedFS tạm và kiểm mọi khóa của `engine.attachment`
- [ ] WP14-03-INFRA-03 Job `backup-drill` có tệp đính kèm thật (chạy trên GitHub Actions sau khi đẩy)

## WP14-04: E2E trong CI (acc2)

- [ ] WP14-04-INFRA-01 Job E2E trong `ci-infra.yml`
- [ ] WP14-04-INFRA-02 Tải log khi lỗi

## WP14-05: CLI `plan` và `apply` (acc1, acc2)

- [x] WP14-05-INFRA-01 Client `dxos-cli` trong realm, bí mật trong `make_env.sh` và `.env.example` (acc1)
- [x] WP14-05-INFRA-02 `dxos plan`, `dxos apply` (acc2)
- [x] WP14-05-TEST-01 Kiểm tay `dxos plan` và `dxos apply`

## WP14-06: Outbox có giới hạn (acc2, acc1)

- [x] WP14-06-BE-01 `ENGINE_OUTBOX_MAX_ATTEMPTS`, trạng thái `stopped`
- [x] WP14-06-BE-02 `schema_version` trong payload
- [x] WP14-06-FE-01 Nhãn "Đã dừng" (acc1)
- [x] WP14-06-TEST-01 `check_events.py`

## WP14-07: ClamAV không trả lời (acc2)

- [x] WP14-07-TEST-01 `check_demo.py` dừng `clamav` khi bật quét

## WP14-08: Tự phục hồi (acc2)

- [x] WP14-08-INFRA-01 `restart: unless-stopped` cho mọi service chạy lâu
- [x] WP14-08-INFRA-02 `dxos reconcile`, `install`, `uninstall`
- [ ] WP14-08-TEST-01 Xóa container (đã kiểm); khởi động lại Docker (cần `sudo systemctl restart docker`, chưa kiểm)

## WP14-09: Xóa tệp qua outbox (acc2)

- [x] WP14-09-BE-01 Chủ đề `file.delete`, handler, `DXOS_FILE_DELETE_DELAY`
- [x] WP14-09-TEST-01 Kho ngừng lúc xóa rồi chạy lại

## WP14-10: Lịch sử chống sửa (acc2)

- [x] WP14-10-DATA-01 Migration `V7`
- [x] WP14-10-BE-01 Chuỗi hash trong `EventLog`, tính bù lúc khởi động
- [x] WP14-10-BE-02 Điểm neo và `hash` trong payload
- [x] WP14-10-BE-03 `GET /api/v1/history/verify`, `dxos history verify`
- [x] WP14-10-TEST-01 Kiểm tay: sửa, xóa, chèn dòng bằng SQL, cắt chuỗi sau điểm neo

## WP14-11: Thống kê (acc2, acc1)

- [x] WP14-11-BE-01 `GET /api/v1/data/{model}/{entity}/stats` (acc2)
- [x] WP14-11-FE-01 Trang thống kê (acc1)
- [x] WP14-11-TEST-01 Kiểm tay: số khớp danh sách theo quyền

## WP14-12: IT/OT (acc1)

- [x] WP14-12-DATA-01 Mẫu `work_item` có thiết bị và mã quan sát
- [x] WP14-12-INFRA-01 Simulator ở `examples/itot-simulator`
- [x] WP14-12-TEST-01 Kiểm tay: gửi lại cùng quan sát không tạo trùng

## WP14-13: Hồ dữ liệu (acc2)

- [x] WP14-13-INFRA-01 `dxos archive <mô hình>`
- [x] WP14-13-TEST-01 Kiểm tay: đọc lại khớp manifest

## Đóng phase

- [ ] Chuỗi script đạt trên stack rỗng và trong CI
- [x] CHANGELOG, TESTING, OPERATIONS, `.env.example`
- [x] Xóa phần đã sửa khỏi BACKLOG và `docs/Bao_cao_ky_thuat.md`
- [ ] Gộp việc còn lại vào BACKLOG, xóa thư mục phase
