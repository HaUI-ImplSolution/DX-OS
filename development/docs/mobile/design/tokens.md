# Design tokens — app mobile cho nông dân

Bảng màu và tông trạng thái lấy từ `frontend/src/styles.css` và `frontend/src/components/Badge.tsx`.

## Màu

| Token | Giá trị | Dùng cho |
|---|---|---|
| `bg` | `#f4f6f3` | Nền màn hình |
| `surface` | `#ffffff` | Thẻ, nền form |
| `border` | `#dfe4dc` | Viền thẻ, đường kẻ |
| `text` | `#1f2a1c` | Chữ chính |
| `textMuted` | `#667066` | Chữ phụ |
| `primary` | `#2e7d32` | Nút chính, liên kết |
| `primaryDark` | `#1b5e20` | Trạng thái nhấn, chip thành công |
| `warning` | `#b26a00` | Chip "Chờ kỹ sư duyệt", banner cách ly |
| `danger` | `#b3261e` | Chip "Cần chụp lại", lỗi |
| `neutral` | `#667066` | Chip "Chờ chẩn đoán" |

## Chip trạng thái

| Trạng thái | Tông | Nền chip | Chữ chip |
|---|---|---|---|
| Chờ chẩn đoán | neutral | `#eceeea` | `#667066` |
| Chờ kỹ sư duyệt | warning | `#fdf0dc` | `#b26a00` |
| Đã duyệt | success | `#e3f1e4` | `#1b5e20` |
| Cần chụp lại | danger | `#fbe6e4` | `#b3261e` |

## Chữ

- Font hệ thống (San Francisco / Roboto), không nhúng font để giữ app nhẹ.
- Cỡ chữ tối thiểu 16sp cho nội dung, 14sp chỉ cho chú thích.
- Tiêu đề màn hình 22sp đậm; tiêu đề thẻ 18sp đậm; nội dung 16sp; chú thích 14sp.
- Không viết hoa toàn bộ, không dùng thuật ngữ kỹ thuật (thay `PHI` bằng "số ngày cách ly").

## Khoảng cách và hình khối

- Lưới 4dp; lề màn hình 16dp; khoảng cách giữa thẻ 12dp.
- Bo góc thẻ 12dp, nút 12dp, chip tròn đầy.
- Vùng chạm tối thiểu 48×48dp; nút chính cao 56dp, rộng hết chiều ngang.
- Nút chụp nổi đường kính 64dp, cách mép 16dp.

## Quy tắc dùng ngoài đồng

- Tương phản chữ/nền tối thiểu 4.5:1 để đọc được dưới nắng.
- Mỗi màn hình có một hành động chính, màu `primary`, đặt ở vùng ngón cái với tới (nửa dưới màn hình).
- Không dùng màu làm tín hiệu duy nhất: chip luôn kèm chữ.
- Thao tác phá huỷ (đăng xuất, xoá mục chờ gửi) luôn có bước xác nhận.
- Văn bản lỗi nói rõ việc cần làm tiếp theo, ví dụ "Kiểm tra mạng rồi thử lại".
- Không dùng animation nặng; tải bằng khung xương thay vì spinner toàn màn hình.
