# Trợ lý nghiệp vụ

Dịch vụ độc lập đề xuất một giá trị cho một trường của hồ sơ.
Mô hình của trợ lý là bộ phân loại hồ sơ tiếng Việt do đội tự huấn luyện, chạy trên CPU ngay trong trợ lý.
Người dùng xem căn cứ, sửa hoặc xác nhận trên giao diện hồ sơ.
Core kiểm quyền, quy tắc và phiên bản trước khi ghi, nên model không có đường ghi trực tiếp.

## Chạy, cấu hình và tắt

Trợ lý chạy cùng stack ở chế độ full (nhóm `app`), nhẹ vì suy luận bằng CPU trong tiến trình; chế độ core không có container hay route của trợ lý.

1. Chạy `dxos start`; trợ lý lên cùng web, cổng và giám sát.
2. Khai báo `ai.tasks` cho thực thể trong tệp mô hình (hoặc tab trợ lý của bảng điều khiển) rồi áp dụng; cú pháp ở [MODEL](../docs/MODEL.md#nhiệm-vụ-trợ-lý-nghiệp-vụ).
   Các mẫu `onestop`, `support_desk` và `clinic_appointment` có sẵn nhiệm vụ phân loại, dùng ngay khi trợ lý chạy.
3. Người dùng mở **Trợ lý nghiệp vụ**, liên kết tài khoản DX-OS, rồi mở một hồ sơ đã lưu, chọn nhiệm vụ và tạo đề xuất.
   Sửa giá trị thì đề xuất được tạo lại để xem lại, sau đó xác nhận lưu bằng một thao tác riêng.
4. Chạy `dxos start --core` thì trợ lý không chạy; `dxos start --full` đưa nó trở lại.

Trợ lý không giữ nền tảng: `dxos start`, `dxos update` và `reset.sh` khởi động nó sau nền tảng, nên trợ lý lỗi hay bị dừng chỉ có cảnh báo, Core và ứng dụng vẫn chạy bình thường.
Giao diện chỉ hiện mục Trợ lý khi dịch vụ trả lời, nên dừng `assistant-control` là ẩn mục đó.

Trợ lý chạy được không cần tệp cấu hình riêng; hai biến tùy chọn `ASSISTANT_CLASSIFIERS_DIR` và `ASSISTANT_OIDC_REVOKE_URL` ở mục 6 của [`deploy/.env.example`](../deploy/.env.example).
Giới hạn RAM và CPU của container nằm ở dịch vụ `assistant-control` trong `deploy/docker-compose.yml`.

Nếu mật khẩu quản trị Keycloak trong `deploy/.env` không còn đúng, `assistant-sso` chỉ ghi cảnh báo và trợ lý không liên kết được tài khoản.
Xem `docker compose logs assistant-sso`, sửa `deploy/.env` rồi chạy lại `docker compose up -d`.

`dxos update` kéo cả image trợ lý ở chế độ full.
Image có sau khi chạy workflow `Release Images` (cùng `services=all`).
Image chạy bằng Node.js và chứa mã trợ lý cùng các trọng số phân loại.
Dựng image từ mã nguồn bằng `docker compose build assistant-init` trong `deploy/`.
Dựng thẳng bằng `docker build -f assistant/Dockerfile .` ở gốc repo thì cần BuildKit.

## Bộ phân loại

Image đóng gói ba bộ phân loại mà các mẫu dùng, đều đạt điều kiện phát hành:

| Bộ phân loại | Mẫu | Đọc trường | Gợi ý cho trường |
|---|---|---|---|
| `onestop` | `onestop` | `reason` | `kind` |
| `support` | `support_desk` | `title`, `detail` | `priority` |
| `clinic` | `clinic_appointment` | `note` | `service` |

Dữ liệu, cách huấn luyện và trọng số ở [`tools/classifier/`](../tools/classifier/README.md); số đo và giới hạn ở [thẻ mô hình](../tools/classifier/MODEL_CARD.md).
Tệp mô hình chỉ chọn được bộ phân loại đã có trọng số; nghiệp vụ khác cần dữ liệu và huấn luyện riêng.

Trợ lý nạp trọng số từ `/app/classifiers` khi khởi động.
Đặt `ASSISTANT_CLASSIFIERS_DIR` để nạp từ thư mục khác bên trong container, ví dụ `/run/secrets/classifiers` nếu đặt tệp dưới `deploy/data/assistant/private/classifiers`.
Mỗi tệp có tên `<classifier>.json`, với `<classifier>` khớp tên nhiệm vụ trong tệp trọng số.
Trợ lý báo `configured` khi nạp được ít nhất một bộ phân loại.
`GET /me` trả `model.classifiers` gồm các tên đã nạp.

Nhiệm vụ đọc các trường chữ theo thứ tự `input_fields`.
Mẫu hỗ trợ kỹ thuật khai `title` rồi `detail` theo thứ tự huấn luyện.
Kết quả từ chối trả `insufficient: true` và `proposal: null`.
Đề xuất lưu `evidence` gồm một đến ba cụm chữ trích từ các trường đầu vào và `confidence` là xác suất của nhãn gợi ý.
Giao diện hiển thị các cụm chữ trong mục **Căn cứ trong hồ sơ** kèm độ tự tin theo phần trăm.
Sửa giá trị khác gợi ý thì đề xuất mới không còn `confidence`.

Thiếu hoặc không đọc được trọng số của nhiệm vụ thì API trả 409 `ERR_CLASSIFIER_UNAVAILABLE` cho nhiệm vụ đó.
Các lựa chọn của trường đầu ra phải khớp tập nhãn nghiệp vụ của tệp trọng số, nếu sai thì API trả 409 `ERR_CLASSIFIER_LABELS`.
Revision của nhiệm vụ bao gồm SHA-256 của tệp trọng số.
Thay tệp và khởi động lại trợ lý làm các đề xuất dùng trọng số cũ hết hiệu lực.

## Đề xuất và xác nhận

Đề xuất thuộc về một chủ, hết hạn sau 120 giây và được xóa sau 30 ngày.
Đề xuất ghi lại hồ sơ, nhiệm vụ, giá trị, căn cứ, phiên bản hồ sơ, phiên bản mô hình và revision của nhiệm vụ.
Sửa nội dung tạo đề xuất mới và vô hiệu đề xuất cũ.
Khi xác nhận, trợ lý đọc lại hồ sơ và cấu hình, rồi chỉ gửi `PATCH` một trường kèm `version` và `modelVersion`.
Hồ sơ, mô hình, nhiệm vụ hoặc trọng số đã đổi thì API trả 409 `ERR_PROPOSAL_STALE`.
Giá trị trùng hồ sơ trả 409 `ERR_PROPOSAL_NO_CHANGE` và không gọi Core.
Timeout hoặc dừng tiến trình khi ghi giữ kết quả `uncertain` và không tự gửi lại; người dùng đối chiếu lịch sử hồ sơ.

Trạng thái đi từ `pending` qua `executing` tới `succeeded`, `failed` hoặc `uncertain`.
Danh tính lấy từ JWT đã kiểm issuer và audience, không nhận từ body hay model.
Liên kết với Core dùng PKCE và state dùng một lần.

### Rà loại hồ sơ ở Hộp việc

Khi trợ lý có trọng số cho một nhiệm vụ phân loại đã khai báo, nút **Rà loại hồ sơ** rà 25 hồ sơ đủ điều kiện đầu tiên chưa rà trên trang Hộp việc đang xem.
Bấm lại để rà lượt tiếp theo.
Hồ sơ có giá trị khác gợi ý hiện **Cần rà** cùng giá trị đề xuất, độ tự tin và cụm chữ căn cứ.
Bấm dấu **Cần rà** mở khối trợ lý trên trang chi tiết và gọi `POST /me/proposals` để tạo đề xuất cần xác nhận.
Giao diện giữ kết quả rà trong bộ nhớ tối đa 120 giây mỗi lượt, còn nguyên khi chuyển trang trong ứng dụng, và xóa khi đăng xuất hoặc hết phiên.
Hồ sơ không đọc được được đếm riêng và rà lại được ở lượt sau.

`POST /assistant-api/v1/me/reviews` nhận từ 1 đến 25 hồ sơ.
Trợ lý đọc hồ sơ bằng phiên Core của người gọi, đọc metadata một lần cho mỗi thực thể, gửi tối đa 5 yêu cầu đồng thời và chỉ rà hồ sơ còn mở thuộc thực thể người gọi được sửa.
Hồ sơ gặp lỗi riêng (không có quyền, không tồn tại, bị giới hạn tốc độ, lỗi máy chủ hoặc quá thời gian chờ) được đếm ở `data.skipped`.
Lỗi phiên (chưa liên kết, liên kết hết hạn, Core trả 401) làm cả lượt trả lỗi.
API bỏ qua nhiệm vụ thiếu trọng số hoặc có tập lựa chọn khác tập nhãn.
`data.items` có một kết quả cho mỗi nhiệm vụ đủ điều kiện, gồm `model`, `entity`, `id`, `task`, `field`, `current`, `suggested`, `evidence`, `mismatch` và `confidence` khi có gợi ý.
Khi bộ phân loại từ chối, `suggested` là `null` và `mismatch` là `false`.
Lượt rà chỉ đọc, không tạo đề xuất và không ghi nội dung hồ sơ vào nhật ký.
API dùng cùng giới hạn 120 yêu cầu mỗi phút theo địa chỉ IP tại cổng của trợ lý.

Các ví dụ dùng mẫu Một cửa sinh viên ở chế độ full.
Đặt `DXOS_URL` là URL công khai của cổng, không có dấu gạch chéo cuối, và `DXOS_TOKEN` là access token có audience `dxos-api` của cán bộ đã liên kết Core trong giao diện trợ lý.
Gửi `Origin` trùng origin công khai và chọn mã ID nội bộ của một hồ sơ thử đang mở mà cán bộ có quyền sửa.
Các phản hồi bên dưới là ví dụ với hồ sơ `17`, lý do “xin xem lại bài thi môn CSDL” và loại hiện tại “Hoãn thi”.

```sh
curl --fail-with-body "$DXOS_URL/assistant-api/v1/me/reviews" \
  -H "Authorization: Bearer $DXOS_TOKEN" -H "Origin: $DXOS_URL" \
  --json '{"items":[{"model":"onestop","entity":"request","id":17}]}'
```

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "items": [{
      "model": "onestop",
      "entity": "request",
      "id": 17,
      "task": "classify_kind",
      "field": "kind",
      "current": "Hoãn thi",
      "suggested": "Phúc khảo",
      "confidence": 0.9791523993616698,
      "evidence": ["xem lại bài", "thi môn CSDL"],
      "mismatch": true
    }],
    "skipped": 0
  }
}
```

Tạo đề xuất cho hồ sơ trên bằng nhiệm vụ trả về trong kết quả rà.

```sh
curl --fail-with-body "$DXOS_URL/assistant-api/v1/me/proposals" \
  -H "Authorization: Bearer $DXOS_TOKEN" -H "Origin: $DXOS_URL" \
  --json '{"model":"onestop","entity":"request","id":17,"task":"classify_kind"}'
