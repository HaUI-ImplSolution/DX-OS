# Plan thực hiện: Phase 1

Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự: chốt phương pháp → nhãn → thu kết quả → chấm → Langfuse → kiểm tra độc lập.
Không có thay đổi API nên không có bước sửa Swagger.

## Thứ tự và phụ thuộc

WP1-01 → WP1-02 → WP1-03 → WP1-04.
Việc chuẩn bị Langfuse self-host và project đánh giá có thể làm trong lúc người đánh giá kiểm tra nhãn, sau khi kiểm tra tài nguyên máy.
Chỉ triển khai sau khi chốt câu hỏi mở của `00_OVERVIEW.md`; không tự ghi nhận có người duyệt hoặc tự tạo quyết định duyệt.
Hiện chưa có người kiểm tra nhãn được chỉ định; mọi nhãn đang ở trạng thái `draft` và không được trình bày là đánh giá độc lập.
Theo `ai/.codex/AGENTS.md`, chỉ một script có ba bước collect/score/publish và một self-check; không tạo framework đánh giá hoặc giao diện riêng.
Trong lần chạy, có thể collect/score trước rồi mới bật Langfuse để publish, tránh giữ model AI và stack Langfuse cùng chiếm RAM khi không cần.

## Xử lý nhiều request và tài nguyên

- Với collector đánh giá, mặc định xử lý tuần tự (`concurrency=1`), một câu một lần; không tự thêm queue, batching, worker pool hoặc retry riêng. Cách này giữ thứ tự, thời gian đo được và tính tái lập; nếu cần smoke test tải thì chỉ tăng concurrency theo quyết định riêng và ghi rõ đó không phải baseline.
- Với request hỏi đáp của runtime, mỗi request độc lập gọi retriever rồi gọi Ollama đồng bộ tối đa 30 giây theo cấu hình hiện tại. Code hiện chưa có hàng đợi, backpressure, rate limit hay giới hạn concurrency ở tầng ứng dụng; nhiều request có thể cùng chờ Ollama và chịu giới hạn tài nguyên của Ollama/máy chủ. Khi Ollama lỗi hoặc timeout, request dùng evidence nguyên văn theo fallback hiện có. Thiết kế queue/concurrency/SLA production không thuộc phase này.
- Không dùng LLM để chấm trong phase; score/self-check không nạp model LLM, nên có thể chạy sau khi tắt Ollama. Publish Langfuse làm riêng sau collect/score để giảm đỉnh RAM.
- Sizing pilot: collect/score cần tối thiểu 2 vCPU, 4 GB RAM và 5 GB trống; nếu chạy Ollama cùng embedding nên dành 4 vCPU, 8 GB RAM và 5–10 GB trống; Langfuse self-host riêng cần tối thiểu 4 vCPU, 8 GB RAM còn trống và 20 GB trống. Khi chạy đồng thời với DX-OS, khuyến nghị 8 vCPU/16 GB RAM; đây là ước lượng cần xác nhận bằng đo thực tế, không phải SLA.
- Trước lần chạy chính thức phải ghi CPU/RAM đỉnh, thời gian/câu, số lỗi/timeout, dung lượng volume và concurrency thực tế. Nếu swap, container restart, hết disk hoặc latency tăng bất thường thì dừng publish, chạy tách collect/score và Langfuse hoặc chuyển máy.

## WP1-01: Bộ đề và nhãn chuẩn

- Mục tham chiếu: `00_OVERVIEW.md`, mục 2, 6 và Q1–Q2.
- Mục tiêu: có bộ câu hỏi, quy tắc chấm và nhãn kiểm tra được; ước lượng M.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Kiểm tra truy xuất | Test logic và thử câu hỏi quy mô nhỏ | Có bộ đề cố định, nhãn theo chunk và lịch sử kiểm tra |

**Hướng làm**

