# Trợ lý nghiệp vụ (tùy chọn)

Dịch vụ độc lập đề xuất một giá trị cho một trường của hồ sơ.
Mô hình của trợ lý là bộ phân loại hồ sơ tiếng Việt do đội tự huấn luyện, chạy trên CPU ngay trong trợ lý (nhiệm vụ `classifier`).
Nhiệm vụ `llm` là đường tùy chọn, dùng nguồn tham khảo và mô hình ngôn ngữ do người vận hành tự chạy.
Người dùng xem căn cứ, sửa hoặc xác nhận trên giao diện hồ sơ.
Core kiểm quyền, quy tắc và phiên bản trước khi ghi, nên model không có đường ghi trực tiếp.

## Bật, cấu hình và tắt

Trợ lý tắt mặc định: chưa bật thì stack không có container hay route của trợ lý.

1. Đặt `COMPOSE_FILE=docker-compose.yml:docker-compose.assistant.yml` trong `deploy/.env`, rồi chạy `dxos start`.
2. Khai báo `ai.tasks` cho thực thể trong tệp mô hình (hoặc tab trợ lý của bảng điều khiển) rồi áp dụng; cú pháp ở [MODEL](../docs/MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ).
   Các mẫu `onestop`, `support_desk` và `clinic_appointment` có sẵn nhiệm vụ phân loại, dùng ngay khi bật trợ lý.
3. Người dùng mở **Trợ lý nghiệp vụ**, liên kết tài khoản DX-OS, rồi mở một hồ sơ đã lưu, chọn nhiệm vụ và tạo đề xuất.
   Sửa giá trị thì đề xuất được tạo lại để xem lại, sau đó xác nhận lưu bằng một thao tác riêng.
4. Tắt bằng cách bỏ dòng `COMPOSE_FILE` rồi chạy `docker compose up -d --remove-orphans`.