```

Phản hồi rút gọn để thấy mã đề xuất, giá trị và căn cứ.

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "insufficient": false,
    "proposal": {
      "id": "3ead3b5e-0b2a-4f71-8c20-acc442fc00b1",
      "state": "pending",
      "snapshot": {
        "field": "kind",
        "previousValue": "Hoãn thi",
        "value": "Phúc khảo",
        "evidence": ["xem lại bài", "thi môn CSDL"],
        "confidence": 0.9791523993616698
      }
    }
  }
}
```

Nếu `insufficient` là `true`, `proposal` là `null`.
Sau khi cán bộ kiểm tra và đồng ý với giá trị, đặt `PROPOSAL_ID` bằng `data.proposal.id` rồi gửi xác nhận.

```sh
curl --fail-with-body "$DXOS_URL/assistant-api/v1/me/proposals/$PROPOSAL_ID/confirm" \
  -H "Authorization: Bearer $DXOS_TOKEN" -H "Origin: $DXOS_URL" \
  --json '{}'
```

Phản hồi thành công gồm bản ghi sau khi Core lưu, dưới đây là dạng rút gọn.

```json
{
  "success": true,
  "message": "OK",
  "data": {
    "coreCommitted": true,
    "changed": true,
    "record": {"id": 17, "kind": "Phúc khảo", "version": 4}
  }
}
```

