# Đóng góp cho DX-OS

DX-OS (DX-Farm, DX-Lab) là mã nguồn mở theo Apache-2.0; riêng `ai/` theo AGPL-3.0.
Mã, tài liệu và báo lỗi đều được hoan nghênh qua GitHub Issues và Pull Request.
Mọi người tham gia tuân theo [Quy tắc ứng xử](./CODE_OF_CONDUCT.md).

## Báo lỗi và đề xuất

- Mở issue bằng mẫu có sẵn: **Bug report** hoặc **Feature request**.
- Với lỗi, ghi các bước tái hiện, kết quả mong đợi, kết quả thực tế và phiên bản (tag hoặc commit).
- Không đưa mật khẩu, token hay dữ liệu thật vào issue.

## Dựng môi trường

```bash
cp deploy/.env.example deploy/.env      # điền mọi biến đang để trống
python3 -m pip install pillow
./deploy/start.sh [farm|lab]            # dựng, chạy ứng dụng và nạp dữ liệu demo
```

Chi tiết ở [`README.md`](./README.md).

## Quy trình

1. Tạo nhánh từ `main`, đặt tên theo việc làm (ví dụ `feature/ten-ngan`, `fix/ten-ngan`).
2. Sửa nhỏ, mỗi PR một mục đích.
3. Chạy kiểm tra của phần đã đổi (bảng dưới).
4. Mở Pull Request theo mẫu; CI phải xanh.

| Phần | Kiểm tra |
|---|---|
| Backend (`backend`) | `./mvnw formatter:validate` và `./mvnw verify` (cần Docker cho Testcontainers) |
| AI (`ai`) | `pytest` |
| Web (`frontend`), Mobile (`mobile`) | `npx tsc --noEmit` |
| Toàn stack | `python3 deploy/demo/e2e.py --app farm` và `--app lab` trên stack mới dựng |

## Quy ước

- Java: định dạng theo `eclipse-java-google-style.xml`, chạy `./mvnw net.revelc.code.formatter:formatter-maven-plugin:2.26.0:format` trước khi commit.
- Package theo lớp: `controller`, `service`, `repository`, `model`, `dto`, `config`, `exception`; tính năng dùng chung nằm trong `core`.
- Response của controller bọc trong `ApiResponse`; lỗi nghiệp vụ có `errorCode` ổn định.
- Đổi API thì cập nhật `@Operation` trong cùng PR.
- Đổi schema bằng migration Flyway mới, không sửa migration đã có.
- File mã tự viết có dòng đầu `SPDX-License-Identifier: Apache-2.0` (`//` cho Java, `#` cho Python).
- Comment ngắn bằng tiếng Anh, chỉ nêu hành vi hiện tại; commit message bằng tiếng Anh.
- Tài liệu Markdown viết mỗi câu một dòng.

## Dependency và dữ liệu

- Mọi thành phần phải có giấy phép nguồn mở; thêm dependency thì cập nhật [`COPYRIGHT.txt`](./COPYRIGHT.txt) và sinh lại SBOM theo [`sbom/README.md`](./sbom/README.md).
- Đóng góp mã cho `ai/` theo AGPL-3.0 (`ai/LICENSE`); các phần còn lại theo Apache-2.0 (`LICENSE`).
- Dữ liệu thuốc, treatment rule và policy chỉ được seed khi có quyết định duyệt rõ ràng trong `data/structured/`.

## Thêm một quy trình vào ứng dụng có sẵn

Quy trình mới nằm trong module của ứng dụng (`app-farm` hoặc `app-lab`), không sửa `dx-core`:

1. Khai báo loại yêu cầu bằng bean `CaseTypeDefinition` (trạng thái, bước chuyển, vai) và mở case qua `WorkflowOperations` để có sẵn lịch sử, người phụ trách và danh sách việc chờ.
2. Cài `CaseSummaryProvider` để yêu cầu hiện trong hộp duyệt, và `NotificationTypeDefinition` cho từng loại thông báo mới.
3. Khai báo route cần giới hạn tốc độ trong `deploy/apisix/apps/<app>.yaml`.
4. Thêm view `vw_*` và đăng ký qua `BiDatasetProvider` nếu cần báo cáo.
5. Thao tác quản trị hoặc xóa dữ liệu thì ghi vết bằng `AuditRecorder.record(AuditEvent.success(...))` sau khi thao tác xong (tên hành động viết hoa như `PARCEL_DELETED`); người thực hiện, địa chỉ client và `request_id` được lấy từ request. Quyết định duyệt của workflow đã có ở `workflow_event`, không ghi lại.

