# Plan thực hiện: Phase 5

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.

## Thứ tự và phụ thuộc

WP5-00 → WP5-01 → WP5-02 → WP5-03 → WP5-04 → WP5-06.
WP5-05 làm song song với WP5-02 đến WP5-04 sau khi WP5-03 chốt hợp đồng metadata, rồi gặp WP5-06 ở bước chạy trên stack.
WP5-04 bắt đầu bằng việc kiểm Flowable chạy được trên Spring Boot 4.1, vì kết quả quyết định cách làm của cả WP.

## WP5-00: Dọn nền

- Mục tham chiếu: tài liệu hướng đi, mục "Những gì giữ lại" và "Những gì sẽ gỡ".
- Mục tiêu: backend và frontend chỉ còn phần dùng lại được cho lõi, build và CI đạt, không còn khái niệm ứng dụng.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chạy backend | Chọn ứng dụng bằng `DXOS_APP` | Một dịch vụ duy nhất, không có khái niệm chọn ứng dụng |
| Build backend | Sáu module | Bốn module: `dx-model`, `dx-core`, `dx-engine`, `dx-monitor` |
| Mở frontend | Shell chọn Farm hoặc Lab theo manifest | Shell chứa một ứng dụng, đăng nhập và điều hướng giữ nguyên |
| CI | Job cho mobile và hai ứng dụng | Chỉ job của phần còn lại |

**Hướng làm**

- Gỡ `app-farm`, `app-lab`, `dx-core-api`, `dx-host`, `mobile/`, `frontend/src/apps/farm`, `frontend/src/apps/lab`, `deploy/switch-app.sh` và mọi tham chiếu `DXOS_APP`.
- Rà từng gói của `dx-core`, giữ gói mà Phase 5 đến 7 cần: bảo mật, audit, lưu trữ và quét tệp, trạng thái hệ thống, client Keycloak, cấu hình chung.
- Gỡ phần gắn với ứng dụng cũ: workflow viết tay, workspace, thông báo cũ, tri thức AI cũ, các seeder cho Keycloak, Mattermost, Superset, AI. Phần nào cần lại thì dựng lại theo thiết kế mới ở phase của nó.
- Tạo `dx-engine` là module chạy được thay `dx-host`; `backend/Dockerfile` build ra dịch vụ này.
- Giữ nguyên `dx-monitor`; `ai/`, `data/`, `bi/`, `chat/` để nguyên và dựng lại ở phase của chúng.
- Compose chỉ còn lõi (`postgres`, `keycloak`, `seaweedfs`, `backend`, `apisix`) và Tầng 2 (`frontend`, `portal`, `monitor`), thêm `clamav` qua profile `av`; bỏ Superset, Mattermost, AI, Ollama, trang quản trị SeaweedFS.
- Vai và cơ sở dữ liệu của Keycloak do `deploy/postgres/init` tạo khi thư mục dữ liệu mới; realm chỉ còn vai `ADMIN` và các client `dxos-web`, `dxos-monitor`, `dxos-backend-admin`.
- Gateway còn một tệp `deploy/apisix/common.yaml`; portal chỉ hiện BI và chat khi có `PORTAL_BI_URL`, `PORTAL_CHAT_URL`.
- Core/App: `dx-core` không import gói nào của ứng dụng; không còn ứng dụng nào.

**Phạm vi ảnh hưởng**

- Module: toàn bộ backend và frontend.
- File chính: `backend/pom.xml`, `backend/Dockerfile`, `frontend/src/appRegistry.ts`, `frontend/src/shell`, `deploy/`, `.github/workflows`.
- Endpoint, bảng DB, biến môi trường: bỏ `DXOS_APP` và các endpoint của Farm, Lab; bảng cũ không còn được tạo.
- Role và client bị ảnh hưởng: bỏ vai của Farm và Lab; client mobile không còn.
- Tương thích ngược và dữ liệu cũ: không giữ; bản cũ ở tag `v1.1.1`, bản công khai trên VPS không đổi cho đến khi triển khai lại.

**Kết quả đầu ra**

- `./mvnw compile` và `npx tsc --noEmit` đạt; không còn từ khóa `farm`, `lab`, `DXOS_APP` trong mã và cấu hình, ngoài `ai/`, `data/`, `bi/`, `chat/`; CI đạt.

**Kiểm kê `dx-core`**

