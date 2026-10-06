# Plan thực hiện: Phase 4

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP4-01 (mã tự sinh) chặn WP4-02 (mobile) và WP4-03 (duyệt), vì cả hai cần mã đã sinh ra để hiển thị.
WP4-04 (danh mục giống) độc lập, làm song song được với WP4-01.
WP4-05 (trang quản lý web) cần WP4-03 (API duyệt) để có dữ liệu hiển thị.
WP4-06 (định tuyến Mattermost) độc lập, làm bất kỳ lúc nào.
Thứ tự: (WP4-01 ∥ WP4-04) → WP4-03 → (WP4-02 ∥ WP4-05 ∥ WP4-06).

## WP4-01: Mã thửa, mã vụ tự sinh và GPS bắt buộc

- Mục tham chiếu: mục 2 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: mã thửa/mã vụ sinh tự động theo quy chuẩn, không cho farmer nhập tay; vị trí GPS bắt buộc khi tạo thửa.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `dto/ParcelCreateRequest.java`: `code` là `@NotBlank` tự do, không có quy tắc sinh; `farmerName` tự do (đã xử lý hướng thay bằng `householdId` ở P2); `latitude`/`longitude` đã có validation phạm vi (`-90..90`, `-180..180`) nhưng là trường tuỳ chọn, không bắt buộc.
- `dto/CropSeasonCreateRequest.java`: `riceVariety` là `@NotBlank` tự do; không có trường mã vụ (vụ mùa hiện chỉ có `id` số); không có check `expectedHarvestDate >= startDate` ở tầng validation (đã xử lý ở P1 WP1-02).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Tạo thửa | Farmer nhập `code` tự do | Backend tự sinh `<MÃ HTX>-<MÃ HỘ>-T<STT thửa của hộ>`, ví dụ `HTX01-H0042-T03`; request không còn nhận `code` |
| Tạo vụ mùa | Không có mã | Backend tự sinh `<MÃ THỬA>-<NĂM><LOẠI VỤ>`, ví dụ `HTX01-H0042-T03-2026DX`; ràng buộc unique `(parcel_id, năm, loại vụ)` |
| Gửi GPS khi tạo thửa | Tuỳ chọn | Bắt buộc, kèm độ chính xác (mét); từ chối nếu sai số > ngưỡng Q2 (30m) hoặc nghi ngờ mock location |
| Dữ liệu thửa cũ chưa có GPS | - | Đánh dấu cờ "thiếu vị trí" (không chặn xem, chỉ không hiện trên bản đồ Superset) |

**Hướng làm**

- `Parcel`: bỏ nhận `code` từ client, sinh trong transaction cùng lúc tạo (`SELECT ... FOR UPDATE` trên `Household` để lấy STT thửa tiếp theo, hoặc dùng sequence riêng theo hộ), kèm unique constraint DB chống trùng khi 2 request đồng thời.
  `MÃ HTX` lấy từ cấu hình (`app.htx-code`, một giá trị cho cả hệ thống — dự án hiện chỉ phục vụ 1 HTX).
- `Parcel` thêm `gpsAccuracyMeters` (BigDecimal), `latitude`/`longitude` đổi thành bắt buộc (`@NotNull`); thêm `locationMissing` (boolean, mặc định `false`, set `true` cho dữ liệu cũ qua migration).
- `CropSeason` thêm `code` (sinh tự động), `seasonType` (enum `DONG_XUAN`/`HE_THU`/`THU_DONG`).
  Tự gợi ý `seasonType` theo tháng bắt đầu (bảng mốc tháng ở dưới, **cần kỹ sư nông nghiệp xác nhận trước khi hard-code** — Q1 ở `00_OVERVIEW.md`), farmer xác nhận lại trước khi lưu.
- Unique constraint `(parcel_id, year, season_type)` trên `CropSeason` để chặn tạo trùng vụ trong cùng năm cho cùng thửa.

**Bảng mốc tháng đã chốt (Q1, chưa kỹ sư nông nghiệp xác nhận)**

