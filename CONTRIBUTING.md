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

`DXOS_APP` trong `.env` chọn ứng dụng (`farm` hoặc `lab`); đổi bằng `./switch-app.sh <app>`.

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
Thêm dependency thì cập nhật [`LICENSES.md`](./LICENSES.md) và sinh lại SBOM theo [`sbom/README.md`](./sbom/README.md).
Dữ liệu thuốc, treatment rule và policy chỉ được seed khi có quyết định duyệt rõ ràng trong `data/structured/`.

## Thêm một quy trình hoặc ứng dụng mới

Backend gồm `dx-core-api` (interface và DTO), `dx-core` (dịch vụ dùng chung), `app-farm` (nghiệp vụ nông nghiệp) và `dx-host` (chạy được, chọn app bằng `DXOS_APP`).
Ứng dụng chỉ phụ thuộc `dx-core-api`; core không biết ứng dụng nào đang chạy.
Để thêm quy trình mới:

1. Thêm package vào module của ứng dụng (`app-farm`), không sửa `dx-core`.
2. Khai báo loại yêu cầu mới bằng một bean `CaseTypeDefinition` (trạng thái, bước chuyển, vai) và mở case qua `WorkflowOperations` (`caseType`, `entityType`) để có sẵn lịch sử, người phụ trách và danh sách việc chờ.
3. Cài `CaseSummaryProvider` để yêu cầu hiện trong hộp duyệt, và `NotificationTypeDefinition` cho từng loại thông báo mới.
4. Khai báo route cần giới hạn tốc độ trong `deploy/apisix/apps/<app>.yaml`; phần chung cho mọi ứng dụng ở `deploy/apisix/common.yaml`.
5. Thêm view `vw_*` và đăng ký qua `BiDatasetProvider` nếu cần báo cáo.

Sơ đồ ở [`development/docs/submission/ARCHITECTURE_OPEN_CORE.md`](./development/docs/submission/ARCHITECTURE_OPEN_CORE.md).
