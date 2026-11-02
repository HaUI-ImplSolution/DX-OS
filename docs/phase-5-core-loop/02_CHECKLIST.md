# Checklist: Phase 5

Giống `01_PLAN.md` nhưng bẻ nhỏ để làm và tick.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WP5-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, MB, DATA, INFRA, TEST.
Mỗi dòng là một việc không quá nửa ngày và có thể kiểm chứng.
Dòng BE và DATA ghi module trong ngoặc vuông: `[model]`, `[core]`, `[engine]`.

## WP5-00: Dọn nền

Kết quả đầu ra: `./mvnw compile` và `npx tsc --noEmit` đạt; không còn từ khóa `farm`, `lab`, `DXOS_APP` trong mã và cấu hình, ngoài `ai/`, `data/`, `bi/`, `chat/`; CI đạt.

**Spec và tài liệu**

- [x] WP5-00-DOC-01 Liệt kê từng gói của `dx-core` với kết luận giữ hoặc gỡ và lý do (bảng "Kiểm kê `dx-core`" ở `01_PLAN.md`).

**Backend**

- [x] WP5-00-BE-01 [core] Gỡ `app-farm` và `app-lab` khỏi reactor và khỏi kho.
- [x] WP5-00-BE-02 [core] Gỡ `dx-core-api`; chuyển phần còn dùng (vai, người dùng hiện tại, lỗi chung, `ApiResponse`) vào `dx-core`.
- [x] WP5-00-BE-03 [core] Gỡ workflow viết tay, workspace, thông báo cũ, tri thức AI cũ và `AuditAlertJob` khỏi `dx-core`.
- [x] WP5-00-BE-04 [core] Gỡ các seeder cho Keycloak, Mattermost, Superset, AI; giữ `KeycloakAdminClient`.
- [x] WP5-00-BE-05 [core] Gỡ migration `core` cũ không còn bảng tương ứng.
- [x] WP5-00-BE-06 [engine] Tạo `dx-engine` thay `dx-host`, khởi động được với bảo mật và audit của `dx-core`.
- [x] WP5-00-BE-07 [core] Cập nhật `backend/pom.xml`, `backend/Dockerfile`, bỏ `AppSelectionCheck` và `AppManifest`.

**Frontend (web)**

- [x] WP5-00-FE-01 Gỡ `apps/farm`, `apps/lab` và `appRegistry`, giữ đăng nhập và điều hướng của shell.

**Mobile**

- [x] WP5-00-MB-01 Gỡ `mobile/` và các tham chiếu tới nó.

**Infra, deploy, CI**

- [x] WP5-00-INFRA-01 Gỡ `DXOS_APP`, `switch-app.sh` và phần chọn app trong `deploy/`.
- [x] WP5-00-INFRA-02 Sửa năm workflow CI: bỏ job của phần đã gỡ, giữ kiểm tra giấy phép và SBOM.

## WP5-01: Định dạng mô hình và bộ kiểm tra

Kết quả đầu ra: mô hình mẫu đọc được; mỗi loại lỗi có một tệp sai được báo đúng dòng.

**Spec và tài liệu**

- [x] WP5-01-DOC-01 Viết đặc tả định dạng phiên bản 0: kiểu trường, quyền, quy tắc, mẫu quy trình, quy ước đặt tên.

**Backend**

- [x] WP5-01-BE-01 [model] Tạo module `dx-model` trong reactor, không phụ thuộc Spring.
- [x] WP5-01-BE-02 [model] JSON Schema `model-v0.schema.json`.
- [x] WP5-01-BE-03 [model] Bộ đọc YAML giữ số dòng và cột của từng nút.
- [x] WP5-01-BE-04 [model] Bộ kiểm tra ngữ nghĩa: tên trùng, tham chiếu, vai, quy tắc, quy ước đặt tên, số phiên bản định dạng.

**Data, seed, migration**

- [x] WP5-01-DATA-01 [model] Mô hình mẫu cho vòng hẹp theo Q5.
- [x] WP5-01-DATA-02 [model] Một tệp sai cho mỗi loại lỗi ở bảng hành vi.

## WP5-02: Áp dụng từ trạng thái rỗng và phiên bản mô hình

