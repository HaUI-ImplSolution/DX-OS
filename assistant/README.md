# Trợ lý nghiệp vụ (tùy chọn)

Dịch vụ độc lập đề xuất một giá trị cho một trường của hồ sơ, dựa trên các trường đầu vào của hồ sơ và nguồn tham khảo.
Người dùng xem căn cứ, sửa hoặc xác nhận trên giao diện hồ sơ.
Core kiểm quyền, quy tắc và phiên bản trước khi ghi, nên model không có đường ghi trực tiếp.
Inference do người vận hành cấu hình và chạy tại máy chủ của bản cài.

## Bật, cấu hình và tắt

Trợ lý tắt mặc định: chưa bật thì stack không có container hay route của trợ lý.

1. Đặt `COMPOSE_FILE=docker-compose.yml:docker-compose.assistant.yml` trong `deploy/.env`, rồi chạy `dxos start`.
2. Chép [`deploy/assistant.env.example`](../deploy/assistant.env.example) thành `deploy/assistant.env` (ngoài Git) và đặt inference, nguồn tham khảo trong đó.
   Tối thiểu cần `ASSISTANT_INFERENCE_URL`, `ASSISTANT_MODEL`, `ASSISTANT_MODEL_REVISION` và `ASSISTANT_SOURCES_FILE`.
   Giới hạn RAM và CPU của container nằm trong `deploy/docker-compose.assistant.yml`.
3. Khai báo `ai.tasks` cho thực thể trong tệp mô hình (hoặc tab trợ lý của bảng điều khiển) rồi áp dụng; cú pháp ở [MODEL](../docs/MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ).
   Hai mẫu `onestop` và `support_desk` có sẵn nhiệm vụ.
4. Người dùng mở **Trợ lý nghiệp vụ**, liên kết tài khoản DX-OS, rồi mở một hồ sơ đã lưu, chọn nhiệm vụ và tạo đề xuất.
   Sửa giá trị thì đề xuất được tạo lại để xem lại, sau đó xác nhận lưu bằng một thao tác riêng.
5. Tắt bằng cách bỏ dòng `COMPOSE_FILE` rồi chạy `docker compose up -d --remove-orphans`.

Nếu mật khẩu quản trị Keycloak trong `deploy/.env` không còn đúng, `assistant-sso` chỉ ghi cảnh báo và trợ lý không liên kết được tài khoản.
Xem `docker compose logs assistant-sso`, sửa `deploy/.env` rồi chạy lại `docker compose up -d`.

Khi đã bật, `dxos update` kéo cả image trợ lý.
Image có sau khi chạy workflow `Release Images` với `services=assistant`.
Image chỉ chứa Node.js và mã của trợ lý, không có npm, corepack và yarn.

## Inference và nguồn tham khảo

Trợ lý hỗ trợ mọi endpoint suy luận tương thích OpenAI Chat Completions hoặc llama.cpp `/completion`.
DX-OS không áp đặt hay đóng gói model mặc định; người vận hành tự chọn model, cấp endpoint và tự chịu trách nhiệm với điều khoản upstream.
Bản cài có thể tham khảo dùng Apertus-8B-Instruct-2509 (Apache-2.0 kèm AUP) hoặc OLMo (Apache-2.0) qua llama.cpp (MIT).
Giấy phép và ghi chú nằm ở [`DEPENDENCIES.txt`](../DEPENDENCIES.txt), mục [9] và [10].
Repository không đóng gói trọng số và không tải trọng số trong request hay lúc Core khởi động.

Endpoint do người vận hành cố định trong bản cài, người dùng không nhập endpoint, key hay model trên giao diện.
Dịch vụ dùng endpoint chat có structured output, hoặc native completion với khung prompt lấy từ template của đúng model (`ASSISTANT_PROMPT_FRAMING_FILE`, tệp JSON gồm `bos`, `systemPrefix`, `systemSuffix`, `userPrefix`, `userSuffix`, `assistantPrefix` và `stop`).
Một lượt suy luận chạy tại một thời điểm, chặn redirect, giới hạn input, output và timeout.
Timeout tối đa 180 giây, cổng vào chờ 240 giây và giao diện chờ 270 giây cho một lượt tạo đề xuất.
Đổi model, trọng số hoặc khung prompt thì cập nhật `ASSISTANT_MODEL_REVISION` và khởi động lại trợ lý, để các đề xuất cũ hết hiệu lực.

Model chỉ nhận các trường mà nhiệm vụ khai báo và các nguồn liên quan.
Nó không nhận token của Core, không có công cụ shell, filesystem hay quyền gọi HTTP.
Tắt inference không ảnh hưởng Core hay form nghiệp vụ.

