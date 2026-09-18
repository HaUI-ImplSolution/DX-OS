# Phase C3: Tên trung tính cho realm, client và hạ tầng

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | Dùng chung (hạ tầng, `dx-core`, web, mobile, demo) |
| Ước lượng | 2 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C1 (cần `RoleProvider` của C2 cho phần vai) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| RD4 ở `refactor/README.md` | Tên realm, client, audience trung tính | Quan trọng | Toàn bộ |
| Đề thi HaUI mục 4.1 | Identity/SSO dùng chung cho mọi ứng dụng | Quan trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: tên `dxfarm` xuất hiện ở khoảng 30 file ngoài tài liệu: realm, client `dxfarm-api`, `dxfarm-frontend`, `dxfarm-mobile`, `dxfarm-superset`, `dxfarm-backend-admin`, audience, database `dxfarm`, bucket `dxfarm-uploads`, route APISIX, script demo.
  Realm import còn chứa vai và tài khoản mẫu của Farm.
- Vì sao làm ở phase này: app Lab dùng cùng realm; tên mang nghĩa nông nghiệp làm core trông gắn với Farm.

## 3. Tổng quan sửa đổi

Đổi tên nền tảng sang `dxos` ở realm, client dùng chung, audience, database, bucket và route.
Client của Farm mobile đổi thành `farm-mobile` (thương hiệu app giữ nguyên scheme `dxfarm://`).
Realm import chỉ còn phần nền tảng; vai của app do core tạo lúc khởi động, tài khoản mẫu do script seed của từng app tạo.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | |
| `dx-core` (cài đặt dùng chung) | Có | Mặc định `application.yaml`: realm, audience |
| `app-farm` | Có | `farm-mobile`, cấu hình client |
| `app-lab` | Không | Chưa tạo |
| `dx-host` (chạy, chọn app) | Không | |
| Frontend (web) | Có | `auth.ts`, `Dockerfile`, khóa lưu trữ |
| Mobile | Có | Client id và URL realm; scheme giữ nguyên |
| AI | Có | Tên thuộc tính nội bộ `_dxfarm_device` |
| Data / Seed / Migration | Có | Tên database, tài khoản mẫu chuyển sang seed |
| Infra / Deploy / CI | Có | `sso/`, `apisix.yaml`, `docker-compose.yml`, `.env.example`, `bi/`, `chat/`, `.gitleaks.toml` |
| Docs / PoF | Có | `sso/README.md`, README, CONTRIBUTING |

## 4. Phạm vi

- Trong phạm vi: đổi tên, tách vai và tài khoản mẫu khỏi realm import, cập nhật mọi nơi tham chiếu.
- Ngoài phạm vi: SSO cho Mattermost (đã chốt không làm); đổi tên scheme và bundle của mobile Farm; đổi tên repository.
- Giả định: Keycloak, Postgres, MinIO dựng lại sạch bằng `deploy/reset.sh`.
- Ranh giới Core/App: tên nền tảng ở core và hạ tầng; tên app (`farm-mobile`, `dxfarm://`) ở cấu hình của app.

## 5. Ưu điểm và hạn chế

- Ưu điểm: một realm và một bộ client dùng chung cho Farm và Lab; core không còn mang tên nông nghiệp.
- Hạn chế: phá toàn bộ phiên đăng nhập và ảnh đã lưu; mobile cần build lại.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: dựng sạch bằng `deploy/reset.sh`, đăng nhập web, mobile, Superset bằng tài khoản mẫu của Farm.
- Sản phẩm bàn giao: `sso/realm-dxos.json`, cấu hình và script đã đổi tên, danh sách tên mới ở `sso/README.md`.
- Tiêu chí đo được:
  - Tìm `dxfarm` ngoài tài liệu lịch sử, scheme và bundle của mobile Farm không ra kết quả nào.
  - Token đăng nhập có audience `dxos-api`; backend từ chối token có audience cũ.
  - Realm import không chứa vai hay user của app.
- Bằng chứng: kết quả tìm kiếm, ảnh chụp đăng nhập ba client.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: C1 (module và `DXOS_APP`); `RoleProvider` của C2 để vai tạo lúc khởi động.
- Phase sau cần từ phase này: C4 (vai và tài khoản mẫu của Lab), C6 (script seed theo app).

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: Open-Core headless có vai trò thực (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 4 ở mục 13 của đề (Identity/SSO là dịch vụ dùng chung).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Tên nền tảng | `dxos` hoặc tên khác | Trước khi bắt đầu | Chốt `dxos` (09/10/2026) |
| Q2 | Tài khoản mẫu | A: seed của từng app tạo qua Admin API. B: giữ trong realm import | Đầu phase | Chốt A (09/10/2026, theo đề xuất) |
| Q3 | Tên database và bucket | Đổi theo `dxos` hay giữ | Đầu phase | Chốt đổi (09/10/2026, theo đề xuất) |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] `sso/README.md`, `mobile/README.md`, `deploy/demo/README.md`.
- [ ] `LICENSES.md`, `.env.example` nếu có đổi.