Kết quả đầu ra: hệ thống rỗng nạp mô hình mẫu có đủ schema, bảng, ràng buộc, vai Keycloak, phiên bản và lịch sử; nạp lại không đổi gì.

**Spec và tài liệu**

- [x] WP5-02-DOC-01 Cập nhật Swagger cho `models/plan` và `models/apply` trước khi code.

**Backend**

- [x] WP5-02-BE-01 [engine] Kết nối cơ sở dữ liệu riêng cho lõi và cấu hình schema `engine`.
- [x] WP5-02-BE-02 [engine] Lưu phiên bản mô hình kèm mã băm; nạp lại cùng mã băm thì bỏ qua.
- [x] WP5-02-BE-03 [engine] Bộ sinh DDL từ mô hình: schema, bảng, khóa, ràng buộc `required` và `unique`, cột trạng thái, cột chủ sở hữu.
- [x] WP5-02-BE-04 [engine] Bộ chạy các bước tuần tự, mỗi bước chạy lại được an toàn, ghi lịch sử trước và sau.
- [x] WP5-02-BE-05 [engine] Client Keycloak Admin tạo realm role từ mô hình.
- [x] WP5-02-BE-06 [engine] Endpoint `plan` liệt kê bước sẽ chạy, endpoint `apply` thực hiện.
- [x] WP5-02-BE-07 [engine] Từ chối tệp khác khi còn lần áp dụng dở của mô hình.
- [x] WP5-02-BE-08 [engine] Khóa để mỗi mô hình chỉ có một lần áp dụng chạy tại một thời điểm.

**Data, seed, migration**

- [x] WP5-02-DATA-01 [engine] Migration bảng phiên bản và lịch sử áp dụng ở `db/migration/engine`.

**Infra, deploy, CI**

- [x] WP5-02-INFRA-01 Tài khoản dịch vụ Keycloak cho lõi và biến `KEYCLOAK_ADMIN_*`.

## WP5-03: API chung

Kết quả đầu ra: đủ năm hành vi ở bảng; tên trường chứa SQL bị từ chối; danh sách 10.000 dòng có bộ lọc đạt ngưỡng.

**Spec và tài liệu**

- [x] WP5-03-DOC-01 Chốt hợp đồng metadata và dạng lỗi theo trường; cập nhật Swagger trước khi code.

**Backend**

- [x] WP5-03-BE-01 [engine] Kho mô hình đang chạy trong bộ nhớ, làm mới khi có phiên bản mới.
- [x] WP5-03-BE-02 [engine] Endpoint `meta` trả trường, kiểu, quyền của người gọi và hành động.
- [x] WP5-03-BE-03 [engine] Bộ dựng truy vấn đọc: lọc theo danh sách toán tử, sắp xếp, phân trang, giá trị qua tham số.
- [x] WP5-03-BE-04 [engine] Bộ kiểm quyền: vai từ token, hợp nhiều vai, `all` và `own`.
- [x] WP5-03-BE-05 [engine] Quyền `own` qua một bước tham chiếu về thực thể cha.
- [x] WP5-03-BE-06 [engine] Tạo, sửa, xóa kèm kiểm quy tắc và báo lỗi theo trường.
- [x] WP5-03-BE-07 [engine] Từ chối ghi cột trạng thái và cột chủ sở hữu.

**Data, seed, migration**

- [x] WP5-03-DATA-01 [engine] Tập dữ liệu 10.000 dòng để đo hiệu năng.

## WP5-04: Flowable nhúng và mẫu phê duyệt một cấp

Kết quả đầu ra: năm hành vi ở bảng đúng; bản ghi và quy trình không lệch trạng thái trong các case lỗi cố ý.

**Spec và tài liệu**

- [x] WP5-04-DOC-01 Cập nhật Swagger cho `decision` trước khi code.

**Backend**

