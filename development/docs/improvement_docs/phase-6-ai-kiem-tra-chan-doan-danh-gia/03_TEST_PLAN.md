# Kế hoạch kiểm thử: Phase 6

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP6-05/03/04; pytest (AI) cho WP6-01; thủ công trên mobile cho WP6-01 phần client.
- Môi trường: clone sạch + seed demo.
- Dữ liệu và tài khoản test: 1 báo cáo có ảnh mờ, 1 báo cáo có ảnh rõ; engineer1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T6-01 | WP6-05 | Happy path | Gọi chẩn đoán AI lần 2 trên báo cáo đã có chẩn đoán | Thành công, không ném lỗi "đã có chẩn đoán" | Codex, 06/10/2026 | Pass — Phase6IntegrationTest.rerunPreservesHistoryAndUsesLatestDiagnosis |
| T6-02 | WP6-05 | Hiệu năng/logic | Đo thời gian giữ connection DB trong lúc gọi AI | Không giữ suốt thời gian chờ AI | Codex, 06/10/2026 | Pass — datasource pool 1, AI callback không có transaction/resource và mượn connection được |
| T6-03 | WP6-01 | Poka-yoke | Gửi ảnh mờ qua `precheck` | "Không đạt", không tạo báo cáo | Codex, 06/10/2026 | Pass — test_phase6.py: ảnh mờ trả passed=false, không cần model |
| T6-04 | WP6-01 | Poka-yoke | Bỏ qua precheck, nộp báo cáo thật với ảnh mờ | Server vẫn từ chối | Codex, 06/10/2026 | Pass — serverRejectsBlurEvenWhenClientSkipsPrecheck; DB/file không được tạo |
| T6-05 | WP6-03 | Poka-yoke | Kỹ sư yêu cầu chụp lại | `DiseaseReport.status = NEEDS_RETAKE`, không đổi `Diagnosis.approvalStatus` | Codex, 06/10/2026 | Pass — retakeChangesReportAndPreservesDiagnosisDecision |
| T6-06 | WP6-03 | Đầy đủ | Farmer gửi báo cáo mới kèm `retakeOf` | Xem được chuỗi báo cáo cũ → mới liên tục | Codex, 06/10/2026 | Pass — linkedRetakeAndIdempotentReplayStayInSameChain |
| T6-07 | WP6-04 | Poka-yoke | Nhập triệu chứng bất kỳ | Danh sách gợi ý chỉ chứa mã trong danh mục 6 bệnh, không có mã lạ | Codex, 06/10/2026 | Pass — RAG candidates lọc mã lạ, gộp bằng chứng tốt nhất |
| T6-08 | WP6-04 | Hồi quy | Nhập tay (`diseaseCode` trực tiếp) trên database sạch chưa có treatment rule | Không còn 404 | Codex, 06/10/2026 | Pass — manualDiagnosisWithoutRuleStoresStructuredEngineerLabel |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng sau khi đổi quan hệ `Diagnosis`-`DiseaseReport` thành 1-N.
- [x] `listByCropSeason` không bị N+1 nặng hơn sau khi đổi sang lấy chẩn đoán mới nhất (đã ghi nhận N+1 cũ trong CHANGES_OVERVIEW, không làm nặng thêm).
- [x] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [x] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T6-01 đến T6-08 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test và demo luồng nghiệp vụ.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).

## Kết quả thực hiện 06/10/2026

- Backend sau cập nhật `Khuong/main` tại `f52f564`: **74 test đạt**, 0 fail/error/skip, có PostgreSQL 16 Testcontainers và schema Hibernate validate sau Flyway V1–V5.
- AI: **34 test đạt**, gồm 6 case P6 sau khi bỏ công cụ đánh giá.
- Web: TypeScript và `npm run build` đạt.
- Mobile: TypeScript và Expo export Android/Hermes đạt; artifact local `/tmp/dxfarm-phase6-parent-mobile-export`.
- Seed theo nhánh cha mới: **4 test đạt**.
- Cấu hình Compose sau cập nhật đạt `config --quiet` với env thử nghiệm riêng.
- Kiểm tra bổ sung V4 → V5 đạt: trạng thái và event workflow cũ được giữ nguyên, báo cáo có chẩn đoán được backfill `PROCESSED`.
- Ruff cho Python trong diff và `git diff --check` đạt.
- Unit/integration dùng mock ở ranh giới HTTP AI; đây là bằng chứng logic, không phải accuracy checkpoint hay chất lượng retrieval trên corpus production.

Kiểm tra transaction dùng pool chỉ 1 connection: callback AI không có transaction/resource DB, vẫn lấy được connection từ cùng datasource.
Chẩn đoán lại và danh sách báo cáo lấy id mới nhất; test PHI xác nhận thuốc từ chẩn đoán trước vẫn chặn thu hoạch.
Test PostgreSQL xác nhận nhãn kỹ sư được lưu có cấu trúc khi chưa có treatment rule, cùng test baseline DB cũ.
Forwarding multipart được kiểm bằng HTTP server thật, có MIME type ảnh và API key; lỗi key dịch vụ được ánh xạ 503.

Trước cập nhật nhánh cha, đã chạy Compose từ snapshot sạch với DB/volume mới và secret thử nghiệm; các dịch vụ chạy đều healthy, job init/ingest hoàn tất.
Lần đó đã thử HTTP thật qua OIDC/PKCE cho precheck/nộp ảnh, nhập tay thiếu rule, history, phân quyền, retake và RAG 6 mã có nguồn. Chưa dựng lại toàn bộ stack sau cập nhật nhánh cha.
Chưa chạy demo điện thoại thật; giữ tiêu chí này chưa đạt cho tới khi có bằng chứng.
Xem [bàn giao](./05_IMPLEMENTATION.md).

Đường AI thật với ảnh synthetic trả 422 do confidence chưa đạt; không tự ghi kết quả “healthy” hoặc duyệt.
Lượt này chỉ kiểm tra runtime/triage, không phải phép đo accuracy độc lập.
