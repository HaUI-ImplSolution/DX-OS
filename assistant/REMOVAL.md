# Gỡ trợ lý

Chạy chế độ core như bước 4 ở [README](./README.md#chạy-cấu-hình-và-tắt) thì Core, sao lưu và cập nhật chạy như bình thường.
Gỡ hẳn khỏi mã nguồn thì làm theo ba nhóm sau.

## Xóa cả tệp hoặc thư mục

- `assistant/`, `sbom/assistant.cdx.json`
- `tools/classifier/` (dữ liệu, huấn luyện và trọng số của bộ phân loại) và `LICENSES/CC-BY-4.0.txt`
- `deploy/lib/assistant-sso.sh`
- `.github/workflows/ci-assistant.yml`
- `frontend/src/assistant/`
- `e2e/web/tests/assistant.spec.ts`

## Xóa một khối hoặc một dòng trong tệp chung

- `.github/dependabot.yml`: mục "The assistant".
- `deploy/.env.example`: hai biến `ASSISTANT_*` ở mục 8.
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

## Khai báo `ai:` trong mô hình (thuộc lõi, có thể giữ)

Khối `ai.tasks` là một phần của định dạng mô hình; không có trợ lý thì nó không làm gì.
Muốn gỡ luôn thì xóa:

- `dx-model`: `Model.AiTask`, `ModelParser.readAiTasks`, lược đồ `ai` và phần `ai` trong `ModelUpgraderTest`.
- `dx-engine`: `CHANGE_AI_TASKS` ở `ModelDiff`, `aiTasks` ở `MetaController`, khối `ai:` của ba mẫu `onestop`, `support_desk` và `clinic_appointment`.
- `frontend`: tab trợ lý trong `ModelForm`, các mục `ai` trong `formGuide`, `labels` và `backendMessages`, `AiTask` trong `admin/types.ts`.
- `e2e`: `ai_task_changes` ở `check_process.py` và dòng `input_fields` ở `check_demo.py`.
- Docs: mục "Nhiệm vụ trợ lý nghiệp vụ" của `docs/MODEL.md`.

Ghi có điều kiện bằng `version` và `modelVersion` là tính năng chung của API, không thuộc phần gỡ này.
