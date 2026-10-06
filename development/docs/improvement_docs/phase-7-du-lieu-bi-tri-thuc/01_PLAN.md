# Plan thực hiện: Phase 7

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP7-01 (VIEW + đổi quyền) phải xong trước WP7-02 (chart mới), vì chart mới nên trỏ thẳng vào VIEW ngay từ đầu, không phải đổi dataset 2 lần.
WP7-03 (nền bản đồ), WP7-04 (Open-Meteo), WP7-05 (hỏi đáp RAG) độc lập, làm song song được.
Thứ tự: WP7-01 → WP7-02 → (WP7-03 ∥ WP7-04 ∥ WP7-05).

## WP7-01: VIEW chỉ bản ghi đã duyệt, đổi quyền `superset_ro`

- Mục tham chiếu: mục 14 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: Superset không đọc được bản ghi chưa duyệt từ bất kỳ bảng nghiệp vụ nào.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `SupersetReadOnlyRoleSeeder.java`: `GRANT SELECT ON ALL TABLES IN SCHEMA public TO superset_ro` và `ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT ON TABLES TO superset_ro` — cấp quyền đọc mọi bảng hiện có và bảng tạo sau này, không phân biệt trạng thái duyệt.
- `bi/README.md`: 5 dataset hiện có trỏ thẳng vào bảng gốc (`parcels`, `crop_seasons`, `disease_reports`, `diagnoses`, `treatment_applications`).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Superset đọc thửa chưa duyệt (P4) | Đọc được (join thẳng bảng `parcels`) | Chỉ đọc được qua `vw_parcels_approved` (lọc `approval_status = APPROVED`) |
| Superset đọc chẩn đoán/báo cáo chưa xử lý xong | Đọc được | Chỉ đọc qua view tương ứng, lọc theo trạng thái cuối cùng hợp lý (ví dụ `diagnoses` đã `APPROVED`) |
| Bảng mới tạo sau này (ví dụ `rice_varieties`, `notifications` từ P4/P5) | Tự động được `superset_ro` SELECT (do `ALTER DEFAULT PRIVILEGES`) | Vẫn tự động được cấp quyền mặc định — chấp nhận được cho bảng danh mục không nhạy cảm; với bảng nghiệp vụ có trạng thái duyệt, cần `REVOKE` tường minh sau khi tạo view tương ứng |

**Hướng làm**

- Tạo `vw_parcels_approved`, `vw_crop_seasons_approved`, `vw_disease_reports_processed`, `vw_diagnoses_approved` (tên cụ thể rà lại khi code theo đúng tên bảng/cột thật), mỗi view `SELECT` các cột cần cho BI, lọc đúng trạng thái "đã duyệt"/"đã xử lý xong" của từng bảng.
- `REVOKE SELECT ON parcels, crop_seasons, disease_reports, diagnoses, treatment_applications FROM superset_ro`, sau đó `GRANT SELECT ON vw_parcels_approved, ... TO superset_ro`.
- Giữ nguyên `ALTER DEFAULT PRIVILEGES` cho bảng mới nói chung (danh mục như `rice_varieties` không nhạy cảm), nhưng thêm `REVOKE` tường minh ngay sau khi tạo bất kỳ bảng nghiệp vụ có trạng thái duyệt mới trong các phase sau — ghi thành quy ước trong README cho người làm phase sau biết.
- `bi/bootstrap_datasources.py`: đổi 5 dataset đang trỏ bảng gốc sang trỏ view mới; export lại dashboard theo đúng quy trình ở `bi/README.md`.

**Phạm vi ảnh hưởng**

- Module và file chính: migration Flyway (view mới), `backend/.../config/SupersetReadOnlyRoleSeeder.java` (thêm REVOKE/GRANT), `bi/bootstrap_datasources.py`, `bi/README.md`, `bi/dxfarm_dashboard_export.zip`.
- Endpoint, bảng DB, biến môi trường: không có endpoint mới; thêm 4 view trong Postgres.
- Role và client bị ảnh hưởng: chỉ `superset_ro` (Superset); không ảnh hưởng backend hay client khác.
- Tương thích ngược và dữ liệu cũ: dashboard cũ đang trỏ bảng gốc phải đổi dataset sang view, nếu không sẽ lỗi quyền (`permission denied`) ngay sau khi REVOKE — phải làm WP7-02 (export lại dashboard) cùng lúc, không tách rời.

