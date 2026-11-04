# Plan thực hiện: Case Assistant

CA-WP-01 → CA-WP-02 → CA-WP-03 → CA-WP-04 → CA-WP-05 → CA-WP-06 → CA-WP-07.
Chỉ bắt đầu triển khai sau khi người dùng duyệt kế hoạch.

## CA-WP-01: Chốt hợp đồng, công cụ và model

- Đặc tả ba thao tác `find`, `summarize`, `similar`; giao diện truyền mô hình/thực thể, bộ lọc và hồ sơ đích theo ngữ cảnh người dùng đã chọn.
- Dùng Haystack `Pipeline`, các retriever in-memory, `DocumentJoiner(reciprocal_rank_fusion)` và integration `OllamaChatGenerator`.
- Viết component mỏng gọi `EmbeddingService` hiện có để không nạp thêm một bản model embedding.
- Dùng Pydantic kiểm request, kế hoạch truy vấn và phần tổng hợp; không tự xây framework agent, HTTP client Ollama hoặc thuật toán RRF mới.
- Kiểm Python 3.11, dependency lock, giấy phép và mức RAM của đúng phiên bản Haystack/integration trước khi chọn.
- Kiểm model local chỉ đọc trước; nếu thử chạy Qwen thì dùng volume hiện có, không tải lại và không sửa/xóa volume.
- Qwen local là thử nghiệm opt-in; chọn model mặc định phát hành là cổng quyết định riêng theo tiêu chuẩn hiện hành.
- Nếu không có model đạt tiêu chuẩn, giữ tìm kiếm và bằng chứng; phần diễn giải sinh không được tuyên bố hoàn tất cho bản phát hành.

