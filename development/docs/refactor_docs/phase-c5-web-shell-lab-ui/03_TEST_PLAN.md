# Kế hoạch kiểm thử: Phase C5

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy stack hoặc thử giao diện chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới.

## 1. Chiến lược

- Mức kiểm thử áp dụng: `tsc`, tìm kiếm import, thủ công trên trình duyệt cho hai chế độ `DXOS_APP`.
- Môi trường: clone sạch, `deploy/reset.sh`, chạy lần lượt `farm` và `lab`.
- Dữ liệu và tài khoản test: Farm (`farmer1`, `engineer1`, `admin1`), Lab (`student1`, `lecturer1`, `tech1`, `admin1`).
- Cách dựng và kịch bản chạy tay: [`RUNBOOK_KIEM_THU.md`](../RUNBOOK_KIEM_THU.md) mục 3, 4 và 5.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC5-01 | WPC5-01 | Biên dịch | `npx tsc --noEmit` | Đạt | Claude, 13/10/2026 | Đạt: `tsc` và `vite build` đạt |
| TC5-02 | WPC5-02 | Hồi quy | `DXOS_APP=farm`, đăng nhập `engineer1`, đi qua các trang | Đủ trang như trước | Claude, stack `DXOS_APP=farm`, 13/10/2026 | Đạt: luồng demo, yêu cầu (bộ lọc, mở dòng, nhận việc), hàng đợi chẩn đoán, tài liệu; menu không có "Nông hộ" với kỹ sư |
| TC5-03 | WPC5-02 | Ranh giới | Tìm import `apps/` trong `shell/` | Không có | Claude, 13/10/2026 | Đạt: `shell/` không import `apps/`; `apps/*` chỉ đi qua `shell/index.ts` |
| TC5-04 | WPC5-03 | Happy path | `student1` báo sự cố; `tech1` nhận, xử lý, đóng | Đủ trạng thái, timeline đầy đủ | Claude, stack `DXOS_APP=lab`, 13/10/2026 | Đạt: gợi ý loại, ưu tiên và bài liên quan khi gõ mô tả; `tech1` nhận, bắt đầu, hoàn tất; `student1` xác nhận 5 sao, ticket đóng, timeline đủ; báo sự cố kèm ảnh PNG hiện ở mục tệp đính kèm |
| TC5-05 | WPC5-04 | Happy path | `student1` gửi yêu cầu mượn; `lecturer1` duyệt; trả thiết bị; `tech1` mở và hoàn tất nhật ký sửa | Phiếu và thiết bị đổi trạng thái đúng | Claude, stack `DXOS_APP=lab`, 13/10/2026 | Đạt: gửi, duyệt, nhận lại báo hỏng; thiết bị chuyển "Đang sửa" và có nhật ký sửa chữa; hoàn tất nhật ký thì thiết bị về "Sẵn sàng" |
| TC5-06 | WPC5-05 | Happy path | `lecturer1` chọn ô trên lịch tuần và đặt phòng; đặt chồng giờ | Lịch cập nhật; lần chồng giờ báo lỗi rõ bằng tiếng Việt | Claude, stack `DXOS_APP=lab`, 13/10/2026 | Đạt: ô chọn điền sẵn giờ, lịch hiện sau khi duyệt, đặt chồng giờ báo "Phòng A101 đã có lịch trong khung giờ này". Đặt bằng `tech1` |
| TC5-07 | WPC5-06 | Happy path | Từng vai đi hết kịch bản của mình; ADMIN xem thống kê; hỏi đáp quy chế | Mọi trang hiển thị dữ liệu đúng; hỏi đáp có nguồn | Claude, stack `DXOS_APP=lab`, 13/10/2026 | Đạt: trang chủ, danh mục, thống kê và hỏi đáp (nguồn, giấy phép CC BY 4.0) đúng theo vai; từ chối lịch kèm lý do, hủy lịch, mở lại ticket (đếm số lần mở lại) |
| TC5-08 | WPC5-01 | Chuyển app | Đổi `DXOS_APP=lab`, restart backend, tải lại web | Web hiện menu và trang Lab, không build lại | Claude, 13/10/2026 | Đạt: cùng một image web; `lab` → `farm` → `lab` chỉ đổi biến và restart backend, tiêu đề, menu và trang đổi theo |
| TC5-09 | WPC5-01 | Âm tính | Manifest trả id lạ | Trang lỗi rõ ràng | Claude, 13/10/2026 | Đạt: trả id `xyz` (chèn vào phản hồi `GET /api/v1/app` ở trình duyệt) thì hiện "Chưa có giao diện cho ứng dụng này" kèm nút đăng xuất. Tài khoản không có vai trong app: "Tài khoản chưa được cấp quyền" |
| TC5-10 | WPC5-03 | Phân quyền | STUDENT mở trang kỹ thuật viên bằng đường dẫn trực tiếp | Bị chặn | Claude, stack `DXOS_APP=lab`, 13/10/2026 | Đạt: `/queue` hiện "Không đủ quyền"; ticket của người khác báo không tìm thấy |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [x] Đăng nhập SSO qua Keycloak (web) vẫn hoạt động (PKCE qua client `dxos-web`, cả hai app).
- [x] Người dùng chỉ thấy dữ liệu thuộc workspace của mình (sinh viên không mở được ticket của người khác).
- [x] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời: timeline ghi đủ bước và người thực hiện; nhận việc khi ticket đã bị người khác nhận thì báo lỗi tiếng Việt và trang tải lại trạng thái hiện tại.
- [x] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối: tệp đính kèm mở được (200), sửa chữ ký thì bị từ chối, hạn 900 giây; việc quá hạn đã kiểm ở phase trước.
- [x] Dựng sạch bằng `reset.sh` lên đủ dịch vụ healthy với `DXOS_APP=lab` và `DXOS_APP=farm`.

**Theo app**

- Farm:
  - [x] FARMER dùng token web bị 403, dùng token mobile được: `GET /api/v1/parcels` với client `farm-mobile` trả 200, với `dxos-web` trả 403; web hiện "Tài khoản không dùng được trên web".
- Lab:
  - [x] STUDENT không thấy ticket của người khác và không thấy bình luận nội bộ (ticket của người khác báo không tìm thấy; ô ghi chú nội bộ chỉ hiện với kỹ thuật viên).

## 4. Tiêu chí đạt của phase (exit criteria)

- TC5-01 đến TC5-10 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: `npx tsc --noEmit` và `vite build` đạt; thao tác trên trình duyệt qua gateway `localhost:9080` ở hai chế độ `DXOS_APP` (13/10/2026).
- Lỗi phát hiện:
  - Thống kê "Mức sử dụng phòng" báo 100% cho phòng chưa có lịch (`LEAST` và `GREATEST` của PostgreSQL bỏ qua NULL); đã sửa ở `LabStatsService` và kiểm lại: phòng chưa có lịch báo 0 giờ.
  - FARMER đăng nhập web bị gateway chặn cả `GET /api/v1/app`, nên web chỉ báo lỗi tải ứng dụng; đã thêm nhánh 403 hiện thông báo tài khoản chỉ dùng được trên di động.
  - Sau một thao tác bị từ chối (ví dụ nhận việc khi đã có người nhận) trang vẫn giữ trạng thái cũ; đã cho trang tải lại sau lỗi.
- Ghi nhận: chuyển app trên cùng một DB giữ dữ liệu của app trước; hộp duyệt của Farm hiện thêm các case Lab cũ với nhãn là chuỗi gốc, hết khi dựng lại sạch. Trang đơn vị chỉ liệt kê loại workspace của app đang chạy.
