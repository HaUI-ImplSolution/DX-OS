# Việc chưa đạt sau P0 đến P8

Tổng hợp ngắn các việc còn lại của 8 phase đã làm.
Chi tiết, lý do và cách xử lý nằm ở file được trỏ tới ở mỗi mục.
Kế hoạch refactor Open-Core và đa ứng dụng nằm ở [`../refactor_docs/README.md`](../refactor_docs/README.md).

## Cần làm trước khi nộp bài

| Việc | Nơi theo dõi |
|---|---|
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 (mới có dàn ý) | `phase-8-*/02_CHECKLIST.md` WP8-06-DOC-03, 04; `development/docs/submission/` |
| Video demo 3-5 phút, rehearsal ít nhất 1 lần | `phase-8-*/02_CHECKLIST.md` WP8-06-DOC-05, 06 |
| Tag `v0.1.0` và bản chốt, bật GitHub Issues, tạo issue mẫu, chuyển repo sang public | `phase-8-*/02_CHECKLIST.md` WP8-03-DOC-01, 03 |
| Quét lại gitleaks trước khi chuyển public | `phase-8-*/02_CHECKLIST.md` mục "Đóng phase" |
| Chốt ngày hạn nộp thật | `phase-8-*/00_OVERVIEW.md` mục 11 |
| Đóng P8: đủ bằng chứng cho DoD, cập nhật trạng thái ở `improvement_docs/README.md` | `phase-8-*/02_CHECKLIST.md` mục "Đóng phase" |

## Phụ thuộc người ngoài

| Việc | Ảnh hưởng | Nơi theo dõi |
|---|---|---|
| Chuyên gia duyệt treatment rule cho các bệnh còn lại (đốm nâu, Help 400SC đang `IN_REVIEW`) | Đạo ôn và khô vằn đã có rule được duyệt; bệnh khác chỉ ở trạng thái chỉ xem xét, không có liều và PHI | `phase-8-*/00_OVERVIEW.md` Q2; `data/README.md` |
| Kỹ sư nông nghiệp xác nhận mốc tháng loại vụ và danh mục giống (đang `IN_REVIEW`) | Gợi ý loại vụ và ngày thu hoạch dựa trên số tạm | `phase-4-*/04_TRADEOFFS_RISKS.md` R1; `legacy_docs/PARCEL_SEASON_CODES.md` |
| Người phụ trách xác nhận nền bản đồ Superset (đang nền trắng) | Dashboard bản đồ | `phase-7-*/02_CHECKLIST.md` WP7-03-DOC-01 |
| Ảnh lá lúa thật cho `seed_demo.py --images` | Demo chẩn đoán AI có ý nghĩa; chart "Tỷ lệ AI đúng/sai" có dữ liệu | `phase-8-*/00_OVERVIEW.md` mục 11 |

## Chưa kiểm thử

| Việc | Nơi theo dõi |
|---|---|
| Thời tiết: gọi endpoint thật và giả lập Open-Meteo lỗi (T7-04, T7-05) | `phase-7-*/02_CHECKLIST.md` WP7-04-TEST-01, 02 |
| Test tích hợp phạm vi dữ liệu giữa hai nông hộ và idempotency thửa/vụ | `phase-8-*/00_OVERVIEW.md` mục 11 |
| Test tự động cho vòng đời vụ mùa, thông báo, yêu cầu chụp lại (đã kiểm tay) | `phase-5-*/03_TEST_PLAN.md`; `phase-6-*/04_TRADEOFFS_RISKS.md` mục 3 |
| Dashboard Superset kiểm tay, chưa có test tự động | `README.md` bảng mức hoàn thiện |

## Giới hạn đã biết của AI và dữ liệu