| Loại vụ | Tháng bắt đầu (ĐBSCL) |
|---|---|
| Đông Xuân (ĐX) | Tháng 11, 12, 1 |
| Hè Thu (HT) | Tháng 3, 4, 5 |
| Thu Đông (TĐ) | Tháng 6, 7, 8 |

**Phạm vi ảnh hưởng**

- Module và file chính: `model/Parcel.java`, `model/CropSeason.java`, `dto/ParcelCreateRequest.java`, `dto/CropSeasonCreateRequest.java`, `service/ParcelService.java`, `service/CropSeasonService.java`, `application.yaml` (thêm `app.htx-code`).
- Endpoint, bảng DB, biến môi trường: `POST /api/v1/parcels`, `POST .../crop-seasons` đổi request/response (bỏ `code` ở request thửa, thêm `code`/`seasonType` ở response vụ); `parcels` thêm `gps_accuracy_meters`, `location_missing`; `crop_seasons` thêm `code`, `season_type` + unique constraint.
- Role và client bị ảnh hưởng: farmer (mobile) — request tạo thửa/vụ đổi shape; web (nếu còn form tạo thửa) phải cập nhật theo.
- Tương thích ngược và dữ liệu cũ: thửa cũ không có mã theo quy chuẩn mới — giữ `code` cũ nguyên vẹn (không đổi ngược), chỉ áp quy chuẩn mới cho thửa tạo từ đây; vụ mùa cũ không có `code`/`seasonType` — migration backfill bằng cách suy ra từ `startDate` hoặc để trống (chấp nhận được, không ảnh hưởng nghiệp vụ).

**Kết quả đầu ra**

- Tạo thửa mới không nhận `code` từ client, mã sinh ra đúng quy chuẩn và không trùng khi 2 request đồng thời.
- Tạo vụ mùa thứ hai cùng thửa, cùng năm, cùng loại vụ bị từ chối (409).

**Phụ thuộc, lưu ý**: cần `Household.code` từ P2 đã tồn tại; Q1 (mốc tháng) chỉ là giá trị mặc định tạm, phải xác nhận với kỹ sư nông nghiệp trước khi dùng cho dữ liệu thật (không phải seed/demo).

## WP4-02: Mobile — màn hình tạo thửa, tạo vụ, lấy GPS

- Mục tham chiếu: mục 2, 15 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: farmer tạo được thửa và vụ mùa ngay trên mobile, kèm lấy GPS tại ruộng, hoạt động được khi mất mạng.
- Ước lượng: L.

**Hiện trạng đã rà soát**

- Mobile hiện chỉ đọc dữ liệu (danh sách thửa/vụ/báo cáo) và gửi báo cáo sâu bệnh; không có màn hình tạo thửa/vụ; không có `expo-location` trong dependency.
- Hàng đợi offline hiện chỉ phục vụ gửi ảnh báo cáo (theo `PROJECT_IMPROVEMENTS.md` mục 2), chưa dùng cho request tạo mới.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Tạo thửa trên mobile | Không có màn hình | Có màn hình nhập diện tích, vị trí (tuỳ chọn thêm tên/địa chỉ), bấm "đang ở ruộng, lấy vị trí" để lấy GPS, hiện độ chính xác cho farmer thấy |
| Tạo vụ trên mobile | Không có màn hình | Có màn hình chọn giống từ danh mục (WP4-04), ngày bắt đầu, hệ thống tự tính ngày thu hoạch dự kiến |
| Tạo thửa/vụ khi mất mạng | Không áp dụng (chưa có tính năng) | Request vào hàng đợi offline giống báo cáo sâu bệnh, kèm `Idempotency-Key`, gửi khi có mạng lại |

**Hướng làm**

- Thêm `expo-location`, màn hình tạo thửa gọi `getCurrentPositionAsync`, hiển thị `accuracy` (mét) cho farmer, từ chối gửi nếu `accuracy` vượt ngưỡng Q2 (hiện cảnh báo, không chặn cứng ở client — backend vẫn là lớp chặn chính theo nguyên tắc 2 tầng).
- Mở rộng hàng đợi offline hiện có (đang phục vụ ảnh báo cáo) để nhận thêm loại request "tạo thửa"/"tạo vụ", mỗi request kèm `Idempotency-Key` sinh ở client.
- Màn hình tạo vụ gọi API danh mục giống (WP4-04) để hiển thị danh sách chọn, gọi tính ngày thu hoạch dự kiến từ backend (không tự tính ở client, tránh lệch logic).

