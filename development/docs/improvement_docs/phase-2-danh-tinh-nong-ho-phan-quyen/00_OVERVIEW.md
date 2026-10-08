# Phase 2: Danh tính nông hộ, phân quyền, migration

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong (CI GitHub xanh, mobile đăng nhập bình thường) |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ước lượng | 4-5 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 1 |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 1 | Phân quyền farmer/engineer/admin | Nghiêm trọng | Toàn bộ (trừ chặn farmer trên web dùng chung hạ tầng của P3 nếu cần thêm lớp gateway — ở đây chặn đủ bằng backend + FE) |
| mục 6 | Danh tính nông hộ và mô hình dữ liệu | Nghiêm trọng | Toàn bộ |
| mục 9 | Phân quyền và bảo mật | Nghiêm trọng | Phần còn lại sau P1: phân quyền theo hành động, `aud` cho API |
| mục 16 | Web frontend | Quan trọng | Một phần: chặn FARMER đăng nhập web |

## 2. Lý do

- Vấn đề đang gặp: quan hệ thửa – chủ hộ hiện chỉ là so khớp chuỗi tên (`Parcel.farmerName` so với claim `name`, không phân biệt hoa thường — `CurrentUserService.canAccess`), nên 2 farmer trùng tên thấy chung thửa, đổi tên trong Keycloak là mất thửa.
  `SecurityConfig` hiện cho mọi user đã đăng nhập gọi mọi endpoint `/api/**` (`anyRequest().authenticated()`), không có `@PreAuthorize` nào ở `ParcelController`/`CropSeasonController` — engineer đang tạo được thửa/vụ dù đề không cho phép.
- Vì sao làm ở phase này: đây là "móng" cho mọi tính năng nghiệp vụ ở P4/P5 (duyệt thửa/vụ phải biết chủ hộ thật là ai), và là rủi ro đã tự ghi trong tài liệu dự án (PLAN.md mục rủi ro) — làm sau sẽ phải sửa lại nhiều chỗ đã viết dựa trên `farmerName`.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Entity `Household`, Flyway, `@PreAuthorize`, audit theo id, xóa mềm |
| Frontend (web) | Có | Chặn đăng nhập/điều hướng cho role FARMER |
| Mobile | Không | Không đổi hành vi farmer, chỉ đổi cách backend nhận diện họ (vẫn qua token cũ) |
| AI | Không | |
| Data / Seed / Migration | Có | Migration ghép `farmerName` cũ sang `Household`, Flyway baseline |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | README, CHANGELOG, ma trận quyền |

## 4. Phạm vi

- Trong phạm vi: WP2-01 đến WP2-05 ở `01_PLAN.md` (Household + migration, ma trận quyền + `@PreAuthorize`, chặn farmer web/Superset + `aud`, audit theo id + xóa mềm, onboarding qua Keycloak Admin API).
- Ngoài phạm vi (không làm ở phase này): "phạm vi phụ trách theo khu vực" cho engineer (hoãn theo D9, phase 0); gateway/APISIX (P3); duyệt thửa/vụ qua workflow (P4, chỉ chuẩn bị nền ở đây).
- Giả định: D9 (phase 0) đã chốt — phân quyền theo thao tác, không theo khu vực.

## 5. Ưu điểm và hạn chế

- Ưu điểm: đóng đúng rủi ro nghiêm trọng nhất về toàn vẹn dữ liệu (lệch chủ hộ); có ma trận quyền một nơi, P4 không phải tự đoán.
- Hạn chế: migrate dữ liệu cũ (ghép `farmerName` → `Household`) có rủi ro ghép sai nếu có lỗi chính tả/trùng tên thật trong dữ liệu demo.
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (chuyển sang Flyway giữa dự án, xử lý dữ liệu farmerName không khớp hộ nào).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: farmer A và farmer B cùng tên không còn thấy chung thửa; engineer gọi `POST /api/v1/parcels` nhận 403; farmer đăng nhập web bị chặn (redirect/thông báo); Superset không cho role FARMER vào.
- Sản phẩm bàn giao: entity `Household`, migration Flyway, ma trận quyền (bảng trong `01_PLAN.md`), `@PreAuthorize` áp cho endpoint ghi của Parcel/CropSeason/harvest, audit (`reportedBy`/`decidedBy` kèm id), xóa mềm cho Parcel/CropSeason/DiseaseReport.
- Tiêu chí đo được: toàn bộ case ở bảng "Hành vi thay đổi" trong `01_PLAN.md` đúng "Sau phase"; test T2-xx pass.
- Bằng chứng: log test, script migrate chạy trên seed demo không lỗi.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: `Clock` bean và pattern `@Version` từ P1 (áp dụng tiếp cho `Household` nếu cần); D9 (phân quyền theo thao tác) từ phase 0.
- Phase sau cần từ phase này: P3 (ranh giới Core) dùng `Household`/ma trận quyền làm ví dụ cụ thể cho sơ đồ; P4 (duyệt thửa/vụ) bắt buộc có `Household` để sinh mã thửa/mã hộ; P9 nếu làm sau này cũng dùng lại Identity/Workflow nhưng không đụng `Household` (đặc thù nông nghiệp).

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề mục 11 (kiến trúc Open-Core, Identity/RBAC là một trụ cột H trong H-P-D-I); câu hỏi hội đồng về phân quyền và toàn vẹn dữ liệu.
- Dùng làm bằng chứng cho: phần "H (Human)" của sơ đồ H-P-D-I (mục 21 `PROJECT_IMPROVEMENTS.md`); câu trả lời "farmer chỉ dùng mobile" trong demo.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Dữ liệu `farmerName` cũ không khớp chính xác tên hộ nào khi ghép (lỗi chính tả, viết hoa khác) xử lý sao | Ghép gần đúng (fuzzy) rồi admin xác nhận lại / Tạo hộ mới riêng cho từng `farmerName` lạ, gộp tay sau | 05/10/2026 | Tạo hộ mới riêng cho từng `farmerName` lạ — fuzzy-match tự động dễ ghép sai người (rủi ro nghiêm trọng hơn trùng lặp), admin gộp tay khi phát hiện trùng |
| Q2 | Xóa mềm hiển thị cho ai | Admin thấy cả bản ghi đã xóa (có filter) / Ẩn hoàn toàn khỏi mọi API, chỉ truy vấn DB trực tiếp khi cần | 05/10/2026 | Admin thấy qua filter riêng (`?includeDeleted=true`, mặc định ẩn) — giữ được khả năng audit/khôi phục mà không ảnh hưởng danh sách thường |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Swagger cho endpoint mới (`/api/v1/households`) và mã lỗi 403 mới.
- [x] Sơ đồ ERD (nếu có) thêm `Household`; `sso/README.md` nếu đổi cấu hình `aud`.
