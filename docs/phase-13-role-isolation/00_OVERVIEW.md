# Phase 13: Cách ly vai theo mô hình và tab Người dùng

| Mục | Giá trị |
|---|---|
| Trạng thái | Code và kiểm xong ba work package đầu; còn Q5 (ghi vết cấp vai) và đóng phase |
| Mức độ | Quan trọng |
| Ứng dụng và module chính | `dx-core`, `dx-engine`, frontend (`src/console`), `deploy/dev`, Keycloak |
| Phụ thuộc | Không; PR #184 đã vào `Khuong/main` |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Phạm vi trong phase này |
|---|---|---|
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md), phần 6 | Vai tách theo ứng dụng, đơn vị dùng chung | Toàn bộ |
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md), phần 6 | Tab Người dùng trong bảng điều khiển | Toàn bộ, làm hai bước |
| [`docs/dynamic-core/BACKLOG.md`](../dynamic-core/BACKLOG.md), phần 3 | Vai còn lại ở Keycloak khi mô hình bỏ vai; hai mô hình đã lưu dùng chung vai | Hết theo cách ly vai |

## 2. Lý do

- Mọi mô hình đang tạo vai cùng tên trong một realm, nên chỉ có chốt chặn từ chối vai trùng; một vai bị bỏ khỏi mô hình vẫn còn ở Keycloak cùng người giữ.
- Hai mô hình không dùng chung được tên vai, ví dụ áp dụng cùng một mẫu cho hai tòa nhà.
- Chưa có chỗ nào trong ứng dụng để quản trị viên xem ai giữ vai nào theo từng ứng dụng.

## 3. Tổng quan sửa đổi

