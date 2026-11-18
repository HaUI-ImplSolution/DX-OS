# Kế hoạch xây dựng sản phẩm trợ lý nghiệp vụ động

Kế hoạch cho trợ lý đề xuất giá trị trường trên hồ sơ, kết nối Dynamic Core qua API.
Sản phẩm có API, cấu hình và vòng đời dịch vụ riêng; kết nối Dynamic Core bằng hợp đồng tích hợp trung tính.
Giao diện hồ sơ là điểm tích hợp ban đầu, dùng lại frontend để tránh xây thêm một ứng dụng giao diện riêng.
Chất lượng với nguồn tiếng Việt khác và bộ đánh giá rộng vẫn là gate mở.
Chưa nghiệm thu sản phẩm hoặc chọn model mặc định để phát hành.

## 1. Quy tắc thực hiện

1. Tuân thủ quy tắc tech lead được người dùng cung cấp, CONTRIBUTING và quyết định phạm vi mới nhất.
2. Kiểm worktree, diff và `origin/Khuong/main` trước khi sửa; giữ checkout khác và thay đổi ngoài phạm vi.
3. Giữ Core trung tính, bốn module và API theo quyền người dùng; không nối provider vào DB nghiệp vụ hoặc dùng ADMIN thay quyền nghiệp vụ.
4. Hợp đồng trước, schema/OpenAPI sinh từ code tiếp theo, triển khai sau; giữ ApiResponse, HTTP status, errorCode và bản dịch VI/EN.
5. Đọc LICENSE/NOTICE đúng phiên bản, artifact và bundled trước khi thêm dependency/model; báo kết quả rồi cập nhật catalog, DEPENDENCIES, NOTICE và SBOM theo thành phần thật.
6. Không mở allowlist, fork/vendor SDK hoặc áp dụng ngoại lệ giấy phép của thử nghiệm khác.
7. Dùng cấu trúc, thư viện và bộ kiểm đang có; không thêm framework/test file hoặc dựng vòng agent riêng.
8. Comment tiếng Anh, Markdown mỗi câu một dòng; fixture, artifact, log và secret cục bộ ở ngoài Git.
9. Không khởi động stack/model chỉ để review docs; bước tích hợp sau chỉ chạy tài nguyên cần thiết trong phạm vi đã được cho phép.
10. Không tự stage/commit/push, sửa issue/PR hoặc tài liệu ngoài phạm vi được ủy quyền; không tự đóng issue.

## 2. Hiện trạng và phần còn lại

Hợp đồng `ai.tasks`, metadata, provider và giao diện hồ sơ đã được triển khai.
Core kiểm `version` và `modelVersion` trước khi ghi; model không có quyền xác nhận.
Nguồn và dữ liệu khảo sát do bản cài quản lý, nằm ngoài Git.
Chất lượng khi thiếu dữ kiện, văn phong, ngân sách tài nguyên và nghiệm thu nâng cấp/rollback còn mở.
Các việc còn lại ở [checklist](./02_CHECKLIST.md) và [BACKLOG](../dynamic-core/BACKLOG.md).

Đối chiếu định hướng [Dynamic Core](../dynamic-core/README.md), [API](../API.md), [MODEL](../MODEL.md), [ARCHITECTURE](../../ARCHITECTURE.md) và [EXTENSIONS](../EXTENSIONS.md) khi sửa hợp đồng.

## 3. Hợp đồng nhiệm vụ động

Phần khai báo ở mỗi thực thể là `ai.tasks`, nằm trong format mô hình hiện hành; hợp đồng chi tiết ở [MODEL](../MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ).
Mỗi nhiệm vụ có mã/nhãn, trường đầu vào, một trường đầu ra, hướng dẫn, danh sách mã nguồn và giới hạn đầu ra.
Đợt đầu chỉ đề xuất trường `text` hoặc `choice` đơn trị đã tồn tại trong thực thể; tập lựa chọn lấy từ metadata hiện hành.
Không đề xuất `status`, assignee, vai, ID, trường hệ thống, quan hệ hoặc dữ liệu dùng trực tiếp để cấp quyền/membership.
Cấu hình này không cấp thêm quyền đọc/ghi và không thay cơ chế access theo vai/phạm vi dòng.