**Kết quả đầu ra**

- `superset_ro` không còn `SELECT` trực tiếp được trên `parcels`/`crop_seasons`/`disease_reports`/`diagnoses`/`treatment_applications` (test bằng kết nối `psql` trực tiếp với role này).
- Dashboard cũ vẫn hiển thị đúng sau khi đổi dataset sang view.

**Phụ thuộc, lưu ý**: phải làm cùng lúc với việc export lại dashboard (WP7-02), không REVOKE rồi để dashboard cũ gãy giữa chừng.

## WP7-02: Thêm chart mới cho dashboard

- Mục tham chiếu: mục 14 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: dashboard có đủ chart theo đề xuất ở mục 14 (báo cáo theo bệnh theo thời gian, điểm nóng dịch bệnh, thửa đang cách ly, lịch thu hoạch dự kiến, tỷ lệ AI đúng/sai).
- Ước lượng: M.

**Hiện trạng đã rà soát**

- Dashboard "DX-Farm — Bản đồ & Trạng thái thửa ruộng" hiện chỉ có 2 chart: bản đồ điểm (deck.gl Scatterplot, dataset `parcels`) và pie vụ mùa theo trạng thái.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Xem xu hướng bệnh theo thời gian | Không có | Chart dạng line/bar theo tuần, dataset `vw_diagnoses_approved` nhóm theo `disease_code` |
| Xem vùng nóng dịch bệnh | Không có | Bản đồ nhiệt (deck.gl Heatmap hoặc tương đương), dùng GPS bắt buộc từ P4 |
| Xem thửa đang cách ly | Không có | Bảng/chart liệt kê thửa có `harvestLockedUntil` còn hiệu lực, dataset `treatment_applications` qua view |
| Xem lịch thu hoạch dự kiến | Không có | Chart theo `expectedHarvestDate` của vụ `ACTIVE` |
| Xem tỷ lệ AI đúng/sai | Không có | Chart so `Diagnosis` do AI tạo với nhãn kỹ sư chốt (dữ liệu từ P6 WP6-04) |

**Hướng làm**

- Build từng chart qua UI Superset (không viết YAML tay, theo đúng quy trình `bi/README.md`), trỏ vào view mới (WP7-01) hoặc dataset đã có.
- Thêm dataset mới nếu cần (ví dụ view riêng cho nhãn huấn luyện AI nếu bảng gốc chưa đủ dùng).
- Export dashboard, đè `bi/dxfarm_dashboard_export.zip`, commit theo đúng 5 bước đã ghi trong `bi/README.md`.

**Phạm vi ảnh hưởng**

- Module và file chính: `bi/dxfarm_dashboard_export.zip`, `bi/README.md` (cập nhật mục "Dashboard mẫu hiện có"), có thể thêm view mới nếu cần (migration).
- Endpoint, bảng DB, biến môi trường: không có endpoint mới; có thể thêm view phục vụ riêng cho chart AI đúng/sai.
- Role và client bị ảnh hưởng: admin/engineer xem dashboard (không farmer, đã chặn từ P2).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng.

**Kết quả đầu ra**

- Dashboard có đủ 5 chart mới cộng 2 chart cũ, tất cả trỏ đúng view đã lọc approved.

**Phụ thuộc, lưu ý**: cần WP7-01 xong trước (view đã tồn tại); nên làm cùng một đợt export để không phải export dashboard nhiều lần.

## WP7-03: Chốt giải pháp nền bản đồ

- Mục tham chiếu: mục 14 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có quyết định rõ ràng về nền bản đồ, ghi lại để trả lời được nếu hội đồng hỏi, không để "treo" như hiện tại.
- Ước lượng: S (quyết định) + tuỳ chọn thêm nếu triển khai.

**Hiện trạng đã rà soát**

- `bi/README.md` đã đánh giá kỹ 4 phương án (Mapbox, OSM các loại, Google Maps, tự host vector tile) và tạm chọn nền trắng, có ghi rõ lý do loại từng phương án (chi phí, nhãn tranh chấp, quy mô không tương xứng).

**Hành vi thay đổi**

