# Plan thực hiện: Phase 0

Phase này không có work package code. Thay vào đó là 4 gói quyết định (DP), mỗi gói gom vài quyết định D{n} liên quan theo chủ đề.
Thứ tự làm: DP0-01 trước tiên (chặn mọi gói khác vì quyết định có cần ứng dụng thứ hai hay không), các DP còn lại làm song song được.
Mỗi DP dưới đây đã có: hiện trạng (đã rà code), các phương án, khuyến nghị dựa trên hiện trạng — người phụ trách chỉ cần đọc, chốt, điền cột "Kết luận".

## Thứ tự và phụ thuộc

DP0-01 → (DP0-02, DP0-03, DP0-04 làm song song) → cập nhật `PHASE_PLAN.md`.

## DP0-01: Phạm vi đề tài (D1)

- Mục tham chiếu: mục 22 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: xác nhận đề tài nông nghiệp có hợp lệ ở vòng HaUI hay cần bổ sung/đổi hướng, trước khi đổ công vào P2-P9.
- Ước lượng: S (việc hỏi + chờ phản hồi, không phải việc code).

**Hiện trạng đã rà soát**

- Đề HaUI (mục 2, 4.3) ghi ứng dụng tầng 3 là "bài toán thực tế của môi trường đại học", vòng HaUI cụ thể hóa thành "DX-Lab HaUI".
  Dự án hiện làm Agri-Carbon cho hợp tác xã nông nghiệp — đúng đề OLP quốc gia, hẹp hơn so với câu chữ vòng HaUI.
- Code hiện tại (`backend/DX-Farm`) không có gì ràng buộc cứng vào nông nghiệp ở tầng hạ tầng: Keycloak, Postgres, cấu trúc approve/reject (`ApprovalStatus`), Mattermost webhook đều là thành phần dùng chung.
  Phần nghiệp vụ nông nghiệp (Parcel, CropSeason, DiseaseReport, Diagnosis) nằm trong cùng một module Spring Boot, chưa tách rõ thành "Core" vs "App" bằng package hay service boundary (liên quan D2-D4, xem DP0-02).

**Phương án**

| Phương án | Mô tả | Được | Mất |
|---|---|---|---|
| A. Giữ nguyên, không thêm app | Chỉ làm DX-Farm, chứng minh Open-Core bằng tài liệu + sơ đồ | Không tốn thêm 3-5 ngày (P9) | Rủi ro cao nhất nếu hội đồng đọc đúng câu chữ "bối cảnh đại học" |
| B. Giữ DX-Farm + thêm app thứ hai bối cảnh trường học (P9) | Thêm ứng dụng nhỏ (phiếu hỗ trợ CNTT / mượn thiết bị) dùng lại Identity, Workflow, Data, thông báo của Core | Bằng chứng trực tiếp nhất cho câu hỏi "đổi ứng dụng, Core có đổi không"; an toàn với cả hai cách đọc đề | Tốn thêm 3-5 ngày, cần P3 (ranh giới Core) đủ tổng quát trước |
| C. Đổi hẳn sang đề tài trường học | Bỏ DX-Farm, làm lại từ đầu bối cảnh đại học | Bám sát câu chữ đề nhất | Mất toàn bộ phần đã làm (AI, mobile, BI nông nghiệp), không khả thi trong 14 ngày |

- **Đã chốt: A.**
  Đã xác nhận với giảng viên hướng dẫn/BTC rằng đề tài Agri-Carbon cho HTX nông nghiệp hợp lệ ở vòng HaUI.
  Rủi ro mục 22 coi như đã gỡ, không cần ứng dụng thứ hai.

**Kết quả đầu ra**

- D1 = A, lý do ở bảng "Quyết định thiết kế đã chốt".
- P9 bị loại khỏi `PHASE_PLAN.md` (xem phần cập nhật ở cuối phase 0).

**Phụ thuộc, lưu ý**: chặn P9; ảnh hưởng gián tiếp P3 (nếu chọn B, P3 phải đủ tổng quát để P9 dùng lại được mà không sửa Core).