**Phạm vi ảnh hưởng**

- Module và file chính: `mobile/` — màn hình mới (tạo thửa, tạo vụ), mở rộng module hàng đợi offline hiện có, thêm dependency `expo-location`.
- Endpoint, bảng DB, biến môi trường: không có endpoint mới riêng cho mobile (dùng chung `POST /api/v1/parcels`, `POST .../crop-seasons` đã đổi ở WP4-01).
- Role và client bị ảnh hưởng: chỉ farmer (mobile).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng, đây là tính năng hoàn toàn mới trên mobile.

**Kết quả đầu ra**

- Farmer tạo được 1 thửa và 1 vụ hoàn chỉnh trên mobile, có GPS, không cần vào web.
- Tắt mạng, tạo thửa, mở mạng lại → request tự gửi, không tạo trùng khi bấm gửi lại.

**Phụ thuộc, lưu ý**: cần WP4-01 (API đã đổi shape) xong trước; không phụ thuộc WP4-03/04/05/06 về code nhưng cần WP4-04 có API danh mục giống để màn tạo vụ hoàn chỉnh.

## WP4-03: Duyệt thửa/vụ qua workflow dùng chung

- Mục tham chiếu: mục 2, 3 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: thửa/vụ mới tạo phải qua admin duyệt mới hoạt động, tái dùng đúng bảng `workflow_case`/`workflow_event` đã có từ P3, không viết cơ chế duyệt riêng.
- Ước lượng: M.

**Hiện trạng đã rà soát**

- `Parcel`, `CropSeason` hiện không có trạng thái duyệt — tạo xong là dùng được ngay.
- `workflow_case`/`workflow_event` (P3) hiện chỉ gắn với `caseType = DIAGNOSIS_APPROVAL`.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Tạo thửa mới | Lưu và dùng được ngay | Tạo `WorkflowCase(caseType = PARCEL_APPROVAL, status = PENDING)`, thửa chưa hiện trên Superset, chưa tạo được vụ trên thửa này |
| Admin duyệt thửa | Không áp dụng | `PATCH /api/v1/workflow-cases/{id}/approve` (dùng chung endpoint của WorkflowController, không viết endpoint riêng cho `PARCEL_APPROVAL`), cập nhật `Parcel` liên quan sang trạng thái sử dụng được |
| Admin từ chối thửa | Không áp dụng | `PATCH .../reject` kèm lý do; farmer sửa lại và gửi lại được (không tạo bản ghi mới, cập nhật request cũ và tạo `WorkflowEvent` mới) |
| Tạo vụ trên thửa chưa duyệt | Không áp dụng (chưa có khái niệm duyệt) | 403/422, chặn ở backend |
| Nhiều yêu cầu chờ duyệt cùng lúc từ 1 farmer | Không giới hạn | Chặn tạo yêu cầu thứ (Q3 + 1) nếu còn đang chờ duyệt (theo Q3, giới hạn 3) |
| Thửa đã duyệt, farmer muốn sửa diện tích/vị trí | Không áp dụng | Tạo yêu cầu "chỉnh sửa" riêng (`caseType = PARCEL_EDIT_REQUEST`), cũng qua admin duyệt, không sửa trực tiếp |

**Hướng làm**

- `Parcel` thêm `approvalStatus` tương tự `Diagnosis` (giữ song song với `WorkflowCase.status`, theo đúng trade-off đã chốt ở P3 TO2) để các câu query hiện có (ví dụ lọc thửa đang hoạt động) không phải join `workflow_case` mỗi lần.
- `ParcelService.create(...)`: sau khi lưu, gọi `WorkflowService.createCase("PARCEL_APPROVAL", parcel.getId(), ...)`.
  Tương tự cho `CropSeasonService.create(...)` với `caseType = "CROP_SEASON_APPROVAL"`.