| Endpoint dưới `/assistant-api/v1` | Chức năng |
|---|---|
| `GET /features` | Trạng thái bật dịch vụ |
| `GET /openapi.json` | OpenAPI, cần đăng nhập |
| `GET /me` | Trạng thái liên kết và danh sách bộ phân loại đã nạp |
| `POST /me/core/link`, `GET /oauth/callback` | Liên kết Core bằng SSO |
| `DELETE /me/core/link` | Hủy liên kết: thu hồi refresh token ở Keycloak, xóa token đã lưu, hủy các đề xuất đang chờ; trả `revoked` cho biết Keycloak có xác nhận không, kiểm Origin và danh tính |
| `POST /me/reviews` | Rà tối đa 25 hồ sơ bằng các nhiệm vụ phân loại đủ điều kiện |
| `POST /me/proposals` | Tạo đề xuất từ model, entity, id và nhiệm vụ |
| `GET /me/proposals`, `GET /me/proposals/{id}` | Lịch sử và chi tiết của chủ còn quyền đọc hồ sơ |
| `POST /me/proposals/{id}/revise` | Sửa giá trị, tạo đề xuất mới |
| `POST /me/proposals/{id}/confirm`, `/cancel` | Xác nhận hoặc hủy, kiểm Origin và danh tính |