Không đổi hành vi nếu giữ nguyên quyết định "nền trắng" — phần này chủ yếu là xác nhận lại quyết định đã có, viết thành câu trả lời chuẩn bị sẵn cho demo, không bắt buộc đổi code.

**Hướng làm**

- Xác nhận lại với người phụ trách: giữ nguyên nền trắng (khuyến nghị, vì đã đánh giá kỹ và các phương án khác đều có chi phí hoặc rủi ro nhãn tranh chấp) hoặc đổi sang một phương án cụ thể nếu có thời gian/ngân sách mới phát sinh.
- Nếu giữ nguyên: chuẩn bị sẵn câu trả lời ngắn cho câu hỏi hội đồng, dẫn đúng lý do đã ghi trong `bi/README.md`.

**Phạm vi ảnh hưởng**

- Module và file chính: không đổi code nếu giữ nguyên quyết định; `bi/README.md` chỉ cần xác nhận lại là đủ.
- Endpoint, bảng DB, biến môi trường: không có.
- Role và client bị ảnh hưởng: không ảnh hưởng.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- Có câu trả lời chuẩn bị sẵn cho câu hỏi hội đồng về nền bản đồ, không bị động khi demo.

**Phụ thuộc, lưu ý**: không phụ thuộc WP khác.

## WP7-04: Nguồn dữ liệu mở thứ hai — Open-Meteo

- Mục tham chiếu: mục 14 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có một nguồn dữ liệu mở dạng sống (không chỉ file tĩnh) phục vụ gợi ý nguy cơ sâu bệnh/lịch thu hoạch, kèm snapshot dự phòng khi API ngoài ngừng hoạt động.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- Hiện có dataset ảnh bệnh lúa (CC BY 4.0) và corpus RAG 9 nguồn — đều là file tĩnh, chưa có nguồn API sống nào.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Xem thời tiết tại vị trí thửa | Không có | Backend gọi Open-Meteo theo `latitude`/`longitude` của thửa (đã bắt buộc từ P4), trả về cho web/mobile |
| Open-Meteo ngừng hoạt động | Không áp dụng (chưa tích hợp) | Dùng snapshot JSON tĩnh đã lưu theo Q1, kèm nhãn rõ "dữ liệu dự phòng, không phải thời gian thực" |

**Hướng làm**

- `service/WeatherService.java` (mới): gọi Open-Meteo REST API (không cần key) theo toạ độ, cache ngắn hạn (ví dụ 1 giờ) để tránh gọi quá nhiều.
- Fallback: nếu gọi API thất bại (timeout, lỗi mạng), đọc từ file snapshot JSON tĩnh (theo Q1) đã chuẩn bị sẵn cho các thửa demo, trả về kèm cờ `isSnapshot = true`.
- Endpoint `GET /api/v1/parcels/{code}/weather` (hoặc gắn vào response chi tiết thửa).

**Phạm vi ảnh hưởng**

- Module và file chính: `service/WeatherService.java` (mới), `controller/ParcelController.java` (endpoint mới), file snapshot JSON trong `data/` hoặc `bi/`.
- Endpoint, bảng DB, biến môi trường: `GET /api/v1/parcels/{code}/weather` (mới); không có bảng mới nếu dùng cache trong bộ nhớ, có thể thêm bảng nếu muốn lưu lịch sử.
- Role và client bị ảnh hưởng: farmer/engineer/admin xem được (không nhạy cảm).
- Tương thích ngược và dữ liệu cũ: thửa chưa có GPS (dữ liệu cũ trước P4, đánh dấu `locationMissing`) không gọi được thời tiết — trả lỗi rõ ràng, không crash.

**Kết quả đầu ra**

- Gọi endpoint thời tiết cho thửa có GPS → trả dữ liệu thật từ Open-Meteo.
- Giả lập Open-Meteo lỗi → trả snapshot kèm cờ rõ ràng, không lỗi 500.

**Phụ thuộc, lưu ý**: cần GPS bắt buộc từ P4 đã có; không phụ thuộc WP khác trong phase này.

## WP7-05: Trang tài liệu và hỏi đáp RAG có trích nguồn

- Mục tham chiếu: mục 21 của `PROJECT_IMPROVEMENTS.md`.
- Mục tiêu: có một màn hình hỏi đáp tài liệu (VietGAP, hướng dẫn...) trả lời kèm trích nguồn, tận dụng corpus RAG đã có.
- Ước lượng: S-M.

