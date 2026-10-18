# Kế hoạch kiểm thử: Phase 2

Quy ước dự án: Claude chỉ chạy compile và đọc diff.
Các case dưới đây do người dùng chạy thủ công; cột "Kết quả" để trống đến khi chạy.

## 1. Chiến lược

- Mức kiểm thử: compile, kiểm tra thủ công trên web, đối chiếu JSON response.
- Môi trường: stack compose hiện có với seed demo và corpus RAG hiện có, không re-ingest.
- Tài khoản: một ENGINEER, một FARMER có báo cáo bệnh.
- Dữ liệu: một ảnh bệnh có rule và RAG, một diagnosis tạo trước migration.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | Contract AI | Gọi `POST /api/v1/disease/diagnose` với ảnh có evidence | `rag_evidence[]` có `document_title`; `recommendation` không có `summary` | | |
| T2-02 | WP2-01 | RAG rỗng | Tắt RAG hoặc dùng bệnh không có chunk | `rag_evidence = []`, `selected_rule_id` vẫn đúng | | |
| T2-03 | WP2-02 | Migration | Khởi động Backend trên DB có diagnosis cũ | Ứng dụng lên, diagnosis cũ giữ `recommendationText`, `knowledgeEvidence = []` | | |
| T2-04 | WP2-02 | Lưu và reload | Chẩn đoán AI rồi gọi lại `GET` diagnosis | `knowledgeEvidence` đúng nội dung, thứ tự và nguồn; `recommendationText = null` | | |
| T2-05 | WP2-02 | Authority | AI trả rule ID lạ hoặc version cũ kèm evidence | Backend vẫn từ chối như trước | | |
| T2-06 | WP2-02 | Phân quyền | FARMER đọc diagnosis PENDING, REJECTED, APPROVED; ENGINEER đọc PENDING | FARMER chỉ thấy `knowledgeEvidence` khi APPROVED; ENGINEER thấy đủ | | |
| T2-07 | WP2-03 | Web có evidence | Mở diagnosis có rule và evidence | Ba khối riêng; nhãn "Độ tin cậy của mô hình", "Nguồn phác đồ", "Độ liên quan của tài liệu"; thuốc, liều, PHI chỉ ở treatment card | | |
| T2-08 | WP2-03 | Web rỗng và hẹp | Mở diagnosis cũ hoặc không có evidence; thu cửa sổ về 640 px | Không có card tham khảo, các nút vẫn hoạt động; layout không tràn | | |

## 3. Hồi quy

- [ ] Low confidence vẫn dừng trước Backend facts và RAG.
- [ ] Chẩn đoán thủ công vẫn tạo và đọc được.
- [ ] Duyệt, từ chối, yêu cầu chụp lại và ghi nhận phun hoạt động.
- [ ] Liều ngoài khoảng bị từ chối; khóa thu hoạch vẫn lấy PHI từ treatment rule.
- [ ] Thông báo và tóm tắt case (`DiagnosisCaseSummaryProvider`) không lỗi khi `recommendationText` null.
- [ ] Knowledge page và gợi ý triệu chứng không đổi.

## 4. Tiêu chí đạt

- T2-01 đến T2-08 và hồi quy đạt.
- `./mvnw compile`, frontend build và `git diff --check` đạt.
- Các tiêu chí ở `00_OVERVIEW.md` mục 7 có bằng chứng (ảnh web có và không có evidence).

## 5. Bằng chứng và báo lỗi

- Lưu ảnh web ở hai trạng thái và JSON response của T2-04.
- Lỗi ngoài phạm vi ghi vào `docs/BACKLOG.md`, không sửa kèm.
