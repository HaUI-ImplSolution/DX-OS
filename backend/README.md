# Backend (Spring Boot)

Backend của DX-OS: thư viện dùng chung và dịch vụ lõi chạy được.
Hướng thiết kế ở [`docs/dynamic-core/README.md`](../docs/dynamic-core/README.md).

## Module

| Module | Vai trò |
|---|---|
| `dx-model` | Định dạng tệp mô hình: đọc và kiểm tra, không phụ thuộc Spring |
| `dx-core` | Thư viện dùng chung: bảo mật, audit, lưu trữ và quét tệp, trạng thái hệ thống, client Keycloak, lỗi và kiểu phản hồi chung |
| `dx-engine` | Dịch vụ chạy được, gồm phần điều khiển, API chung, Flowable nhúng, trục sự kiện (outbox), tệp đính kèm, danh mục và quản trị người dùng cho bảng điều khiển, đóng gói fat jar |
| `dx-monitor` | Dịch vụ giám sát chạy riêng ([`dx-monitor/README.md`](./dx-monitor/README.md), cùng `Dockerfile`, đích `monitor`), không phụ thuộc module nào ở trên: nhận sự kiện bất thường của gateway, dò trạng thái dịch vụ, đọc `audit_log` và sự kiện đăng nhập của Keycloak, phục vụ màn hình `/monitor/` cho ADMIN và gửi cảnh báo Mattermost |

`dx-monitor` không phụ thuộc `dx-core`, nên vẫn chạy khi backend dừng.

## Build và kiểm tra

```bash
./mvnw formatter:validate    # kiểm định dạng mã
./mvnw verify                # compile, test, đóng gói (test tích hợp cần Docker cho Testcontainers)
```

`ArchitectureTest` trong `dx-engine` kiểm các luật phân lớp và ranh giới gói của các module.
Test của client Keycloak, bộ gửi webhook và bộ dò dịch vụ chạy với WireMock (chỉ ở scope test, không cần Docker); test nào mất vài giây gắn `@Tag("slow")`.

Chạy cả stack ở [`deploy/`](../deploy/): `docker compose up -d`.

## Cấu hình

- `dx-engine/src/main/resources/application.yaml`: cấu hình của dịch vụ.
- Biến môi trường đặt ở `deploy/.env` (mẫu `deploy/.env.example`).
- Schema của `dx-core` do Flyway quản lý ở `db/migration/core`, bảng của `dx-engine` ở `db/migration/engine`, mỗi nơi có bảng lịch sử riêng.
- Flowable đặt `search_path` của kết nối nó dùng về `engine` và không trả lại, nên truy vấn vào bảng ngoài `engine` (như `audit_log` ở `public`) luôn ghi rõ schema.
- Bảng nghiệp vụ `m_<mô hình>` do pgschema đưa về trạng thái đích khi áp dụng mô hình; image backend kèm sẵn công cụ này, chạy `dx-engine` ngoài Docker thì cần `pgschema` trên `PATH` hoặc đặt `ENGINE_PGSCHEMA_COMMAND`.
- `dx-monitor` chạy Flyway riêng với `db/migration/monitor` trong schema `monitor`.

## API

Swagger UI ở `/swagger-ui.html`, JSON ở `/v3/api-docs`, sinh từ annotation springdoc trong mã.
Mọi response bọc trong `ApiResponse`; lỗi có `errorCode` ổn định.
Một người dùng gọi quá ngưỡng một lệnh trong một phút thì nhận `429` với `ERR_TOO_MANY_REQUESTS` và header `Retry-After`; `RATE_LIMIT_ENABLED=false` tắt giới hạn này (xem [`deploy/OPERATIONS.md`](../deploy/OPERATIONS.md)).
