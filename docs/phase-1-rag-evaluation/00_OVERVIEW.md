# Phase 1: Đánh giá truy xuất RAG

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft, chưa triển khai |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ứng dụng và module chính | Công cụ đánh giá riêng của AI, corpus Farm và Lab |
| Ước lượng | 2–3 ngày cho công cụ; rà nhãn, chuẩn bị self-host và kiểm tra độc lập ước lượng riêng |
| Người phụ trách | Người triển khai và người kiểm tra nhãn độc lập, chưa chỉ định |
| Bắt đầu, dự kiến xong | Lập kế hoạch 25/10/2026; ngày hoàn thành chưa chốt |
| Phụ thuộc | Bộ nhãn được kiểm tra và Langfuse self-host dành riêng cho đánh giá |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu chủ dự án | Đánh giá RAG trực quan, bên thứ ba kiểm tra và chạy lại được | Quan trọng | Truy xuất tài liệu |
| `docs/BACKLOG.md`, mục 4 | Chất lượng truy xuất MiniLM và kết quả ngoài chủ đề còn hạn chế | Quan trọng | Đo chất lượng tìm kiếm với câu có đáp án; chưa sửa hạn chế |
| [`ai/app/rag/README.md`](../../ai/app/rag/README.md) | Chunking, embedding, bộ lọc và cosine search hiện tại | Tham chiếu | Dùng lại hành vi hiện có |
| [`docs/_template`](../_template/README.md) | Mẫu kế hoạch và điều kiện đóng phase | Tham chiếu | Toàn bộ |
| [`ai/.codex/AGENTS.md`](../../ai/.codex/AGENTS.md) | Tái sử dụng trước, ít file và dependency, có một kiểm tra chạy được | Quy tắc thực hiện | Công cụ đánh giá |

## 2. Lý do

Các test hiện tại kiểm tra logic RAG nhưng chưa có bộ nhãn được kiểm tra độc lập và kết quả truy xuất chấm bằng công cụ chuẩn.
Bên thứ ba cần xem được dữ liệu đầu vào, nhãn, kết quả từng câu và tự tính lại điểm.
Langfuse cung cấp giao diện kiểm tra; tính khách quan còn phụ thuộc nguồn bộ đề, người kiểm tra nhãn và quy trình chạy.

## 3. Tổng quan sửa đổi