- Parser/schema kiểm tham chiếu trường, kiểu đầu ra, mã nguồn và giới hạn, báo lỗi bằng cơ chế hiện có.
- Metadata chỉ cung cấp nhiệm vụ và thông tin cần cho UI; cấu hình nguồn không chứa URL tùy ý, secret hoặc đường dẫn host cho browser/model.
- Provider lấy cấu hình đã áp dụng và bản ghi bằng quyền người gọi; browser chỉ chọn target/task, không tự gửi schema, nguồn hoặc vai làm bằng chứng.
- Cùng target nhưng nhiệm vụ, hướng dẫn, lựa chọn hoặc nguồn đổi thì đề xuất cũ mất hiệu lực.
- Bỏ hoặc đổi tên trường đang được nhiệm vụ tham chiếu phải được plan/validation phát hiện; mô hình không khai báo AI giữ hành vi cũ.
- Console schema/form/editor, model upgrade và diff/plan phải hiểu cấu hình mới, không âm thầm làm mất khối AI khi sửa mô hình.

Hợp đồng đầu ra model gồm `status` (`proposed` hoặc `insufficient`), `value`, `sources` và `checks`.
`checks` dùng tập lựa chọn của trường đầu vào choice/unit đơn trị, không gồm trường đầu ra; null nghĩa là chưa đủ căn cứ đối chiếu.
Server so giá trị theo nguồn với hồ sơ để tạo cảnh báo theo tên trường, không thêm lệnh ghi hoặc nhánh mã theo ngành.
Không yêu cầu model sinh giải thích dài; UI hiển thị tên trường và giá trị, cảnh báo riêng và nguồn tham khảo thu gọn.
`insufficient` chỉ hợp lệ với giá trị null và không tạo proposal.
Không dùng lời tự đánh giá độ tin cậy của model làm bằng chứng đủ điều kiện ghi.
Một hợp đồng chung phục vụ nhiều nhiệm vụ và domain; yêu cầu vượt kiểu đầu ra hiện có cần phần mở rộng được review, không hứa mọi domain đều tự hoạt động.

### API và nguồn cấu hình

| Hợp đồng | Thay đổi cần đặc tả trước code |
|---|---|
| Metadata thực thể hiện có | Trả nhiệm vụ hợp lệ và cấu hình cần cho provider theo quyền người gọi; không tạo endpoint đọc cấu hình cần ADMIN hoặc phát credential xuống client |
| `GET /assistant-api/v1/me` | Trạng thái liên kết Core và model chỉ đọc; GET không khởi động model hoặc chạy probe |
| `POST /assistant-api/v1/me/proposals` | Tạo đề xuất từ model/entity/record/task đã chọn; server tự lấy dữ liệu, cấu hình và snapshot, không nhận patch tùy ý từ LLM/browser |
| `GET /me/proposals`, `/me/proposals/{id}` | Danh sách và chi tiết đề xuất theo chủ, kèm kiểm quyền đọc hồ sơ |
| `POST /me/proposals/{id}/revise` | Nhận giá trị người dùng sửa cho đúng trường; tạo proposal thay thế và vô hiệu bản cũ nguyên tử trước khi xem lại/xác nhận |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Dùng lại cổng xác nhận/hủy, không nhận patch mới trong request confirm |
| `PUT/PATCH /api/v1/data/{model}/{entity}/{id}` | Thêm điều kiện `modelVersion` bên cạnh `version`, lọc điều kiện khỏi dữ liệu nghiệp vụ và kiểm cả nhánh không đổi giá trị |
| Lỗi | Phân biệt thiếu liên kết, model chưa sẵn sàng/bận, quá ngữ cảnh, đầu ra sai, thiếu căn cứ, proposal cũ và phiên bản đổi; giữ HTTP/envelope/i18n nhất quán |

Các route rút gọn trong bảng nằm dưới `/assistant-api/v1`; đây là hợp đồng đã triển khai, chi tiết ở [assistant/README](../../assistant/README.md).
Luồng sửa đề xuất chỉ nhận giá trị của đúng trường đã cấu hình và kiểm lại schema; không cho đổi owner, target, nhiệm vụ hoặc bằng chứng nguồn.
P1 phải chốt schema request này trước triển khai UI, không cho frontend sửa trực tiếp snapshot đã lưu.
Đồng bộ timeout browser, APISIX, Fastify và inference; một slot bận trả lỗi rõ thay vì dựng hàng đợi vô hạn hoặc hệ thống job mới.

