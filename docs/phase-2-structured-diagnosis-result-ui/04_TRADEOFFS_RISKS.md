# Trade-off và rủi ro: Phase 2

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Lưu evidence | A: JSONB trong `diagnoses`, B: bảng con, C: cột `recommendation_*` cố định | A | Một cột, giữ đúng evidence tại thời điểm chẩn đoán | Khó query BI theo từng chunk | Thấp |
| TO2 | Nguồn của evidence | A: resolve lúc lưu, B: resolve lúc đọc | A | `toResponse` giữ static, snapshot ổn định | `reviewStatus` của nguồn có thể cũ nếu catalogue đổi sau | Thấp |
| TO3 | Hiển thị evidence | A: danh sách chunk, B: chia theo mục | A | Không đổi ingestion | Chưa nhóm theo triệu chứng, phòng ngừa, lây lan | Vừa |
| TO4 | LLM writer | A: bỏ khỏi diagnosis, B: giữ và hiển thị `explanation` | A | Bớt một lời gọi LLM; writer chỉ trả lại đoạn nguyên văn của evidence nên không thêm giá trị | Không có đoạn tóm tắt | Thấp |

Kết luận: chọn A cho cả bốn quyết định.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dự phòng |
|---|---|---|---|---|---|
| R1 | Người dùng hiểu evidence là chỉ định điều trị | Vừa | Cao | Card riêng có ghi chú "chỉ để tham khảo", thuốc, liều, PHI chỉ lấy từ `treatmentRule` | Ẩn card, giữ treatment card |
| R2 | Evidence quá lớn hoặc sai schema | Thấp | Vừa | Giữ tối đa 5 phần tử, cắt content ở 2000 ký tự | Lưu `[]`, không làm hỏng diagnosis |
| R3 | Hibernate không map JSON sang `Passage` | Thấp | Vừa | Dùng `@JdbcTypeCode(SqlTypes.JSON)` như `WorkflowEvent` và `Workspace` | Đổi sang record riêng trong `model/` |
| R4 | Code đọc `recommendationText` gặp null | Vừa | Thấp | Đã rà: chỉ `DiagnosisCaseSummaryProvider` cần kiểm | Gán chuỗi rỗng khi đọc |
| R5 | Similarity bị hiểu là xác suất mắc bệnh | Vừa | Cao | Nhãn "Độ liên quan của tài liệu" đặt trong từng mục, tách khỏi "Độ tin cậy của mô hình" | Ẩn điểm này |
| R6 | AI và Backend triển khai lệch phiên bản | Thấp | Thấp | Thay đổi tương thích hai chiều: field thiếu hoặc thừa đều không làm hỏng parse | Khôi phục image cũ |

## 3. Nợ kỹ thuật chấp nhận

- Evidence chưa chia theo mục, cần đổi ingestion, ghi vào `docs/BACKLOG.md`.
- Chưa có field cấu trúc cho "Phương pháp áp dụng"; `TreatmentRule.notes` không hiển thị.
- Mobile chưa nhận card tham khảo.
- Class `GroundedRecommendationWriter` còn trong repo nhưng không được diagnosis gọi.

## 4. Điều kiện dừng và rollback

- Dừng và xem lại plan khi evidence làm đổi kết quả validation rule hoặc FARMER thấy dữ liệu PENDING.
- Rollback: web ẩn card, Backend ngừng ghi cột mới; không drop cột `knowledge_evidence`.

## 5. Tác động ngoài phase

- Phase sau về chia evidence theo mục dùng lại cột JSONB này, chỉ thêm field vào mỗi phần tử.
- Không ảnh hưởng Lab, Knowledge page, ingestion hay demo hiện có.