**Hiện trạng đã rà soát**

- `ai/app/rag/retriever.py`: `RAGRetriever.retrieve(...)` đã trả về `source_ids` cho mỗi kết quả — khả năng trích nguồn đã có sẵn ở tầng AI, chỉ thiếu giao diện người dùng.
- Endpoint `/disease/knowledge/retrieve` (AI) hiện không yêu cầu xác thực trước P1; sau P1 vẫn chưa bắt buộc `X-Service-Api-Key` theo rà soát ở P1 WP1-03 (cần xác nhận lại khi code phase này, vì P1 đã áp dụng cho `/disease/predict` và `/disease/knowledge/retrieve` — endpoint này nằm trong phạm vi đã sửa).

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Người dùng (farmer/engineer/admin) muốn tra cứu tài liệu VietGAP, hướng dẫn | Không có giao diện, chỉ có API kỹ thuật | Trang `/knowledge` (web), nhập câu hỏi, nhận đoạn trả lời kèm danh sách nguồn (`source_ids` → tên tài liệu) |

**Hướng làm**

- Backend: endpoint passthrough `POST /api/v1/knowledge/ask` (mobile/web không gọi thẳng AI theo nguyên tắc dự án), forward sang AI `/disease/knowledge/retrieve` kèm `X-Service-Api-Key`, map `source_ids` sang tên tài liệu hiển thị được (cần bảng ánh xạ nguồn nếu chưa có, kiểm tra `data/structured` khi code).
- Web: trang `/knowledge`, ô nhập câu hỏi, hiển thị kết quả + danh sách nguồn dạng link/tên tài liệu.
- Không dùng LLM tự sinh câu trả lời ngoài phạm vi nguồn đã ingest (giữ đúng nguyên tắc deterministic-first/LLM không tự kết luận đã áp dụng ở P6).

**Phạm vi ảnh hưởng**

- Module và file chính: `controller/KnowledgeController.java` (mới), `frontend/src/pages/Knowledge.tsx` (mới).
- Endpoint, bảng DB, biến môi trường: `POST /api/v1/knowledge/ask` (mới); không có bảng mới nếu chỉ passthrough.
- Role và client bị ảnh hưởng: mọi role đã đăng nhập (farmer qua mobile nếu muốn mở rộng sau, web cho engineer/admin trong phạm vi phase này).
- Tương thích ngược và dữ liệu cũ: không ảnh hưởng.

**Kết quả đầu ra**

- Nhập câu hỏi về VietGAP/hướng dẫn, nhận câu trả lời kèm ít nhất 1 nguồn trích dẫn xác định được.
- README mục "RAG trả lời có trích nguồn" chuyển sang đã tick.

**Phụ thuộc, lưu ý**: tái dùng nguyên `/disease/knowledge/retrieve` đã có, không viết retriever mới; không phụ thuộc WP khác trong phase này.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `GET /api/v1/parcels/{code}/weather` | Mới | Không |
| Endpoint | `POST /api/v1/knowledge/ask` | Mới | Không |
| Quyền DB | `superset_ro` | `REVOKE SELECT` trên 5 bảng gốc, `GRANT SELECT` trên view mới | Có (dashboard cũ phải đổi dataset) |
| Bảng DB | `vw_parcels_approved`, `vw_crop_seasons_approved`, `vw_disease_reports_processed`, `vw_diagnoses_approved` | View mới | Không |

## Dữ liệu, migration và rollback

- Migration: tạo 4 view qua Flyway (đã có từ P2), kèm `REVOKE`/`GRANT` trong cùng migration hoặc trong `SupersetReadOnlyRoleSeeder`.
- Dữ liệu cũ: không ảnh hưởng, view chỉ là lớp đọc.
- Rollback: `REVOKE`/`GRANT` có thể đảo ngược bằng migration ngược; xoá view không mất dữ liệu gốc.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D10 (kế thừa phase 0) | Open-Meteo + snapshot dự phòng | Xem `phase-0-chot-quyet-dinh-pham-vi/01_PLAN.md` |
| Q1 | Snapshot thời tiết lưu file JSON tĩnh trong repo | Khớp quy ước hiện có, không cần thêm hạ tầng |