Danh mục nguồn do bản cài quản lý gồm mã, revision, tiêu đề, giấy phép và văn bản; người vận hành giữ hồ sơ xuất xứ và thông báo bản quyền.
YAML tham chiếu mã nguồn; revision được lấy từ catalog, không chứa secret, đường dẫn máy hoặc URL để model tự tải.
Đổi nội dung nguồn phải có revision mới và áp dụng cấu hình mới; không sửa âm thầm artifact dưới cùng revision.
Đợt đầu chỉ dùng hướng dẫn giả lập công bố mở, không thêm kho tài liệu mật hoặc cơ chế ACL tri thức mới.
Ví dụ nghiệp vụ mở rộng các template hiện có; dữ liệu demo và benchmark để ngoài Git theo quyết định người dùng.
Nguồn của bản cài được người vận hành nạp qua catalog, giữ nguyên kiểm tra giấy phép hiện có.

Các điểm sửa chính ở P1 là `dx-model` (Model/parser/schema/upgrader), `dx-engine` (metadata, model plan và guard update), cùng Console schema/form/editor.
Không thêm module Java hoặc service AI trong Core; các nhánh kiểm quyền/transaction hiện có phải được tái dùng thay vì tạo đường ghi đặc biệt cho AI.

## 4. Luồng suy luận và xác nhận

1. Người dùng mở hồ sơ đã lưu và chọn nhiệm vụ; bản nháp chưa lưu phải lưu trước, không tự đưa thay đổi chưa lưu vào ngữ cảnh.
2. Provider kiểm danh tính, quyền đọc bản ghi và cấu hình nhiệm vụ hiện hành; lấy metadata, phiên bản, đầu vào và nguồn tương ứng.
3. Chỉ chọn nguồn đã đăng ký cho mô hình/nhiệm vụ và được phép sử dụng; đầu vào quá dài phải báo rõ, không cắt mất hướng dẫn hoặc căn cứ âm thầm.
4. Gửi một request hữu hạn tới inference self-host bằng giao thức/structured output nguyên bản đã kiểm; không có tool discovery, history chat hoặc vòng tự gọi lại để sửa kết quả.
5. Parse và kiểm schema/kiểu/lựa chọn/giới hạn cùng mã nguồn ở server; JSON hỏng, nguồn không tồn tại hoặc nội dung ngoài phạm vi không trở thành proposal có thể xác nhận.
6. Tạo proposal riêng theo chủ với giá trị, nguồn/revision, snapshot, `version`, `modelVersion`, revision nhiệm vụ/model và TTL tính từ lúc proposal được lưu.
7. UI hiển thị giá trị hiện tại, đề xuất và căn cứ; người dùng hủy, xác nhận hoặc sửa nội dung rồi xem lại snapshot xác nhận mới.
8. Confirm kiểm chủ/Origin/TTL, atomic claim, đọc lại quyền/cấu hình/nguồn và gọi Core với điều kiện phiên bản; model không có route/token xác nhận.
9. Core kiểm lại quyền ghi, trạng thái được sửa, quy tắc dữ liệu, phiên bản hồ sơ và mô hình dưới cơ chế khóa/giao dịch hiện có; chỉ cập nhật trường được xác nhận.
10. Trả receipt từ Core; timeout sau khi gửi ghi thành `uncertain`, không tự gửi lại, kiểm trạng thái bằng GET không thực hiện thêm lệnh ghi.

Nguồn và nội dung hồ sơ là dữ liệu, không phải chỉ thị có quyền đổi nhiệm vụ hoặc lấy bí mật.
Đợt đầu không nhận file upload làm tri thức, không OCR, crawler hoặc đọc URL theo chỉ dẫn của model.
Với kho nhỏ, dùng văn bản ngắn đã chuẩn bị và chọn nguồn theo cấu hình, không dựng vector DB, embedding hoặc reranker khi chưa cần.
Đây là đề xuất có căn cứ từ ngữ cảnh giới hạn, chưa phải chức năng hỏi đáp RAG đầy đủ của #210.
Một trích dẫn tồn tại không chứng minh kết luận đúng; bộ nghiệm thu phải đánh giá quan hệ giữa đề xuất và nguồn.