Container `assistant-control` chạy trên network nội bộ `assistant`, không có đường ra Internet và không thấy các dịch vụ khác của stack.
Chỉ `backend`, `keycloak`, `apisix` và `assistant-egress` nằm trên network đó.
`assistant-egress` (cùng image, không thêm thành phần) là đường ra duy nhất và là proxy chuyển tiếp chỉ tới host và cổng của `ASSISTANT_INFERENCE_URL`.
Với endpoint `http`, nó chỉ cho `POST` tới `/v1/chat/completions` và `/completion`; với `https`, nó chỉ cho kết nối tới đúng host và cổng đó.
Đích khác bị từ chối, và chưa đặt `ASSISTANT_INFERENCE_URL` thì không đi đâu được.

Danh mục nguồn là một mảng JSON ngoài Git, do `ASSISTANT_SOURCES_FILE` chỉ tới.
Mỗi mục có `id`, `title`, `revision`, `text` (tối đa 12.000 ký tự) và `license` (CC0-1.0, CC-BY-4.0 hoặc ODbL-1.0).
Nhiệm vụ tham chiếu nguồn theo `id`; thiếu nguồn thì nhiệm vụ báo chưa cấu hình, trợ lý không tự lấy tài liệu từ Internet.
Nguồn được nạp một lần khi khởi động; đổi nội dung thì đổi `revision` và khởi động lại.
Tệp nguồn, khung prompt và key đặt trong `deploy/data/assistant/private` (là `/run/secrets` trong container), key inference có mode 0600.
Bản cài tự chuẩn bị nguồn có giấy phép hợp lệ vì dữ liệu demo nằm ngoài Git.

## Đề xuất và xác nhận

Model trả `status`, `value`, `sources` và `checks`.
Server chỉ tạo đề xuất khi `status` là `proposed`, giá trị đúng kiểu của trường, và mọi nguồn nằm trong danh mục của nhiệm vụ.
Thiếu căn cứ thì không có đề xuất; JSON hỏng, lựa chọn ngoài danh sách hoặc nguồn bịa bị từ chối.
`checks` là giá trị mà nguồn cho là đúng cho từng trường đối chiếu (choice hoặc unit đơn trị).
Chỗ khác với hồ sơ hiện tại thành `warnings` (`{field, expected, actual}`) để người dùng kiểm tra, không bao giờ thành lệnh ghi.
Căn cứ hiển thị là văn bản nguồn đã cung cấp, không phải lời tự đánh giá của model, và người dùng vẫn phải đối chiếu nội dung trước khi xác nhận.

Đề xuất thuộc về một chủ, hết hạn sau 120 giây và được xóa sau 30 ngày.
Nó ghi lại hồ sơ, nhiệm vụ, giá trị, nguồn, phiên bản hồ sơ và mô hình, và revision của nguồn và model.
Sửa nội dung tạo đề xuất mới và vô hiệu đề xuất cũ.
Khi xác nhận, trợ lý đọc lại hồ sơ và cấu hình, rồi chỉ gửi `PATCH` một trường kèm `version` và `modelVersion`.
Hồ sơ, mô hình, nhiệm vụ hay nguồn đã đổi thì trả 409 `ERR_PROPOSAL_STALE`.
Giá trị trùng hồ sơ trả 409 `ERR_PROPOSAL_NO_CHANGE` và không gọi Core.
Timeout hoặc dừng tiến trình khi ghi giữ kết quả `uncertain` và không tự gửi lại; người dùng đối chiếu lịch sử hồ sơ.

Trạng thái đi từ `pending` qua `executing` tới `succeeded`, `failed` hoặc `uncertain`.
Danh tính lấy từ JWT đã kiểm issuer và audience, không nhận từ body hay model.
Liên kết với Core dùng PKCE và state dùng một lần.

| Endpoint dưới `/assistant-api/v1` | Chức năng |
|---|---|
| `GET /features` | Trạng thái bật dịch vụ |
| `GET /openapi.json` | OpenAPI, cần đăng nhập |
| `GET /me` | Trạng thái liên kết và cấu hình model |
| `POST /me/core/link`, `GET /oauth/callback` | Liên kết Core bằng SSO |
| `DELETE /me/core/link` | Hủy liên kết: thu hồi refresh token ở Keycloak, xóa token đã lưu, hủy các đề xuất đang chờ; trả `revoked` cho biết Keycloak có xác nhận không, kiểm Origin và danh tính |
| `POST /me/proposals` | Tạo đề xuất từ model, entity, id và nhiệm vụ |
| `GET /me/proposals`, `GET /me/proposals/{id}` | Lịch sử và chi tiết của chủ còn quyền đọc hồ sơ |
| `POST /me/proposals/{id}/revise` | Sửa giá trị, tạo đề xuất mới |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Xác nhận hoặc hủy, kiểm Origin và danh tính |