## Thêm một ứng dụng mới

Mẫu tham chiếu là `app-lab` ([`backend/app-lab/README.md`](./backend/app-lab/README.md)); ranh giới core và ứng dụng ở [`ARCHITECTURE.md`](./ARCHITECTURE.md).
Dưới đây `<id>` là mã ứng dụng (chữ thường), cũng là giá trị `DXOS_APP`.

### Backend

1. **Module.**
   Tạo `backend/app-<id>/pom.xml` theo `app-lab/pom.xml` (chỉ phụ thuộc `dx-core-api`).
   Thêm module vào `<modules>` và `<dependencyManagement>` của `backend/pom.xml`, dependency vào `dx-host/pom.xml`, và hai dòng `COPY` (pom và `src`) vào `backend/Dockerfile`.
2. **Khai báo app.**
   Bean `AppModule` có `id()` bằng `<id>`, tên, menu (đường dẫn, vai được thấy) và loại workspace; đây là interface duy nhất bắt buộc.
3. **Nạp có điều kiện.**
   Lớp `@AutoConfiguration` với `@ConditionalOnProperty(name = "dxos.app", havingValue = "<id>")`, `@ComponentScan`, `@EntityScan`, `@EnableJpaRepositories`, đăng ký trong `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
   Cùng lớp khai báo bean Flyway riêng (`db/migration/<id>`, bảng lịch sử riêng, `@DependsOn("coreFlyway")`), theo `LabAutoConfiguration`.
4. **Chọn app.**
   Thêm `<id>` vào `KNOWN_APPS` của `AppSelectionCheck` trong `dx-host` và tạo `application-<id>.yaml` (đặt `spring.sql.init.mode: never` nếu không có script seed).
5. **Schema.**
   Migration ở `db/migration/<id>`; bảng của core (`workspaces`, `workflow_case`, `workflow_event`, `notifications`, `audit_log`, `chat_outbox`) dùng chung, không tạo lại.
6. **Interface và facade.**
   Cài các interface cần dùng (vai, quy trình, thông báo, BI, tri thức, tệp, sức khỏe) và gọi core qua facade; những gì ứng dụng khai báo và core cung cấp ở [`ARCHITECTURE.md`](./ARCHITECTURE.md) mục 3.

### Hạ tầng và dữ liệu

7. **Gateway.**
   Tạo `deploy/apisix/apps/<id>.yaml` với các route cần giới hạn tốc độ (có thể chỉ chứa chú thích); `apisix-config` dừng nếu thiếu file.
8. **BI.**
   Thêm một mục vào `APPS` của `bi/bootstrap_datasources.py` (tên kết nối, danh sách view, bundle dashboard hoặc `None`).
9. **Tri thức.**
   Thêm corpus vào `CORPORA` của `ai/app/rag/ingestion_service.py` và dữ liệu ở `data/rag/<id>/`.
   Corpus là bắt buộc: `DXOS_APP` không có trong `CORPORA` làm job `rag-ingest` thất bại và dịch vụ `ai` không khởi động.
10. **Dữ liệu demo và E2E.**
    Tạo `deploy/demo/seed_<id>.py` (hàm `seed`, danh sách `ACCOUNTS`) và `deploy/demo/e2e_<id>.py` (hàm `run`), đăng ký vào `SEEDS` của `seed_demo.py` và `SCENARIOS` của `e2e.py`; thêm một dòng vào ma trận của `.github/workflows/e2e.yml`.

### Web

11. Tạo `frontend/src/apps/<id>/index.tsx` xuất mặc định một `AppUiModule` (lời giới thiệu, nhãn vai, `routes`).
12. Mỗi `route` có `path` trùng đường dẫn của mục menu trong `AppModule`; thêm một dòng vào `frontend/src/appRegistry.ts`.
13. Chỉ import từ `src/shell` (qua `index.ts`) và từ thư mục của app.

### Chạy thử

```bash
cd backend && ./mvnw formatter:validate verify
./deploy/start.sh <id>
python3 deploy/demo/e2e.py --app <id>
```

Ghi `<id>` vào mục đổi ứng dụng của `README.md` và comment `DXOS_APP` của `deploy/.env.example`.
