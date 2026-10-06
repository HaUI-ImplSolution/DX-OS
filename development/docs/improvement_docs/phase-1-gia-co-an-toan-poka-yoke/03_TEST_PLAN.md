# Kế hoạch kiểm thử: Phase 1

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit, backend) cho WP1-02/WP1-03; thủ công (compose, CI) cho WP1-01/WP1-04.
- Môi trường: clone sạch + `deploy/docker-compose.yml` + seed demo đã dọn theo WP1-02-DATA-01.
- Dữ liệu và tài khoản test: farmer1 (có 1 thửa, 1 vụ ACTIVE), engineer1, admin1; 1 báo cáo đã có chẩn đoán APPROVED kèm treatment rule để test `apply`.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01 | Poka-yoke | `docker compose up` sau khi xóa một biến `*_PASSWORD` bất kỳ khỏi `.env` | Compose dừng ngay, log nêu đúng tên biến thiếu | | Đạt (local) |
| T1-02 | WP1-02 | Âm tính | `POST .../diagnosis/{id}/apply` với `appliedAt` = ngày mai | 422, thông báo "ngày phun không được ở tương lai" | | Đạt (local, unit + E2E) |
| T1-03 | WP1-02 | Âm tính | `POST .../diagnosis/{id}/apply` với `appliedAt` lùi quá N ngày (N theo Q1) | 422, thông báo nêu rõ giới hạn N ngày | | Đạt (local, unit + E2E) |
| T1-04 | WP1-02 | Âm tính | `POST .../crop-seasons/{id}/disease-reports` trên vụ đã `HARVESTED` | 422, thông báo "vụ mùa đã thu hoạch" | | Đạt (local, unit + E2E) |
| T1-05 | WP1-02 | Poka-yoke | Tạo `CropSeason` thứ hai `ACTIVE` trên thửa đang có vụ `ACTIVE` | 409, không tạo bản ghi mới | | Đạt (local, unit + Postgres + E2E) |
| T1-06 | WP1-02 | Âm tính | Tạo `CropSeason` với `expectedHarvestDate < startDate` | 400 | | Đạt (local, unit + E2E) |
| T1-07 | WP1-02 | Đồng thời | 2 request `approve` cùng lúc trên 1 `Diagnosis` (giả lập bằng 2 thread/2 client) | 1 request 200, request còn lại 409, không có trạng thái bị ghi đè âm thầm | | Đạt (local, Postgres/Testcontainers) |
| T1-08 | WP1-03 | Phân quyền (client) | `POST /disease/predict` không header `X-Service-Api-Key` | 401 | | Đạt (local, pytest + container) |
| T1-09 | WP1-03 | Phân quyền (client) | `POST /disease/knowledge/retrieve` không header `X-Service-Api-Key` | 401 | | Đạt (local, pytest + container) |
| T1-10 | WP1-03 | Âm tính | Upload file `.txt` đổi tên thành `.jpg` qua endpoint báo cáo | 400, không lưu file | | Đạt (local, unit + E2E) |
| T1-11 | WP1-03 | Bảo mật dữ liệu | Upload ảnh thật có EXIF chứa GPS, tải lại file đã lưu trên server và đọc EXIF | Không còn EXIF GPS trong file đã lưu | | Đạt (local, unit + E2E) |
| T1-12 | WP1-04 | Hạ tầng | Chạy script reset 2 lần liên tiếp trên máy sạch | Cả hai lần `docker compose ps` toàn bộ service `healthy` | | Đạt (local, 2 lần reset) |
| T1-13 | WP1-04 | Hạ tầng | Mở PR thử đổi 1 dòng ở mỗi: `backend/`, `frontend/`, `mobile/`, `ai/` | Đúng 4 job CI tương ứng chạy và xanh | | Chờ PR thử trên GitHub |

## 3. Hồi quy (không được hỏng)

- [ ] Khóa PHI vẫn chặn đúng (HTTP 422) cho case hợp lệ cũ (khai `appliedAt` = hôm nay vẫn được chấp nhận).
- [ ] Farmer chỉ thấy thửa và báo cáo của mình (không đổi bởi phase này, nhưng phải còn đúng sau khi thêm `@Version`/validator).
- [ ] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [ ] Đăng nhập SSO qua Keycloak (web, mobile, Superset) vẫn hoạt động sau khi đổi `.env.example`.
- [ ] Thông báo Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.
- [ ] `apply` lần đầu trên một `Diagnosis` hợp lệ vẫn tạo được `TreatmentApplication` và `harvestLockedUntil` đúng công thức `appliedAt + phiDays`.

## 4. Tiêu chí đạt của phase (exit criteria)

- T1-01 đến T1-13 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log CI (link run), output `gitleaks`, kết quả `mvn test` lưu kèm PR.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
