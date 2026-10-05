# Kế hoạch kiểm thử: Phase 6

Theo D6 (phase 0): chỉ test case ảnh hưởng logic/an toàn hệ thống, không coverage đầy đủ happy path.
WP6-06 (báo cáo đánh giá AI) không phải test code, xem `02_CHECKLIST.md` mục riêng.

## 1. Chiến lược

- Mức kiểm thử áp dụng: unit/integration (JUnit) cho WP6-05/03/04; pytest (AI) cho WP6-01; thủ công trên mobile cho WP6-01 phần client.
- Môi trường: clone sạch + seed demo.
- Dữ liệu và tài khoản test: 1 báo cáo có ảnh mờ, 1 báo cáo có ảnh rõ; engineer1.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T6-01 | WP6-05 | Happy path | Gọi chẩn đoán AI lần 2 trên báo cáo đã có chẩn đoán | Thành công, không ném lỗi "đã có chẩn đoán" | | |
| T6-02 | WP6-05 | Hiệu năng/logic | Đo thời gian giữ connection DB trong lúc gọi AI | Không giữ suốt thời gian chờ AI | | |
| T6-03 | WP6-01 | Poka-yoke | Gửi ảnh mờ qua `precheck` | "Không đạt", không tạo báo cáo | | |
| T6-04 | WP6-01 | Poka-yoke | Bỏ qua precheck, nộp báo cáo thật với ảnh mờ | Server vẫn từ chối | | |
| T6-05 | WP6-03 | Poka-yoke | Kỹ sư yêu cầu chụp lại | `DiseaseReport.status = NEEDS_RETAKE`, không đổi `Diagnosis.approvalStatus` | | |
| T6-06 | WP6-03 | Đầy đủ | Farmer gửi báo cáo mới kèm `retakeOf` | Xem được chuỗi báo cáo cũ → mới liên tục | | |
| T6-07 | WP6-04 | Poka-yoke | Nhập triệu chứng bất kỳ | Danh sách gợi ý chỉ chứa mã trong danh mục 6 bệnh, không có mã lạ | | |
| T6-08 | WP6-04 | Hồi quy | Nhập tay (`diseaseCode` trực tiếp) trên database sạch chưa có treatment rule | Không còn 404 | | |

## 3. Hồi quy (không được hỏng)

- [ ] Khóa PHI (P1) vẫn chặn đúng sau khi đổi quan hệ `Diagnosis`-`DiseaseReport` thành 1-N.
- [ ] `listByCropSeason` không bị N+1 nặng hơn sau khi đổi sang lấy chẩn đoán mới nhất (đã ghi nhận N+1 cũ trong CHANGES_OVERVIEW, không làm nặng thêm).
- [ ] Gửi báo cáo với `Idempotency-Key` cũ không tạo báo cáo trùng.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` vẫn lên đủ dịch vụ healthy.

## 4. Tiêu chí đạt của phase (exit criteria)

- T6-01 đến T6-08 đạt.
- Mọi case hồi quy ở mục 3 đạt.
- File báo cáo đánh giá AI (WP6-06) tồn tại và đủ các phần yêu cầu.
- Không còn lỗi nghiêm trọng mở; lỗi còn lại đã ghi ở `04_TRADEOFFS_RISKS.md`.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log test, file báo cáo đánh giá AI, ảnh minh hoạ false positive/negative.
- Lỗi phát hiện: tạo issue GitHub, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