| Gói | Kết luận | Ghi chú |
|---|---|---|
| `security` | Giữ | Lấy vai qua `RoleProvider` để `dx-engine` cấp vai từ mô hình; các điểm cắm chính sách theo ứng dụng rà khi làm, giữ nếu `dx-engine` cần |
| `audit` | Giữ | Gỡ `AuditAlertJob` vì gắn với thông báo chat; dựng lại ở phase thông báo |
| `storage` | Giữ | Lưu tệp và quét mã độc, dùng cho tệp đính kèm ở Phase 7 |
| `status` | Giữ | Bỏ phụ thuộc vào danh sách ứng dụng |
| `exception` | Giữ | Giữ các lỗi chung; gỡ lỗi riêng của AI và liên kết tài khoản |
| `scheduling` | Giữ | Khóa công việc dùng cho tác vụ thử lại ở dịch vụ lõi |
| `identity` | Tách | Giữ `KeycloakAdminClient` và `CurrentUserService`; gỡ toàn bộ phần workspace |
| `config` | Tách | Giữ cấu hình OpenAPI, bất đồng bộ, lịch, thời gian; gỡ bốn seeder (Keycloak, Mattermost, vector AI, vai chỉ đọc Superset) |
| `workflow` | Gỡ | Thay bằng Flowable |
| `notification` | Gỡ | Dựng lại trên trục sự kiện ở Phase 7 |
| `ai`, `knowledge` | Gỡ | Dựng lại ở phase AI |
| Migration `core` | Rút còn bảng audit | Bỏ bảng workspace, workflow, thông báo, hàng đợi chat; đánh số lại |

Phần còn dùng của `dx-core-api` (kiểu phản hồi, lỗi chung, vai, audit, lưu trữ, trạng thái, khóa công việc, người dùng hiện tại) chuyển vào `dx-core`; phần về ứng dụng, báo cáo, AI, thông báo, quy trình, workspace bỏ cùng module.

**Phụ thuộc, lưu ý**: quyết định D6 và D8; rủi ro R7, R8 ở `04`.

## WP5-01: Định dạng mô hình và bộ kiểm tra

- Mục tham chiếu: tài liệu hướng đi, mục "Tệp mô hình" và "Cần quyết khi thiết kế chi tiết".
- Mục tiêu: một tệp mô hình được đọc, kiểm tra và báo lỗi đúng dòng; chưa đụng vào hệ thống nào.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Tệp mô hình hợp lệ | Chưa có khái niệm mô hình | Đọc được thành cấu trúc nội bộ có số phiên bản định dạng |
| Tham chiếu tới thực thể hoặc vai chưa khai báo | Không áp dụng | Lỗi nêu dòng, cột và lý do |
| Tên thực thể hoặc trường ngoài `[a-z][a-z0-9_]*` | Không áp dụng | Bị từ chối |
| Tệp ghi số phiên bản định dạng không hỗ trợ | Không áp dụng | Lỗi rõ ràng |

**Hướng làm**

- Tệp YAML, một tệp nhiều thực thể, có trường `format` ghi số phiên bản.
- JSON Schema cho cấu trúc, bộ kiểm tra ngữ nghĩa cho tham chiếu, vai, quy tắc.
- Kiểu trường phiên bản 0: văn bản, số, ngày giờ, lựa chọn, tham chiếu, người dùng.
- Quyền: theo vai, theo dòng `all` hoặc `own`; quy tắc: bắt buộc, duy nhất.
- Quy trình: khai báo mẫu `approval` kèm tham số vai duyệt.
- Core/App: module `dx-model` chỉ chứa định dạng và kiểm tra, không có Spring.

**Phạm vi ảnh hưởng**

- Module: `dx-model` (mới).
- File chính: `schema/model-v0.schema.json`, bộ đọc và bộ kiểm tra, một mô hình mẫu.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- Mô hình mẫu đọc được; mỗi loại lỗi ở bảng trên có một tệp sai tương ứng được báo đúng dòng.

**Phụ thuộc, lưu ý**: Q2, Q5, Q6.

## WP5-02: Áp dụng từ trạng thái rỗng và phiên bản mô hình

- Mục tham chiếu: tài liệu hướng đi, mục "GitOps cho nghiệp vụ" và "Hai phần của lõi".
- Mục tiêu: nạp một mô hình thì hệ thống có schema, ràng buộc, vai Keycloak, phiên bản và lịch sử áp dụng.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Nạp mô hình hợp lệ vào hệ thống rỗng | Chưa có | Sinh schema `m_<tên mô hình>`, bảng, ràng buộc, cột trạng thái và cột chủ sở hữu, vai Keycloak |
| Nạp lại đúng tệp đã áp dụng | Chưa có | Không làm gì |
| Một bước lỗi giữa chừng | Chưa có | Dừng, lịch sử ghi bước đã xong và bước lỗi; chạy lại thì làm tiếp |
| Xin xem trước | Chưa có | Liệt kê các bước sẽ chạy, chưa so sánh với phiên bản trước |