## DP0-02: Kiến trúc Open-Core (D2, D3, D4)

- Mục tham chiếu: mục 7, 8 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: chọn công cụ cho 3 trụ cột hạ tầng Core (workflow, gateway, storage) để P3 có việc cụ thể thay vì phải tự chọn giữa chừng.
- Ước lượng: S-M (đọc tài liệu công cụ, không code).

**D2 — Workflow engine**

Hiện trạng đã rà: trạng thái duyệt hiện là enum (`ApprovalStatus`, `CropSeasonStatus`) + `if` trong service (`DiagnosisService`, `DiseaseReportService`), không có bảng lịch sử, không có `assignedTo`, không có API danh sách chờ.
Dùng cho 2 use case hiện tại (báo cáo, chẩn đoán) và cần mở rộng cho thửa/vụ (P4) và có thể cho P9.

| Phương án | Được | Mất |
|---|---|---|
| Flowable (nhúng, Apache-2.0) | Có sẵn lịch sử, task, assignee, danh sách chờ chuẩn BPMN; đề có gợi ý tên | Thời gian học + tích hợp vào Spring Boot hiện có, rủi ro cao nhất của dự án theo chính `PHASE_PLAN.md` P3 |
| Tự làm bảng `workflow_case` / `workflow_event` | Chủ động, nhẹ, khớp đúng 5 trường đề yêu cầu (trạng thái, người tạo, người xử lý, thời gian, lịch sử) không thừa | Không có UI/BPMN có sẵn, phải tự viết API danh sách chờ |

- **Đã chốt: tự làm** (khớp lộ trình rút gọn 14 ngày của `PHASE_PLAN.md` mục 3).
  Lý do: đề chỉ yêu cầu 5 trường cụ thể, không yêu cầu BPMN; rủi ro tích hợp Flowable trong thời gian ngắn cao hơn lợi ích.

**D3 — Cửa API (gateway)**

Hiện trạng đã rà: `frontend/nginx.conf` chỉ proxy `/api/` và `/uploads/` tới backend.
AI service (cổng 8000) không đi qua nginx, nhưng container vẫn publish cổng ra host (`deploy/docker-compose.yml`, biến `AI_HOST_PORT`), và 2 trong 3 endpoint AI (`/disease/predict`, `/disease/knowledge/retrieve`) không có dependency xác thực (`ai/app/api/router.py`), chỉ `/inference/diagnose` có `authorize_backend`.
Keycloak, Postgres, Superset, Mattermost cũng đều publish cổng ra host.

| Phương án | Được | Mất |
|---|---|---|
| APISIX standalone | Đúng gợi ý của đề, config file-based không cần etcd | Thêm một dịch vụ, thời gian học |
| Traefik | Nhẹ, cấu hình qua label Docker, học nhanh | Ít được đề nhắc tên trực tiếp (nhưng đề chấp nhận "hoặc tương đương") |
| Nâng cấp Nginx hiện có | Tận dụng ngay thành phần đã có, chỉ thêm route + JWT verify module | Nginx JWT verify (lua hoặc njs) phức tạp hơn so với 2 lựa chọn trên |

- **Đã chốt: APISIX standalone** (không etcd, cấu hình bằng file YAML).
  Plugin `openid-connect` có sẵn tích hợp trực tiếp với Keycloak realm (xác thực JWT qua realm, không cần tự viết lớp verify như Traefik ForwardAuth), đúng gợi ý của đề.
  Việc gấp nhất bất kể chọn gì: ngừng publish cổng AI (8000) ra host, bắt buộc `X-Service-Api-Key` cho toàn bộ endpoint AI, không chỉ `/diagnose`.

**D4 — Lưu ảnh**

Hiện trạng đã rà: `/uploads/**` hiện phục vụ công khai qua Nginx, không cần token; ảnh lưu thẳng filesystem local của container backend (xem mục 9 `PROJECT_IMPROVEMENTS.md`).