## 5. Các bước và đầu ra

| Bước | Công việc | Điều kiện chuyển bước |
|---|---|---|
| P0 — Chốt hợp đồng và chọn model | Rà cách đơn giản nhất, schema nhiệm vụ, nguồn mở và một request mẫu; khảo sát model trên nhiệm vụ ngắn | Đúng cấu trúc, đúng căn cứ, biết từ chối thiếu thông tin; audit và tài nguyên được review |
| P1 — Schema và API | Bổ sung cấu hình mô hình/metadata, hợp đồng proposal trường và guard sửa trường; cập nhật OpenAPI theo annotation/schema hiện có | Mô hình cũ tương thích; phát hiện tham chiếu hỏng và chặn race mô hình/hồ sơ |
| P2 — Provider gọn | Tái dùng auth/Core client/storage; một adapter inference, kiểm đầu ra, lưu proposal và xác nhận | Không ghi trước xác nhận, không đọc chéo chủ, không có vòng agent hoặc fallback cloud |
| P3 — UI nghiệp vụ | Khối đề xuất trong màn hồ sơ dùng MUI/react-admin hiện có; trạng thái nguồn/model, xem lại và xác nhận | Đổi domain không sửa component; lỗi AI không cản form hoặc quy trình thông thường |
| P4 — Deploy và state | Provider/inference tùy chọn, cấu hình, sao lưu và migration | Cài mới, nâng cấp và model tắt đều an toàn |
| P5 — Rà tích hợp | Rà consumer, manifest, cấu hình, CI và tài liệu theo chức năng đang dùng | Không có dependency hoặc hướng dẫn không có consumer |
| P6 — Kiểm và demo | Ma trận CP, hai domain thật, thay YAML và đo cùng workload | Guard đạt toàn bộ; chất lượng/tài nguyên có bằng chứng; người dùng review đầu ra |

P0 và đặc tả P1 phải rõ trước triển khai; khảo sát tập trung vào nhiệm vụ và ngân sách đã chốt.
Không chọn model mặc định chỉ từ khảo sát GPU; trọng số mở chưa chứng minh đáp ứng cam kết OSAID của đội.
Khảo sát model trên nhiệm vụ ngắn; cấu trúc JSON hợp lệ không chứng minh chất lượng tiếng Việt hoặc đáp ứng ngân sách tài nguyên.
Nếu chưa có model đạt cam kết nguồn mở và chất lượng thì giữ gate đó mở; prototype tùy chọn không được công bố là bản phát hành đạt OSAID.
Không thêm manifest/artifact mới chỉ vì tên model được nêu trong kế hoạch.

## 6. Triển khai, state và khôi phục

| Khu vực | Trách nhiệm |
|---|---|
| `dx-model` và `dx-engine` | Hợp đồng nhiệm vụ, metadata, kế hoạch mô hình và ghi có điều kiện |
| `assistant/src/inference/` | Endpoint cố định, ngân sách ngữ cảnh, một slot, timeout và hủy |
| `assistant/src/tools/` | Đọc dữ liệu theo chủ, kiểm nguồn/đầu ra, tạo và xác nhận đề xuất |
| `assistant/src/storage/` | Snapshot, mã hóa credential, TTL, atomic claim và receipt |
| `frontend/src/assistant/` và Console | UI dùng metadata, cấu hình nhiệm vụ và xác nhận riêng |
| `deploy/`, CI và hồ sơ giấy phép | Cấu hình, khởi tạo SSO/state, backup, build và kiểm thành phần |