- [x] WP5-04-BE-01 [engine] Dựng Flowable trên Spring Boot 4.1 và chạy thử; ghi kết luận vào R1.
- [x] WP5-04-BE-02 [engine] Cấu hình Flowable dùng chung nguồn dữ liệu và giao dịch, bảng ở schema `engine`.
- [x] WP5-04-BE-03 [engine] Mẫu BPMN `approval` với tham số vai duyệt.
- [x] WP5-04-BE-04 [engine] Tạo hồ sơ: ghi bản ghi, khởi động quy trình, đặt `submitted` trong một giao dịch.
- [x] WP5-04-BE-05 [engine] Endpoint `decision`: kiểm vai, đổi trạng thái qua quy trình, 409 khi đã quyết định.
- [x] WP5-04-BE-06 [engine] Chỗ gây lỗi có điều khiển sau khi đổi trạng thái để kiểm giao dịch, chỉ bật bằng biến môi trường.
- [x] WP5-04-BE-07 [engine] Lưu người quyết định và thời điểm vào hồ sơ và tác vụ của quy trình.

## WP5-05: Giao diện react-admin dựng từ metadata

Kết quả đầu ra: sáu kiểu trường dựng được; nạp mô hình thứ hai thì có thêm màn hình mà không build lại.

**Frontend (web)**

- [x] WP5-05-FE-01 Tạo ứng dụng trong shell và thêm react-admin.
- [x] WP5-05-FE-02 `authProvider` dùng token Keycloak của shell.
- [x] WP5-05-FE-03 `dataProvider` gọi API chung, bóc `ApiResponse`, ánh xạ lỗi theo trường sang tiếng Việt.
- [x] WP5-05-FE-04 Bảng ánh xạ sáu kiểu trường sang input và field.
- [x] WP5-05-FE-05 Vòng lặp dựng danh sách, tạo, sửa, chi tiết từ `/meta`.
- [x] WP5-05-FE-06 Trạng thái và nút quyết định cho vai duyệt.
- [x] WP5-05-FE-07 Ẩn thao tác theo quyền của người gọi trong metadata.

**Data, seed, migration**

- [x] WP5-05-DATA-01 [model] Mô hình thứ hai rất nhỏ để thử nạp thêm.

**Infra, deploy, CI**

- [x] WP5-05-INFRA-01 Thêm react-admin vào kiểm tra giấy phép và SBOM; chỉ dùng gói MIT.

## WP5-06: Chạy trên stack và kiểm chứng

Kết quả đầu ra: mọi tiêu chí ở `00` mục 6 có bằng chứng.

**Infra, deploy, CI**

- [x] WP5-06-INFRA-01 Dockerfile và image cho dịch vụ lõi.
- [x] WP5-06-INFRA-02 Sửa compose chính: dịch vụ lõi thay `backend` cũ, dùng chung PostgreSQL, Keycloak, APISIX.
- [x] WP5-06-INFRA-03 Route APISIX cho dịch vụ lõi, kiểm token ở gateway.
- [x] WP5-06-INFRA-04 Vai cơ sở dữ liệu: ghi cho lõi, chỉ đọc cho báo cáo.
- [x] WP5-06-INFRA-05 Cập nhật `.env.example` và `COPYRIGHT.txt`.

**Docs**

- [x] WP5-06-DOC-01 Viết hướng dẫn chạy và kiểm thử thủ công.
- [x] WP5-06-DOC-02 Ghi kết quả kiểm chứng vào `docs/dynamic-core/README.md`, bỏ các mục đã xác minh khỏi "Chưa xác minh".

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [x] WP5-06-TEST-01 Chạy các case T5-01 đến T5-21 và ghi kết quả (`deploy/dev/check_api.py`, `check_recovery.py`, kiểm giao diện thủ công).

## Đóng phase

- [x] `./mvnw compile` đạt cho toàn bộ module (backend), `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [x] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [x] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn milestone.
- [x] `.env.example`, migration chạy được trên clone sạch.
- [x] Không còn từ khóa `farm`, `lab`, `DXOS_APP` trong mã và cấu hình, ngoài `ai/`, `data/`, `bi/`, `chat/`.
- [x] Các README và `CONTRIBUTING.md` không còn nội dung về Farm, Lab; trỏ tới tag `v1.1.1` cho bản cũ.
- [x] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [x] Dọn dữ liệu và ảnh test không còn dùng.
- [x] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [x] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [x] Cập nhật trạng thái phase ở đầu `00_OVERVIEW.md`.