**Hướng làm**

- Bảng của lõi ở schema `engine`: phiên bản mô hình (nội dung, mã băm, thời điểm, người áp dụng), lịch sử từng bước.
- Mỗi bước chạy lại được an toàn; trạng thái bước lưu trước và sau khi chạy.
- Sinh DDL trực tiếp từ mô hình đã kiểm; tên chỉ lấy từ mô hình, không nối chuỗi từ nơi khác.
- Quy tắc `required` và `unique` thành ràng buộc trong PostgreSQL.
- Vai: tạo realm role qua Keycloak Admin API bằng tài khoản dịch vụ riêng.
- Endpoint: `POST /api/v1/models/plan` và `POST /api/v1/models/apply`, quyền quản trị.
- Core/App: toàn bộ ở `dx-engine`, dùng `KeycloakAdminClient` và bảo mật của `dx-core`.

**Phạm vi ảnh hưởng**

- Module: `dx-engine`.
- File chính: dịch vụ áp dụng, bộ sinh DDL, client Keycloak Admin, controller `models`.
- Endpoint, bảng DB, biến môi trường: bảng ở schema `engine`; biến `KEYCLOAK_ADMIN_*`, `ENGINE_DB_*`.
- Role và client bị ảnh hưởng: vai quản trị lõi.
- Tương thích ngược và dữ liệu cũ: không áp dụng, cơ sở dữ liệu dựng lại từ rỗng.

**Kết quả đầu ra**

- Hệ thống rỗng nạp mô hình mẫu có đủ schema, bảng, ràng buộc, vai Keycloak, một phiên bản và lịch sử các bước.
- Nạp lại không đổi gì; ép lỗi một bước thì lịch sử ghi đúng chỗ dừng và chạy lại hoàn tất.

**Phụ thuộc, lưu ý**: WP5-00, WP5-01; rủi ro R6 ở `04`.

## WP5-03: API chung

- Mục tham chiếu: tài liệu hướng đi, mục "Hai phần của lõi" và "Quy trình không lách được".
- Mục tiêu: một API dùng cho mọi thực thể, đọc mô hình đang chạy để quyết định mọi thứ.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Giao diện hỏi có những thực thể nào | Chưa có | `GET /api/v1/meta/models` và `GET /api/v1/meta/models/{model}/entities/{entity}` trả trường, kiểu, quyền của người gọi, hành động |
| Đọc danh sách | Chưa có | Lọc, sắp xếp, phân trang; vai `own` chỉ thấy dòng của mình |
| Tạo, sửa, xóa | Chưa có | Kiểm quyền theo vai, kiểm quy tắc, báo lỗi theo từng trường |
| Ghi vào cột trạng thái | Chưa có | 403 |
| Tên thực thể, trường, toán tử lọc không có trong mô hình | Chưa có | 400 |

**Hướng làm**

- Đường dẫn `/api/v1/data/{model}/{entity}` và `/{id}`.
- Truy vấn dựng từ mô hình đã nạp vào bộ nhớ, làm mới khi có phiên bản mới; giá trị luôn đi qua tham số; toán tử lọc nằm trong danh sách trắng; giới hạn cỡ trang.
- `own` là dòng có trường người dùng trùng `sub` của token; kiểm thêm một bước qua tham chiếu về thực thể cha.
- Vai lấy từ token Keycloak, token nhiều vai được hợp lại.
- Phản hồi bọc `ApiResponse<T>`, lỗi qua `GlobalExceptionHandler`.
- Core/App: ở `dx-engine`; không có tên thực thể nào trong mã.

**Phạm vi ảnh hưởng**

- Module: `dx-engine`.
- File chính: bộ dựng truy vấn, bộ kiểm quyền, controller `data` và `meta`.
- Endpoint, bảng DB, biến môi trường: các endpoint trên; không thêm bảng.
- Role và client bị ảnh hưởng: mọi vai khai báo trong mô hình; web.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- Đủ năm hành vi ở bảng trên; tên trường chứa SQL bị từ chối; danh sách 10.000 dòng có bộ lọc đạt ngưỡng ở `00` mục 6.

**Phụ thuộc, lưu ý**: WP5-02; rủi ro R2, R5.

