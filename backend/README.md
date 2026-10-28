# Backend (Spring Boot)

Backend của DX-OS: một core dùng chung và các ứng dụng cắm vào qua interface.
Kiến trúc và sơ đồ ở [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## Module

| Module | Vai trò |
|---|---|
| `dx-core-api` | Interface mà ứng dụng cài, facade mà ứng dụng gọi, DTO và lỗi dùng chung |
| `dx-core` | Cài đặt dùng chung: định danh và workspace, workflow, hộp duyệt, thông báo, audit, lưu trữ, cổng AI và tri thức, bảo mật, trạng thái hệ thống |
| `app-farm` | Ứng dụng nông nghiệp ([`app-farm/README.md`](./app-farm/README.md)) |
| `app-lab` | Ứng dụng vận hành CNTT của khoa ([`app-lab/README.md`](./app-lab/README.md)) |
| `dx-host` | Chạy được: ghép core với ứng dụng chọn bằng `DXOS_APP`, đóng gói fat jar |
| `dx-monitor` | Dịch vụ giám sát chạy riêng ([`dx-monitor/README.md`](./dx-monitor/README.md), cùng `Dockerfile`, đích `monitor`), không phụ thuộc module nào ở trên: nhận sự kiện bất thường của gateway, dò trạng thái dịch vụ, đọc `audit_log` và sự kiện đăng nhập của Keycloak, phục vụ màn hình `/monitor/` cho ADMIN và gửi cảnh báo Mattermost |

Ứng dụng chỉ phụ thuộc `dx-core-api`; `dx-core` không phụ thuộc ứng dụng; `dx-monitor` không phụ thuộc `dx-core-api` hay `dx-core`, nên vẫn chạy khi backend dừng.
Những gì ứng dụng khai báo và core cung cấp ở [`ARCHITECTURE.md`](../ARCHITECTURE.md) mục 3, cách thêm ứng dụng ở [`CONTRIBUTING.md`](../CONTRIBUTING.md).

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
- `dx-monitor` chạy Flyway riêng với `db/migration/monitor` trong schema `monitor`, độc lập với core và ứng dụng.

## API

Swagger UI ở `/swagger-ui.html`, JSON ở `/v3/api-docs`, sinh từ annotation springdoc trong mã.
Mọi response bọc trong `ApiResponse`; lỗi có `errorCode` ổn định.