## State và giới hạn

State là một SQLite chứa token liên kết đã mã hóa theo chủ, đề xuất và phiên liên kết đang chờ.
Master key ở `deploy/data/assistant/private/master.key` chỉ giải mã state trên máy này.
`deploy/backup.sh` không sao lưu state của trợ lý; sau khi khôi phục Core, người dùng liên kết lại tài khoản.
State có sẵn mà thiếu hoặc sai master key thì trợ lý không khởi động; xóa `deploy/data/assistant` rồi bật lại để tạo state mới.

Một trợ lý phục vụ nhiều người, mỗi người có state riêng và dùng chung một slot suy luận; không chạy nhiều bản trên cùng SQLite.
Hỏi đáp mở, embedding, reranker và tác tử tự tạo đề xuất theo sự kiện chưa có.
Model tự host và trọng số Apache-2.0 chưa chứng minh model đạt định nghĩa Open Source AI của OSI; việc này còn mở ở [BACKLOG](../docs/dynamic-core/BACKLOG.md).

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

Workflow `Assistant` (`.github/workflows/ci-assistant.yml`) chạy khi đổi `assistant/`, tệp compose, tệp SSO hay SBOM của trợ lý.
Nó build và test dịch vụ không cần key, so SBOM với tệp khóa, kiểm tệp compose có và không có `assistant.env`, và kiểm route cổng ở mọi chế độ.
Các workflow khác không chạy và không phụ thuộc trợ lý.
Hai bài kiểm trình duyệt ở `e2e/web/tests/assistant.spec.ts` tự bỏ qua khi không đặt `E2E_ASSISTANT_RECORD_PATH` (hồ sơ mà đề xuất khác giá trị hiện tại) hoặc `E2E_ASSISTANT_UNCHANGED_RECORD_PATH` (hồ sơ mà đề xuất trùng giá trị hiện tại).

## Đo độ đúng của đề xuất

Một lệnh chạy trợ lý trên bộ mẫu cố định và in số đề xuất đúng, sai, từ chối đúng và từ chối nhầm.
Người vận hành dùng số này để chọn model và để so lại khi đổi model, khung prompt hay nguồn tham khảo.

Bộ mẫu ở [`benchmark/suites.json`](./benchmark/suites.json) gồm 28 hồ sơ: 12 theo nhiệm vụ của mẫu `onestop`, 9 theo mẫu `support_desk` và 7 về quan hệ giữa hai số.
Hồ sơ, nguồn (CC0-1.0) và đáp án do dự án tự viết cho bộ đo, không lấy từ dữ liệu thật.
Bộ mẫu có cả ca khó: lý do chứa từ khóa của loại khác, lời nhấn mạnh như "khẩn cấp", lệnh nằm trong lý do, hai số bằng nhau, và hồ sơ thiếu dữ kiện mà trợ lý phải từ chối.
Lệnh chạy đúng mã tạo đề xuất của trợ lý (lời nhắc, lược đồ, kiểm đầu ra và đối chiếu) trên một Core giả trong bộ nhớ, nên không cần stack, tài khoản hay hồ sơ thật.

Chạy từ mã nguồn, với các biến của `deploy/assistant.env`:

```sh
cd assistant && npm ci
ASSISTANT_INFERENCE_URL=http://127.0.0.1:8080 ASSISTANT_MODEL=<tên model> ASSISTANT_MODEL_REVISION=<revision> npm run benchmark
```

Hoặc trong container của bản cài đã bật trợ lý, dùng đúng cấu hình đang chạy: `docker compose run --rm assistant-control dist/benchmark.js`.
`--suite onestop`, `--suite support` hoặc `--suite numeric` chỉ chạy một bộ, `--cases <tệp>` dùng bộ mẫu khác cùng khuôn, và `--json` in kết quả từng hồ sơ cho máy đọc.
Cấu hình khung prompt, khóa, thời gian chờ và số token lấy từ cùng các biến `ASSISTANT_*` như dịch vụ.

Mỗi hồ sơ được xếp vào một loại:

| Kết quả | Ý nghĩa |
|---|---|
| đúng | Đề xuất đúng giá trị của đáp án |
| sai | Đề xuất một giá trị khác đáp án |
| đoán khi thiếu dữ kiện | Đề xuất một giá trị trong khi đáp án là từ chối |
| từ chối đúng | Từ chối đúng chỗ đáp án là từ chối |
| từ chối nhầm | Từ chối trong khi đáp án có giá trị |
| không có đầu ra hợp lệ | Model trả đầu ra mà các kiểm tra của trợ lý loại, bị cắt giữa chừng hoặc quá thời gian chờ |

