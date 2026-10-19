# Kế hoạch kiểm thử: Phase 2

Chưa ghi case nào đạt trong giai đoạn lập kế hoạch.
Chỉ cập nhật kết quả sau khi chạy trên code và migration của phase.

## 1. Chiến lược

- Mức kiểm thử: unit/contract AI, integration Backend/Flyway, frontend build và kiểm tra thủ công giao diện.
- Môi trường: database sạch và database có diagnosis cũ; dùng RAG data hiện có, không re-ingest.
- Dữ liệu: AI response có ba evidence, evidence rỗng, rule hợp lệ, rule ID/version sai và diagnosis legacy.
- Tài khoản: ENGINEER/ADMIN và FARMER để kiểm tra masking hiện có.
- Hồi quy chỉ tập trung vào diagnosis, duyệt/từ chối, ghi nhận phun và PHI.
- Không chạy đánh giá ranking, Lab E2E hoặc mobile build vì các phần đó không thay đổi.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T2-01 | WP2-01 | AI contract | Chạy diagnosis khi retriever trả evidence | Response có content/source/similarity và đầy đủ chunk/document metadata |  |  |
| T2-02 | WP2-01 | Không summary | Kiểm recommendation của diagnosis mới | Không có đoạn ghép CV, rule và RAG; selected rule/approval vẫn đúng |  |  |
| T2-03 | WP2-01 | RAG unavailable | Cho retriever lỗi hoặc trả rỗng | Response có `rag_evidence=[]`; rule path vẫn hoàn tất |  |  |
| T2-04 | WP2-02 | Migration sạch | Chạy Flyway trên database sạch | Column JSONB/default/nullable được tạo và ứng dụng khởi động |  |  |
| T2-05 | WP2-02 | Dữ liệu cũ | Migrate database có diagnosis cũ | Text cũ giữ nguyên, evidence mặc định rỗng, đọc lịch sử không lỗi |  |  |
| T2-06 | WP2-02 | Persistence | Lưu AI diagnosis có evidence rồi reload | Evidence và thứ tự không đổi; source metadata được resolve |  |  |
| T2-07 | WP2-02 | Validation | Gửi evidence vượt giới hạn hoặc field bắt buộc không hợp lệ | Backend từ chối an toàn và không lưu diagnosis hỏng |  |  |
| T2-08 | WP2-02 | Rule authority | AI trả rule ID lạ hoặc version cũ kèm evidence hợp lệ | Backend vẫn từ chối như trước; evidence không ảnh hưởng rule validation |  |  |
| T2-09 | WP2-02 | Phân quyền | FARMER đọc PENDING/REJECTED/APPROVED; ENGINEER đọc PENDING | Evidence tuân theo masking hiện có |  |  |
| T2-10 | WP2-03 | Web có evidence | Mở diagnosis có treatment rule và evidence | Confidence, treatment card và knowledge card hiển thị riêng, đúng payload |  |  |
| T2-11 | WP2-03 | Web evidence rỗng | Mở diagnosis không có evidence | Knowledge card không xuất hiện; treatment card và thao tác vẫn hoạt động |  |  |
| T2-12 | WP2-03 | Không trùng | Kiểm màn hình có rule, evidence và text legacy trong payload | Web không render text legacy; thuốc/liều/PHI chỉ nằm trong treatment card |  |  |
| T2-13 | WP2-03 | Responsive/a11y | Kiểm desktop, 640 px và màn hình nhỏ | Không tràn; heading, progress và link nguồn có accessible name phù hợp |  |  |
| T2-14 | WP2-03 | Format và contract | Đối chiếu payload mẫu và kiểm PENDING, APPROVED, REJECTED, low-confidence, healthy, thiếu rule/evidence | Đúng tên key, kiểu/nullable và thứ tự/nhãn trong `05_EXPECTED_OUTPUT_FORMAT.md`; confidence khác similarity; nguồn rule khác nguồn tài liệu; không parse `notes` để tạo phương pháp |  |  |

## 3. Hồi quy bắt buộc

**AI/Backend**

- [ ] Low confidence vẫn dừng trước Backend facts và RAG theo contract hiện có.
- [ ] Backend reload disease và treatment rule từ database.
- [ ] Chẩn đoán thủ công vẫn tạo và đọc được.
- [ ] Lịch sử diagnosis và concurrency guard vẫn hoạt động.
- [ ] RAG lỗi không làm thất bại diagnosis có rule hợp lệ.

**Web Farm**

- [ ] Confidence/progress và treatment card hiện có không đổi dữ liệu hoặc layout ngoài phần cần thiết.
- [ ] Điều kiện nút duyệt, từ chối, yêu cầu chụp lại và ghi nhận phun không đổi.
- [ ] PENDING/APPROVED/REJECTED vẫn hiển thị đúng vai trò.
- [ ] Content evidence được render như text, không phải HTML.
- [ ] Người dùng phân biệt được “Độ tin cậy của mô hình” với “Độ liên quan của tài liệu”.
- [ ] Low-confidence, healthy, thiếu rule và evidence rỗng có thông điệp/ẩn hiện đúng đặc tả format.

**Nghiệp vụ an toàn**

- [ ] Liều ngoài khoảng vẫn bị từ chối.
- [ ] PHI và ngày khóa thu hoạch vẫn lấy từ treatment rule.
- [ ] Evidence không được dùng để tạo hoặc ghi đè treatment application.

## 4. Tiêu chí đạt

- T2-01 đến T2-14 và các hồi quy bắt buộc đạt.
- Migration chạy được với database sạch và dữ liệu cũ.
- Không có code thay đổi ingestion, corpus, ranking, Lab hoặc mobile.
- AI/Backend contract khớp OpenAPI; frontend build và `git diff --check` đạt.
- Các tiêu chí trong `00_OVERVIEW.md` mục 7 có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Lưu output test/build không chứa secret trong artifact hoặc đường dẫn được nhóm thống nhất.
- Chụp web ở hai trạng thái: có evidence và evidence rỗng.
- Lỗi ngoài phạm vi được ghi issue/backlog thay vì sửa kèm trong phase.