Lập bộ câu hỏi và nhãn theo chunk trên corpus cố định.
Thu kết quả từ retriever thật bằng quyền chỉ đọc.
Chấm tại đầu ra `RAGRetriever`, trước ngưỡng và gộp tài liệu của Backend.
Tính Recall@k và nDCG@k bằng `ir_measures`.
Đưa thí nghiệm, chunk và điểm lên Langfuse self-host để kiểm tra trực quan trên trình duyệt.
Lưu đầu vào và kết quả chuẩn để người khác chạy lại.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api`, `dx-core`, `app-farm`, `app-lab`, `dx-host` | Không | Giữ API, nghiệp vụ và dữ liệu Backend |
| Frontend, Mobile | Không | Dùng giao diện Langfuse cho đánh giá |
| AI | Có | Script, kiểm tra và dependency đánh giá riêng; runtime không đổi |
| Data / Seed / Migration | Có, giới hạn | Chỉ thêm bộ đề trong `data/rag/evaluation/`; corpus, seed và schema không đổi |
| Infra / Deploy / CI sản phẩm | Không | Langfuse chạy bằng Compose chính thức bên ngoài repository, project và dữ liệu riêng |
| Docs | Có | Hướng dẫn chạy và giải thích kết quả trong phạm vi đánh giá |

## 4. Phạm vi

- Trong phạm vi: bộ đề pilot, nhãn theo chunk, đầu ra retriever, hai metric, Langfuse Experiments và bằng chứng chạy lại.
- Ngoài phạm vi: cải thiện RAG, đổi chunker, embedding, ranking, ngưỡng, corpus nghiệp vụ, LLM, CV, thuốc, liều, PHI, API, UI và CI/CD.
- Không chép ngưỡng 0,40 hoặc bước gộp tài liệu của Backend vào script; không chấm câu trả lời cuối, từ chối ngoài chủ đề hay toàn pipeline.
- Giả định: có môi trường vector và model embedding đang dùng; nhãn do AI chuẩn bị chỉ là bản nháp cho tới khi người đánh giá kiểm tra.
- Ranh giới Core/App: công cụ đặt ngoài runtime; không thêm interface và không sửa module Backend.
- Nhánh làm việc: `eval/rag-baseline`, tách từ `Khuong/main` đã cập nhật tại thời điểm lập nhánh.

## 5. Ưu điểm và hạn chế

Metric được tính bằng thư viện công khai, dữ liệu gốc chạy lại được và có dashboard xem từng trường hợp.
Kết quả chỉ đại diện cho bộ đề đã chấm và phiên bản corpus được ghi nhận.
Chỉ dùng Langfuse không biến nhãn nội bộ thành đánh giá độc lập.
Rủi ro nhãn, dữ liệu đánh giá, self-host và phụ thuộc được ghi ở [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Có bộ đề được kiểm tra theo quy trình ở `01_PLAN.md`, ghi rõ người kiểm tra và phạm vi nhãn đã duyệt.
- Có Recall@k và nDCG@k cho từng câu và từng nhóm; chỉ tính điểm chính thức khi nhãn đã được kiểm tra.
- Có một lệnh self-check cho công cụ; không thêm test phụ thuộc Langfuse/ir_measures vào môi trường CI hiện có.
- Người xem mở Langfuse thấy câu hỏi, chunk chuẩn, chunk thực tế, thứ tự, điểm và cấu hình; có trường hợp điểm thấp để kiểm tra.
- Bên thứ ba dùng cùng qrels và run tính lại điểm bằng `ir_measures`, sai khác không quá `1e-8` trước khi làm tròn.
- Kết quả thiếu câu hỏi, nhãn lỗi hoặc sai corpus bị báo lỗi; không âm thầm loại câu khó để tăng điểm.
- Có bản xuất đầu vào và kết quả để chạy lại khi không truy cập được Langfuse.
- Phân biệt tính lại điểm trên run đã lưu với chạy lại truy xuất; công bố rõ nguồn vector và lần ingest thay vì suy ra từ tên model hiện tại.
- Cách khởi động Farm/Lab và các file runtime giữ nguyên; đối chiếu diff, không dựng lại toàn stack chỉ để kiểm tra tài liệu hoặc script đánh giá.
- Không commit/push `report/`, `report.zip`, kết quả cục bộ, secret hay checksum; không sửa `.gitignore` cho việc này.
- Checklist đóng phase hoàn tất, có bằng chứng và không có thay đổi ngoài danh sách cho phép.

## 7. Phụ thuộc và thứ tự

Chốt phương pháp và người kiểm tra nhãn trước khi thực hiện; đóng băng nhãn trước lần chạy chấm cuối.
Thu kết quả, tính điểm và xuất dashboard sau khi dữ liệu hợp lệ.
Các cải tiến embedding, chunking hoặc ranking là công việc riêng sau khi có baseline.

## 8. Ảnh hưởng đến thi

Dùng làm bằng chứng cho phần đánh giá truy xuất trong báo cáo và phần demo kiểm tra kết quả.
Chưa quy đổi thành điểm rubric hoặc tuyên bố được một tổ chức chứng nhận.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Chốt phép chấm và quy mô pilot? | Recall@k, nDCG@k; 36 câu hỏi đáp phủ 18 tài liệu, thêm 6 query chẩn đoán đúng workflow; xem WP1-01 | Trước code | Đề xuất sau rà soát 26/10/2026, chờ duyệt |
| Q2 | Ai kiểm tra nhãn và ai đại diện bên thứ ba? | Người hiểu nghiệp vụ, không phải tác nhân tạo nhãn tự động; ghi rõ vai trò và phạm vi kiểm tra | Trước khi đóng băng nhãn | Chưa chỉ định |
| Q3 | Máy chạy Langfuse và người được xem? | Localhost bằng Compose chính thức ngoài repository; project đánh giá riêng, quyền do chủ dự án cấp | Trước khi khởi chạy | Chủ dự án chọn local; cần chốt tài nguyên và cách bên thứ ba truy cập |

## 10. Tài liệu cần cập nhật khi xong

- [ ] Hướng dẫn chạy trong `ai/README.md`; phương pháp và bằng chứng trong `docs/TESTING.md`.
- [ ] `CHANGELOG.md`: bổ sung trạng thái cuối, giữ nguyên log cũ.
- [ ] `LICENSES.md`: ghi dependency đánh giá; xử lý SBOM đúng phạm vi runtime theo `sbom/README.md`.
- [ ] `docs/BACKLOG.md`: chỉ sửa mục liên quan khi có kết quả; không xóa hạn chế chỉ vì đã đo.
- [ ] Theo quy ước template, chuyển việc còn lại sang backlog và xóa thư mục phase khi hoàn tất bàn giao.