| Phương án | Được | Mất |
|---|---|---|
| MinIO | Đúng gợi ý đề ("object/file storage"), API S3 chuẩn, license Apache-2.0 | Thêm dịch vụ tốn RAM, cần sửa code upload/đọc ảnh |
| Storage service tự bọc trên filesystem | Nhanh, ít thay đổi hạ tầng | Không phải "object storage" đúng nghĩa, khó chứng minh với đề mục 4.1 |

- **Đã chốt: MinIO.**
  Không cắt khỏi lộ trình rút gọn 14 ngày dù `PHASE_PLAN.md` mục 3 từng gợi ý có thể cắt.
  Bắt buộc: link có hạn (signed URL) thay cho `/uploads` công khai; ảnh cũ trong `/uploads` local cần kế hoạch migrate hoặc chạy song song một thời gian (chi tiết ở P3).

**Kết quả đầu ra**

- D2, D3, D4 có giá trị chốt, dùng trực tiếp làm "Hướng làm" của P3.

**Phụ thuộc, lưu ý**: chặn toàn bộ `01_PLAN.md` của P3 khi phase đó được tạo.

## DP0-03: Vận hành nghiệp vụ nông nghiệp (D5, D6, D7, D8, D9)

- Mục tham chiếu: mục 1, 5, 9, 13, 18 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: chốt nhanh 5 quyết định nhỏ hơn, phần lớn đã có khuyến nghị rõ trong `PROJECT_IMPROVEMENTS.md`, chỉ cần xác nhận.
- Ước lượng: S.

| # | Hiện trạng đã rà | Khuyến nghị | Vì sao không chọn phương án còn lại |
|---|---|---|---|
| D5 kênh thông báo | Mobile chưa có thư viện thông báo nào; `BACKEND_CHANGES.md` từng ghi rõ cố tình không thêm device token | **Đã chốt**: hộp thông báo trong app (bảng `Notification`), `GET` danh sách (poll khi mở/foreground) + `PATCH` đánh dấu đã đọc; push để sau | Push cần dịch vụ ngoài (FCM/APNs) hoặc ntfy/UnifiedPush — thêm phụ thuộc không cần cho MVP; chỉ GET không đủ vì mất đồng bộ trạng thái đã đọc giữa thiết bị |
| D6 test phần lõi | Chỉ 2 file test (`TreatmentRuleTest`, `CatalogActiveFilterTest`) trong `backend/DX-Farm/src/test`; state machine duyệt, khóa PHI, phạm vi dữ liệu FARMER, idempotency chưa có test tự động | **Đã chốt**: bật lại test, nhưng chỉ cho nghiệp vụ lõi dùng chung và chỉ case ảnh hưởng logic/an toàn hệ thống (không test toàn bộ happy path) | Đề đã yêu cầu "kiểm thử tối thiểu cho chức năng lõi" (đề mục 7, 9); phạm vi hẹp để không vượt ngân sách 14 ngày |
| D7 phạm vi AI | Checkpoint YOLO chỉ nhận 5 lớp bệnh lá (đạo ôn, đốm nâu, than lá, tungro, khô vằn — `ai/app/cv/README.md`), không có lớp "khác/không rõ"; không detection đạt ngưỡng thì fallback `Rice___healthy` | **Đã chốt**: giữ 5 bệnh lá hiện có cho I1 (CV), bổ sung đường "nhập triệu chứng → RAG xếp hạng trong danh mục bệnh có sẵn" cho trường hợp AI không nhận diện được (đã thiết kế ở mục 5 `PROJECT_IMPROVEMENTS.md`) | Huấn luyện thêm lớp background/sâu hại là việc AI tốn nhiều thời gian, không khả thi trong 14 ngày; đổi tên báo cáo "sâu bệnh" → "bệnh" đã đủ trung thực với phạm vi thật |
| D8 danh mục giống | `startDate`/`expectedHarvestDate` hiện nhập tay tự do, mặc định "OM 5451", không có bảng giống | **Đã chốt**: biên soạn danh mục tối thiểu (5-10 giống phổ biến ĐBSCL) từ nguồn công khai có ghi chú nguồn (Viện Lúa ĐBSCL, Sở NN), đánh dấu trạng thái duyệt giống cách làm với `data/structured` | Hoãn/nhập tay tiếp tục không giải quyết yêu cầu "gợi ý ngày thu hoạch có căn cứ" ở mục 2; không cần chờ chuyên gia nông nghiệp vì số liệu giống lúa là thông tin công khai, không phải quyết định nghiệp vụ |
| D9 ma trận quyền engineer | `@PreAuthorize`/`hasRole` hiện chỉ xuất hiện ở `SecurityConfig`, `GlobalExceptionHandler` và `DiagnosisController`; `ParcelController`/`CropSeasonController` chưa chặn theo hành động — engineer hiện tạo được thửa/vụ | **Đã chốt**: theo thao tác (không theo khu vực) cho P2, "phạm vi phụ trách theo xã/khu vực" để sau | Thêm ràng buộc khu vực đòi có dữ liệu địa giới hành chính gắn với thửa — chưa có, tốn thêm thời gian không cần thiết cho điều kiện không bị loại |

