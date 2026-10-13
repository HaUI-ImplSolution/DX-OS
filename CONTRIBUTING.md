# Đóng góp cho DX-Farm

DX-Farm là mã nguồn mở theo Apache-2.0.
Mọi đóng góp (mã, tài liệu, báo lỗi) đều được hoan nghênh qua GitHub Issues và Pull Request.

## Báo lỗi và đề xuất

- Mở issue bằng mẫu có sẵn: **Bug report** hoặc **Feature request**.
- Với lỗi, ghi các bước tái hiện, kết quả mong đợi, kết quả thực tế và phiên bản (tag hoặc commit).
- Không đưa mật khẩu, token hay dữ liệu thật của nông hộ vào issue.

## Dựng môi trường

```bash
cd deploy
cp .env.example .env      # điền mọi biến đang để trống
docker compose up -d --build
python3 -m pip install pillow
python3 demo/seed_demo.py
```

`DXOS_APP` trong `.env` chọn ứng dụng (`farm` hoặc `lab`); `./start.sh [app]` chạy ứng dụng đó kèm dữ liệu demo và cũng dùng để đổi ứng dụng.

Hướng dẫn đầy đủ ở [`README.md`](./README.md).

## Quy trình

1. Tạo nhánh từ `main`, đặt tên theo việc làm (ví dụ `feature/ten-ngan`, `fix/ten-ngan`).
2. Sửa nhỏ, mỗi PR một mục đích.
3. Chạy kiểm tra của phần đã đổi (bảng dưới).
4. Mở Pull Request theo mẫu; CI phải xanh.

| Phần | Kiểm tra |
|---|---|
| Backend (`backend`) | `./mvnw formatter:validate` và `./mvnw verify` (cần Docker cho Testcontainers) |
| AI (`ai`) | `pytest` |
| Web (`frontend`) | `npx tsc --noEmit` |
| Mobile (`mobile`) | `npx tsc --noEmit` |
| Toàn stack | `python3 deploy/demo/e2e.py` trên stack mới dựng, lần lượt với `DXOS_APP=farm` và `DXOS_APP=lab` |

## Quy ước mã

- Java: định dạng theo `eclipse-java-google-style.xml`, chạy `./mvnw net.revelc.code.formatter:formatter-maven-plugin:2.26.0:format` trước khi commit.
- Package theo lớp: `controller`, `service`, `repository`, `model`, `dto`, `config`, `exception`; tính năng dùng chung nằm trong `core`.
- Mọi response của controller bọc trong `ApiResponse`; lỗi nghiệp vụ trả mã ổn định ở `errorCode`.
- Đổi API thì cập nhật annotation springdoc (`@Operation`) trong cùng PR.
- Schema đổi bằng migration Flyway mới (`db/migration`), không sửa migration đã có.
- Mỗi file mã tự viết có dòng đầu `SPDX-License-Identifier: Apache-2.0` (`//` cho Java, `#` cho Python).
- Comment ngắn bằng tiếng Anh, chỉ nêu hành vi hiện tại.
- Tài liệu Markdown viết mỗi câu một dòng.
- Commit message bằng tiếng Anh.

## Giấy phép của dependency

Mọi thành phần phải có giấy phép OSI-approved.
Đóng góp mã cho thư mục `ai/` theo AGPL-3.0 (`ai/LICENSE`); các phần còn lại theo Apache-2.0 (`LICENSE`).
Thêm dependency thì cập nhật [`LICENSES.md`](./LICENSES.md) và sinh lại SBOM theo [`sbom/README.md`](./sbom/README.md).
Dữ liệu thuốc, treatment rule và policy chỉ được seed khi có quyết định duyệt rõ ràng trong `data/structured/`.

## Thêm một quy trình mới vào ứng dụng có sẵn

Quy trình mới nằm trong module của ứng dụng (`app-farm` hoặc `app-lab`), không sửa `dx-core`:

1. Thêm package vào module của ứng dụng.
2. Khai báo loại yêu cầu bằng một bean `CaseTypeDefinition` (trạng thái, bước chuyển, vai) và mở case qua `WorkflowOperations` (`caseType`, `entityType`) để có sẵn lịch sử, người phụ trách và danh sách việc chờ.
3. Cài `CaseSummaryProvider` để yêu cầu hiện trong hộp duyệt, và `NotificationTypeDefinition` cho từng loại thông báo mới.
4. Khai báo route cần giới hạn tốc độ trong `deploy/apisix/apps/<app>.yaml`.
5. Thêm view `vw_*` và đăng ký qua `BiDatasetProvider` nếu cần báo cáo.

## Thêm một ứng dụng mới

Backend gồm `dx-core-api` (interface và DTO), `dx-core` (dịch vụ dùng chung), `app-farm`, `app-lab` và `dx-host` (chạy được, chọn app bằng `DXOS_APP`).
Ứng dụng chỉ phụ thuộc `dx-core-api`; core không biết ứng dụng nào đang chạy.
Ví dụ mẫu là `app-lab` ([`backend/app-lab/README.md`](./backend/app-lab/README.md)); sơ đồ ở [`ARCHITECTURE.md`](./ARCHITECTURE.md).
Dưới đây `<id>` là mã của ứng dụng (chữ thường), cũng là giá trị `DXOS_APP`.

