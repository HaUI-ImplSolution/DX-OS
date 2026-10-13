# Backend (Spring Boot)

Backend của DX-OS: một core dùng chung và các ứng dụng cắm vào qua interface.
Kiến trúc và sơ đồ ở [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Module

| Module | Vai trò |
|---|---|
| `dx-core-api` | Interface mà ứng dụng cài, facade mà ứng dụng gọi, DTO và lỗi dùng chung |
| `dx-core` | Cài đặt dùng chung: định danh và workspace, workflow, hộp duyệt, thông báo, lưu trữ, cổng AI và tri thức, bảo mật, trạng thái hệ thống |
| `app-farm` | Ứng dụng nông nghiệp ([`app-farm/README.md`](./app-farm/README.md)) |
| `app-lab` | Ứng dụng vận hành CNTT của khoa ([`app-lab/README.md`](./app-lab/README.md)) |
| `dx-host` | Chạy được: ghép core với ứng dụng chọn bằng `DXOS_APP`, đóng gói fat jar |

Ứng dụng chỉ phụ thuộc `dx-core-api`; `dx-core` không phụ thuộc ứng dụng.
Danh sách interface ở [`ARCHITECTURE.md`](../ARCHITECTURE.md) mục 3, cách thêm ứng dụng ở [`CONTRIBUTING.md`](../CONTRIBUTING.md).

## Build và kiểm tra

```bash
./mvnw formatter:validate    # kiểm định dạng mã
./mvnw verify                # compile, test, đóng gói (test tích hợp cần Docker cho Testcontainers)
```

Chạy cả stack ở [`deploy/`](../deploy/): `./deploy/start.sh [farm|lab]`.

## Cấu hình

- `dx-host/src/main/resources/application.yaml`: cấu hình chung; `application-farm.yaml` và `application-lab.yaml`: cấu hình riêng của từng app.
- Biến môi trường đặt ở `deploy/.env` (mẫu `deploy/.env.example`).
- Schema do Flyway quản lý: `db/migration/core` của core và `db/migration/<app>` của từng ứng dụng, mỗi nơi một bảng lịch sử.

## API

Swagger UI ở `/swagger-ui.html`, JSON ở `/v3/api-docs`, sinh từ annotation springdoc trong mã.
Mọi response bọc trong `ApiResponse`; lỗi có `errorCode` ổn định.