1. Khởi tạo thư mục state và master key với quyền hạn chế; từ chối khởi động khi database có sẵn nhưng thiếu key.
2. Cấu hình inference, framing và catalog nguồn riêng cho bản cài; không tải trọng số trong request hoặc khi Core khởi động.
3. Sao lưu SQLite nhất quán trước khi nâng phiên bản schema; giữ master key độc lập ngoài kho restic và kiểm restore trên bản sao.
4. Migration phải giữ owner, SSO, snapshot và lịch sử; không thực thi lại thao tác có kết quả chưa rõ.
5. Kiểm cài mới, nâng cấp, model tắt và rollback bằng snapshot/image tương thích; xác nhận vẫn kiểm phiên bản Core.

Purge dữ liệu, thu hồi key hoặc xóa backup là quyết định vận hành riêng.

### Nâng cấp và rollback

CP-20c đã được kiểm local trên bản cài riêng từ hai bộ source/artifact; còn chờ review kết quả, chưa đại diện cho nâng cấp giữa hai bản phát hành.

1. Ghim image, schema và cấu hình trước/sau nâng cấp; cất master key độc lập, giữ snapshot nhất quán và bản sao cấu hình để phục hồi.
   Giữ snapshot rollback ở kho độc lập hoặc nhóm tag riêng và kiểm retention để lần backup tiếp theo không dọn mất mốc phục hồi.
2. Rollback riêng provider dùng image/cấu hình tương thích; phục hồi SQLite trước migration nếu bản cũ không đọc được schema mới, giữ dữ liệu Core hiện tại.
3. Rollback toàn bản cài phải phục hồi bộ image, cấu hình, Postgres/Core/SSO, kho tệp và state trợ lý tương thích; không chạy image cơ sở dữ liệu hoặc Keycloak cũ trên dữ liệu đã nâng cấp khi chưa xác minh tương thích.
4. Kiểm hai chủ, liên kết SSO, quyền, lịch sử, proposal cũ và trạng thái `pending`/`executing`/`uncertain`; không đọc chéo chủ, ghi lặp hoặc tự xác nhận sau phục hồi.
5. Kiểm thiếu/sai khóa, snapshot lỗi và schema không tương thích; giữ trợ lý dừng khi chưa phục hồi được, không tự tạo key hoặc database thay thế.

Rollback provider không hoàn tác giao dịch đã ghi Core; phục hồi toàn bản cài về snapshot cũ có thể mất dữ liệu phát sinh sau snapshot.
Trước phục hồi toàn bản cài, dừng nhận ghi và tác vụ cập nhật tự động, chụp thêm trạng thái hiện tại, xác định khoảng dữ liệu có thể mất và điểm quay lại khi phục hồi lỗi.
Kiểm bằng Core/SSO/provider thật và inference giả lập trên dữ liệu giả; migration fixture hoặc restore SQLite riêng chưa đủ đóng CP-20c.

## 7. Tài nguyên và cách đo

Provider và inference dùng chung cho nhiều chủ, với một slot suy luận.
Mỗi request chỉ gồm nhiệm vụ, trường cần đọc và nguồn liên quan.
Không có gateway riêng theo tài khoản; model vẫn cần RAM/VRAM theo trọng số và cấu hình chạy.

Không đưa số RAM hoặc độ trễ đích khi chưa đo cùng model, host và workload.
Chốt input/output/context/timeout/concurrency sau P0; đầu vào không vừa phải báo rõ, không tự mở thêm worker hay tải model khác.
Đo Core/OS riêng, provider idle, model loaded-idle, cold/warm, chờ/busy, timeout/hủy và tắt model.
Ghi RAM/RSS/cgroup/VRAM/swap theo đúng nghĩa, số mẫu và từng giai đoạn; không cộng các peak khác thời điểm hoặc gọi mẫu tốt nhất là SLA.
Inference không publish Internet, không nhận Core/bot credential, không tải trọng số trong request và không là dependency bắt buộc để Core khởi động.
Endpoint inference cố định ở deployment; chặn redirect và URL người dùng cung cấp bằng transport/network hiện có, không chỉ dựa vào prompt.

## 8. Kịch bản demo và ranh giới nghiệm thu

1. Mở một mô hình `approval`, đọc hồ sơ giả, tạo đề xuất phân loại hồ sơ có nguồn và xác nhận; kiểm lịch sử Core.
   Nhánh text dùng cùng hợp đồng và cần ca nghiệm thu riêng trước khi công bố chất lượng sinh nội dung.
