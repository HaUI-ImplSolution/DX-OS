# Checklist: Phase 4

Mã dòng: `WP4-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP4-01: Mã thửa, mã vụ tự sinh và GPS bắt buộc

Kết quả đầu ra: mã sinh đúng quy chuẩn, không trùng khi đồng thời; GPS bắt buộc khi tạo thửa.

**Spec và tài liệu**

- [ ] WP4-01-DOC-01 Viết tài liệu quy chuẩn mã thửa/mã vụ (file riêng hoặc mục README).
- [ ] WP4-01-DOC-02 Cập nhật Swagger cho `POST /api/v1/parcels`, `POST .../crop-seasons`.

**Backend**

- [ ] WP4-01-BE-01 Thêm `app.htx-code` vào `application.yaml`/`.env.example`.
- [ ] WP4-01-BE-02 `ParcelService.create(...)`: sinh `code` theo quy chuẩn trong transaction, bỏ nhận `code` từ request.
- [ ] WP4-01-BE-03 `Parcel`: thêm `gpsAccuracyMeters`, `locationMissing`; đổi `latitude`/`longitude` thành bắt buộc.
- [ ] WP4-01-BE-04 `CropSeason`: thêm `code`, `seasonType`; `CropSeasonService.create(...)` sinh `code`, gợi ý `seasonType` theo tháng bắt đầu (dùng bảng mốc tạm, đánh dấu rõ "chờ xác nhận chuyên môn" trong comment).
- [ ] WP4-01-BE-05 Unique constraint `(parcel_id, year, season_type)` trên `CropSeason`.

**Data, seed, migration**

- [ ] WP4-01-DATA-01 Migration thêm cột mã/GPS; set `locationMissing = true` cho dữ liệu cũ chưa có GPS, `approvalStatus = APPROVED` cho thửa/vụ cũ.

**Test**

- [ ] WP4-01-TEST-01 2 request tạo thửa đồng thời cùng hộ → mã không trùng (T4-01).
- [ ] WP4-01-TEST-02 Tạo vụ trùng `(thửa, năm, loại vụ)` → 409 (T4-02).
- [ ] WP4-01-TEST-03 Tạo thửa thiếu GPS → 400 (T4-03).

## WP4-02: Mobile — màn hình tạo thửa, tạo vụ, lấy GPS

Kết quả đầu ra: farmer tạo được thửa/vụ hoàn chỉnh trên mobile, có GPS, hoạt động offline.

**Backend**: không có (dùng API đã đổi ở WP4-01/04).

**Mobile**

- [ ] WP4-02-MB-01 Thêm dependency `expo-location`.
- [ ] WP4-02-MB-02 Màn hình tạo thửa: nhập diện tích, nút "đang ở ruộng, lấy vị trí", hiện độ chính xác.
- [ ] WP4-02-MB-03 Màn hình tạo vụ: chọn giống từ danh mục (gọi API WP4-04), nhập ngày bắt đầu, hiện ngày thu hoạch gợi ý.
- [ ] WP4-02-MB-04 Mở rộng hàng đợi offline hiện có để nhận request tạo thửa/tạo vụ, kèm `Idempotency-Key`.

**Test**

- [ ] WP4-02-TEST-01 Tạo thửa khi mất mạng, mở mạng lại → request tự gửi, không tạo trùng khi gửi lại (T4-04).
- [ ] WP4-02-TEST-02 Lấy GPS với sai số vượt ngưỡng → cảnh báo ở client, backend vẫn là lớp chặn chính (T4-05).

## WP4-03: Duyệt thửa/vụ qua workflow dùng chung

Kết quả đầu ra: thửa/vụ mới qua đúng trạng thái chờ duyệt, tái dùng `workflow_case`/`workflow_event` của P3.

**Spec và tài liệu**

- [ ] WP4-03-DOC-01 Swagger cho `POST /api/v1/parcels/{id}/edit-requests`.

**Backend**

- [ ] WP4-03-BE-01 `Parcel`: thêm `approvalStatus`.
- [ ] WP4-03-BE-02 `ParcelService.create(...)`: gọi `WorkflowService.createCase("PARCEL_APPROVAL", ...)`.
- [ ] WP4-03-BE-03 `CropSeasonService.create(...)`: gọi `WorkflowService.createCase("CROP_SEASON_APPROVAL", ...)`; chặn nếu `parcel.approvalStatus != APPROVED`.
- [ ] WP4-03-BE-04 Chặn tạo yêu cầu mới nếu farmer đã có đủ (Q3 = 3) `WorkflowCase PENDING` cùng `caseType`.
- [ ] WP4-03-BE-05 Endpoint `POST /api/v1/parcels/{id}/edit-requests`, tạo `WorkflowCase(caseType = PARCEL_EDIT_REQUEST)`.

**Data, seed, migration**

- [ ] WP4-03-DATA-01 Migration thêm `parcels.approval_status`.

**Test**

- [ ] WP4-03-TEST-01 Tạo vụ trên thửa chưa duyệt → 403/422 (T4-06).
- [ ] WP4-03-TEST-02 Admin duyệt thửa qua `WorkflowController` → farmer tạo được vụ ngay sau (T4-07).
- [ ] WP4-03-TEST-03 Farmer tạo yêu cầu chờ duyệt thứ 4 (vượt Q3) → bị chặn (T4-08).

## WP4-04: Danh mục giống lúa và gợi ý ngày thu hoạch

Kết quả đầu ra: tạo vụ chỉ chọn được giống trong danh mục; ngày thu hoạch gợi ý đúng công thức.

**Backend**

- [ ] WP4-04-BE-01 Thêm `model/RiceVariety.java`, `repository/RiceVarietyRepository.java`, `controller/RiceVarietyController.java`.
- [ ] WP4-04-BE-02 Đổi `CropSeasonCreateRequest.riceVariety` → `riceVarietyCode`.
- [ ] WP4-04-BE-03 `CropSeasonService.create(...)`: tính `expectedHarvestDate` gợi ý từ `growthDaysMin`/`growthDaysMax`; bắt buộc `reasonForOverride` nếu farmer nhập lệch quá khoảng.

**Data, seed, migration**

- [ ] WP4-04-DATA-01 Seed 5-10 giống phổ biến ĐBSCL, ghi nguồn, trạng thái duyệt theo quy ước `data/structured`.
- [ ] WP4-04-DATA-02 Migration thêm bảng `rice_varieties`, cột `crop_seasons.rice_variety_code`, `reason_for_override`.

**Test**

- [ ] WP4-04-TEST-01 Tạo vụ với `riceVarietyCode` không tồn tại → 400 (T4-09).
- [ ] WP4-04-TEST-02 Nhập `expectedHarvestDate` lệch quá khoảng không kèm lý do → 400 (T4-10).

## WP4-05: Trang quản lý cho admin và engineer

Kết quả đầu ra: admin/engineer có trang quản lý thật với router, danh sách lọc/phân trang, chi tiết, timeline.

**Backend**

- [ ] WP4-05-BE-01 Thêm phân trang/lọc cho `GET /api/v1/parcels`, `GET .../crop-seasons` nếu `findAll` hiện tại chưa đủ.

**Frontend (web)**

- [ ] WP4-05-FE-01 Thêm `react-router-dom`, tách route `/admin/*`, `/engineer/*`.
- [ ] WP4-05-FE-02 Trang `/admin/requests`: danh sách `WorkflowCase` lọc theo trạng thái/hộ/ngày, nút duyệt/từ chối.
- [ ] WP4-05-FE-03 Trang `/engineer/queue`: danh sách việc của tôi (`assignedTo = me`).
- [ ] WP4-05-FE-04 Trang chi tiết case: thông tin hộ + ảnh (presigned URL) + kết quả AI + timeline.
- [ ] WP4-05-FE-05 Menu điều hướng theo role.

**Test**

- [ ] WP4-05-TEST-01 Admin vào `/admin/requests`, thấy đúng danh sách yêu cầu chờ duyệt kèm thông tin hộ (T4-11).
- [ ] WP4-05-TEST-02 Engineer vào `/engineer/queue`, thấy đúng việc được giao (T4-12).

## WP4-06: Định tuyến thông báo Mattermost theo loại sự kiện

Kết quả đầu ra: yêu cầu tạo thửa/vụ vào đúng webhook admin, không lẫn channel báo cáo sâu bệnh.

**Backend**

- [ ] WP4-06-BE-01 Thêm `event/ParcelCreatedEvent.java`, `event/CropSeasonCreatedEvent.java`.
- [ ] WP4-06-BE-02 `MattermostNotificationListener`: đổi sang map `eventType → webhookUrl`, fallback về `MATTERMOST_WEBHOOK_URL` nếu biến riêng không set.

**Infra, deploy, CI**

- [ ] WP4-06-INFRA-01 Thêm `MATTERMOST_WEBHOOK_URL_ADMIN`, `MATTERMOST_WEBHOOK_URL_ENGINEER` vào `.env.example` (không default).

**Test**

- [ ] WP4-06-TEST-01 Tạo yêu cầu thửa mới → thông báo xuất hiện ở webhook admin, không ở webhook báo cáo (T4-13).

## Đóng phase

- [ ] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web, mobile) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, bằng tiếng Anh.
- [ ] Swagger khớp code.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
