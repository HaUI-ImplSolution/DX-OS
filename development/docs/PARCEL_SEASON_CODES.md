# Mã thửa, mã vụ và quy tắc duyệt

## Mã thửa

`<MÃ HTX>-<MÃ HỘ>-T<STT>`, ví dụ `HTX01-H0042-T03`.

- Mã HTX lấy từ `HTX_CODE` (`app.htx-code`), mặc định `HTX01`.
- Mã hộ là `Household.code` (`H0042`).
- STT tăng dần theo từng hộ, hai chữ số trở lên, lấy từ bảng `household_parcel_counter` trong cùng transaction với việc lưu thửa; hai request đồng thời nhận hai số khác nhau.
- STT không dùng lại sau khi xóa hay bị từ chối.
- Client không gửi mã, backend sinh.
- Thửa tạo trước khi có quy tắc này giữ mã cũ.

## Mã vụ

`<MÃ THỬA>-<NĂM><LOẠI VỤ>`, ví dụ `HTX01-H0042-T03-2026DX`.

| Loại vụ | Hậu tố | Tháng bắt đầu gợi ý |
|---|---|---|
| Đông Xuân (`DONG_XUAN`) | `DX` | 11, 12, 1 |
| Hè Thu (`HE_THU`) | `HT` | 3, 4, 5 |
| Thu Đông (`THU_DONG`) | `TD` | 6, 7, 8 |

- Năm là năm của ngày bắt đầu vụ.
- Mốc tháng nằm ở `app.season-start-months` (`SEASON_MONTHS_*`), tự biên soạn, chưa có kỹ sư nông nghiệp xác nhận.
  Tháng ngoài các mốc không có gợi ý, nông dân tự chọn loại vụ.
- Một thửa có tối đa một vụ mỗi loại mỗi năm và một vụ `ACTIVE` tại một thời điểm; vụ bị từ chối không giữ chỗ.
- Vụ tạo trước khi có quy tắc này không có mã và loại vụ.

## GPS

- Tạo thửa bắt buộc có `latitude`, `longitude`; nông dân gửi thêm `gpsAccuracyMeters` lấy từ điện thoại.
- Sai số lớn hơn `GPS_MAX_ACCURACY_METERS` (30 m) hoặc `mockedLocation=true` bị từ chối (`422`, `GPS_ACCURACY_TOO_LOW`, `GPS_ACCURACY_MISSING`, `GPS_MOCKED`).
- Quản trị viên nhập hộ không cần sai số.
- Thửa cũ chưa có tọa độ mang cờ `locationMissing`.

## Duyệt

- Thửa và vụ do nông dân tạo ở trạng thái `PENDING` cho tới khi quản trị viên duyệt qua `PATCH /api/v1/workflow-cases/{id}/approve` hoặc `/reject` (kèm lý do).
  Thửa và vụ do quản trị viên tạo được duyệt ngay.
- Chưa duyệt thì chưa tạo được vụ trên thửa (`422 PARCEL_NOT_APPROVED`), chưa gửi báo cáo hay thu hoạch trên vụ (`422 CROP_SEASON_NOT_APPROVED`).
- Bị từ chối thì nông dân sửa và gửi lại (`POST /parcels/{id}/resubmit`, `POST /crop-seasons/{id}/resubmit`); lịch sử nằm ở timeline của case.
- Sửa thửa đã duyệt là một yêu cầu riêng (`POST /parcels/{id}/edit-requests`), thửa giữ giá trị cũ tới khi được duyệt.
- Mỗi nông dân có tối đa `MAX_PENDING_REQUESTS` (3) yêu cầu chờ duyệt cùng lúc (`422 TOO_MANY_PENDING_REQUESTS`).