- `CropSeasonService.create(...)`: thêm check `parcel.getApprovalStatus() == APPROVED` trước khi cho tạo vụ.
- Thêm check giới hạn Q3 (số `WorkflowCase` đang `PENDING` của cùng `caseType` do cùng farmer tạo) ngay trong `ParcelService`/`CropSeasonService.create(...)`, không đợi tới lớp workflow mới chặn.
- Yêu cầu "chỉnh sửa thửa đã duyệt": endpoint mới `POST /api/v1/parcels/{id}/edit-requests`, tạo `WorkflowCase(caseType = PARCEL_EDIT_REQUEST)`, admin duyệt thì áp thay đổi vào `Parcel` thật.

**Phạm vi ảnh hưởng**

- Module và file chính: `model/Parcel.java` (thêm `approvalStatus`), `service/ParcelService.java`, `service/CropSeasonService.java`, `controller/ParcelController.java` (endpoint edit-request mới).
- Endpoint, bảng DB, biến môi trường: `POST /api/v1/parcels/{id}/edit-requests` (mới); `parcels` thêm cột `approval_status`.
- Role và client bị ảnh hưởng: farmer (tạo yêu cầu), admin (duyệt qua `WorkflowController` đã có từ P3).
- Tương thích ngược và dữ liệu cũ: thửa/vụ đã tồn tại trước phase này coi như đã `APPROVED` (migration set mặc định), không bắt duyệt lại.

**Kết quả đầu ra**

- Thửa mới tạo ở trạng thái chờ duyệt, không tạo được vụ trên thửa đó.
- Admin duyệt xong, farmer tạo được vụ bình thường.
- Farmer thứ (Q3 + 1) yêu cầu chờ duyệt bị chặn.

**Phụ thuộc, lưu ý**: cần WP4-01 (mã đã sinh) để `WorkflowCase` hiển thị đúng mã thửa/vụ ở trang quản lý (WP4-05); dùng đúng `WorkflowController` đã có từ P3, không viết controller duyệt riêng.

## WP4-04: Danh mục giống lúa và gợi ý ngày thu hoạch

- Mục tham chiếu: mục 2 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: farmer chọn giống từ danh sách có nguồn, hệ thống tự gợi ý ngày thu hoạch, không nhập tay tự do.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `CropSeasonCreateRequest.riceVariety` là chuỗi tự do, mặc định hiện tại ở mobile/web là `"OM 5451"` (theo `PROJECT_IMPROVEMENTS.md` mục 2), không có bảng danh mục.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chọn giống lúa khi tạo vụ | Gõ tay, mặc định "OM 5451" | Chọn từ danh mục (`GET /api/v1/rice-varieties`), mỗi giống có số ngày sinh trưởng min-max và nguồn |
| Ngày thu hoạch dự kiến | Nhập tùy ý | Gợi ý = `startDate + số ngày sinh trưởng (trung bình min-max)`; farmer chỉnh lệch quá khoảng cho phép phải nhập lý do |

**Hướng làm**

- Bảng `RiceVariety` (`code`, `name`, `growthDaysMin`, `growthDaysMax`, `source`, `approvalStatus` — theo đúng quy ước đang dùng cho `data/structured`, không tự bịa số).
- Seed tối thiểu 5-10 giống phổ biến ĐBSCL, nguồn ghi rõ (Viện Lúa ĐBSCL, Sở NN...), trạng thái `APPROVED` chỉ khi có nguồn xác nhận, còn lại giữ `IN_REVIEW` giống cách làm hiện tại với RAG/treatment rule.
- `CropSeasonCreateRequest` đổi `riceVariety` (String) thành `riceVarietyCode` (tham chiếu `RiceVariety.code`); backend tính `expectedHarvestDate` gợi ý, trả về cho client xác nhận trước khi lưu.
- Nếu farmer nhập `expectedHarvestDate` lệch quá khoảng `[growthDaysMin, growthDaysMax]` tính từ `startDate`, bắt buộc kèm `reasonForOverride` (String, không rỗng).