2. Chuyển sang mô hình `work_item` với trường choice, dùng cùng provider/UI để phân loại từ danh mục của mô hình.
3. Đổi tên trường hoặc tập lựa chọn bằng YAML và apply theo quy trình hiện có; metadata/UI đổi theo, proposal cũ bị chặn.
4. Dùng mô hình `reservation` làm hồi quy tính độc lập, không tạo demo riêng phức tạp và không tác động kiểm xung đột tài nguyên.
5. Thử thiếu căn cứ, người không có quyền, hồ sơ đã đổi, hai confirm đồng thời và model tắt; cho thấy nghiệp vụ vẫn được Core kiểm soát.

Hai domain minh họa là dữ liệu cấu hình, không biến provider thành sản phẩm chỉ phục vụ hai ngành.
Tính động kiểm trong giới hạn các kiểu trường/quy trình đã hỗ trợ; YAML không phải ngôn ngữ lập trình hoặc công cụ sửa mã bằng AI.
Phần hỏi đáp sinh câu trả lời, embedding/reranker và tác tử tự sinh proposal theo sự kiện của #210/định hướng Dynamic Core vẫn chưa thực hiện trong đợt này.
Có thể dùng webhook hiện có để vô hiệu đề xuất khi dữ liệu thay đổi nếu cần, nhưng không thêm subscription Core theo từng người hoặc để event tự cấp danh tính ghi.
Không tuyên bố hoàn tất #210 hoặc toàn bài thi chỉ vì demo trợ lý chạy được.
[Checklist](./02_CHECKLIST.md), [ma trận kiểm](./03_TEST_PLAN.md) và [risk-off](./04_TRADEOFFS_RISKS.md) là tiêu chí review cho phạm vi mới.

## 9. Cải thiện chất lượng qua các mẫu hiện có

Mục tiêu là giảm suy đoán khi thiếu dữ kiện, sửa lỗi so sánh ở ranh giới và đối chiếu lựa chọn theo nguồn mà không gắn mã vào từng nghiệp vụ.
Giữ nguyên Core, bốn module, quyền theo chủ, xác nhận, điều kiện phiên bản, xử lý no-op và uncertain.
Không thêm dependency, model, dịch vụ hay kho truy xuất trong đợt này; dữ liệu và bằng chứng nằm ngoài Git.

1. Giữ bộ 30 ca làm đối chứng, chốt thêm ca mới về thiếu dữ kiện, so sánh bằng/nhỏ hơn/lớn hơn và chọn sai danh mục trước khi thử thay đổi.
2. Giữ một lượt suy luận, sinh quyết định đủ căn cứ trước giá trị và đo cả từ chối đúng lẫn từ chối nhầm.
3. Cấp quan hệ so sánh của các số nguyên an toàn đã được khai báo làm đầu vào, không thêm ngôn ngữ biểu thức hoặc phép tính theo ngành.
4. Suy ra giá trị cần có của trường đối chiếu độc lập với giá trị đang chọn; nhãn và lựa chọn vẫn lấy từ metadata, hướng dẫn vẫn lấy từ nguồn.
5. Hướng dẫn model sinh nội dung ngắn, hoàn chỉnh và không chép nhãn chỉ dẫn; đầu ra vẫn phải qua kiểm kiểu, lựa chọn, nguồn và giới hạn.
6. Giữ một slot, thời hạn và tín hiệu hủy; không có vòng tự sửa, hàng đợi vô hạn hoặc tự ghi.
7. Bổ sung kiểm trong các file hiện có cho từ chối sớm, ngân sách/hủy/bận, số liệu dẫn xuất, sai nguồn và hồi quy quyền/ghi/lịch sử.
8. Chạy lại đủ 30 ca cùng ca mới, tách kiểm mô phỏng, model thật và Core/giao diện thật; ghi cả lỗi còn lại và độ trễ, không hạ đáp án để tăng điểm.

Chỉ chấp nhận cải thiện sau khi kiểm các rào chắn, mức giữ đúng ca cũ, hồi quy và cách diễn đạt mới.
Tỷ lệ đúng trên bộ nhỏ không phải nghiệm thu toàn bộ chất lượng, OSAID hoặc mọi domain.