**D6 — danh sách case bắt buộc có test (chỉ crash/phá logic, không phải coverage đầy đủ)**

- Khóa PHI: `appliedAt` tương lai hoặc lùi quá N ngày bị từ chối (P1); khai báo thu hoạch khi còn khóa PHI hiệu lực bị chặn (422).
- Idempotency: gửi 2 request tạo báo cáo cùng `Idempotency-Key` đồng thời không tạo 2 bản ghi.
- Phạm vi dữ liệu theo role: FARMER gọi API xem/sửa thửa hoặc báo cáo không thuộc hộ mình bị 403/404, không rò dữ liệu qua response.
- Chuyển trạng thái workflow: duyệt một yêu cầu đã REJECTED/APPROVED (chuyển trạng thái không hợp lệ) bị từ chối, không ghi đè lịch sử.
- Ràng buộc vụ mùa: tạo vụ ACTIVE thứ hai trên thửa đã có vụ ACTIVE, hoặc `expectedHarvestDate < startDate`, bị từ chối ở backend (không chỉ FE).
- Danh sách trên là khung tối thiểu cho P1/P2/P3/P8; mỗi phase tự bổ sung case cụ thể theo WP của mình, không cần quay lại sửa dòng D6 này.

**Kết quả đầu ra**

- D5-D9 có giá trị chốt; D9 ghi rõ có làm "phạm vi khu vực" ở P2 hay hoãn.

**Phụ thuộc, lưu ý**: D6 chặn `02_CHECKLIST.md`/`03_TEST_PLAN.md` của P1 (lớp TEST); D9 chặn P2 WP phân quyền; D7 chặn P6; D8 chặn P4.

## DP0-04: Dữ liệu mở và lịch trình (D10, ngày hạn)

- Mục tham chiếu: mục 14, 23 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: chọn nguồn dữ liệu mở thứ hai và chốt ngày hạn thật để chia lại ước lượng ngày của từng phase.
- Ước lượng: S.

**D10 — Nguồn dữ liệu mở thứ hai**

Hiện trạng: đã có dataset ảnh bệnh lúa (CC BY 4.0) và corpus RAG 9 nguồn, đáp ứng yêu cầu "tối thiểu 2 nguồn có giấy phép" một phần; thiếu nguồn dạng sống (API thật, không phải file tĩnh).

- **Đã chốt: Open-Meteo** (không cần API key, giấy phép mở, có dữ liệu mưa/nhiệt độ theo tọa độ — khớp sẵn với yêu cầu GPS bắt buộc ở mục 2) cho gợi ý nguy cơ sâu bệnh hoặc lịch thu hoạch.
  Bắt buộc kèm snapshot JSON tĩnh để demo khi API ngoài ngừng hoạt động (đề mục 9 có hỏi câu này, xem mục 20 `PROJECT_IMPROVEMENTS.md`).

**Ngày hạn**

