# Phase C1: Tách Maven đa module, giữ nguyên hành vi

| Mục | Giá trị |
|---|---|
| Trạng thái | Draft |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | `dx-core-api`, `dx-core`, `app-farm`, `dx-host` |
| Ước lượng | 4-5 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C0 |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề thi HaUI mục 4.1, 12 | Open-Core tách khỏi giao diện và ứng dụng, chứng minh được | Nghiêm trọng | Ranh giới ở mức biên dịch |
| RD1, RD2, RD8, RD9 ở `refactor_docs/README.md` | Host + plugin, `DXOS_APP`, tên module, Flyway tách location | Nghiêm trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: core và Farm cùng một module Maven; ranh giới là quy ước package, compiler không chặn.
  `Parcel` và `ParcelRepository` tham chiếu thẳng entity `Household` của core.
  Migration V2 và V4 của core đọc bảng `parcels` và `diagnoses` của Farm.
- Vì sao làm ở phase này: mọi phase sau xây trên ranh giới này; tách cơ học trước, khi chưa đổi hành vi, giữ được bộ test hiện có làm lưới an toàn.

## 3. Tổng quan sửa đổi

Dựng parent Maven và bốn module; tạo `dx-core-api` với facade và kiểu dữ liệu công khai; chuyển code core vào `dx-core`, code Farm vào `app-farm`; thêm `dx-host` chọn app bằng `DXOS_APP` (mặc định `farm`); tách migration thành hai location và bỏ backfill chéo module.
Tên `Household`, `NotificationType` và `WorkflowStatus` giữ nguyên ở phase này; C2 đổi.
Hành vi nhìn từ client không đổi.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Có | Facade, view, DTO và lỗi dùng chung |
| `dx-core` (cài đặt dùng chung) | Có | Nhận code `core/*` và phần chung |
| `app-farm` | Có | Nhận toàn bộ code nghiệp vụ, chỉ phụ thuộc `dx-core-api` |
| `app-lab` | Không | Chưa tạo |
| `dx-host` (chạy, chọn app) | Có | Mới |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Có | Hai location Flyway, bỏ backfill chéo |
| Infra / Deploy / CI | Có | Đường dẫn build, Dockerfile, CI |
| Docs / PoF | Có | README, CONTRIBUTING, lệnh build |

## 4. Phạm vi

- Trong phạm vi: cấu trúc module, facade, di chuyển code, `dx-host`, migration, đường dẫn build.
- Ngoài phạm vi: đổi tên `Household`, `NotificationType`, enum workflow (C2); đổi tên realm (C3); app Lab (C4).
- Giả định: RD8 và RD9 đã chốt ở C0; DB dựng lại sạch bằng `deploy/reset.sh`.
- Ranh giới Core/App: `app-farm` chỉ có `dx-core-api` trên classpath; `dx-core-api` và `dx-core` không import gói của Farm.

## 5. Ưu điểm và hạn chế

- Ưu điểm: compiler chứng minh ranh giới; không đổi hành vi nên đối chiếu được với baseline.
- Hạn chế: diff lớn (di chuyển hàng trăm file); lịch sử `git blame` bị đứt nếu không dùng `git mv`.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md`.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: `./mvnw compile` ở `backend/` thành công cho cả bốn module; chạy `dx-host` với `DXOS_APP=farm` cho kết quả như trước.
- Sản phẩm bàn giao: parent pom, bốn module, Dockerfile và CI mới, migration tách hai location.
- Tiêu chí đo được:
  - `app-farm` không có import nào vào gói của `dx-core`; chỉ có `dx-core-api`.
  - `dx-core-api` và `dx-core` không import gói của Farm.
  - `DXOS_APP` lạ làm backend dừng khi khởi động với thông báo rõ.
  - OpenAPI sau phase không khác baseline ở C0 ngoài thông tin đóng gói.
- Bằng chứng: kết quả compile, diff OpenAPI, kết quả test và E2E do người dùng chạy.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: RD8, RD9 (C0), baseline.
- Phase sau cần từ phase này: C2 làm trên ranh giới đã có; C3, C4 cần module và `dx-host`.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: kiến trúc 3 tầng và Open-Core headless (10 điểm); khả năng mở rộng và chất lượng kỹ thuật (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 1 và 3 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Giữ lịch sử file khi di chuyển | A: `git mv` rồi sửa package. B: tạo mới rồi xóa cũ | Đầu phase | Chốt A (09/10/2026, theo đề xuất) |
| Q2 | Facade ở C1 dùng tên `WorkspaceDirectory` hay `HouseholdDirectory` | Tên cuối ngay (`Workspace*`) hay đổi ở C2 | Đầu phase | Chốt `WorkspaceDirectory` trả `WorkspaceView` (09/10/2026, theo đề xuất) |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [ ] Lệnh build ở `README.md`, `CONTRIBUTING.md`, workflow `ci.yml`.
- [ ] `ARCHITECTURE_OPEN_CORE.md` mục 2 (ranh giới ở mức biên dịch).
- [ ] `.env.example` nếu thêm `DXOS_APP`.
