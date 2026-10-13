# Checklist: Phase C5

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC5-{xx}-{lớp}-{nn}`.

## WPC5-01: Vỏ web và nạp module theo manifest

Kết quả đầu ra: Farm như trước; id app lạ có thông báo lỗi.

**Frontend (web)**

- [x] WPC5-01-FE-01 Tạo `shell/` và registry ánh xạ id app sang hàm nạp module.
- [x] WPC5-01-FE-02 Gọi `GET /api/v1/app` khi khởi động, dựng menu và route theo manifest.
- [x] WPC5-01-FE-03 `auth.ts`: vai lấy từ manifest, không dùng danh sách cố định.
- [x] WPC5-01-FE-04 Trang lỗi cho id app lạ và lỗi tải manifest.

**Infra, deploy, CI**

- [x] WPC5-01-INFRA-01 `frontend/Dockerfile`: không còn tham số chọn app lúc build.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC5-01-TEST-01 `npx tsc --noEmit` đạt (TC5-01).

## WPC5-02: Trang dùng chung và tách module Farm

Kết quả đầu ra: `tsc` đạt; Farm đủ trang như trước.

**Frontend (web)**

- [x] WPC5-02-FE-01 Chuyển `ParcelPanel`, `CropSeasonPanel`, `DiseaseReportPanel`, `DiagnosisPanel`, `ReferencePanel`, `DemoFlow`, `Queue` vào `apps/farm/`.
- [x] WPC5-02-FE-02 Giữ `Households` trong `apps/farm`; tạo trang `Workspaces` dùng chung cho Lab.
- [x] WPC5-02-FE-03 `Requests`, `CaseDetail`, `Timeline`, `RequestDetails` dùng nhãn trạng thái và tóm tắt từ API, không từ loại case Farm.
- [x] WPC5-02-FE-04 `Knowledge` dùng namespace của app.
- [x] WPC5-02-FE-05 `labels.ts`, `errorMessages.ts` tách phần chung và phần Farm; trạng thái lạ hiển thị nguyên chuỗi.
- [x] WPC5-02-FE-06 Kiểm vỏ không import `apps/*`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC5-02-TEST-01 Farm đủ trang và luồng như trước (TC5-02, TC5-03).

## WPC5-03: Giao diện Lab: ticket

Kết quả đầu ra: tạo, trao đổi, xử lý, xác nhận một ticket hoàn chỉnh qua web.

**Frontend (web)**

- [x] WPC5-03-FE-01 `apps/lab/api` và kiểu dữ liệu cho ticket, bình luận, danh mục, SLA.
- [x] WPC5-03-FE-02 Form báo sự cố: gợi ý loại, ưu tiên và bài tri thức khi gõ mô tả; đính kèm ảnh.
- [x] WPC5-03-FE-03 Danh sách ticket: lọc theo trạng thái, loại, ưu tiên, quá hạn; phân trang.
- [x] WPC5-03-FE-04 Chi tiết ticket: timeline, bình luận công khai và nội bộ, đồng hồ SLA, tệp đính kèm.
- [x] WPC5-03-FE-05 Hàng chờ kỹ thuật viên: nhận, bắt đầu, chờ người dùng, xử lý (ghi chú, nguyên nhân gốc), số ticket đang xử lý của từng người.
- [x] WPC5-03-FE-06 Xác nhận kèm đánh giá sao, mở lại.
- [x] WPC5-03-FE-07 Hộp thông báo; nhãn và lỗi tiếng Việt.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC5-03-TEST-01 Vòng đời ticket qua web (TC5-04).

## WPC5-04: Giao diện Lab: thiết bị và mượn trả

Kết quả đầu ra: mượn, duyệt, trả, ghi bảo trì qua web.

**Frontend (web)**

- [x] WPC5-04-FE-01 Danh sách thiết bị: lọc theo loại, phòng, trạng thái.
- [x] WPC5-04-FE-02 Chi tiết thiết bị: lịch sử bảo trì và mượn.
- [x] WPC5-04-FE-03 Mượn trả: gửi yêu cầu, duyệt, từ chối, trả kèm tình trạng.
- [x] WPC5-04-FE-04 Bảo trì: mở và hoàn tất nhật ký sửa chữa.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC5-04-TEST-01 Mượn trả và bảo trì qua web (TC5-05).

## WPC5-05: Giao diện Lab: đặt phòng

Kết quả đầu ra: đặt phòng và duyệt qua web; lịch phản ánh đúng.

**Frontend (web)**

- [x] WPC5-05-FE-01 Lịch tuần theo phòng, chọn ô điền sẵn giờ.
- [x] WPC5-05-FE-02 Form đặt phòng và thông báo lỗi tiếng Việt theo mã lỗi (trùng giờ, vượt sức chứa, phòng bảo trì).
- [x] WPC5-05-FE-03 Duyệt, từ chối, hủy; hiển thị trạng thái trên lịch.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC5-05-TEST-01 Đặt phòng và xung đột qua web (TC5-06).

## WPC5-06: Giao diện Lab: tổng quan, thống kê, hỏi đáp

Kết quả đầu ra: kịch bản demo Lab đầu-cuối chạy qua web theo từng vai.

**Frontend (web)**

- [x] WPC5-06-FE-01 Trang chủ theo vai.
- [x] WPC5-06-FE-02 Trang thống kê: SLA, thời gian xử lý, tải kỹ thuật viên, sử dụng phòng, hài lòng.
- [x] WPC5-06-FE-03 Hỏi đáp quy chế bằng trang tri thức dùng chung.
- [x] WPC5-06-FE-04 Liên kết tới Superset cho ADMIN.
- [x] WPC5-06-FE-05 Menu Lab theo vai.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WPC5-06-TEST-01 Kịch bản đầu-cuối Lab theo từng vai (TC5-07).

## Đóng phase

- [x] `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Dọn dữ liệu và ảnh test không còn dùng.
- [x] README, CHANGELOG cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
