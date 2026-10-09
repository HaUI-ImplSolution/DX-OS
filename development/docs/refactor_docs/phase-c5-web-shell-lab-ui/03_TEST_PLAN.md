# Kế hoạch kiểm thử: Phase C5

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy stack hoặc thử giao diện chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới.

## 1. Chiến lược

- Mức kiểm thử áp dụng: `tsc`, tìm kiếm import, thủ công trên trình duyệt cho hai chế độ `DXOS_APP`.
- Môi trường: clone sạch, `deploy/reset.sh`, chạy lần lượt `farm` và `lab`.
- Dữ liệu và tài khoản test: Farm (`farmer1`, `engineer1`, `admin1`), Lab (`student1`, `lecturer1`, `tech1`, `admin1`).

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC5-01 | WPC5-01 | Biên dịch | `npx tsc --noEmit` | Đạt | | |
| TC5-02 | WPC5-02 | Hồi quy | `DXOS_APP=farm`, đăng nhập `engineer1`, đi qua các trang | Đủ trang như trước | | |
| TC5-03 | WPC5-02 | Ranh giới | Tìm import `apps/` trong `shell/` | Không có | | |
| TC5-04 | WPC5-03 | Happy path | `student1` báo sự cố kèm ảnh; `tech1` nhận, xử lý, đóng | Đủ trạng thái, timeline đầy đủ | | |
| TC5-05 | WPC5-04 | Happy path | `student1` gửi yêu cầu mượn; `lecturer1` duyệt; trả thiết bị; `tech1` mở và hoàn tất nhật ký sửa | Phiếu và thiết bị đổi trạng thái đúng | | |
| TC5-06 | WPC5-05 | Happy path | `lecturer1` chọn ô trên lịch tuần và đặt phòng; đặt chồng giờ | Lịch cập nhật; lần chồng giờ báo lỗi rõ bằng tiếng Việt | | |
| TC5-07 | WPC5-06 | Happy path | Từng vai đi hết kịch bản của mình; ADMIN xem thống kê; hỏi đáp quy chế | Mọi trang hiển thị dữ liệu đúng; hỏi đáp có nguồn | | |
| TC5-08 | WPC5-01 | Chuyển app | Đổi `DXOS_APP=lab`, restart backend, tải lại web | Web hiện menu và trang Lab, không build lại | | |
| TC5-09 | WPC5-01 | Âm tính | Manifest trả id lạ | Trang lỗi rõ ràng | | |
| TC5-10 | WPC5-03 | Phân quyền | STUDENT mở trang kỹ thuật viên bằng đường dẫn trực tiếp | Bị chặn | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] Đăng nhập SSO qua Keycloak (web) vẫn hoạt động.
- [ ] Người dùng chỉ thấy dữ liệu thuộc workspace của mình.
- [ ] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [ ] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.

**Theo app**

- Farm:
  - [ ] FARMER dùng token web bị 403, dùng token mobile được.
- Lab:
  - [ ] STUDENT không thấy ticket của người khác và không thấy bình luận nội bộ.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC5-01 đến TC5-10 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả `tsc`, ảnh chụp hai chế độ lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