## Mạng

Container `assistant-control` chạy trên network nội bộ `assistant`, không có đường ra Internet và không thấy các dịch vụ khác của stack.
Chỉ `backend`, `keycloak` và `apisix` nằm chung network đó.
Bộ phân loại tính ngay trong tiến trình của trợ lý, nên nội dung hồ sơ không được gửi tới dịch vụ nào khác.

## State và giới hạn

State là một SQLite chứa token liên kết đã mã hóa theo chủ, đề xuất và phiên liên kết đang chờ.
Master key ở `deploy/data/assistant/private/master.key` chỉ giải mã state trên máy này.
`deploy/backup.sh` không sao lưu state của trợ lý; sau khi khôi phục Core, người dùng liên kết lại tài khoản.
State có sẵn mà thiếu hoặc sai master key thì trợ lý không khởi động; xóa `deploy/data/assistant` rồi chạy lại trợ lý để tạo state mới.

Một trợ lý phục vụ nhiều người, mỗi người có state riêng; không chạy nhiều bản trên cùng SQLite.
Số đo nghiệm thu của bộ phân loại lấy trên câu do đội viết, chưa trên hồ sơ thật.
Hỏi đáp mở, embedding, reranker và tác tử tự tạo đề xuất theo sự kiện chưa có.

## Thu hồi và thay khóa

- **Hủy liên kết của một người:** nút "Hủy liên kết" ở trang Trợ lý nghiệp vụ, hoặc `DELETE /assistant-api/v1/me/core/link`.
  Khóa hoặc xóa tài khoản ở Keycloak cũng chặn trợ lý: lần làm mới token kế tiếp bị từ chối và token đã lưu bị xóa.
- **Thay master key:** dừng trợ lý, chạy lệnh dưới, rồi chạy lại.
  Lệnh mã hóa lại toàn bộ state bằng khóa mới trong một giao dịch rồi mới thay `master.key`; bị ngắt giữa chừng thì chạy lại lệnh.
  Các phiên liên kết đang dở bị xóa, người dùng bắt đầu liên kết lại.

  ```sh
  docker compose stop assistant-control
  docker compose run --rm assistant-init dist/rotate.js /bootstrap
  docker compose up -d
  ```

## Kiểm tra