- Đề xuất pilot 36 câu hỏi đáp: hai câu cho mỗi tài liệu hiện có, gồm 12 câu Farm và 24 câu Lab.
- Mỗi tài liệu có một câu gọi trực tiếp chủ đề và một câu diễn đạt tự nhiên không nêu tên chủ đề; người kiểm tra xác nhận có đáp án và không chỉ sao chép câu trong corpus.
- Thêm sáu lượt tham khảo chẩn đoán tương ứng sáu mã CV, dùng query thực của node `retrieve_knowledge`; không tự đặt thêm câu hỏi chẩn đoán khác workflow.
- Đây là pilot phủ tài liệu, không phải mẫu đại diện người dùng; quy mô lớn hơn cần quyết định riêng sau baseline.
- Dùng ví dụ nhỏ cố định để kiểm công cụ, giữ bộ chấm cuối tách biệt; không chỉnh retriever hoặc chọn câu theo điểm đã thấy.
- Dùng lại `CORPORA`, `load_corpus` và `chunk_text`, bỏ chunk trùng đúng như ingest; không sao chép corpus vào một bộ dữ liệu nghiệp vụ mới.
- Khóa nhãn gồm namespace, mã tài liệu và `chunk_index`; đối chiếu tiêu đề/nội dung DB để ánh xạ, báo lỗi nếu không ánh xạ duy nhất; không dùng UUID database làm khóa nhãn.
- Nhãn `0`: không hỗ trợ, `1`: hỗ trợ một phần, `2`: trả lời trực tiếp; Recall coi nhãn từ 1 là liên quan, nDCG dùng gain tuyến tính tương ứng nhãn.
- Với corpus nhỏ, rà toàn bộ chunk trong phạm vi bộ lọc của từng câu; nhãn tường minh 0/1/2 và ghi nhận đã rà đủ, không coi đoạn chưa đọc là không liên quan.
- Người đánh giá kiểm tra nhãn mà chưa xem thứ hạng hoặc điểm retriever; xử lý bất đồng trước lần chấm cuối.
- Ghi người tạo, người kiểm tra, ngày, lý do gắn nhãn và trạng thái `draft`/`reviewed`; nhãn tự động không tự chuyển thành `reviewed`.
- Đóng băng bộ đề và ghi nội dung chunk, nguồn corpus, cấu hình, phiên bản bộ đề trong artifact cục bộ; không thêm bản sao corpus hoặc checksum vào repository.

**Phạm vi ảnh hưởng**

Chỉ thêm `data/rag/evaluation/`: câu hỏi, qrels chuẩn TREC và hướng dẫn gắn nhãn ngắn.
Corpus nguồn, registry, seed và migration giữ nguyên.
Không dùng ảnh, hội thoại hoặc thông tin thật của người dùng.

**Kết quả đầu ra**

Bộ đề đủ nhóm, không trùng mã, nhãn truy ngược được về chunk, và có người kiểm tra độc lập được ghi nhận thật.
Bộ đề còn `draft` chỉ được dùng kiểm tra công cụ, không xuất điểm với nhãn “đánh giá độc lập”.

## WP1-02: Thu kết quả từ retriever hiện tại

- Mục tham chiếu: `00_OVERVIEW.md`, mục 4 và 6; ước lượng M.
- Mục tiêu: lưu đúng kết quả thực tế mà không sửa hành vi truy xuất.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Chạy một bộ câu hỏi | Chưa có lệnh thu kết quả chuẩn | Script gọi `RAGRetriever` thật và xuất run theo từng câu |

**Hướng làm**

