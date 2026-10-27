# Trade-off và rủi ro Phase 2

## 1. Trade-off

| # | Quyết định | Phương án | Chọn | Được | Mất | Chi phí đảo ngược |
|---|---|---|---|---|---|---|
| TO1 | Scope gate hybrid trước retrieval | A: rule + embedding threshold; B: LLM classifier | A | Giải thích được, rẻ, ổn định, dễ test | Có thể bỏ sót câu diễn đạt lạ | Thấp |
| TO2 | Corpus Farm đa topic nhưng giữ disease corpus và Lab riêng | A: thêm song song theo namespace; B: gộp và đổi toàn bộ | A | Hồi quy thấp, không lẫn dữ liệu, rollback dễ | Cần quản lý nhiều loại document/version | Vừa |
| TO3 | Crawl staging rồi duyệt trước atomic ingest | A: duyệt trước ingest; B: tự động ingest | A | Kiểm soát chất lượng, pháp lý và an toàn | Chậm hơn, cần người duyệt | Thấp |
| TO4 | Không lấy thuốc/liều/PHI từ bài crawl tự do | A: Backend facts; B: RAG tự trả | A | Giữ safety contract | Một số câu trả lời phải nói chưa đủ dữ liệu | Cao |

Kết luận: chọn A cho cả bốn quyết định; chỉ đổi sau khi có metric và người phụ trách chốt.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | False reject câu hỏi lúa diễn đạt lạ | Vừa | Vừa | Calibration holdout, từ khóa có trọng số, cho người dùng hỏi lại | Recall in-scope giảm | Giảm ngưỡng có kiểm soát hoặc đưa về `IN_SCOPE_NO_EVIDENCE` |
| R2 | False accept câu ngoài lĩnh vực | Vừa | Cao | Gate trước retrieval, negative set đa dạng, không cho evidence mơ hồ đi ra | Citation có topic lệch | Tắt answer generation, chỉ trả refusal |
| R3 | Nguồn crawl lỗi thời hoặc trái ngữ cảnh Việt Nam | Cao | Cao | Locale, accessed_at, authority level, chuyên gia duyệt | Nội dung khác vụ/vùng | Hạ authority, gắn cảnh báo, không ingest |
| R4 | Vi phạm robots, terms hoặc bản quyền | Vừa | Cao | Allowlist, lưu metadata/license, rate limit, chỉ lưu excerpt cần thiết | 403/robots/điều khoản cấm | Gỡ source khỏi pipeline và xóa artifact theo chính sách |
| R5 | Bài tham khảo chứa khuyến cáo thuốc không còn hiệu lực | Vừa | Cao | Không đưa vào treatment facts; kiểm PPD/nhãn hiện hành | Source cũ hoặc thiếu ngày | Chỉ dùng làm bối cảnh, yêu cầu kỹ sư duyệt |
| R6 | Chunking làm mất ngữ cảnh bảng/heading | Vừa | Vừa | Locator, chunk theo heading, benchmark nhiều kích thước | Recall giảm ở câu hỏi dài | Chạy lại chunker/version mới, rollback |
| R7 | Crawler làm quá tải website | Thấp | Vừa | Rate limit, cache, retry giới hạn, crawl theo lịch | 429/timeout tăng | Dừng job, backoff và dùng snapshot đã có |
| R8 | Prompt injection nằm trong tài liệu | Thấp | Cao | Treat source as data, prompt chỉ cho phép evidence, test adversarial | Output chứa chỉ dẫn ngoài evidence | Chặn document, không ingest, tăng sanitizer |
| R9 | Scope gate dùng chung làm từ chối nhầm câu hỏi Lab | Vừa | Vừa | Classifier nhận namespace, calibration riêng Farm/Lab | Recall Lab giảm dù Farm đạt | Tắt gate riêng cho Lab và giữ retrieval baseline trong thời gian sửa |
| R10 | Farm và Lab truy xuất chéo do filter mặc định | Thấp | Cao | Truyền namespace tường minh, test boundary và log namespace | Citation sai ứng dụng | Chặn response, rollback code/config và reindex nếu cần |
| R11 | Nguồn Farm tiếng Việt nhưng không đủ chất lượng hoặc không cập nhật | Vừa | Cao | Kiểm authority, ngày cập nhật, chuyên gia duyệt và ưu tiên cơ quan chuyên ngành | Nhiều bài sao chép, thiếu tác giả/ngày | Không ingest hoặc hạ trạng thái review |
| R12 | Crawler Lab vượt ra ngoài domain SICT do redirect/link nhúng | Thấp | Cao | Kiểm tra host sau mọi redirect, chặn external URL và lưu allowed_host | Artifact có host ngoài `sict.haui.edu.vn` | Dừng job, xóa artifact staging và chạy lại allowlist |

## 3. Nợ kỹ thuật chấp nhận

- Chưa hỗ trợ mọi cây trồng ngoài lúa; chuyển thành phase riêng khi có taxonomy và nguồn.
- Chưa mở rộng Lab ra ngoài tài liệu chính thức đã được duyệt; nội dung mới cần owner của HaUI xác nhận.
- Chưa xây knowledge graph hoặc câu trả lời nhiều bước; giữ RAG vector để kiểm soát phạm vi.
- Chưa tự động cập nhật định kỳ production; operator phải duyệt trước khi ingest.

## 4. Điều kiện dừng và rollback

- Dừng ingest khi scope precision/recall giảm dưới ngưỡng chốt, có citation sai nguồn, hoặc xuất hiện khuyến cáo thuốc/liều/PHI không được duyệt.
- Rollback bằng cách ingest lại corpus version trước; giữ nguyên raw/processed và log để điều tra.
- Nếu scope gate lỗi production, tắt feature flag để giữ luồng RAG bệnh hiện tại, sau đó sửa trên holdout.

## 5. Tác động ngoài phase

- Lên phase sau: mở rộng cây trồng, cập nhật nguồn theo mùa, human feedback và tối ưu reranking.
- Lên Farm: có thêm citation và topic metadata; diagnosis/rule hiện tại không đổi.
- Lên Lab: có thêm scope contract, nhưng giữ registry, namespace và taxonomy Lab riêng.
- Lên demo/tài liệu: cần quay lại kịch bản hỏi ngoài phạm vi và hỏi canh tác lúa ngoài bệnh.