Mỗi mô hình có một client Keycloak riêng (`dxos-m-<tên mô hình>`) và các vai của mô hình là client role của client đó.
Token mang vai ở `resource_access.<client>.roles`; engine đọc vai của người gọi theo từng mô hình.
Tab Người dùng ở bảng điều khiển đọc và đổi vai, đơn vị của người dùng qua một API ADMIN mỏng trên Admin REST API của Keycloak.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-model` | Không | Tệp mô hình giữ nguyên |
| `dx-core` | Có | Đọc client role từ token; client Keycloak thêm phương thức về client, vai, người dùng |
| `dx-engine` | Có | Bước tạo client và vai khi áp dụng; so quyền theo vai của mô hình; API người dùng |
| Frontend (web) | Có | Tab Người dùng; thông báo mới bằng tiếng Việt |
| Data / Migration | Không | Không có bảng mới |
| Infra / Deploy / CI | Có | `deploy/dev`: người dùng thử gán client role, script `check_users.py` |
| Keycloak realm | Có | `sso/realm-dxos.json`: tài khoản dịch vụ của backend thêm quyền quản lý client |
| Docs | Có | BACKLOG, ARCHITECTURE, README thiết kế, TESTING, CHANGELOG |

## 4. Phạm vi

- Trong phạm vi: cách ly vai bằng client role; tab Người dùng bước 1 (xem, gán và gỡ vai và đơn vị) và bước 2 (tạo người dùng với mật khẩu tạm, khóa và mở, đặt lại mật khẩu); ghi vết mọi thay đổi về tài khoản vào `audit_log`.
- Ngoài phạm vi: đơn vị (vẫn là nhóm Keycloak dùng chung); đăng nhập bằng nhà cung cấp ngoài, chính sách mật khẩu, MFA (để Keycloak, mở bằng liên kết); mobile.
- Giả định: bản 2.0.0 chưa triển khai nơi nào nên không có dữ liệu cần di chuyển; stack dev dựng lại bằng `reset.sh`.
- Ranh giới: vai `ADMIN` và luồng OTP có điều kiện giữ là vai realm; chỉ vai do mô hình khai báo chuyển sang client role.

## 5. Ưu điểm và hạn chế

- Ưu điểm: vai thuộc riêng từng ứng dụng đúng với khái niệm của Keycloak; hai mô hình dùng được cùng tên vai; quản lý vai theo ứng dụng trong Admin Console; không còn vai bị thừa hưởng giữa các mô hình.
- Hạn chế: mỗi mô hình thêm một client Keycloak; vai chỉ vào token khi cấu hình scope của client web đúng (đã kiểm, xem `01_PLAN.md`); script e2e phải đổi cách gán vai.
- Trade-off lớn: xem [`04_TRADEOFFS_RISKS.md`](./04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: hai mô hình cùng khai báo vai `MANAGER`; người giữ `MANAGER` của mô hình A không vào được dữ liệu của mô hình B; quản trị viên mở tab Người dùng, thấy ai giữ vai nào ở từng ứng dụng, gán và gỡ được vai và đơn vị.
- Tiêu chí đo được:
  - áp dụng mô hình thứ hai có vai trùng tên vai của mô hình đầu thì thành công;
  - người chỉ giữ vai của mô hình A gọi API dữ liệu của mô hình B nhận 403;
  - API người dùng không có đường nào cấp hay gỡ vai realm `ADMIN`, và ADMIN không khóa được chính mình;
  - mỗi thay đổi về tài khoản có một bản ghi audit với người quản trị thực hiện, và không bản ghi nào chứa mật khẩu;
  - chín script ở `deploy/dev` đạt trên stack rỗng.
- Bằng chứng (04/11/2026): `./mvnw clean verify` và `npx tsc --noEmit` đạt; chín script ở `deploy/dev` đạt 428/428 trên stack rỗng dựng riêng (51, 17, 71, 65, 56, 44, 26, 59, 39), bảng `audit_log` ghi 41 bản ghi không lỗi; tab Người dùng thử bằng Chrome. Chi tiết ở `03_TEST_PLAN.md`.

## 7. Phụ thuộc và thứ tự

- Làm cách ly vai trước, tab Người dùng sau, vì tab cần biết vai thuộc ứng dụng nào.

## 8. Ảnh hưởng đến thi

- Dùng tối đa Keycloak thay vì tự đặt quy ước đặt tên; phục vụ phần quản trị người dùng của ứng dụng minh họa.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Đề xuất | Kết luận |
|---|---|---|---|---|
| Q1 | Tên client của mô hình | `dxos-m-<tên mô hình>` hoặc đúng tên mô hình | `dxos-m-<tên mô hình>`, để không đụng client có sẵn như `dxos-web` | Chốt theo đề xuất |
| Q2 | Mô hình bỏ một vai thì sao | Giữ vai ở Keycloak, hoặc xóa vai (mức mất dữ liệu) | Giữ: vai nằm trong client của mô hình nên không mô hình khác thừa hưởng được | Chốt theo đề xuất |
| Q3 | Chốt chặn trùng vai giữa các mô hình | Bỏ, hoặc giữ | Bỏ vì không còn cần; giữ cấm khai báo vai `ADMIN` để tránh nhầm | Chốt theo đề xuất |
| Q4 | Người dùng thử ở e2e gán vai | Đổi script sang client role | Đổi, bằng một hàm dùng chung trong `kc.py` | Chốt theo đề xuất |
| Q5 | Ghi vết cấp vai | Sự kiện audit ở backend, hoặc bật admin events của Keycloak | A cộng giữ B: audit ở backend với tám hành động và tên đăng nhập; sự kiện quản trị của Keycloak đã bật sẵn, giữ nguyên | Chốt theo đề xuất |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `README_EN.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Swagger (`@Operation` bằng tiếng Anh) cho API người dùng.
- [x] `ARCHITECTURE.md`, `docs/dynamic-core/README.md`, `docs/TESTING.md`, `deploy/OPERATIONS.md`.
- [x] `docs/dynamic-core/BACKLOG.md`: xóa các dòng đã xong, thêm việc phát sinh.