Nhiệm vụ `llm` cần thêm cấu hình ở mục [Mô hình ngôn ngữ](#mô-hình-ngôn-ngữ-tùy-chọn).
Giới hạn RAM và CPU của container nằm trong `deploy/docker-compose.assistant.yml`.

Nếu mật khẩu quản trị Keycloak trong `deploy/.env` không còn đúng, `assistant-sso` chỉ ghi cảnh báo và trợ lý không liên kết được tài khoản.
Xem `docker compose logs assistant-sso`, sửa `deploy/.env` rồi chạy lại `docker compose up -d`.

Khi đã bật, `dxos update` kéo cả image trợ lý.
Image có sau khi chạy workflow `Release Images` với `services=assistant`.
Image chạy bằng Node.js và chứa mã trợ lý cùng các trọng số phân loại.
Dựng image từ mã nguồn bằng `docker compose build assistant-init` trong `deploy/` khi đã bật trợ lý.
Dựng thẳng bằng `docker build -f assistant/Dockerfile .` ở gốc repo thì cần BuildKit.

## Bộ phân loại

Image đóng gói ba bộ phân loại đạt điều kiện nghiệm thu:

| Bộ phân loại | Mẫu | Đọc trường | Gợi ý cho trường |
|---|---|---|---|
| `onestop` | `onestop` | `reason` | `kind` |
| `support` | `support_desk` | `title`, `detail` | `priority` |
| `clinic` | `clinic_appointment` | `note` | `service` |

Dữ liệu, cách huấn luyện và trọng số ở [`tools/classifier/`](../tools/classifier/README.md); số đo và giới hạn ở [thẻ mô hình](../tools/classifier/MODEL_CARD.md).
Tệp mô hình chỉ chọn được bộ phân loại đã có trọng số; nghiệp vụ khác cần dữ liệu và huấn luyện riêng.

Trợ lý nạp trọng số từ `/app/classifiers` khi khởi động.
Đặt `ASSISTANT_CLASSIFIERS_DIR` để nạp từ thư mục khác bên trong container, ví dụ `/run/secrets/classifiers` nếu đặt tệp dưới `deploy/data/assistant/private/classifiers`.
Thư mục đặt ở biến này thay cho thư mục mặc định.
Mỗi tệp có tên `<classifier>.json`, với `<classifier>` khớp tên nhiệm vụ trong tệp trọng số.
Trợ lý báo `configured` khi nạp được ít nhất một bộ phân loại hoặc khi có mô hình ngôn ngữ.
`GET /me` trả `model.classifiers` gồm các tên đã nạp và `model.llmConfigured` cho biết mô hình ngôn ngữ đã được cấu hình.

Nhiệm vụ phân loại đọc các trường chữ theo thứ tự `input_fields`.
Mẫu hỗ trợ kỹ thuật khai `title` rồi `detail` theo thứ tự huấn luyện.
Kết quả từ chối trả `insufficient: true` và `proposal: null`.
Đề xuất lưu `sources: []` và `evidence` gồm một đến ba cụm chữ trích từ các trường đầu vào.
Giao diện hiển thị các cụm chữ trong mục **Căn cứ trong hồ sơ**.

Thiếu hoặc không đọc được trọng số của nhiệm vụ thì API trả 409 `ERR_CLASSIFIER_UNAVAILABLE` cho nhiệm vụ đó.
Các lựa chọn của trường đầu ra phải khớp tập nhãn nghiệp vụ của tệp trọng số, nếu sai thì API trả 409 `ERR_CLASSIFIER_LABELS`.
Revision của nhiệm vụ bao gồm SHA-256 của tệp trọng số.
Thay tệp và khởi động lại trợ lý làm các đề xuất dùng trọng số cũ hết hiệu lực.

## Đề xuất và xác nhận

Đề xuất thuộc về một chủ, hết hạn sau 120 giây và được xóa sau 30 ngày.
Đề xuất ghi lại hồ sơ, nhiệm vụ, giá trị, căn cứ, phiên bản hồ sơ, phiên bản mô hình và revision của nhiệm vụ.
Sửa nội dung tạo đề xuất mới và vô hiệu đề xuất cũ.
Khi xác nhận, trợ lý đọc lại hồ sơ và cấu hình, rồi chỉ gửi `PATCH` một trường kèm `version` và `modelVersion`.
Hồ sơ, mô hình, nhiệm vụ, nguồn hoặc trọng số đã đổi thì API trả 409 `ERR_PROPOSAL_STALE`.
Giá trị trùng hồ sơ trả 409 `ERR_PROPOSAL_NO_CHANGE` và không gọi Core.
Timeout hoặc dừng tiến trình khi ghi giữ kết quả `uncertain` và không tự gửi lại; người dùng đối chiếu lịch sử hồ sơ.

Trạng thái đi từ `pending` qua `executing` tới `succeeded`, `failed` hoặc `uncertain`.
Danh tính lấy từ JWT đã kiểm issuer và audience, không nhận từ body hay model.
Liên kết với Core dùng PKCE và state dùng một lần.

### Rà loại hồ sơ ở Hộp việc

Khi trợ lý có trọng số cho một nhiệm vụ phân loại đã khai báo, nút **Rà loại hồ sơ** rà 25 hồ sơ đủ điều kiện đầu tiên chưa rà trên trang Hộp việc đang xem.
Bấm lại để rà lượt tiếp theo.
Hồ sơ có giá trị khác gợi ý hiện **Cần rà** cùng giá trị đề xuất và cụm chữ căn cứ.
Bấm dấu **Cần rà** mở khối trợ lý trên trang chi tiết và gọi `POST /me/proposals` để tạo đề xuất cần xác nhận.
Giao diện giữ kết quả rà trong bộ nhớ tối đa 120 giây mỗi lượt.

`POST /assistant-api/v1/me/reviews` nhận `{"items":[{"model":"onestop","entity":"request","id":1}]}` với từ 1 đến 25 hồ sơ.
Trợ lý đọc lại hồ sơ và metadata thực thể bằng phiên Core của người gọi và chỉ rà hồ sơ còn mở thuộc thực thể người gọi được sửa.
API bỏ qua hồ sơ không đọc được và nhiệm vụ thiếu trọng số hoặc có tập lựa chọn khác tập nhãn.
`data.items` có một kết quả cho mỗi nhiệm vụ đủ điều kiện, gồm `model`, `entity`, `id`, `task`, `field`, `current`, `suggested`, `evidence` và `mismatch`.
Khi bộ phân loại từ chối, `suggested` là `null` và `mismatch` là `false`.
Lượt rà chỉ đọc, không tạo đề xuất và không ghi nội dung hồ sơ vào nhật ký.
API dùng cùng giới hạn 120 yêu cầu mỗi phút theo địa chỉ IP tại cổng của trợ lý.

| Endpoint dưới `/assistant-api/v1` | Chức năng |
|---|---|
| `GET /features` | Trạng thái bật dịch vụ |
| `GET /openapi.json` | OpenAPI, cần đăng nhập |
| `GET /me` | Trạng thái liên kết, danh sách bộ phân loại đã nạp và trạng thái mô hình ngôn ngữ |
| `POST /me/core/link`, `GET /oauth/callback` | Liên kết Core bằng SSO |
| `DELETE /me/core/link` | Hủy liên kết: thu hồi refresh token ở Keycloak, xóa token đã lưu, hủy các đề xuất đang chờ; trả `revoked` cho biết Keycloak có xác nhận không, kiểm Origin và danh tính |
| `POST /me/reviews` | Rà tối đa 25 hồ sơ bằng các nhiệm vụ phân loại đủ điều kiện |
| `POST /me/proposals` | Tạo đề xuất từ model, entity, id và nhiệm vụ |
| `GET /me/proposals`, `GET /me/proposals/{id}` | Lịch sử và chi tiết của chủ còn quyền đọc hồ sơ |
| `POST /me/proposals/{id}/revise` | Sửa giá trị, tạo đề xuất mới |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Xác nhận hoặc hủy, kiểm Origin và danh tính |

## Mô hình ngôn ngữ (tùy chọn)

Mục này chỉ cần cho nhiệm vụ `llm`.
Bản phát hành không kèm mô hình ngôn ngữ.

Chép [`deploy/assistant.env.example`](../deploy/assistant.env.example) thành `deploy/assistant.env` (ngoài Git) và cấu hình `ASSISTANT_INFERENCE_URL`, `ASSISTANT_MODEL`, `ASSISTANT_MODEL_REVISION` và `ASSISTANT_SOURCES_FILE`.
Trợ lý hỗ trợ mọi endpoint suy luận tương thích OpenAI Chat Completions hoặc llama.cpp `/completion`.
Người vận hành tự chọn mô hình, cấp endpoint và tự chịu trách nhiệm với điều khoản upstream.
DX-OS không chứng nhận mô hình người vận hành chọn đạt định nghĩa Open Source AI của OSI.
Nhiệm vụ `llm` trả 409 `ERR_INFERENCE_REQUIRED` khi bản cài chưa cấu hình endpoint.
Ghi chú về mô hình ngôn ngữ và llama.cpp (MIT) nằm ở [`DEPENDENCIES.txt`](../DEPENDENCIES.txt).

Endpoint do người vận hành cố định trong bản cài, người dùng không nhập endpoint, key hay model trên giao diện.
Dịch vụ dùng endpoint chat có structured output, hoặc native completion với khung prompt lấy từ template của đúng model (`ASSISTANT_PROMPT_FRAMING_FILE`, tệp JSON gồm `bos`, `systemPrefix`, `systemSuffix`, `userPrefix`, `userSuffix`, `assistantPrefix` và `stop`).
Một lượt suy luận chạy tại một thời điểm, chặn redirect, giới hạn input, output và timeout.
Timeout tối đa 180 giây, cổng vào chờ 240 giây và giao diện chờ 270 giây cho một lượt tạo đề xuất.
Đổi model, trọng số hoặc khung prompt thì cập nhật `ASSISTANT_MODEL_REVISION` và khởi động lại trợ lý, để các đề xuất cũ hết hiệu lực.

Model chỉ nhận các trường mà nhiệm vụ khai báo và các nguồn liên quan.
Nó không nhận token của Core, không có công cụ shell, filesystem hay quyền gọi HTTP.
Tắt inference không ảnh hưởng Core, form nghiệp vụ hay nhiệm vụ phân loại.

Model trả `status`, `value`, `sources` và `checks`.
Server chỉ tạo đề xuất khi `status` là `proposed`, giá trị đúng kiểu của trường, và mọi nguồn nằm trong danh mục của nhiệm vụ.
Thiếu căn cứ thì không có đề xuất; JSON hỏng, lựa chọn ngoài danh sách hoặc nguồn bịa bị từ chối.
`checks` là giá trị mà nguồn cho là đúng cho từng trường đối chiếu (choice hoặc unit đơn trị).
Chỗ khác với hồ sơ hiện tại thành `warnings` (`{field, expected, actual}`) để người dùng kiểm tra, không bao giờ thành lệnh ghi.
Căn cứ hiển thị là văn bản nguồn đã cung cấp, không phải lời tự đánh giá của model, và người dùng vẫn phải đối chiếu nội dung trước khi xác nhận.

Danh mục nguồn là một mảng JSON ngoài Git, do `ASSISTANT_SOURCES_FILE` chỉ tới.
Mỗi mục có `id`, `title`, `revision`, `text` (tối đa 12.000 ký tự) và `license` (CC0-1.0, CC-BY-4.0 hoặc ODbL-1.0).
Nhiệm vụ tham chiếu nguồn theo `id`; thiếu nguồn thì nhiệm vụ báo chưa cấu hình, trợ lý không tự lấy tài liệu từ Internet.
Nguồn được nạp một lần khi khởi động; đổi nội dung thì đổi `revision` và khởi động lại.
Tệp nguồn, khung prompt và key đặt trong `deploy/data/assistant/private` (là `/run/secrets` trong container), key inference có mode 0600.
Bản cài tự chuẩn bị nguồn có giấy phép hợp lệ vì dữ liệu demo nằm ngoài Git.

## Mạng và đường ra

Container `assistant-control` chạy trên network nội bộ `assistant`, không có đường ra Internet và không thấy các dịch vụ khác của stack.
Chỉ `backend`, `keycloak`, `apisix` và `assistant-egress` nằm trên network đó.
`assistant-egress` (cùng image, không thêm thành phần) là đường ra duy nhất và là proxy chuyển tiếp chỉ tới host và cổng của `ASSISTANT_INFERENCE_URL`.
Với endpoint `http`, nó chỉ cho `POST` tới `/v1/chat/completions` và `/completion`; với `https`, nó chỉ cho kết nối tới đúng host và cổng đó.
Đích khác bị từ chối, và chưa đặt `ASSISTANT_INFERENCE_URL` thì không đi đâu được.

## State và giới hạn

State là một SQLite chứa token liên kết đã mã hóa theo chủ, đề xuất và phiên liên kết đang chờ.
Master key ở `deploy/data/assistant/private/master.key` chỉ giải mã state trên máy này.
`deploy/backup.sh` không sao lưu state của trợ lý; sau khi khôi phục Core, người dùng liên kết lại tài khoản.
State có sẵn mà thiếu hoặc sai master key thì trợ lý không khởi động; xóa `deploy/data/assistant` rồi bật lại để tạo state mới.

Một trợ lý phục vụ nhiều người, mỗi người có state riêng; không chạy nhiều bản trên cùng SQLite.
Các nhiệm vụ `llm` dùng chung một slot suy luận.
Số đo nghiệm thu của bộ phân loại lấy trên câu do đội viết, chưa trên hồ sơ thật.
Hỏi đáp mở, embedding, reranker và tác tử tự tạo đề xuất theo sự kiện chưa có.

## Thu hồi và thay khóa

- **Hủy liên kết của một người:** nút "Hủy liên kết" ở trang Trợ lý nghiệp vụ, hoặc `DELETE /assistant-api/v1/me/core/link`.
  Khóa hoặc xóa tài khoản ở Keycloak cũng chặn trợ lý: lần làm mới token kế tiếp bị từ chối và token đã lưu bị xóa.
- **Thay master key:** dừng trợ lý, chạy lệnh dưới, rồi bật lại.
  Lệnh mã hóa lại toàn bộ state bằng khóa mới trong một giao dịch rồi mới thay `master.key`; bị ngắt giữa chừng thì chạy lại lệnh.
  Các phiên liên kết đang dở bị xóa, người dùng bắt đầu liên kết lại.

  ```sh
  docker compose stop assistant-control
  docker compose run --rm assistant-init dist/rotate.js /bootstrap
  docker compose up -d
  ```

- **Thay khóa gọi inference:** ghi khóa mới vào tệp của `ASSISTANT_INFERENCE_KEY_FILE` (mode 0600) rồi `docker compose restart assistant-control`.
  Đổi đường dẫn tệp hoặc biến trong `deploy/assistant.env` thì chạy `docker compose up -d --force-recreate assistant-control assistant-egress`.

## Kiểm tra

Workflow `Assistant` (`.github/workflows/ci-assistant.yml`) chạy khi đổi `assistant/`, `tools/classifier/`, tệp compose, tệp SSO hay SBOM của trợ lý.
Nó build và test dịch vụ không cần key, so SBOM với tệp khóa, kiểm tệp compose có và không có `assistant.env`, và kiểm route cổng ở mọi chế độ.
Các workflow khác không chạy và không phụ thuộc trợ lý.
Chạy `PYTHONDONTWRITEBYTECODE=1 python3 tools/classifier/make_parity_fixture.py` từ gốc repo để sinh lại bộ đối chiếu khi đổi trọng số hoặc dữ liệu huấn luyện.
Tệp đối chiếu `assistant/test/fixtures/classifier.json` lấy câu gốc và biến thể đã dùng để huấn luyện do HaUI-ImplSolution biên soạn theo CC-BY-4.0.
Lệnh `npm test` trong thư mục `assistant` kiểm nhãn, xác suất và cụm chữ của Node với Python.
Hai bài kiểm trình duyệt ở `e2e/web/tests/assistant.spec.ts` tự bỏ qua khi không đặt `E2E_ASSISTANT_RECORD_PATH` (hồ sơ mà đề xuất khác giá trị hiện tại) hoặc `E2E_ASSISTANT_UNCHANGED_RECORD_PATH` (hồ sơ mà đề xuất trùng giá trị hiện tại).

## Gỡ trợ lý

Tắt tạm thì làm như bước 4 ở trên; Core, sao lưu và cập nhật chạy như bình thường.
Gỡ hẳn khỏi mã nguồn thì làm theo ba nhóm sau.

**Xóa cả tệp hoặc thư mục**

- `assistant/`, `sbom/assistant.cdx.json`
- `tools/classifier/` (dữ liệu, huấn luyện và trọng số của bộ phân loại) và `LICENSES/CC-BY-4.0.txt`
- `deploy/docker-compose.assistant.yml`, `deploy/assistant-sso.sh`, `deploy/assistant.env.example`
- `.github/workflows/ci-assistant.yml`
- `frontend/src/assistant/`
- `e2e/web/tests/assistant.spec.ts`

**Xóa một khối hoặc một dòng trong tệp chung**

- `.github/dependabot.yml`: mục "The optional assistant".
- `.github/workflows/release-images.yml`: biến `OPTIONAL`, chữ `assistant` ở phần mô tả và nhánh đặt build context gốc repo cho `assistant`.
- `deploy/apisix/common.yaml`: khối giữa `# assistant:begin` và `# assistant:end`.
- `deploy/apisix/render.lua`: mục `assistant-api` trong `public` và mục `assistant` trong `excluded`.
- `deploy/dxos`: đoạn bỏ qua `assistant-*` trong `pending_services`.
- `deploy/update.sh`: các dòng thêm `assistant-control`.
- `frontend/src/admin/AdminApp.tsx`: import, `useAssistantEnabled`, mục menu, route và `lazy`.
- `frontend/src/admin/resources.tsx`: `RecordAssistant`.
- `frontend/src/admin/InboxPage.tsx`: nút "Rà loại hồ sơ" và phần làm nổi hồ sơ cần rà.
- `frontend/src/shell/en/index.ts`: `assistantEn`.
- Giấy phép, văn bản: `DEPENDENCIES.txt`, `NOTICE` (cả bản trong `*/licenses/`), hai khối bộ phân loại trong `REUSE.toml`, `sbom/README.md`, `website/docs/dependencies.md`, `website/docs/pof-audit.md`, `CONTRIBUTING.md` và `.github/PULL_REQUEST_TEMPLATE.md`.
- Giấy phép, dữ liệu máy đọc trong `.github/licenses/`: `component-catalog.json` (mô tả của `node` và các tệp trọng số), `license-inventory.json` (các mục `llama.cpp`, `DX-OS record classifier weights`, `DX-OS record classifier data` và `MASSIVE vi-VN`) và `dependency-exceptions.json` (hai mục `DX-OS record classifier data` và `MASSIVE vi-VN`).
- `.github/scripts/check_licenses.py`: `CLASSIFIER_DATA_PATHS`, và `assistant` trong danh sách dự án của `check_declared_licenses`, `LEGAL_CONTEXTS`, `SHIPPED_BASES` và `COPIED_SCRIPTS`.
- `.github/scripts/check_license_inventory.py`: `assistant` trong `NPM_PROJECTS`, rồi chạy script với `generate` để sinh lại `docs/DEPENDENCY_INVENTORY.md`.
- `.github/scripts/check_image_packages.py`: chữ `node` trong câu về lớp nền được phân phối lại, rồi chạy script với `generate` để sinh lại `docs/IMAGE_PACKAGES.md`.
- Tài liệu: hai `README`, `ARCHITECTURE.md`, `CHANGELOG.md`, `ROADMAP.md`, `docs/OPERATIONS.md`, `docs/TESTING.md`, `docs/DEMO.md`, `docs/TRINH_BAY.md`, `docs/README.md`, `docs/DX_OS_TEXTBOOK.md`, `docs/dynamic-core/README.md`, `docs/dynamic-core/BACKLOG.md`, `docs/dynamic-core/DANH_GIA.md`, `website/docs/council-qa.md`, `website/docs/evidence-matrix.md` và `website/docs/huong-dan/nguoi-xu-ly.md`.
- Dữ liệu còn lại: `deploy/data/assistant` và client `dxos-assistant` trong Keycloak.

**Khai báo `ai:` trong mô hình (thuộc lõi, có thể giữ)**

Khối `ai.tasks` là một phần của định dạng mô hình; không có trợ lý thì nó không làm gì.
Muốn gỡ luôn thì xóa:

- `dx-model`: `Model.AiTask`, `ModelParser.readAiTasks`, lược đồ `ai` và phần `ai` trong `ModelUpgraderTest`.
- `dx-engine`: `CHANGE_AI_TASKS` ở `ModelDiff`, `aiTasks` ở `MetaController`, khối `ai:` của ba mẫu `onestop`, `support_desk` và `clinic_appointment`.
- `frontend`: tab trợ lý trong `ModelForm`, các mục `ai` trong `formGuide`, `labels` và `backendMessages`, `AiTask` trong `admin/types.ts`.
- `e2e`: `ai_task_changes` ở `check_process.py` và dòng `input_fields` ở `check_demo.py`.
- Docs: mục "Nhiệm vụ trợ lý nghiệp vụ" của `docs/MODEL.md`.

Ghi có điều kiện bằng `version` và `modelVersion` là tính năng chung của API, không thuộc phần gỡ này.