### Backend

1. **Module.**
   Tạo `backend/app-<id>/pom.xml` theo `app-lab/pom.xml` (parent `dx-os-parent`, phụ thuộc `dx-core-api` và các starter Spring).
   Thêm module vào `<modules>` và `<dependencyManagement>` của `backend/pom.xml`, thêm dependency vào `dx-host/pom.xml`, và hai dòng `COPY` (pom và `src`) vào `backend/Dockerfile`.
2. **Khai báo app.**
   Một bean `AppModule` có `id()` bằng `<id>`, tên hiển thị, menu (đường dẫn, vai được thấy) và loại workspace.
   Bean này là interface duy nhất bắt buộc; core khởi động được với app chỉ có `AppModule`.
3. **Nạp có điều kiện.**
   Một lớp `@AutoConfiguration` với `@ConditionalOnProperty(name = "dxos.app", havingValue = "<id>")`, `@ComponentScan`, `@EntityScan`, `@EnableJpaRepositories`, đăng ký trong `META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`.
   Cùng lớp khai báo bean Flyway riêng (`locations` là `db/migration/<id>`, bảng lịch sử riêng, `@DependsOn("coreFlyway")`); lớp này chạy trước Flyway và Hibernate mặc định, theo `LabAutoConfiguration`.
4. **Chọn app.**
   Thêm `<id>` vào `KNOWN_APPS` của `AppSelectionCheck` trong `dx-host`, và `application-<id>.yaml` cho cấu hình riêng (đặt `spring.sql.init.mode: never` nếu app không có script seed).
5. **Schema.**
   Migration của app nằm ở `db/migration/<id>`; bảng của core (`workspaces`, `workflow_case`, `workflow_event`, `notifications`) dùng chung, không tạo lại.
6. **Interface cần cài** (đầy đủ ở [`ARCHITECTURE.md`](./ARCHITECTURE.md) mục 3):

   | Cần | Interface |
   |---|---|
   | Vai và quyền | `RoleProvider`, `EndpointPolicyContributor`, `ClientRestrictionPolicy` |
   | Quy trình | `CaseTypeDefinition`, `WorkflowDecisionHandler`, `CaseSummaryProvider` |
   | Thông báo và chat | `NotificationTypeDefinition`, `NotificationChannel` |
   | Báo cáo | `BiDatasetProvider` (view `vw_<id>_*`) |
   | Tri thức và tệp | `KnowledgeNamespace`, `FileProcessor` |
   | Sức khỏe | `HealthContributor` |

7. **Facade gọi core.**
   `WorkflowOperations`, `ApprovalInbox`, `WorkspaceDirectory`, `NotificationPublisher`, `ObjectStorage`, `FileStore`, `UserContext`, `KnowledgeGateway`, `AiGateway`.

### Hạ tầng và dữ liệu

8. **Gateway.**
   Tạo `deploy/apisix/apps/<id>.yaml` với các route cần giới hạn tốc độ (có thể chỉ chứa dòng chú thích); `apisix-config` dừng nếu file không tồn tại.
9. **BI.**
   Thêm một mục vào `APPS` của `bi/bootstrap_datasources.py` (tên kết nối, danh sách view, bundle dashboard hoặc `None`).
10. **Tri thức.**
    Thêm corpus vào `CORPORA` của `ai/app/rag/ingestion_service.py` và dữ liệu ở `data/rag/<id>/`; job `rag-ingest` nạp theo `DXOS_APP`.
    Corpus là bắt buộc: giá trị `DXOS_APP` không có trong `CORPORA` làm job thất bại và dịch vụ `ai` (phụ thuộc job này) không khởi động.
11. **Dữ liệu demo và E2E.**
    Tạo `deploy/demo/seed_<id>.py` (hàm `seed`, danh sách `ACCOUNTS`) và `deploy/demo/e2e_<id>.py` (hàm `run`), đăng ký vào `SEEDS` của `seed_demo.py` và `SCENARIOS` của `e2e.py`; thêm một dòng vào ma trận của `.github/workflows/e2e.yml`.

### Web

12. Tạo `frontend/src/apps/<id>/index.tsx` xuất mặc định một `AppUiModule` (lời giới thiệu, nhãn vai, `routes`).
13. Mỗi `route` có `path` trùng đường dẫn của mục menu trong `AppModule`; thêm một dòng vào `frontend/src/appRegistry.ts`.
14. Chỉ import từ `src/shell` (qua `index.ts`) và từ thư mục của app.

### Chạy thử

```bash
cd backend && ./mvnw formatter:validate verify
./deploy/start.sh <id>                  # dựng, chạy app, nạp dữ liệu demo
python3 deploy/demo/e2e.py --app <id>
```

Thêm giá trị `<id>` vào `README.md` (mục chọn ứng dụng) và comment `DXOS_APP` của `deploy/.env.example`.