Nguồn công cụ: [Haystack](https://github.com/deepset-ai/haystack), [DocumentJoiner](https://docs.haystack.deepset.ai/docs/documentjoiner), [InMemoryDocumentStore](https://docs.haystack.deepset.ai/docs/inmemorydocumentstore), [OllamaChatGenerator](https://docs.haystack.deepset.ai/docs/ollamachatgenerator).
In-memory store được upstream khuyến nghị cho thử nghiệm; ở đây chỉ dùng cho tập ngữ cảnh tạm bị chặn kích thước, không nhận vai trò kho chỉ mục lâu dài.
Khả năng sử dụng trên tải mục tiêu phải qua CA-WP-07.

## CA-WP-02: Adapter chỉ đọc và hợp đồng API

### Điểm nối Backend

Thêm package `engine.assistant` cùng cấp với `data`, `meta`, `files`.
Controller dùng `ApiResponse`, lỗi chuẩn và `@Operation` như các endpoint hiện có.
Service gọi trực tiếp các dịch vụ đọc đã kiểm quyền trong cùng tiến trình; không đi vòng qua HTTP loopback, không tạo SQL nghiệp vụ mới.
Hợp đồng adapter dựa trên API và cách phân lớp của lõi dynamic hiện tại.

API dự kiến:

| Method | Endpoint | Nội dung |
|---|---|---|
| `GET` | `/api/v1/assistant/capabilities` | Trạng thái bật/tắt, thao tác hỗ trợ và giới hạn; không gọi AI |
| `POST` | `/api/v1/assistant/query` | `operation`, `model`, `entity`, `query`, `filters`, `recordId` và `historyWindow` khi cần |

`interpretQuery=false` tìm nội dung với bộ lọc người dùng đã chọn, không nhờ LLM suy ra điều kiện.
`interpretQuery=true` yêu cầu LLM đề xuất điều kiện; chưa bật model hoặc không hiểu đủ thì hỏi lại, không âm thầm chuyển thành tìm nội dung.
Mỗi bằng chứng có ID, loại `record`/`event`, ID hồ sơ, ID sự kiện khi có, nhãn và nội dung; liên kết được giao diện dựng từ model/entity đã kiểm.
Các API nội bộ là `/api/v1/assistant/plan`, `/rank`, `/summarize`, đều cần khóa dịch vụ; không truy cập trực tiếp từ trình duyệt.

Client không được gửi nội dung bằng chứng, URL công cụ, namespace truy xuất hoặc quyền tự khai.
Backend xác định phạm vi từ token và metadata đang có hiệu lực.
LLM có thể đề xuất bộ lọc theo schema; Backend chỉ nhận trường, toán tử và giá trị đã kiểm, không nhận SQL hoặc biểu thức thực thi.
Chưa hỗ trợ điều kiện OR/NOT, join hoặc loại điều kiện mà API chung không biểu đạt được; cần hỏi lại thay vì âm thầm bỏ điều kiện.
Bộ lọc do người dùng chọn luôn được giữ và kết hợp AND với điều kiện đề xuất đã kiểm.
Trùng cùng trường/toán tử/giá trị thì gộp; trùng trường/toán tử nhưng khác giá trị thì hỏi lại, không ghi đè.
Ngày hoặc ý định mơ hồ phải hỏi lại; không thay bằng điều kiện rộng hơn.

### Hợp đồng kết quả

Kết quả gồm `status`, `reasonCode`, `operation`, phạm vi và bộ lọc thực thi, `coverage`, `records`, `evidence`, `summary`, `generationStatus`, thời điểm đọc và thời gian từng phần.
`status` gồm `FOUND`, `NO_EVIDENCE`, `NEEDS_CLARIFICATION`, `NEEDS_NARROWING`, `STALE_CONTEXT`, `UNAVAILABLE`.
`summary` là các ý ngắn cùng `evidenceIds`; `generationStatus` phân biệt đã sinh, tắt, không sẵn sàng hoặc nội dung không hợp lệ.
Một câu trả lời lỗi không được thay bằng nội dung suy đoán; bằng chứng hợp lệ vẫn hiển thị khi chỉ phần sinh lỗi.
Số lượng chính xác lấy từ `PageResponse.totalElements` với bộ lọc tương ứng; không đếm số kết quả top-k rồi gọi là tổng.

### Mốc bảo vệ luồng chính

Tắt feature thì service không gọi AI và không cần secret, model hoặc database AI để khởi động Backend.
Đọc metadata không coi là đã được đọc mọi bản ghi; mọi lần lấy bản ghi/lịch sử tiếp tục đi qua `DataService`.
Không giữ transaction database trong lúc chờ AI.

## CA-WP-03: Thu ngữ cảnh, kiểm quyền và phiên bản

- Lấy metadata theo người dùng, loại trường `file` và nội dung định danh không cần thiết khỏi payload AI.
- Với `find`/`similar`, gọi danh sách theo quyền và bộ lọc với một trang tối đa 200 dòng; đọc tổng số và dòng trong cùng một transaction ngắn `REPEATABLE READ` ở adapter.
- Nếu tổng vượt 200 hoặc tổng byte/ngữ cảnh vượt ngân sách, trả `NEEDS_NARROWING`; không chỉ tìm trên trang đầu rồi kết luận không có.
- Với `similar`, kiểm quyền hồ sơ đích trước và loại chính hồ sơ đó khỏi kết quả.
- Chỉ đọc lịch sử của hồ sơ đích hoặc tối đa số hồ sơ top-k được chọn, tối đa 100 sự kiện mỗi hồ sơ trong khoảng `historyWindow` đã chọn.
- Nếu API lịch sử hiện có đọc toàn bộ, thêm overload đọc giới hạn trong `DataService`/`EventLog`, trả tối đa giới hạn + 1 cùng ID sự kiện mới nhất; giữ hợp đồng endpoint cũ.
- Snapshot gồm phiên bản mô hình, định danh bản ghi, dữ liệu đã dùng và sự kiện lịch sử đã dùng.
- Trước khi sinh, đọc lại các nguồn được chọn qua quyền; trước khi trả, đối chiếu lại toàn bộ tập ứng viên, tổng số, quyền và phiên bản trong một transaction đọc ngắn mới.
- So sánh định danh và nội dung đã dùng của mọi ứng viên để phát hiện cả thêm/xóa hoặc sửa bản ghi không nằm trong top-k làm thay đổi thứ hạng.
- Với lịch sử, đối chiếu nội dung cửa sổ đã dùng và ID sự kiện mới nhất của hồ sơ để phát hiện ghi chú vừa được thêm.
- Khi nguồn sửa/xóa, mô hình đổi hoặc quyền hiện hành không còn hợp lệ, trả `STALE_CONTEXT`, không tự gọi lại model ngoài ngân sách.
- Không coi `updated_at` của bản ghi là đủ để phát hiện ghi chú mới.
- Không dùng cache kết quả/corpus chung giữa người dùng; token không chuyển sang AI hoặc ghi vào log.

Kết quả thể hiện thông tin tại thời điểm đọc được ghi trong response, không khóa hồ sơ đến khi người dùng xem.
Người dùng mở lại nguồn để kiểm trạng thái mới nhất trước khi thực hiện quyết định nghiệp vụ.

Hiệu lực đổi thành viên đơn vị hiện theo token mới của Core.
Không cam kết thu hồi nhanh hơn Core; kiểm với token mới, không sửa cơ chế SSO trong kế hoạch này.
Giới hạn đồng bộ nhiều bản sao Backend giữ nguyên theo BACKLOG; bản nghiệm thu dùng một bản sao.

## CA-WP-04: Pipeline tìm kiếm và tổng hợp

Luồng dự kiến:

```text
Web chọn thực thể/thao tác
  → adapter kiểm quyền và giới hạn
  → AI đề xuất truy vấn có schema nếu cần và đã bật LLM
  → Backend kiểm và thu tập bản ghi hợp lệ
  → Haystack: BM25 + cosine → DocumentJoiner/RRF → top-k
  → Backend kiểm lại các nguồn được chọn, lấy lịch sử cần thiết
  → Haystack/Ollama tổng hợp từ bằng chứng nếu bật
  → Backend đối chiếu nguồn và trả kết quả có cấu trúc
```

- API nội bộ AI dùng `X-Service-Api-Key`, tách schema/pipeline hồ sơ khỏi `/knowledge/search` hiện có.
- AI chỉ nhận dữ liệu Backend cấp, không có quyền DB nghiệp vụ, token người dùng hoặc công cụ tự gọi Backend.
- Dùng một `InMemoryDocumentStore` riêng cho mỗi yêu cầu, không dùng tên index chung; xóa dữ liệu và đóng tài nguyên cả khi lỗi/hủy.
- Tái sử dụng embedding đã nạp; chuẩn hóa từ và kiểm ngưỡng bằng bộ thử tiếng Việt trước khi chốt.
- Số top-k khởi điểm 4; giới hạn ứng viên 200, tổng payload 128 KiB, mỗi trường văn bản 4.000 ký tự.
- Không cắt ngầm nội dung: vượt giới hạn ứng viên, byte, trường hoặc số sự kiện thì trả `NEEDS_NARROWING` và giới hạn bị vượt, chưa sinh tổng hợp.
- Người dùng có thể chọn bộ lọc hoặc khoảng lịch sử nhỏ hơn; phần tóm tắt phải ghi đúng khoảng đã đọc, không nhận là toàn bộ lịch sử.
- Các giới hạn trên là ngân sách đề xuất, điều chỉnh sau phép đo và ghi vào cấu hình, không phải số đo đã đạt.
- Kết quả LLM chỉ chứa các ID bằng chứng đã cấp; label, trạng thái, ngày, số lượng và liên kết được Backend gắn vào.
- Không suy ra nguyên nhân chậm trễ, quy định, kết luận duyệt hoặc thông tin chưa có trong nguồn.
- Input có chỉ dẫn trong mô tả/ghi chú được xem là dữ liệu; model không có tool ghi hoặc truy cập mạng tùy ý.
- Giới hạn số lượt model: tối đa một lượt đề xuất truy vấn và một lượt tổng hợp; không có vòng agent không giới hạn hoặc retry tự động nhiều lượt.
- Timeout và giới hạn đồng thời phải bao gồm toàn bộ tác vụ, kể cả hàng chờ; không chiếm worker vô hạn sau khi client hủy.

Không chuyển kho tài liệu pgvector hiện có sang Haystack hoặc sửa retriever tài liệu trong kế hoạch này.
Không thêm reranker; công cụ nào đã giải quyết được nhu cầu thì dùng trực tiếp.

## CA-WP-05: Giao diện chung

- Thêm panel hoặc trang trợ lý nạp chậm trong `frontend/src/assistant/`, dùng component/theme hiện có.
- Danh sách có ô tìm theo nội dung, tự truyền model/entity và giữ bộ lọc danh sách hiện hành; tìm thông thường vẫn dùng được khi AI tắt/lỗi.
- Chi tiết hồ sơ có hai thao tác tóm tắt diễn biến và tìm trường hợp tương tự, tự lấy ID từ bản ghi đang mở.
- Kết quả tìm có thể mở bản gốc hoặc chọn ngay thao tác trên hồ sơ, không yêu cầu quay lại tra ID.
- Trang `/assistant` chỉ là lối tìm kiếm bổ sung, chọn một loại hồ sơ có quyền đọc; bỏ form thao tác kỹ thuật và ô nhập ID.
- Giữ liên kết sâu có model/entity/recordId hợp lệ để mở đúng hồ sơ, không tự gọi sinh khi mở trang.
- Chỉ gửi định danh và câu hỏi; Backend tự lấy bằng chứng.
- Đặt tên và trạng thái do Backend trả trước phần tổng hợp AI; không nhờ model trả lời lại dữ kiện đã có trên giao diện.
- Thu gọn nguồn đối chiếu, mở đúng nguồn khi bấm trích dẫn; bộ lọc diễn giải và khoảng lịch sử nằm trong tùy chọn nâng cao.
- Hiện phạm vi tìm, tổng số theo bộ lọc và thời điểm đọc; không gọi số kết quả top-k là tổng hồ sơ phù hợp.
- Đổi loại hồ sơ, bộ lọc, câu hỏi hoặc đóng panel thì hủy chờ và không nhận kết quả cũ vào ngữ cảnh mới.
- Mỗi liên kết sâu có ngữ cảnh riêng; đổi query string hoặc Back/Forward đặt lại loại hồ sơ, bản ghi và thao tác cùng lúc, không ghép ID mới vào loại cũ.
- Giao diện dừng chờ sau tối đa 65 giây; không coi hủy HTTP là đã dừng ngay tác vụ model ở server.
- Hiện thông báo rõ khi phải thu hẹp, hỏi lại, nguồn thay đổi hoặc AI không sẵn sàng; người dùng tiếp tục dùng danh sách và bộ lọc thường.
- Render văn bản qua component an toàn, không nhận HTML hoặc liên kết tùy ý do model tạo.
- Không thêm màn hình vào dịch vụ lõi, không đổi bố cục console, không tạo giao diện theo tên ngành.

Phần điều chỉnh trải nghiệm chỉ sửa `frontend/src/assistant/`, điểm gắn vào `frontend/src/admin/resources.tsx` và tài liệu liên quan.
Tái sử dụng React-admin, MUI, hợp đồng trợ lý và API đọc hồ sơ hiện có; không thêm dependency.
Không sửa Backend, AI, schema, phân quyền, workflow, deploy hoặc CI cho phần điều chỉnh này.
Lỗi ngữ nghĩa Qwen đã ghi nhận vẫn là giới hạn riêng; bố cục nguồn và dữ kiện không được mô tả như đã khắc phục chất lượng model.

## CA-WP-06: Triển khai tùy chọn và tài liệu

- Thêm `deploy/docker-compose.ai.yml` làm overlay; giữ lệnh và service khởi động lõi hiện có.
- Profile AI chạy dịch vụ tìm/xếp hạng; LLM là profile tùy chọn riêng hoặc URL nội bộ do người vận hành cấp.
- AI không publish cổng host; route người dùng đi qua endpoint Backend đã kiểm token.
- Suy luận dùng dịch vụ nội bộ; tắt telemetry của công cụ theo cấu hình được hỗ trợ, không gửi nội dung hồ sơ ra dịch vụ ngoài.
- Overlay chỉ chuyển biến được khai báo; giá trị mặc định đặt trong `.env.example`, secret và model bắt buộc khi bật được kiểm rõ.
- Không thêm `depends_on` bắt buộc từ Backend sang AI/Ollama; AI chết không làm Backend unhealthy.
- Model dùng cache/volume bền vững; bước chuẩn bị chỉ tải khi thiếu, restart không tải lại, có kiểm khi chạy offline.
- Không tự nối volume Ollama cũ của máy vào cấu hình phát hành; dùng override local ngoài repo để thử lại nếu cần.
- Chỉ build thành phần đổi; tái sử dụng cache, đặt giới hạn tài nguyên và kiểm dung lượng trước khi dựng AI.
- Overlay dùng lại lõi đang chạy cùng project khi kiểm local; phép thử clone sạch dùng project riêng và chạy tuần tự theo dung lượng, không mặc định dựng hai toàn stack cùng lúc.
- Cập nhật `ai/README.md`, `frontend/README.md`, `backend/README.md`, `deploy/OPERATIONS.md`, `ARCHITECTURE.md`, README hai ngôn ngữ và CHANGELOG đúng thay đổi đã làm.
- Số đo ghi gọn ở `docs/TESTING.md`; dữ liệu cá nhân, credential, artifact và `report/` không đưa vào Git.
- Cập nhật dependency lock, `COPYRIGHT.txt`, `NOTICE`, `sbom/` và `.github/license-catalog.json` theo dependency/model thực tế; không thêm file giấy phép khác thay quy ước mới.
- `docs/dynamic-core/README.md` chỉ sửa sau khi người dùng duyệt thay đổi định hướng; kế hoạch này chưa sửa tệp đó.

## CA-WP-07: Đo, hồi quy và nghiệm thu

- Mở rộng test hiện có ở `ai/tests/`, Backend và `deploy/dev/check_api.py`, `check_case.py`, `check_demo.py`; chỉ thêm tệp trong đúng phạm vi đã duyệt.
- Bộ đề tổng hợp có ít nhất ba mô hình có sẵn: một cửa, hỗ trợ và đặt tài nguyên; nhãn do người đọc kiểm trước khi chấm.
- Đo lọc đúng và tổng số đúng bằng kết quả API chuẩn; đo tìm gần nghĩa bằng Recall@4/nDCG@4 với `ir_measures` ở dependency kiểm thử.
- Tách kết quả trên tập ứng viên hợp lệ khỏi đánh giá toàn bộ luồng, có ghi ca yêu cầu thu hẹp hoặc không trả lời.
- Đo tổng hợp: từng nhận định có được nguồn hỗ trợ không, đúng trình tự/đối tượng không, có thêm kết luận không có căn cứ không.
- So sánh với BM25/từ khóa và đọc thủ công; công bố số đo thật, không sửa bộ đề để làm điểm đẹp hoặc yêu cầu hybrid luôn thắng.
- Đo CPU/RAM, cold/warm, độ trễ p50/p95 và hai yêu cầu đồng thời trên phần cứng đích; chưa thêm nền tảng đánh giá riêng.
- Dùng tiêu chí chấp nhận đề xuất ở kế hoạch kiểm thử trước khi chạy nghiệm thu; không hạ tiêu chí sau khi xem kết quả nếu chưa được người dùng duyệt.
- Hồi quy chuỗi `check_*.py` hiện có với AI tắt và bật; ghi rõ check nào đã chạy, check nào chưa chạy.
- Dựng bản sao sạch với project/data riêng, không tạo thêm cả stack nặng khi chưa đủ dung lượng; dừng và dọn đúng tài nguyên thử sau khi xong.
- Bàn giao link local và mẫu câu hỏi để người dùng kiểm; commit/push là bước sau khi có lệnh riêng.

## Ranh giới tệp dự kiến

| Vùng | Thay đổi cho phép |
|---|---|
| `backend/dx-engine/.../assistant/` | Adapter, client AI, DTO, controller và kiểm nguồn |
| `dx-engine` config/test, `data/DataService`, `data/EventLog` | Cấu hình feature, test; overload đọc lịch sử có giới hạn/watermark, không đổi quyền hoặc hợp đồng cũ |
| `ai/app/assistant/`, `ai/app/api/`, `ai/app/core/` | Pipeline hồ sơ, schema/auth/config; tái sử dụng embedder |
| `ai/requirements*`, `ai/tests/`, `ai/Dockerfile` | Dependency đã kiểm, test và đóng gói cần thiết |
| `frontend/src/assistant/`, điểm gắn trong `src/admin/` | Giao diện trợ lý chung, không thiết kế lại ứng dụng |
| `deploy/docker-compose.ai.yml`, `.env.example`, công cụ môi trường và `deploy/dev/` | AI tùy chọn, cấp cấu hình khi bật, phép kiểm phạm vi này |
| Tài liệu và danh mục giấy phép đã liệt kê | Cập nhật đúng trạng thái thực tế khi triển khai |

Không sửa migration nghiệp vụ, BPMN, quy tắc quyền, `ModelParser`, luồng apply/rollback hoặc CI để làm test trợ lý qua.
Thiếu năng lực nằm ngoài ranh giới thì ghi lại để review, không tự mở rộng phạm vi.
