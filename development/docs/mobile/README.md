# Mobile (Expo) cho nông dân

> **Cập nhật:** 30/09/2026.
> **Trạng thái:** thiết kế UI đã duyệt; backend phạm vi farmer, client Keycloak `dxfarm-mobile` và app `mobile/` (Expo SDK 57, `expo-router`) đã có luồng online đầu-cuối: đăng nhập, xem thửa/vụ/báo cáo, chụp và gửi, chi tiết báo cáo, hàng đợi gửi khi mất mạng; cấu hình `eas.json` cho APK; chưa build APK.

Bản mobile React Native (Expo) giúp nông dân chụp ảnh sâu bệnh và gửi báo cáo ngay tại ruộng.
Tài liệu trong thư mục này là nguồn tham chiếu duy nhất cho phần mobile.

## Mục lục

| Tài liệu | Nội dung |
|---|---|
| [`mobile/README.md`](../../../mobile/README.md) | Hướng dẫn chạy thử trên điện thoại thật, đóng gói APK, biến môi trường, tài khoản demo |
| [`PLAN.md`](./PLAN.md) | Các giai đoạn từ thiết kế UI đến hoàn thành luồng, kèm checklist và cách verify |
| [`BACKEND_CHANGES.md`](./BACKEND_CHANGES.md) | Đặc tả thay đổi backend (phạm vi dữ liệu farmer, ẩn khuyến nghị chưa duyệt, idempotency) |
| [`design/screens.md`](./design/screens.md) | Danh sách màn hình, luồng điều hướng, trạng thái rỗng/lỗi/tải |
| [`design/tokens.md`](./design/tokens.md) | Màu, chữ, khoảng cách, quy tắc dùng ngoài đồng |
| [`design/prototype.html`](./design/prototype.html) | Prototype HTML bấm được (mở trực tiếp bằng trình duyệt, dữ liệu giả) |

## Phạm vi

- Chỉ role FARMER.
- Luồng v1: đăng nhập → chọn thửa và vụ → chụp và gửi báo cáo → theo dõi trạng thái → đọc khuyến nghị đã duyệt và khoá thu hoạch (PHI); mất mạng thì ảnh chờ trong máy và tự gửi lại.
- ENGINEER và ADMIN tiếp tục dùng web (`frontend/`).
  Việc duyệt cần xem ảnh, kết quả CV, khuyến nghị và quy tắc PHI cùng lúc nên hợp với màn hình lớn.

## Quyết định đã chốt

- Chẩn đoán AI vẫn do kỹ sư kích hoạt thủ công trên web; farmer thấy trạng thái "Chờ chẩn đoán" sau khi gửi.
- Farmer chỉ thấy nội dung chẩn đoán (bệnh, khuyến nghị, thuốc, liều, PHI) khi `APPROVED`.
- Farmer chỉ thấy thửa của mình, lọc theo `farmerName` khớp claim `name` của token (áp dụng cho cả web khi đăng nhập bằng FARMER).
- Khi mất mạng, ảnh được lưu trong máy và tự gửi lại khi có mạng, an toàn với gửi trùng nhờ `Idempotency-Key`.
- Dùng Expo với `expo-router`; chụp ảnh bằng `expo-image-picker` (camera hệ thống); không thêm state library, không dựng monorepo.

## Backlog (chưa làm ở đợt này)

- [ ] Backend tự gọi AI diagnosis (`createFromAi`, bất đồng bộ) ngay sau khi lưu báo cáo.
- [ ] Push notification khi kỹ sư duyệt hoặc từ chối, khi có khoá thu hoạch mới.
- [ ] Màn hình duyệt nhanh cho kỹ sư trên mobile.
- [ ] Ảnh kèm thời điểm chụp và toạ độ GPS/EXIF.
- [ ] `POST /api/v1/crop-seasons/{id}/harvest` kiểm tra thửa thuộc FARMER (hiện chỉ có khoá PHI).
- [ ] `POST /api/v1/parcels` gán `farmerName` theo người tạo khi là FARMER (hiện do người tạo nhập).
- [ ] `frontend/src/components/DiagnosisPanel.tsx` chịu được diagnosis rút gọn (trường `null`) khi đăng nhập bằng FARMER.
- [ ] Cột owner theo `preferred_username` trên `Parcel` thay cho so khớp theo tên.