- **Đã chốt: không áp dụng ràng buộc 14 ngày.**
  Người phụ trách xác nhận không bị ép theo mốc 14 ngày của đề, làm đầy đủ theo ước lượng thật (~40 người-ngày cho P0-P8 + P10, đã bỏ P9 theo D1) thay vì lộ trình rút gọn ở `PHASE_PLAN.md` mục 3.
- `PHASE_PLAN.md` mục 3 ("Lộ trình rút gọn nếu hạn chỉ 14 ngày") giữ lại làm phương án dự phòng, không áp dụng trừ khi có mốc thời gian mới ép buộc.
- Số người làm song song: chưa chốt, không chặn các phase sau (chỉ ảnh hưởng tốc độ, không ảnh hưởng nội dung).

**Kết quả đầu ra**

- D10 có giá trị chốt; quyết định "không giới hạn 14 ngày" đã ghi vào `PHASE_PLAN.md` mục 3.

**Phụ thuộc, lưu ý**: D10 chặn P7; các phase từ P1 trở đi làm theo ước lượng đầy đủ, không cắt work package nào vì thời gian.

## Thay đổi hợp đồng (API, schema, cấu hình)

Không áp dụng — phase này không sửa code, không có endpoint/bảng/biến môi trường mới.

## Dữ liệu, migration và rollback

Không áp dụng.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Phương án A — giữ nguyên DX-Farm, không làm ứng dụng thứ hai (P9 loại bỏ) | Đã xác nhận với giảng viên hướng dẫn/BTC: đề tài Agri-Carbon hợp lệ ở vòng HaUI, rủi ro mục 22 coi như gỡ |
| D2 | Tự làm bảng `workflow_case`/`workflow_event` dùng chung | Nhẹ, đúng 5 trường đề yêu cầu, kiểm soát được trong 14 ngày, không rủi ro tích hợp Flowable |
| D3 | APISIX standalone (không etcd, config file) | Có plugin `openid-connect` tích hợp trực tiếp với Keycloak realm, tốn ít công tự viết lớp verify JWT hơn Traefik |
| D4 | MinIO | Đúng nghĩa "object storage" theo đề; ảnh cũ trong `/uploads` local cần kế hoạch migrate khi làm P3 |
| D5 | Hộp thông báo trong app: GET danh sách (poll) + PATCH đánh dấu đã đọc; push để sau | Đồng bộ trạng thái đã đọc giữa thiết bị, không mất khi xoá app; không phụ thuộc dịch vụ push ngoài |
| D6 | Bật test cho nghiệp vụ lõi dùng chung, chỉ case ảnh hưởng logic/an toàn hệ thống | Đề đã yêu cầu kiểm thử tối thiểu cho chức năng lõi; thu hẹp phạm vi còn khóa PHI, idempotency, phạm vi dữ liệu theo role, chuyển trạng thái workflow — không viết test cho toàn bộ happy path |
| D7 | Giữ 5 bệnh lá lúa hiện có, thêm đường nhập triệu chứng (RAG xếp hạng trong danh mục) cho trường hợp AI không nhận diện được | Huấn luyện thêm lớp mới tốn thời gian AI không khả thi trong 14 ngày; đổi tên báo cáo "sâu bệnh" → "bệnh" đủ trung thực |
| D8 | Biên soạn danh mục giống tối thiểu từ nguồn công khai (Viện Lúa ĐBSCL, Sở NN), ghi trạng thái duyệt | Thông tin công khai, không cần chờ chuyên gia, giải quyết được yêu cầu gợi ý ngày thu hoạch có căn cứ |
| D9 | Phân quyền engineer theo thao tác, hoãn "phạm vi khu vực" | Chưa có dữ liệu địa giới hành chính gắn với thửa, thêm ràng buộc khu vực không cần thiết cho điều kiện không bị loại |
| D10 | Open-Meteo + snapshot dự phòng | Không cần API key, giấy phép mở, khớp sẵn với GPS bắt buộc ở mục 2, có phương án khi API ngoài ngừng hoạt động |