- Thêm duy nhất `ai/scripts/evaluate_rag.py`; dùng lại `RAGRetriever` và `EmbeddingService`, không viết lại tìm kiếm, boost hoặc chia chunk.
- Thu trong một transaction PostgreSQL chỉ đọc, snapshot nhất quán; không gọi ingest, seed hoặc migration trong script.
- So khớp tiêu đề, loại tài liệu, mã bệnh, nội dung, nguồn và thứ tự chunk với corpus bằng helper hiện có; sai/thiếu/thừa trong namespace thì dừng.
- Database hiện không lưu tên model đã tạo vector; phải có bằng chứng ingest cùng model/artifact và cấu hình đang dùng, không suy ra điều này chỉ từ vector 384 chiều hoặc nội dung khớp.
- Nếu không rõ nguồn vector thì chưa công bố baseline tái lập; chỉ chuẩn bị database đánh giá riêng bằng lệnh ingest hiện có, không ingest đè DB sản phẩm.
- Hỏi đáp Farm/Lab: gọi retriever với k=4 và `document_type=None` như API hiện tại; truyền namespace tường minh, không vô tình dùng mặc định `disease_knowledge` cho Lab.
- Tham khảo chẩn đoán: gọi lại node `retrieve_knowledge` với mã bệnh đã biết; dùng `rag_tool` của node chỉ để ghi query/tham số thực nhận và gọi retriever thật, không chạy toàn graph, CV hoặc Backend.
- Query mặc định thực là `Explain disease {disease_code} in detail`, k=3 và lọc đúng bệnh; ghi rõ đây là đánh giá nhánh retrieval, không phải chất lượng chẩn đoán.
- Cả ba nhóm đều chấm ngay đầu ra retriever; không chép ngưỡng 0,40 hoặc logic gộp tài liệu của Backend vào script.
- Lưu nguyên top-k, cosine gốc, thứ tự cuối và tổng thời gian gọi; không hứa có toàn bộ ứng viên trước top-k hoặc thời gian từng bước mà runtime không trả ra.
- Run chuẩn TREC dùng điểm đơn điệu theo thứ hạng cuối; cosine lưu riêng để thư viện không đảo lại thứ tự sau boost.
- Mọi câu có bản ghi kể cả trả rỗng; lỗi node có `errors` hoặc lỗi kỹ thuật làm lần chạy không hợp lệ, không âm thầm chuyển lỗi thành điểm 0 hoặc bỏ câu.

**Phạm vi ảnh hưởng**

Chỉ công cụ đánh giá trong `ai/scripts/` và dữ liệu xuất cục bộ ngoài Git.
Không sửa `ai/app/`, không gọi Backend, không thay role hay client của sản phẩm.
Model và nguồn vector phải xác minh được; không dựng toàn stack hoặc ghi đè database đang dùng.

**Kết quả đầu ra**

Có run chuẩn TREC, bản đối chiếu chunk và metadata cấu hình cho tất cả câu hỏi.
Đầu ra không có secret, URL ảnh có chữ ký hoặc dữ liệu nghiệp vụ.

## WP1-03: Tính hai chỉ số bằng ir_measures

- Mục tham chiếu: `00_OVERVIEW.md`, mục 6; ước lượng S.
- Mục tiêu: điểm tính lại được bằng công cụ công khai.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Chấm truy xuất | Chưa có baseline định lượng đầy đủ | Recall@k và nDCG@k cho từng câu và nhóm |

**Hướng làm**

- Cài `ir_measures` trong môi trường đánh giá riêng qua `ai/requirements-eval.txt`; khóa phiên bản/provider `pytrec_eval`, dùng thư viện tính metric, stdlib đọc/ghi JSON/TREC.
- Ghi đầy đủ k, nhãn liên quan, gain nDCG, mốc đo trước Backend, phiên bản dependency/model, cấu hình và số câu trong metadata.
- Xuất điểm từng câu và macro-average riêng cho Farm hỏi đáp, Farm chẩn đoán và Lab hỏi đáp; không gộp k khác nhau thành một điểm quảng bá.
- Câu có nhãn liên quan nhưng trả rỗng được tính 0; câu có qrels nhưng không có bản ghi thực thi phải bị báo thiếu.
- Câu không có bất kỳ nhãn liên quan nào không thuộc bộ chấm hai metric này; báo riêng để sửa bộ đề, không gán điểm tốt cho việc từ chối.
- Không đặt ngưỡng “RAG đạt chuẩn” tùy ý; phase đạt khi phép đo đúng và chạy lại được, kể cả điểm thấp.
- Thêm chế độ `--self-check` trong chính script, dùng ví dụ nhỏ để kiểm input, thứ tự và metric; chấm lại artifact bằng CLI `ir_measures`, không viết thêm bộ máy tính điểm.
- Import dependency theo bước: score/self-check không nạp model AI hoặc nối DB; collect/score không cần Langfuse đang chạy.

**Phạm vi ảnh hưởng**

