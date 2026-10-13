# Kế hoạch kiểm thử: Phase C4

Quy ước dự án: mặc định chỉ compile và đọc diff.
Chạy test, dựng stack hoặc gọi thử endpoint chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".
Không thêm file test mới nếu người dùng không yêu cầu; các case dưới đây kiểm thủ công hoặc qua gọi API.

## 1. Chiến lược

- Mức kiểm thử áp dụng: compile, tìm kiếm import, gọi API thủ công, kiểm tra quyền, kiểm tra hai chế độ `DXOS_APP`, kiểm đồng thời bằng hai phiên.
- Môi trường: clone sạch, `deploy/reset.sh`, chạy lần lượt `DXOS_APP=lab` và `DXOS_APP=farm`.
- Dữ liệu và tài khoản test: `student1`, `lecturer1`, `tech1`, `tech2`, `admin1` của Lab (seed ở C6; trước đó tạo tay qua Admin API).
- Cách dựng và kịch bản chạy tay: [`RUNBOOK_KIEM_THU.md`](../RUNBOOK_KIEM_THU.md) mục 3 và 5.3.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC4-01 | WPC4-01 | Ranh giới | Khởi động `DXOS_APP=lab`, xem bảng | Có bảng Lab, không có bảng Farm, không lỗi seed | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: DB sạch, migration lab V1-V3, có bảng Lab, không có bảng Farm |
| TC4-02 | WPC4-01 | Ranh giới | Khởi động `DXOS_APP=farm`, xem bảng | Có bảng Farm, không có bảng Lab | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: `DXOS_APP=farm` có bảng Farm, không có bảng Lab |
| TC4-03 | WPC4-02 | Happy path | TECHNICIAN thêm phòng và thiết bị | Lưu được; mã tài sản trùng bị 409 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-04 | WPC4-02 | Phân quyền | STUDENT thêm thiết bị, sửa SLA | 403 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-05 | WPC4-03 | Happy path | STUDENT tạo ticket kèm ảnh | Mã `TK-{năm}-{số}`, NEW, có hạn phản hồi và hạn xử lý theo ưu tiên | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-06 | WPC4-03 | Happy path | `tech1` nhận, bắt đầu, trả lời công khai; `student1` trả lời | Phản hồi đầu tiên được ghi; bình luận hiển thị đúng | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-07 | WPC4-03 | Phân quyền | STUDENT xem bình luận nội bộ và ticket của người khác | Không thấy; 404 hoặc 403 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-08 | WPC4-03 | Happy path | `tech1` đặt WAITING_USER, `student1` trả lời | Tự về IN_PROGRESS | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-09 | WPC4-03 | Poka-yoke | Resolve không có ghi chú hoặc nguyên nhân gốc | 422 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-10 | WPC4-03 | Happy path | Resolve, `student1` xác nhận kèm 4 sao | CLOSED, điểm được ghi, `workflow_event` đủ | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-11 | WPC4-03 | Poka-yoke | Mở lại lần thứ tư | 422 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-12 | WPC4-03 | Đồng thời | `tech1` và `tech2` cùng nhận một ticket | Một bên 409 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-13 | WPC4-04 | Happy path | `student1` mượn thiết bị, `lecturer1` duyệt, trả | Thiết bị: sẵn sàng → đang mượn → sẵn sàng | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-14 | WPC4-04 | Poka-yoke | Mượn thiết bị đang mượn hoặc đang sửa | 422, không tạo phiếu | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-15 | WPC4-04 | Poka-yoke | Mượn quá 7 ngày; phiếu thứ ba đang mở | 422 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-16 | WPC4-04 | Happy path | Trả thiết bị báo hỏng | Thiết bị đang sửa, có nhật ký sửa chữa | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-17 | WPC4-05 | Happy path | `lecturer1` đặt phòng, `tech1` duyệt | Lịch hiện trong `schedule` | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-18 | WPC4-05 | Poka-yoke | Đặt chồng giờ cùng phòng | Bị từ chối | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-19 | WPC4-05 | Đồng thời | Hai phiên đặt cùng phòng cùng giờ gần như đồng thời | Chỉ một lịch hiệu lực | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: 4 yêu cầu song song chỉ 1 thành công; chèn trực tiếp vào DB bị ràng buộc `room_bookings_no_overlap` chặn |
| TC4-20 | WPC4-05 | Poka-yoke | Đặt quá 4 giờ, quá khứ, vượt sức chứa, phòng bảo trì; STUDENT tự đặt | 422 hoặc 403 | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-21 | WPC4-06 | Happy path | Ticket được nhận, được trả lời, đã xử lý | Người liên quan có thông báo; kỹ thuật viên có tin ở kênh chat khi ticket mới | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: thông báo trong app đúng người; ticket mới hiện ở kênh Mattermost `ky-thuat-vien` với mã, tiêu đề, loại, ưu tiên, người gửi |
| TC4-22 | WPC4-06 | Lịch | Ticket đạt 80% rồi quá hạn xử lý (rút ngắn SLA khi thử) | Cảnh báo, rồi đánh dấu quá hạn và báo cho người liên quan; không gửi trùng | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt (SLA NORMAL rút còn 6 phút, cron mỗi phút): cảnh báo ở 80% rồi quá hạn, mỗi loại đúng 1 thông báo sau thêm 2 lượt kiểm tra |
| TC4-23 | WPC4-07 | Happy path | `suggest` với "máy chiếu phòng A2 không lên hình" | Loại thiết bị, từ khóa khớp, lý do; kèm đoạn tri thức liên quan | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-24 | WPC4-07 | Happy path | `suggest` với "cả phòng máy mất mạng giữa giờ thi" | Ưu tiên khẩn, lý do nêu rõ | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-25 | WPC4-07 | Happy path | Hỏi đáp quy chế phòng máy | Đoạn trích kèm nguồn và giấy phép | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt |
| TC4-26 | WPC4-08 | Happy path | `lab-stats/summary` trên dữ liệu mẫu | Số liệu khớp đếm tay ở DB | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: số ticket và trạng thái khớp đếm ở DB |
| TC4-27 | WPC4-08 | Phân quyền | Vai chỉ đọc đọc view Lab và một bảng không đăng ký | Đọc được view; bảng khác bị từ chối | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: `superset_ro` đọc `vw_lab_*`, `tickets` và `device_loans` bị từ chối |
| TC4-28 | WPC4-01 | Ranh giới | Tìm import ngoài `dx-core-api` trong `app-lab` | Không có | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: không import nào ngoài `dx-core-api` và gói của Lab |
| TC4-29 | WPC4-01 | Hồi quy | Kiểm diff `dx-core`, `app-farm` | Không đổi ngoài bổ sung interface | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: không có thay đổi ở `dx-core`, `app-farm` |
| TC4-30 | WPC4-03 | Hồi quy | Hộp duyệt liệt kê case Lab | Có ticket, phiếu mượn, đặt phòng với tóm tắt đúng | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: hộp duyệt có ticket, phiếu mượn, đặt phòng |
| TC4-31 | WPC4-01 | Ranh giới | Khởi động `dx-host` với `DXOS_APP=lab`, chưa cài interface nào ngoài `AppModule` (kiểm của TC2-14) | Core lên được, `GET /api/v1/app` trả id `lab` | Claude, stack `DXOS_APP=lab`, 12/10/2026 | Đạt: `GET /api/v1/app` trả `lab` với 4 vai |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [x] Đăng nhập SSO qua Keycloak (web, Superset) vẫn hoạt động (đăng nhập PKCE của các tài khoản qua client `dxos-web`).
- [x] Lab phân quyền theo vai và người gửi; loại workspace (`CLASS`, `DEPARTMENT`) chỉ khai báo, chưa dùng để lọc dữ liệu. Phạm vi xem theo người gửi đã kiểm ở TC4-07.
- [x] Workflow ghi đủ lịch sử, người phụ trách và chặn ghi đè đồng thời (HTTP 409).
- [x] Link ảnh có hạn hoạt động, hết hạn thì bị từ chối (tệp đính kèm ticket: còn hạn 200, sửa chữ ký 403, sau 15 phút 403).
- [x] Thông báo trong app và Mattermost vẫn gửi, lỗi webhook không làm hỏng nghiệp vụ (webhook 400 chỉ ghi cảnh báo).
- [x] Gửi lại yêu cầu với `Idempotency-Key` cũ không tạo bản ghi trùng (tạo ticket).
- [x] Dựng từ clone sạch bằng `reset.sh` lên đủ dịch vụ healthy với `DXOS_APP=lab` và `DXOS_APP=farm`.

**Theo app**

- Farm:
  - [x] Với `DXOS_APP=farm`, API và hành vi Farm giống trước phase (`e2e.py` 10/10 bước, không thao tác nào của baseline OpenAPI bị mất).
- Lab:
  - [x] STUDENT chỉ thấy ticket và phiếu của mình; TECHNICIAN thấy toàn bộ.
  - [ ] Dữ liệu mẫu nạp lại hai lần không tạo trùng (seed ở C6).

## 4. Tiêu chí đạt của phase (exit criteria)

- Mọi case TC4-01 đến TC4-30 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: log khởi động hai chế độ, kết quả gọi API (script chạy qua `deploy/demo/dxos_client.py`: 77 bước đạt ở lượt đầu, 2 bước phân loại lỗi đã sửa và đạt ở lượt sau, SLA và nhắc mượn trả đạt).
- Backend: `./mvnw verify` đạt 80 test (13 `dx-core`, 21 `app-farm`, 46 `dx-host`), 0 lỗi, 0 bỏ qua (Claude, 13/10/2026).
- AI: `python -m pytest tests -q` đạt 64 test (Claude, 13/10/2026).
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