Cuối kết quả có số đạt (đúng cộng từ chối đúng) trên tổng và theo từng bộ, và số đối chiếu: trong các hồ sơ có đề xuất, bao nhiêu hồ sơ có cảnh báo đơn vị đúng như đáp án, bỏ sót hoặc thừa.
Kết quả ghi model, revision, kiểu gọi, mã băm của bộ mẫu và thời gian chạy, nên hai lần đo chỉ so được khi cùng bộ mẫu.
Mọi hồ sơ đều được in, kể cả hồ sơ làm sai; lệnh không bỏ ca khó để số đẹp hơn.

Chưa có model chạy được thì lệnh in lý do và thoát với mã 2, không in số: thiếu `ASSISTANT_INFERENCE_URL`, `ASSISTANT_MODEL` hoặc `ASSISTANT_MODEL_REVISION`, hoặc model không trả lời được ba hồ sơ đầu.
Lệnh chỉ chạy khi có model tự host, không chạy trong CI chung, và không ghi gì vào Core hay vào state của trợ lý.
Số đo chỉ nói về bộ mẫu này, không thay cho việc người duyệt đối chiếu nguồn trước khi xác nhận.

## Gỡ trợ lý

Tắt tạm thì làm như bước 5 ở trên; Core, sao lưu và cập nhật chạy như bình thường.
Gỡ hẳn khỏi mã nguồn thì làm theo ba nhóm sau.

**Xóa cả tệp hoặc thư mục**

- `assistant/` (gồm `benchmark/`), `sbom/assistant.cdx.json`
- `deploy/docker-compose.assistant.yml`, `deploy/assistant-sso.sh`, `deploy/assistant.env.example`
- `.github/workflows/ci-assistant.yml`
- `frontend/src/assistant/`
- `e2e/web/tests/assistant.spec.ts`

**Xóa một khối hoặc một dòng trong tệp chung**

- `.github/dependabot.yml`: mục "The optional assistant".
- `.github/workflows/release-images.yml`: biến `OPTIONAL` và chữ `assistant` ở phần mô tả.
- `deploy/apisix/common.yaml`: khối giữa `# assistant:begin` và `# assistant:end`.
- `deploy/apisix/render.lua`: mục `assistant-api` trong `public` và mục `assistant` trong `excluded`.
- `deploy/dxos`: đoạn bỏ qua `assistant-*` trong `pending_services`.
- `deploy/update.sh`: các dòng thêm `assistant-control`.
- `frontend/src/admin/AdminApp.tsx`: import, `useAssistantEnabled`, mục menu, route và `lazy`.
- `frontend/src/admin/resources.tsx`: `RecordAssistant`.
- `frontend/src/shell/en/index.ts`: `assistantEn`.
- Giấy phép: `DEPENDENCIES.txt`, `NOTICE`, `.github/component-catalog.json` (mô tả của `node`), `sbom/README.md`, `website/docs/dependencies.md`.
- Tài liệu: hai `README`, `ARCHITECTURE.md`, `CHANGELOG.md`, `ROADMAP.md`, `docs/OPERATIONS.md`, `docs/TESTING.md`, `docs/DEMO.md`, `docs/README.md`, `docs/DX_OS_TEXTBOOK.md`, `docs/dynamic-core/README.md`, `docs/dynamic-core/BACKLOG.md`, `docs/dynamic-core/DANH_GIA.md`, `website/docs/council-qa.md` và `website/docs/evidence-matrix.md`.
- Dữ liệu còn lại: `deploy/data/assistant` và client `dxos-assistant` trong Keycloak.

**Khai báo `ai:` trong mô hình (thuộc lõi, có thể giữ)**

Khối `ai.tasks` là một phần của định dạng mô hình; không có trợ lý thì nó không làm gì.
Muốn gỡ luôn thì xóa:

- `dx-model`: `Model.AiTask`, `ModelParser.readAiTasks`, lược đồ `ai` và phần `ai` trong `ModelUpgraderTest`.
- `dx-engine`: `CHANGE_AI_TASKS` ở `ModelDiff`, `aiTasks` ở `MetaController`, khối `ai:` của hai mẫu `onestop` và `support_desk`.
- `frontend`: tab trợ lý trong `ModelForm`, các mục `ai` trong `formGuide`, `labels` và `backendMessages`, `AiTask` trong `admin/types.ts`.
- `e2e`: `ai_task_changes` ở `check_process.py` và dòng `input_fields` ở `check_demo.py`.
- Docs: mục "Nhiệm vụ trợ lý nghiệp vụ" của `docs/MODEL.md`.

Ghi có điều kiện bằng `version` và `modelVersion` là tính năng chung của API, không thuộc phần gỡ này.