## WP5-04: Flowable nhúng và mẫu phê duyệt một cấp

- Mục tham chiếu: tài liệu hướng đi, mục "Quy trình không lách được".
- Mục tiêu: trạng thái hồ sơ chỉ đổi qua Flowable, cùng giao dịch với việc ghi hồ sơ.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Tạo hồ sơ ở thực thể có quy trình | Chưa có | Ghi hồ sơ, khởi động quy trình, trạng thái `submitted` trong một giao dịch |
| Vai duyệt quyết định | Chưa có | `POST .../{id}/decision` với `approve` hoặc `reject`, trạng thái thành `approved` hoặc `rejected` |
| Quyết định lần hai | Chưa có | 409 |
| Vai không phải vai duyệt | Chưa có | 403 |
| Lỗi sau khi đổi trạng thái | Chưa có | Cả bản ghi và quy trình quay về như cũ |

**Hướng làm**

- Việc đầu tiên: dựng Flowable trên Spring Boot 4.1 và chạy thử; kết quả quyết định hướng làm tiếp (xem R1).
- Mẫu BPMN `approval` viết và kiểm thử sẵn, tham số là vai duyệt; mô hình chỉ chọn mẫu và điền tham số.
- Bảng của Flowable nằm trong schema `engine`.
- Cột trạng thái chỉ do dịch vụ quy trình ghi; API chung từ chối ghi cột này.
- Core/App: ở `dx-engine`; mẫu BPMN là tài nguyên của module.

**Phạm vi ảnh hưởng**

- Module: `dx-engine`.
- File chính: cấu hình Flowable, mẫu BPMN, dịch vụ quy trình, endpoint `decision`.
- Endpoint, bảng DB, biến môi trường: `POST /api/v1/data/{model}/{entity}/{id}/decision`; bảng `ACT_*` ở schema `engine`.
- Role và client bị ảnh hưởng: vai duyệt khai báo trong mô hình.
- Tương thích ngược và dữ liệu cũ: không áp dụng.

**Kết quả đầu ra**

- Năm hành vi ở bảng trên đúng; bản ghi và quy trình không bao giờ lệch trạng thái trong các case lỗi cố ý gây ra.

**Phụ thuộc, lưu ý**: WP5-03; rủi ro R1, R3.

## WP5-05: Giao diện react-admin dựng từ metadata

- Mục tham chiếu: tài liệu hướng đi, mục "Ranh giới các tầng" và "Công cụ".
- Mục tiêu: giao diện đọc metadata rồi tự dựng màn hình, không viết riêng cho thực thể nào.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đăng nhập | Shell hiện có | Dùng lại đăng nhập Keycloak của shell cho ứng dụng mới |
| Mở ứng dụng | Chưa có | Đọc `/meta`, mỗi thực thể thành một màn hình danh sách, tạo, sửa, chi tiết |
| Thực thể có quy trình | Chưa có | Chi tiết hiện trạng thái và nút quyết định cho vai duyệt |
| Nạp thêm mô hình | Chưa có | Có thêm màn hình sau khi tải lại, không build lại |
| Lỗi từ API | Chưa có | Hiện thông báo tiếng Việt theo từng trường |

**Hướng làm**

- `dataProvider` gọi API chung và bóc `ApiResponse`; `authProvider` dùng token của shell.
- Bảng ánh xạ kiểu trường sang input và field của react-admin; màn hình dựng bằng vòng lặp trên metadata.
- Chỉ dùng gói MIT của react-admin; thêm vào kiểm tra giấy phép và SBOM.
- Core/App: ứng dụng Tầng 2, là ứng dụng duy nhất trong shell.

**Phạm vi ảnh hưởng**

- Module: frontend.
- File chính: ứng dụng mới, `dataProvider`, `authProvider`, bảng ánh xạ kiểu trường.
- Endpoint, bảng DB, biến môi trường: dùng `/meta` và `/data`.
- Role và client bị ảnh hưởng: web, mọi vai.
- Tương thích ngược và dữ liệu cũ: không áp dụng, đây là ứng dụng duy nhất của shell.

**Kết quả đầu ra**

- Sáu kiểu trường dựng được form và danh sách; mô hình thứ hai nạp vào thì giao diện hiện màn hình mới; `npx tsc --noEmit` đạt.

**Phụ thuộc, lưu ý**: hợp đồng metadata của WP5-03; rủi ro R4.

## WP5-06: Chạy trên stack và kiểm chứng