**Phạm vi ảnh hưởng**

- Module và file chính: `model/RiceVariety.java` (mới), `repository/RiceVarietyRepository.java`, `controller/RiceVarietyController.java` (mới), `dto/CropSeasonCreateRequest.java`, `service/CropSeasonService.java`.
- Endpoint, bảng DB, biến môi trường: `GET /api/v1/rice-varieties` (mới); bảng `rice_varieties` (mới); `crop_seasons` thêm `rice_variety_code` (đổi từ `rice_variety` tự do), `reason_for_override` (nullable).
- Role và client bị ảnh hưởng: farmer (mobile) chọn giống từ danh sách; dữ liệu danh mục do admin/kỹ sư quản lý (không có UI quản lý riêng ở phase này, thêm qua migration/seed).
- Tương thích ngược và dữ liệu cũ: vụ mùa cũ có `riceVariety` dạng chuỗi tự do — giữ nguyên cột cũ song song (đổi tên hoặc giữ cả hai), không cố ánh xạ ngược sang `code` nếu không khớp chính xác.

**Kết quả đầu ra**

- Tạo vụ mới chỉ chọn được giống có trong danh mục.
- Ngày thu hoạch gợi ý khớp đúng công thức, lệch quá khoảng phải có lý do.

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác, làm song song được với WP4-01.

## WP4-05: Trang quản lý cho admin và engineer

- Mục tham chiếu: mục 3, 12 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có một trang quản lý thật (router, danh sách có lọc/phân trang, chi tiết, timeline) thay cho web một trang chọn tuần tự hiện tại.
- Ước lượng: L.

**Hiện trạng đã rà soát**

- `frontend/src/App.tsx` là trang duy nhất, không có `react-router` hay thư viện router nào trong `package.json`; luồng hiện tại là chọn thửa → vụ → báo cáo tuần tự qua các panel (`ParcelPanel`, `CropSeasonPanel`, `DiseaseReportPanel`, `DiagnosisPanel`).
- Backend chưa có endpoint "liệt kê tất cả" có phân trang cho báo cáo; `findAll` của thửa không phân trang, không lọc (theo `PROJECT_IMPROVEMENTS.md` mục 12).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Admin xem yêu cầu tạo thửa/vụ | Không có nơi xem tập trung | Trang `/admin/requests`, danh sách lọc theo trạng thái/hộ/ngày, bấm vào xem chi tiết + thông tin hộ |
| Engineer xem báo cáo/chẩn đoán chờ xử lý | Chọn tuần tự thửa → vụ → báo cáo | Trang `/engineer/queue`, danh sách từ `GET /workflow-cases?assignedTo=me&status=PENDING` |
| Xem lịch sử xử lý một case | Không có | Trang chi tiết hiển thị timeline từ `GET /workflow-cases/{id}/events` (đã có từ P3) |

**Hướng làm**

- Thêm `react-router-dom`, tách `App.tsx` hiện tại thành route `/` (giữ nguyên luồng cũ cho farmer-trên-web nếu còn dùng tạm, hoặc bỏ nếu P2 đã chặn farmer khỏi web) và route mới `/admin/*`, `/engineer/*`.
- Trang danh sách dùng chung component bảng có phân trang/lọc, gọi API danh sách đã có/mới (`GET /workflow-cases`, và API `findAll` có phân trang cho `Parcel`/`CropSeason`/`DiseaseReport` nếu chưa có — bổ sung cùng lúc).
- Trang chi tiết: thông tin hộ (từ `Household`, P2) + ảnh báo cáo (presigned URL, P3) + kết quả AI + timeline (từ `workflow_case`/`workflow_event`, P3).
- Menu điều hướng theo role (dùng claim role đã có từ `CurrentUserService`).

**Phạm vi ảnh hưởng**

