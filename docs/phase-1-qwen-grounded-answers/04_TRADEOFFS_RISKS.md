# Trade-off và rủi ro: Phase 1

## 1. Trade-off

| # | Quyết định | Chọn | Được | Mất | Đảo ngược |
|---|---|---|---|---|---|
| TO1 | Nơi điều phối hỏi đáp | Service app dùng KnowledgeGateway rồi AiGateway có sẵn | Core giữ nguyên, trích dẫn giữ nghĩa | Thêm một lượt gọi AI và DTO nhỏ tại mỗi app | Thấp |
| TO2 | Định dạng câu trả lời | Schema cố định, UI quyết định nhãn/bố cục | Dễ đọc và kiểm hợp đồng | Ít linh hoạt hơn văn bản tự do | Thấp |
| TO3 | Dữ liệu chẩn đoán | Cột JSONB nullable riêng ở Farm | Nội dung đúng phiên bản đã xem/duyệt | Một migration và mapper quyền cần kiểm kỹ | Vừa |
| TO4 | Client đã phát hành | Opt-in qua query, giữ passages/DTO cũ | Client cũ tiếp tục dùng luồng hiện tại | Cần kiểm hai chế độ và payload cũ | Thấp |
| TO5 | Chất lượng với model hiện tại | Giữ Qwen2.5:1.5b và dependency | Không mở rộng nâng cấp/hạ tầng | Có thể chưa đạt tỷ lệ generated/readability mong muốn | Thấp |

TO1–TO2 theo hướng đã thống nhất với chủ dự án.
TO3–TO4 là đề xuất cần duyệt cùng kế hoạch trước khi code.

## 2. Rủi ro

| # | Rủi ro | Xác suất | Ảnh hưởng | Giảm thiểu | Dấu hiệu sớm | Dự phòng |
|---|---|---|---|---|---|---|
| R1 | Đúng JSON nhưng sai nghĩa, bịa nguồn hoặc chỉ dẫn | Vừa | Cao | Giới hạn context, kiểm nguồn/đầu ra và đánh giá nội dung riêng | Câu không được evidence hỗ trợ | Trả excerpt có nhãn, giữ kỹ sư duyệt |
| R2 | Văn bản dài, lặp, khó đọc như luồng cũ | Vừa | Cao | Schema cố định, giới hạn, UI dựng mục và nguồn mở rộng | Summary dài, lặp giữa steps/note | Từ chối đầu ra, không cắt cụt để ép đạt |
| R3 | Model nhỏ không tuân thủ schema hoặc giới hạn | Vừa | Cao | Kiểm capability trên đúng version trước khi nối app | Nhiều invalid/fallback dù evidence đủ | Dừng, báo giới hạn; không tự đổi model/dependency |
| R4 | Qwen chậm, tranh CPU/RAM với CV/embedding | Cao | Cao | Một lần gọi, giới hạn sinh/timeout, đo cold/warm và đồng thời | Nhiều timeout, tăng thời gian nghiệp vụ | Bỏ opt-in, về luồng hiện tại |
| R5 | Lộ giải thích chẩn đoán trước duyệt | Vừa | Cao | Kiểm mọi mapper report/history/mobile, dùng nguyên quyền hiện có | FARMER nhận field mới trong PENDING | Chặn bàn giao cho tới khi test quyền đạt |
| R6 | Nội dung thay đổi khi xem lại hoặc sai diagnosis | Vừa | Cao | Lưu snapshot riêng, không gọi model lúc đọc, giữ guard rerun/version | Sau duyệt nội dung khác lúc kỹ sư xem | Giữ bản lưu, từ chối ghi khi xung đột |
| R7 | Làm đổi ý nghĩa Core KnowledgePassage | Thấp | Cao | DTO answer thuộc app, diff Core bằng không | Văn bản sinh xuất hiện trong passages.content | Dừng và sửa lại điểm nối trong app |
| R8 | Dùng điểm retrieval làm bằng chứng chất lượng sinh | Vừa | Vừa | Chấm nội dung riêng, ghi trạng thái pilot và generated/fallback | Công bố Recall thành accuracy câu trả lời | Sửa cách báo cáo, giữ artifact đối chiếu |
| R9 | Payload mới làm hỏng client cũ hoặc rollback | Vừa | Cao | Trường optional, query opt-in, migration thêm cột | Lỗi deserialization/null hoặc app cũ không lên | Client/handler về chế độ cũ, giữ schema mở rộng |
| R10 | Lỗi Ollama vẫn chạy sinh sau timeout HTTP | Vừa | Vừa | Kiểm hành vi hủy request và tài nguyên, không tự retry | CPU tiếp tục tăng sau request lỗi | Giới hạn sinh, dừng nghiệm thu tải nếu không kiểm soát được |
| R11 | Dùng prompt chung khiến chatbot tự chẩn đoán hoặc phần chẩn đoán nói như đã quan sát ảnh | Vừa | Cao | Profile riêng, đúng ngữ cảnh được cấp, kiểm cặp câu hỏi/report và nội dung nguồn | Câu khẳng định không có dữ kiện, trộn bệnh từ lượt khác | Từ chối phần sinh, giữ CV/rule/evidence và mode dự phòng |

## 3. Nợ kỹ thuật chấp nhận

- Hỏi đáp sinh chỉ trên web Farm/Lab, từng lượt; không nâng cấp chatbot mobile hoặc thêm lịch sử hội thoại/streaming.
- Chưa thêm model kiểm chứng, reranker hoặc hệ đo mới; đánh giá nội dung bằng bộ cố định và người duyệt.
- Chưa sửa dữ liệu tri thức/nhãn pilot hoặc khẳng định kiểm định độc lập.
- Không thể chứng minh đúng nghĩa chỉ bằng schema, source IDs hoặc prompt; nội dung sinh vẫn cần kiểm và duyệt theo nghiệp vụ.

## 4. Điều kiện dừng và rollback

- Dừng nếu cần sửa Core, gateway, deploy/CI, nâng model/dependency hoặc đổi retrieval/corpus để đạt mục tiêu.
- Dừng nếu không giữ được dữ liệu điều trị, quyền xem, nội dung đã duyệt, tương thích hoặc ngân sách thời gian.
- Lỗi ngoài domain chỉ ghi nhận; không mở rộng PR để sửa kèm.
- Rollback code/image hoặc bỏ opt-in; giữ migration đã áp dụng và cột nullable, không xóa dữ liệu hay lịch sử Flyway.
- Không yêu cầu client gọi trực tiếp Ollama/AI hoặc mở cổng mới để khắc phục lỗi.

## 5. Tác động ngoài phase

- Core Backend giữ nguyên code, hợp đồng, schema và quan hệ dependency; vẫn một cửa gateway và service key nội bộ.
- UI dùng chung có thêm phần hiển thị answer optional; không đổi shell auth/router/manifest.
- Mobile chỉ thêm hiển thị giải thích trong chi tiết báo cáo Farm; chatbot mobile giữ hành vi trích dẫn hiện tại.
- Hỏi đáp không thay ticket/SLA/mượn/đặt phòng; giải thích Farm không thay rule, duyệt hoặc khóa thu hoạch.
- Hạ tầng dùng chung có thể chịu thêm tải Qwen; kết luận tài nguyên phải dựa trên phép đo, không chỉ dựa vào diff code.
- Chỉ sửa các mục tài liệu mô tả hành vi thực sự đổi; report và artifact cục bộ không đưa vào Git.
