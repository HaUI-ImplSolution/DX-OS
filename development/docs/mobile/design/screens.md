# Màn hình và luồng điều hướng — app mobile cho nông dân

App chỉ dành cho role FARMER.
Điều hướng dạng stack (`expo-router`), không có tab bar.
Mỗi màn hình có đúng một hành động chính.

## Luồng chính

```
Đăng nhập → Thửa của tôi → Vụ mùa của thửa → Báo cáo của vụ → Chi tiết báo cáo
                                                  │
                                                  └─ (nút chụp) → Chụp và gửi → quay lại Báo cáo của vụ
Hồ sơ: mở từ nút chữ "Hồ sơ" ở thanh tiêu đề màn Thửa của tôi
```

## 1. Đăng nhập

- Mục đích: vào hệ thống bằng tài khoản Keycloak.
- Hành động chính: nút "Đăng nhập" mở trình duyệt hệ thống tới Keycloak (Authorization Code + PKCE).
- Đang tải: nút chuyển thành "Đang mở…", vô hiệu hoá.
- Lỗi: thông báo ngắn dưới nút, ví dụ "Không đăng nhập được, thử lại".
- Role không phải FARMER: đăng xuất và hiện "Ứng dụng này dành cho nông dân".

## 2. Thửa của tôi

- Mục đích: chọn thửa cần báo cáo.
- Nội dung: thẻ mỗi thửa gồm mã thửa, diện tích, địa điểm, badge "Đang cách ly" nếu vụ đang có khoá thu hoạch.
- Hành động chính: chạm thẻ để vào Vụ mùa của thửa.
- Đang tải: 3 thẻ khung xương.
- Rỗng: "Bạn chưa có thửa ruộng nào. Liên hệ hợp tác xã để được thêm thửa."
- Lỗi: thông báo và nút "Thử lại"; kéo xuống để làm mới.

## 3. Vụ mùa của thửa

- Mục đích: xem tình trạng vụ và chọn vụ để báo cáo.
- Nội dung: vụ ACTIVE nổi bật ở trên (giống lúa, ngày bắt đầu, dự kiến thu hoạch); các vụ đã thu hoạch ở dưới, mờ hơn.
- Banner cách ly khi `isolated`: "Chưa được thu hoạch, còn N ngày (đến dd/mm/yyyy)" và tên thuốc.
- Hành động chính: chạm vụ ACTIVE để vào Báo cáo của vụ.
- Rỗng: "Thửa này chưa có vụ mùa."

## 4. Báo cáo của vụ

- Mục đích: xem các báo cáo đã gửi và gửi báo cáo mới.
- Nội dung: lưới 2 cột, mỗi thẻ có ảnh, ngày gửi và chip trạng thái.
- Hành động chính: nút tròn nổi "Chụp ảnh" ở góc phải dưới.
- Chip trạng thái: "Chờ chẩn đoán", "Chờ kỹ sư duyệt", "Đã duyệt", "Cần chụp lại".
- Dải "Chờ gửi (N)" (kèm "Gửi lỗi (M)" nếu có) ở đầu danh sách khi vụ có ảnh trong hàng đợi, chạm để mở Hồ sơ.
- Rỗng: "Chưa có báo cáo nào. Chụp ảnh lá hoặc thân cây bị bệnh để gửi."

## 5. Chụp và gửi

- Mục đích: chụp ảnh sâu bệnh và gửi đi.
- Bước 1: mở camera hệ thống; lần đầu xin quyền camera kèm lời giải thích tiếng Việt.
- Bước 2 (xem lại): ảnh lớn, gợi ý "Ảnh rõ nét, thấy rõ vết bệnh?", hai nút "Chụp lại" và "Gửi báo cáo" (nút chính).
- Đang gửi: thanh tiến trình, nút vô hiệu hoá, không cho thoát nhầm.
- Thành công: quay về Báo cáo của vụ, báo cáo mới xuất hiện đầu danh sách với chip "Chờ chẩn đoán".
- Lỗi mạng hoặc không có mạng: hộp thoại "Không gửi được. Ảnh đã lưu, sẽ tự gửi khi có mạng." rồi quay về Báo cáo của vụ.
- Lỗi từ backend (ví dụ ảnh bị từ chối): thông báo lỗi và nút "Gửi lại".
- Từ chối quyền camera: hướng dẫn mở Cài đặt.

## 6. Chi tiết báo cáo

- Mục đích: theo dõi kết quả và đọc khuyến nghị.
- Nội dung: ảnh, thông tin người gửi và thời điểm, timeline 3 bước: Đã gửi → Đã chẩn đoán → Kỹ sư duyệt.
- Chưa có diagnosis: bước 1 hoàn thành, các bước sau chưa đạt; ghi "Đang chờ chẩn đoán".
- `PENDING`: hiện "Kỹ sư đang xem xét", không hiện tên bệnh, thuốc hay liều.
- `APPROVED`: thẻ kết quả gồm tên bệnh, khuyến nghị, thuốc, liều, số ngày cách ly (PHI); có dòng nhắc "Ghi nhận việc phun thuốc do kỹ sư thực hiện".
- `REJECTED`: hiện lý do kỹ sư nhập và nút "Chụp lại" (mở màn Chụp và gửi cho cùng vụ).
- Hành động chính: "Chụp lại" khi `REJECTED`; các trạng thái khác không có nút chính, chỉ kéo xuống để làm mới.

## 7. Hồ sơ

- Nội dung: họ tên, vai trò "Nông dân", phiên bản app.
- Hành động chính: "Đăng xuất" (có xác nhận).
- Danh sách "Chờ gửi" gồm ảnh nhỏ, thửa hoặc vụ, trạng thái (đang chờ, đang gửi, lỗi) và nút xoá (có xác nhận) cho mục chưa gửi.

## Ánh xạ trạng thái

| `diagnosis` | Chip | Nội dung farmer thấy |
|---|---|---|
| `null` | Chờ chẩn đoán | Ảnh và thời điểm gửi |
| `PENDING` | Chờ kỹ sư duyệt | Chỉ trạng thái |
| `APPROVED` | Đã duyệt | Bệnh, khuyến nghị, thuốc, liều, PHI |
| `REJECTED` | Cần chụp lại | Lý do từ chối |