- Module và file chính: `frontend/src/` — router mới, trang mới (`pages/AdminRequests.tsx`, `pages/EngineerQueue.tsx`, `pages/CaseDetail.tsx` hoặc tên tương đương), `backend/.../controller/ParcelController.java`/`CropSeasonController.java` (thêm phân trang/lọc nếu chưa đủ từ WP4-03).
- Endpoint, bảng DB, biến môi trường: có thể cần thêm endpoint `GET /api/v1/parcels?page=&size=&status=` nếu `findAll` hiện tại chưa hỗ trợ phân trang.
- Role và client bị ảnh hưởng: admin, engineer (web); không ảnh hưởng mobile.
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng dữ liệu, chỉ thêm giao diện mới.

**Kết quả đầu ra**

- Admin vào `/admin/requests`, thấy đúng danh sách yêu cầu chờ duyệt kèm thông tin hộ, duyệt/từ chối ngay tại trang.
- Engineer vào `/engineer/queue`, thấy đúng danh sách việc của mình.

**Phụ thuộc, lưu ý**: cần WP4-03 (API duyệt qua workflow) có dữ liệu trước khi trang quản lý hiển thị có ý nghĩa.

## WP4-06: Định tuyến thông báo Mattermost theo loại sự kiện

- Mục tham chiếu: mục 2, 13 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: yêu cầu tạo thửa/vụ bắn thông báo vào đúng channel (`Ban quản trị`), không dồn chung một channel public như hiện tại.
- Ước lượng: S.

**Hiện trạng đã rà soát**

- `MattermostWebhookClient`/`MattermostNotificationListener` hiện chỉ cấu hình 1 webhook URL (`MATTERMOST_WEBHOOK_URL`), gửi cho 2 sự kiện hiện có (báo cáo mới, chẩn đoán chờ duyệt) vào cùng 1 channel public.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Có yêu cầu tạo thửa/vụ mới | Không có thông báo nào | Bắn thông báo vào webhook riêng cho channel `Ban quản trị` |
| Báo cáo sâu bệnh mới, chẩn đoán chờ duyệt | Vào 1 channel public duy nhất | Giữ nguyên, hoặc định tuyến vào channel kỹ sư nếu đã có (tuỳ cấu hình) |

**Hướng làm**

- Đổi `MattermostNotificationListener` nhận map `eventType → webhookUrl` (đọc từ nhiều biến môi trường, ví dụ `MATTERMOST_WEBHOOK_URL_ADMIN`, `MATTERMOST_WEBHOOK_URL_ENGINEER`, giữ `MATTERMOST_WEBHOOK_URL` làm fallback mặc định nếu biến riêng không set).
- Thêm sự kiện mới: `ParcelCreatedEvent`, `CropSeasonCreatedEvent` (publish sau khi tạo `WorkflowCase` ở WP4-03), route vào webhook admin.

**Phạm vi ảnh hưởng**

- Module và file chính: `service/MattermostNotificationListener.java`, `service/MattermostMessageBuilder.java`, `event/` (2 event mới), `application.yaml`/`.env.example` (biến webhook mới).
- Endpoint, bảng DB, biến môi trường: `MATTERMOST_WEBHOOK_URL_ADMIN`, `MATTERMOST_WEBHOOK_URL_ENGINEER` (mới, không default, rỗng thì tắt đúng theo quy ước hiện có).
- Role và client bị ảnh hưởng: không ảnh hưởng client, chỉ ảnh hưởng admin/engineer nhận thông báo đúng channel.
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng; nếu không cấu hình webhook riêng thì rơi về hành vi cũ (1 webhook chung).

**Kết quả đầu ra**

- Tạo yêu cầu thửa/vụ mới → thông báo xuất hiện ở webhook admin, không lẫn vào channel báo cáo sâu bệnh.

**Phụ thuộc, lưu ý**: cần WP4-03 publish đúng event sau khi tạo `WorkflowCase`; không phụ thuộc WP khác về code.

## Ghi chú triển khai

- Endpoint duyệt dùng chung nằm ở `WorkflowController` (`PATCH /{id}/approve`, `/{id}/reject`, chỉ ADMIN); Core nhận quyết định rồi giao cho `WorkflowDecisionHandler` theo `caseType`, nên không biết thửa hay vụ là gì.
  Chẩn đoán vẫn duyệt qua endpoint riêng (endpoint chung trả 409 cho `DIAGNOSIS_APPROVAL`).