Workflow `Assistant` (`.github/workflows/ci-assistant.yml`) chạy khi đổi `assistant/`, `tools/classifier/`, tệp compose, tệp SSO hay SBOM của trợ lý.
Nó build và test dịch vụ, so SBOM với tệp khóa, kiểm tệp compose, và kiểm route cổng ở mọi chế độ.
Workflow `Platform` dựng image trợ lý cùng các image khác.
Chạy `PYTHONDONTWRITEBYTECODE=1 python3 tools/classifier/make_parity_fixture.py` từ gốc repo để sinh lại bộ đối chiếu khi đổi trọng số hoặc dữ liệu huấn luyện.
Tệp đối chiếu `assistant/test/fixtures/classifier.json` lấy câu gốc và biến thể đã dùng để huấn luyện, phát hành theo CC-BY-4.0 như thẻ dữ liệu nêu.
Lệnh `npm test` trong thư mục `assistant` kiểm nhãn, xác suất và cụm chữ của Node với Python.
Hai bài kiểm trình duyệt ở `e2e/web/tests/assistant.spec.ts` tự bỏ qua khi không đặt `E2E_ASSISTANT_RECORD_PATH` (hồ sơ mà đề xuất khác giá trị hiện tại) hoặc `E2E_ASSISTANT_UNCHANGED_RECORD_PATH` (hồ sơ mà đề xuất trùng giá trị hiện tại).

## Gỡ trợ lý

Chạy chế độ core như bước 4 ở trên thì Core, sao lưu và cập nhật chạy như bình thường.
Gỡ hẳn khỏi mã nguồn thì làm theo ba nhóm sau.

**Xóa cả tệp hoặc thư mục**

- `assistant/`, `sbom/assistant.cdx.json`
- `tools/classifier/` (dữ liệu, huấn luyện và trọng số của bộ phân loại) và `LICENSES/CC-BY-4.0.txt`
- `deploy/assistant-sso.sh`
- `.github/workflows/ci-assistant.yml`
- `frontend/src/assistant/`
- `e2e/web/tests/assistant.spec.ts`

**Xóa một khối hoặc một dòng trong tệp chung**

- `.github/dependabot.yml`: mục "The assistant".
- `deploy/.env.example`: hai biến `ASSISTANT_*` ở mục 6.
- `.github/workflows/release-images.yml`: chữ `assistant` ở phần mô tả, ở `ALL` và nhánh đặt build context gốc repo cho `assistant`.
- `.github/workflows/ci-platform.yml`: `assistant/**` và `tools/classifier/model/**` trong khóa cache của job `images`.
- `deploy/docker-compose.yml`: các dịch vụ `assistant-init`, `assistant-sso`, `assistant-control`, mạng `assistant` và dòng `networks` của `backend`, `keycloak`, `apisix`.
- `deploy/apisix/common.yaml`: khối giữa `# assistant:begin` và `# assistant:end`.
- `deploy/apisix/render.lua`: mục `assistant-api` trong `public` và mục `assistant` trong `excluded`.
- `deploy/dxos`: hàm `up_stack`, đoạn bỏ qua `assistant-*` trong `pending_services` và các tên `assistant-*` trong `switch_mode`.
- `deploy/update.sh`: `assistant-control` trong `SERVICES`, và phần tách `PLATFORM`/`ASSISTANT` khi khởi động lại.
- `deploy/reset.sh`: phần tách `PLATFORM`/`ASSISTANT` khi khởi động và đoạn bỏ qua `assistant-*` ở `pending_services`.
- `frontend/src/admin/AdminApp.tsx`: import, `useAssistantEnabled`, mục menu, route và `lazy`.
- `frontend/src/admin/resources.tsx`: `RecordAssistant`.
- `frontend/src/admin/InboxPage.tsx`: nút "Rà loại hồ sơ" và phần làm nổi hồ sơ cần rà.
- `frontend/src/shell/en/index.ts`: `assistantEn`.
- `frontend/src/shell/auth.ts`: `clearInboxReviews` và dòng import của nó.
- Giấy phép, văn bản: `DEPENDENCIES.txt`, `NOTICE` (cả bản trong `*/licenses/`), hai khối bộ phân loại trong `REUSE.toml`, `sbom/README.md`, `website/docs/dependencies.md`, `website/docs/pof-audit.md`, `CONTRIBUTING.md` và `.github/PULL_REQUEST_TEMPLATE.md`.
- Giấy phép, dữ liệu máy đọc trong `.github/licenses/`: `component-catalog.json` (mô tả của `node` và các tệp trọng số), `license-inventory.json` (các mục `DX-OS record classifier weights`, `DX-OS record classifier data`, `MASSIVE vi-VN` và `SLURP`) và `dependency-exceptions.json` (các mục `DX-OS record classifier data`, `MASSIVE vi-VN` và `SLURP`).
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