Dùng chung script ở WP1-02 và một file dependency đánh giá; không thêm file test vào `ai/tests/` vì CI hiện không cài dependency đánh giá.
Không thêm dependency vào `ai/requirements.txt`, `ai/requirements.lock`, Docker image hoặc CI.

**Kết quả đầu ra**

Kết quả khớp ví dụ kiểm tra tính tay và lệnh chấm độc lập với sai số không quá `1e-8` trước làm tròn.
Nguồn công thức, provider và ý nghĩa chỉ số được ghi trong hướng dẫn.

## WP1-04: Langfuse và bàn giao bằng chứng

- Mục tham chiếu: `00_OVERVIEW.md`, mục 6 và Q3; ước lượng M.
- Mục tiêu: bên thứ ba kiểm tra từng câu trên giao diện có sẵn và chạy lại phép chấm.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Xem kết quả | Chưa có experiment đánh giá | Langfuse hiển thị bộ đề, nhãn, kết quả, điểm và dữ liệu quá trình |

**Hướng làm**

- Dùng Langfuse self-host theo Compose chính thức ở thư mục ngoài repository DX-OS; chỉ chạy khi đủ tài nguyên, không tích hợp tracing vào FastAPI/LangGraph sản phẩm.
- Dùng project Compose, network, volume, database và object storage riêng; không dùng chung PostgreSQL/MinIO của DX-OS hoặc sửa cấu hình sản phẩm.
- Ghim release tương thích với API/SDK được chọn; dùng image có sẵn theo hướng dẫn chính thức, không build lại DX-OS hoặc mã nguồn Langfuse.
- Bind giao diện vào loopback với cổng chưa bị dùng, dự kiến `http://localhost:3000`; nếu script chạy trong container, cấu hình địa chỉ kết nối riêng đúng môi trường thay vì dùng localhost của container.
- Dùng SDK Langfuse chính thức trong môi trường đánh giá riêng, kiểm tra tương thích và giấy phép; không viết REST client, cơ chế queue hoặc retry riêng khi SDK có sẵn.
- Gửi Recall và nDCG do `ir_measures` tính, không tạo LLM judge trong phase này.
- Mỗi lần chạy ghi tên phiên bản bộ đề, trạng thái kiểm tra nhãn, cấu hình, số câu và hai score của từng câu.
- Một observation retrieval cho mỗi lần gọi thật, gồm input, tham số, top-k, cosine, thứ tự và tổng thời gian; hai metric là score gắn cùng câu.
- Tiêu đề/chunk/overlap, cấu hình embedding và nguồn vector là metadata hoặc snapshot, không phải trace các bước đã chạy trong lần hỏi.
- Không dựng span giả cho ứng viên trước boost, embedding, chunking hoặc Backend; Langfuse không tự tạo dữ liệu mà collector chưa thu.
- Dùng màn Dataset/Experiment và input/output/metadata có sẵn; không làm UI, biểu đồ hoặc cây trace tùy biến chỉ để trình bày đẹp hơn.
- Dataset/traces chỉ chứa câu hỏi đánh giá và tài liệu công khai được phép gửi; không đưa credential, GPS, account, ảnh hoặc URL ảnh vào metadata.
- Dùng định danh lần chạy/câu hỏi và cơ chế ID của SDK để gửi lại không tạo câu trùng; không tự viết hệ thống đồng bộ hai chiều.
- Langfuse lỗi thì giữ artifact và báo publish chưa hoàn thành; không chạy lại retriever để thay kết quả hoặc âm thầm ghi đè lần chạy đã bàn giao.
- Bàn giao qrels, run và lệnh `ir_measures` để bên thứ ba tự chấm; dashboard là giao diện kiểm tra, không phải chứng nhận độc lập.

**Phạm vi ảnh hưởng**

Chỉ phần publish của script, môi trường Langfuse đánh giá riêng và tài liệu liên quan.
Không đăng ký dịch vụ Cloud, gửi lời mời hay public project.
Không thêm biến Langfuse vào env/compose của sản phẩm; cấu hình được nhập riêng trong phiên chạy đánh giá.
Người đánh giá xem trực tiếp trên máy local hoặc qua kết nối riêng được chủ dự án cho phép; không coi URL localhost là đường dẫn bên ngoài truy cập được.