- `crop_seasons` có thêm `approval_status` (plan chỉ nêu `parcels`), vì vụ chờ duyệt không được tính là vụ đang canh tác.
- Yêu cầu chỉnh sửa thửa lưu ở bảng `parcel_edit_requests`; `workflow_case` unique theo `(case_type, entity_type, entity_id)` nên không thể gắn nhiều yêu cầu vào cùng một thửa.
- Trang quản lý dùng `/requests`, `/requests/:caseId`, `/queue` thay cho `/admin/*` và `/engineer/*`, vì gateway chuyển `/admin/*` sang Keycloak.
  Dữ liệu do `GET /api/v1/approval-requests` cung cấp (case kèm thửa, vụ hoặc chẩn đoán, lọc theo hộ và ngày).
- Quản trị viên tạo thửa, vụ hoặc yêu cầu chỉnh sửa thì được duyệt ngay (vẫn có đủ event trong timeline); giới hạn yêu cầu chờ chỉ áp cho nông dân và tính trên cả ba loại yêu cầu.
- Danh mục giống seed 9 giống, mọi giống `IN_REVIEW`: số ngày sinh trưởng là số liệu công khai làm tròn, chưa kỹ sư xác nhận.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /api/v1/parcels` | Bỏ nhận `code`, GPS bắt buộc | Có |
| Endpoint | `POST .../crop-seasons` | Bỏ `riceVariety` tự do, nhận `riceVarietyCode`; thêm `code` sinh tự động trong response | Có |
| Endpoint | `POST /api/v1/parcels/{id}/edit-requests` | Mới | Không (thêm mới) |
| Endpoint | `GET /api/v1/rice-varieties` | Mới | Không (thêm mới) |
| Bảng DB | `parcels` | Thêm `gps_accuracy_meters`, `location_missing`, `approval_status` | Không (thêm cột) |
| Bảng DB | `crop_seasons` | Thêm `code`, `season_type`, `rice_variety_code`, `reason_for_override`; unique `(parcel_id, year, season_type)` | Có (unique constraint) |
| Bảng DB | `rice_varieties` | Bảng mới | Không |
| Biến môi trường | `app.htx-code` | Mới | Không |
| Biến môi trường | `MATTERMOST_WEBHOOK_URL_ADMIN`, `MATTERMOST_WEBHOOK_URL_ENGINEER` | Mới, không default | Không |

## Dữ liệu, migration và rollback

- Migration: thêm cột mã/GPS cho `parcels`/`crop_seasons`, bảng `rice_varieties`, qua Flyway (đã có từ P2).
- Dữ liệu cũ: thửa/vụ tồn tại trước phase này mặc định `approvalStatus = APPROVED`, `locationMissing = true` nếu chưa có GPS; không bắt buộc backfill `code`/`seasonType` cho vụ mùa cũ.
- Rollback: các cột mới đều nullable hoặc có default, rollback bằng migration ngược không mất dữ liệu nghiệp vụ đã có; unique constraint mới trên `crop_seasons` cần rà dữ liệu cũ trước khi bật (giống WP1-02 đã làm ở P1).

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D8 (kế thừa phase 0) | Tự biên soạn danh mục giống từ nguồn công khai | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Đông Xuân tháng 11-1, Hè Thu tháng 3-5, Thu Đông tháng 6-8; cấu hình ở `app.season-start-months`, chưa kỹ sư xác nhận | Gợi ý loại vụ chỉ là giá trị mặc định, nông dân luôn xác nhận trước khi lưu |
| Q2 | Ngưỡng sai số GPS 30 mét | Giữ đúng đề xuất gốc, đủ chặt cho định vị bằng điện thoại thường |
| Q3 | Tối đa 3 yêu cầu chờ duyệt cùng lúc mỗi farmer | Đủ cho hộ nhiều thửa, vẫn chặn spam |