- Mục tham chiếu: tài liệu hướng đi, mục "Chưa xác minh".
- Mục tiêu: dịch vụ lõi chạy cùng hạ tầng và mỗi giả định rủi ro có kết luận đạt hoặc không đạt.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chạy dịch vụ lõi | Chưa có | Một lệnh compose dựng dịch vụ lõi cùng PostgreSQL, Keycloak, APISIX |
| Gọi API qua gateway | Chưa có | Route riêng cho dịch vụ lõi, kiểm token ở gateway |
| Kết nối báo cáo | Chưa có | Vai cơ sở dữ liệu chỉ đọc cho Superset |
| Kết quả kiểm chứng | Ở mục "Chưa xác minh" | Ghi đạt hoặc không đạt cùng số đo |

**Hướng làm**

- Image dịch vụ lõi; sửa compose chính; route APISIX; vai cơ sở dữ liệu ghi và đọc tách riêng.
- Chạy theo `03_TEST_PLAN.md`, ghi kết quả vào tài liệu hướng đi.
- Core/App: không đổi.

**Phạm vi ảnh hưởng**

- Module: deploy, docs.
- File chính: Dockerfile của dịch vụ lõi, compose, cấu hình APISIX, script khởi tạo vai cơ sở dữ liệu.
- Endpoint, bảng DB, biến môi trường: `ENGINE_*`, `KEYCLOAK_ADMIN_*` trong `.env.example`.
- Role và client bị ảnh hưởng: vai cơ sở dữ liệu của lõi và của báo cáo.
- Tương thích ngược và dữ liệu cũ: không áp dụng, cơ sở dữ liệu dựng lại từ rỗng.

**Kết quả đầu ra**

- Toàn bộ tiêu chí ở `00` mục 6 có bằng chứng.

**Phụ thuộc, lưu ý**: WP5-04, WP5-05.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `POST /api/v1/models/plan`, `POST /api/v1/models/apply` | Mới | Không |
| Endpoint | `GET /api/v1/meta/models`, `GET /api/v1/meta/models/{model}/entities/{entity}` | Mới | Không |
| Endpoint | `/api/v1/data/{model}/{entity}` và `/{id}` | Mới | Không |
| Endpoint | `POST /api/v1/data/{model}/{entity}/{id}/decision` | Mới | Không |
| Bảng DB | Schema `engine`: phiên bản mô hình, lịch sử áp dụng, bảng của Flowable | Mới | Không |
| Bảng DB | Schema `m_<tên mô hình>`: sinh từ mô hình | Mới | Không |
| Biến môi trường | `KEYCLOAK_ADMIN_*`, `ENGINE_DB_*` | Mới | Không |

## Dữ liệu, migration và rollback

- Migration: bảng của lõi ở `db/migration/engine`, chạy khi dịch vụ lõi khởi động; schema `m_*` do bước áp dụng sinh ra, không qua Flyway.
- Dữ liệu cũ: không chuyển; cơ sở dữ liệu dev dựng lại từ rỗng, bản công khai trên VPS giữ `v1.1.1` đến khi triển khai lại.
- Rollback: xóa schema `m_*` và `engine`, xóa vai Keycloak do lõi tạo; `deploy/reset.sh` dựng lại stack thử; quay về bản cũ bằng tag `v1.1.1`.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Một dịch vụ Java gồm phần điều khiển, API chung và Flowable | Đổi trạng thái và ghi hồ sơ cùng một giao dịch |
| D2 | API chung đọc mô hình lúc chạy, không sinh mã | API không biết trước thực thể; đổi mô hình không đổi mã |
| D3 | Dịch vụ lõi là kết nối ghi duy nhất vào dữ liệu nghiệp vụ | Không có đường vòng qua cơ sở dữ liệu |
| D4 | Mỗi mô hình một schema `m_<tên>`, bảng của lõi ở `engine` | Tách dữ liệu nghiệp vụ khỏi dữ liệu của lõi |
| D5 | Áp dụng qua REST ở phase này, CLI mỏng làm sau | Dùng chung một đường cho CLI và giao diện |
| D6 | Gỡ Farm, Lab và các phần gắn với app ngay đầu phase | Xây lõi từ nền sạch; bản cũ giữ ở tag `v1.1.1` |
| D7 | Tên định danh theo `[a-z][a-z0-9_]*` | Chặn chèn SQL từ gốc |
| D8 | `dx-core` thành thư viện không có khái niệm ứng dụng; `dx-engine` là dịch vụ chạy được | Lõi chỉ phụ thuộc vào thứ trung tính |
