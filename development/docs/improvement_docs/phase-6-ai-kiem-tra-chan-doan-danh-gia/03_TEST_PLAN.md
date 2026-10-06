# Kế hoạch kiểm thử: Phase 6

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP6-05/03/04; pytest (AI) cho WP6-01; thủ công trên mobile cho WP6-01 phần client.
- Môi trường: seed demo, PostgreSQL 16 Testcontainers và stack local cho kiểm tra HTTP/UI.
- Dữ liệu: ảnh mờ/ảnh rõ; tài khoản `farmer1` (mobile), `engineer1` (web).

## 2. Test case

| ID | Kiểm tra | Kết quả mong đợi | Bằng chứng tự động |
|---|---|---|---|
| T6-01 | Chạy AI lần hai trên cùng báo cáo | Tạo chẩn đoán mới; giữ bản cũ và lấy bản mới làm hiện hành | `rerunPreservesHistoryAndUsesLatestDiagnosis` |
| T6-02 | Gọi AI với pool DB chỉ một connection | Không giữ transaction/connection trong lúc chờ HTTP AI | Callback AI không có transaction/resource; mượn được connection |
| T6-03 | Precheck ảnh mờ | `passed=false`, không tạo báo cáo | `test_phase6.py`, hồi quy sọc/nhiễu/chữ sắc |
| T6-04 | Bỏ qua precheck, nộp ảnh mờ | Server trả 422, không lưu DB/file | `serverRejectsBlurEvenWhenClientSkipsPrecheck` và HTTP thật |
| T6-05 | Kỹ sư yêu cầu ảnh mới | NEEDS_RETAKE, giữ quyết định chẩn đoán cũ | `retakeChangesReportAndPreservesDiagnosisDecision` |
| T6-06 | Farmer gửi ảnh với retakeOf | Chuỗi cũ/mới đúng hộ/vụ; retry không tạo trùng | `linkedRetakeAndIdempotentReplayStayInSameChain` |
| T6-07 | Gợi ý bệnh từ triệu chứng | Chỉ trả mã thuộc danh mục; có evidence/nguồn | Lọc mã lạ, gộp evidence và HTTP RAG thật |
| T6-08 | Chẩn đoán tay khi thiếu treatment rule | Lưu nhãn có cấu trúc, chờ khuyến nghị, không trả 404 | `manualDiagnosisWithoutRuleStoresStructuredEngineerLabel` |

## 3. Hồi quy (không được hỏng)

- [x] Khóa PHI (P1) vẫn chặn đúng sau khi đổi quan hệ `Diagnosis`-`DiseaseReport` thành 1-N.
- [x] Danh sách báo cáo lấy chẩn đoán hiện hành bằng truy vấn theo nhóm.
- [x] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [x] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T6-01 đến T6-08 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test và demo luồng nghiệp vụ.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).

## 6. Kết quả kiểm chứng mới nhất

| Phần | Kết quả | Phạm vi |
|---|---|---|
| Backend | 74 test đạt; không fail/error/skip | PostgreSQL 16 Testcontainers, T6-01/02/04/05/06/08, PHI, migration V1–V5 và phân quyền |
| AI | 48 test đạt; Ruff check/format đạt | CV, RAG, auth; ảnh mờ có sọc/nhiễu/chữ sắc; ảnh rõ sau nén và ở hai độ phân giải |
| Web | TypeScript/build đạt | Chromium xác nhận history tải lại khi approvalStatus đổi trên cùng ID |
| Mobile | TypeScript đạt; bundle Android HTTP 200 | Expo Go/Metro; chưa xác nhận đầy đủ camera/offline/retake trên điện thoại |
| HTTP thật | Đạt precheck và nộp ảnh trực tiếp | FARMER, client mobile, Authorization Code + PKCE |

**Hồi quy ảnh mờ:** thuật toán cũ cho qua 5 ca có chữ/viền sắc, sọc màn hình hoặc nhiễu; thuật toán mới chặn chúng.
Ảnh báo cáo #9 lúc 21:34 có điểm mới 9.613 và bị từ chối; ảnh lá lúa #10 đạt 30.113 và được chấp nhận (ngưỡng 20).
Bỏ qua precheck và nộp #9 trực tiếp trả 422; số báo cáo của vụ giữ nguyên 6 → 6.

RAG qua Backend trả 6 mã hợp lệ, có evidence/nguồn. Phản hồi duyệt trong kiểm tra history được mock ở trình duyệt để không đổi dữ liệu demo.
Unit/integration dùng mock ở ranh giới HTTP AI; các kiểm tra này chứng minh logic, không đo độ chính xác mô hình.

Đã đối chiếu WP6-01/03/04/05 với quy ước AI và skill verification-before-completion, RAG, LangGraph, code-quality, JPA, Spring Boot.
Truy vấn JPA dùng text block; kiểm ảnh dùng chung logic, không thêm dependency/framework.
Code giữ confidence 0.70; container test đang hạ tạm xuống 0.00 theo yêu cầu của người dùng, độc lập với ngưỡng ảnh mờ.

## 7. Xác nhận trên điện thoại

- [ ] Camera: ảnh mờ bị chặn trước khi gửi; ảnh lá lúa rõ được nhận và xem được trên web.
- [ ] Offline: ảnh chờ được gửi lại khi có mạng; ảnh mờ bị từ chối, không tạo trùng.
- [ ] Retake: kỹ sư yêu cầu ảnh mới; farmer gửi lại và xem được chuỗi báo cáo.

Chỉ đóng phần demo điện thoại sau khi ba mục trên được xác nhận.