| Việc | Nơi theo dõi |
|---|---|
| Không có báo cáo đánh giá AI (test set độc lập, chỉ số theo lớp); ngưỡng `0.70` chưa đo lại | `phase-6-*/04_TRADEOFFS_RISKS.md` mục 3 |
| Crop classifier còn nhận nhầm ngô, cỏ, lúa mì thành lúa; chưa nghiệm thu khả năng chặn mọi ảnh ngoài miền; build toàn stack từ đầu trên CI chưa kiểm chứng | `phase-6-*/04_TRADEOFFS_RISKS.md` TO7; `pending_docs/CV_RICE_IMAGE_VALIDATION_SUMMARY.md` |
| Ngưỡng độ nét 20 và độ sáng 20-245 chưa hiệu chuẩn bằng ảnh ngoài ruộng | `phase-6-*/04_TRADEOFFS_RISKS.md` R3 |
| Nộp báo cáo cần AI service chạy, chưa có đường nhận ảnh chưa kiểm | `phase-6-*/04_TRADEOFFS_RISKS.md` R4 |
| Kho RAG chỉ có 6 tài liệu bệnh lúa, chưa có VietGAP hay quy trình canh tác | `phase-7-*/00_OVERVIEW.md` mục 11 |
| Ngưỡng `knowledge.min-score` 0.40 và giới hạn 60 câu hỏi/phút/IP chưa hiệu chỉnh | `phase-7-*/00_OVERVIEW.md` mục 11 |
| `vw_ai_diagnosis_outcomes` nhận biết chẩn đoán AI qua `symptom_text` và `model_version`, dễ sai; nên có cột nguồn trong `diagnoses` | `phase-7-*/00_OVERVIEW.md` mục 11 |
| Snapshot thời tiết chỉ 5 điểm ĐBSCL, chụp ngày 07/10/2026 | `phase-7-*/00_OVERVIEW.md` mục 11 |
| `knowledge-sources.json` sao tay từ `data/rag/source_registry.json`, chưa có kiểm tra lệch | `phase-7-*/00_OVERVIEW.md` mục 11 |

## Chức năng chưa có

| Việc | Nơi theo dõi |
|---|---|
| Push thật (FCM, UnifiedPush); hộp thông báo chỉ kéo về khi mở app | `phase-5-*/00_OVERVIEW.md` mục 11; P10 |
| Hộp thông báo cho engineer và admin; mobile chỉ tải 30 thông báo mới nhất | `phase-5-*/04_TRADEOFFS_RISKS.md` mục 3 |
| Phun lặp lại trên cùng một chẩn đoán; SLA nhắc kỹ sư khi bản khai chờ lâu | `phase-5-*/04_TRADEOFFS_RISKS.md` mục 3 |
| Thu hoạch thay hộ không có trong timeline workflow | `phase-5-*/04_TRADEOFFS_RISKS.md` mục 3 |
| Thời tiết chưa có màn hình web hay mobile, chưa có chart thời tiết; trang `/knowledge` chưa có trên mobile | `phase-7-*/00_OVERVIEW.md` mục 11 |
| Phạm vi phụ trách theo khu vực cho engineer | `phase-2-*/00_OVERVIEW.md` (D9) |
| Tài khoản `engineer1` vào Superset thấy danh sách dashboard rỗng, chưa rõ nguyên nhân | `phase-7-*/00_OVERVIEW.md` mục 11 |

## Cấu hình và vận hành

| Việc | Nơi theo dõi |
|---|---|
| Các tham số `app.*`, `knowledge.*`, `storage.presign-ttl` chỉ đổi được trong `application.yaml`, chưa truyền qua `deploy/.env` | `phase-5-*/04_TRADEOFFS_RISKS.md` mục 3 |
| Bảng danh mục mới phải thêm tay vào `READABLE_RELATIONS` của `SupersetReadOnlyRoleSeeder` | `phase-7-*/00_OVERVIEW.md` mục 11 |
| Mobile viết cứng ngưỡng GPS 30 m | `mobile/src/config.ts` |
| Export dashboard Superset chưa có `query_context` cho từng chart | `phase-7-*/00_OVERVIEW.md` mục 11 |

## Backlog (P10)

Carbon/AWD và IoT, polygon ruộng với PostGIS, GraphRAG, Mattermost hai chiều, icon và splash mobile, domain với HTTPS cho APK, scale thật.
Chi tiết ở [`phase-10-de-sau/00_OVERVIEW.md`](./phase-10-de-sau/00_OVERVIEW.md).

## Trạng thái từng phase

| Phase | Còn lại |
|---|---|
| P0 đến P4 | Không còn việc mở |
| P5 | Các mục "Chức năng chưa có" về thông báo, phun thuốc, thu hoạch |
| P6 | Các mục "Giới hạn đã biết của AI" |
| P7 | Thời tiết chưa test, nền bản đồ, các mục chưa chốt ở `00_OVERVIEW.md` mục 11 |
| P8 | Mục "Cần làm trước khi nộp bài" và Q2 |