**Kết quả đầu ra**

Người xem mở từng câu thấy nhãn chuẩn, chunk thực tế, thứ tự và điểm khớp bản xuất.
Có người khác tính lại điểm từ artifact và được hướng dẫn chạy lại collect trong môi trường cùng cấu hình; hai việc được ghi nhận riêng.
Chưa có người kiểm tra nhãn thì giữ trạng thái “đánh giá nội bộ”.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint, interface, bảng DB | Toàn bộ sản phẩm | Không | Không |
| Dữ liệu đánh giá | `data/rag/evaluation/` | Câu hỏi, qrels, thông tin kiểm tra nhãn | Không |
| Công cụ | `ai/scripts/evaluate_rag.py` | Thu kết quả, chấm và publish tách bước | Không |
| Dependency | `ai/requirements-eval.txt` | Chỉ môi trường đánh giá | Không |
| Cấu hình | `DATABASE_URL`, `LANGFUSE_BASE_URL`, `LANGFUSE_PUBLIC_KEY`, `LANGFUSE_SECRET_KEY` | Chỉ phiên đánh giá riêng; không ghi giá trị thật trong Git | Không |

## Dữ liệu, migration và rollback

Không có migration hoặc ghi dữ liệu sản phẩm.
Kết quả chạy nằm ở thư mục đầu ra ngoài repository, không dùng `report/` hoặc thêm `.gitignore` để cất kết quả.
Dừng script và riêng project Compose Langfuse để rollback môi trường đánh giá; giữ volume làm bằng chứng, chỉ xóa khi chủ dự án yêu cầu.
Không dừng, prune hoặc xóa image/volume của DX-OS và các dự án khác để lấy tài nguyên.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Tách công cụ khỏi runtime, chỉ đọc vector | Giữ domain nhỏ và cách chạy sản phẩm |
| D2 | Chấm truy xuất bằng thư viện, dashboard bằng Langfuse | Công thức và dữ liệu kiểm tra được |
| D3 | Người kiểm tra nhãn phải được ghi nhận thật | Không tự gọi bộ nhãn AI là đánh giá độc lập |
| D4 | Không sửa retriever sau khi nhìn điểm trong phase này | Giữ baseline phản ánh hệ thống đang có |
| D5 | Chỉ đo đầu ra retriever, trước xử lý Backend | Không sao chép logic sản phẩm trong công cụ chấm |
| D6 | Một script, một self-check, dùng thư viện và UI có sẵn | Theo thứ tự tái sử dụng của `ai/.codex/AGENTS.md` |

## Danh sách file được phép thay đổi khi triển khai

- Chỉ thêm hai file mã/cấu hình: `ai/scripts/evaluate_rag.py` có self-check và `ai/requirements-eval.txt`.
- Thêm bộ đề và hướng dẫn trong `data/rag/evaluation/`.
- Cập nhật tối thiểu `ai/README.md`, `docs/TESTING.md`, `LICENSES.md`, `CHANGELOG.md`; `docs/BACKLOG.md` chỉ khi có việc liên quan.
- SBOM chỉ thay đổi nếu có dependency runtime theo quy tắc `sbom/README.md`; dự kiến không có.
- Ngoài danh sách này phải cập nhật kế hoạch và chốt lại phạm vi trước khi sửa; lỗi ngoài domain chỉ ghi nhận.

## Nguồn phương pháp

- [ir_measures: đầu vào qrels/run và cách chấm](https://ir-measur.es/en/latest/getting-started.html).
- [ir_measures: chỉ số](https://ir-measur.es/en/latest/measures.html) và [provider](https://ir-measur.es/en/latest/providers.html).
- [Langfuse Datasets](https://langfuse.com/docs/evaluation/experiments/datasets) và [Annotation Queues](https://langfuse.com/docs/evaluation/evaluation-methods/annotation-queues).
- [Langfuse: tương thích phiên bản](https://langfuse.com/docs/compatibility).
- [Langfuse: Docker Compose self-host](https://langfuse.com/self-hosting/deployment/docker-compose).
