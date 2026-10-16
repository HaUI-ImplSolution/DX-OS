# DX-Lab: checklist kiểm tay theo luồng

Mỗi dòng là một bước kiểm cùng kết quả mong đợi.
Làm đúng bước và thấy đúng kết quả thì đổi `[ ]` thành `[x]`; lệch thì để trống và ghi chú ngay sau dòng đó.
Mô tả đầy đủ từng luồng ở [`LAB_FLOWS.md`](./LAB_FLOWS.md) mục 4.

## 0. Chuẩn bị

- [X] `./deploy/start.sh lab` chạy xong, `http://localhost:9080` mở được trang đăng nhập.
- [X] Mở mỗi vai ở một cửa sổ ẩn danh hoặc một hồ sơ trình duyệt riêng, vì cùng một trình duyệt dùng chung phiên SSO.

| Username                   | Mật khẩu      | Vai              |
| -------------------------- | --------------- | ---------------- |
| `student1`, `student2` | `student123`  | Sinh viên       |
| `lecturer1`              | `lecturer123` | Giảng viên     |
| `tech1`, `tech2`       | `tech123`     | Kỹ thuật viên |
| `admin1`                 | `admin123`    | Quản trị       |

Muốn dữ liệu sạch trước khi kiểm: `./deploy/start.sh lab --reset --yes`.

## 1. Đăng nhập và menu

- [X] `student1` đăng nhập được; menu gồm Tổng quan, Yêu cầu hỗ trợ, Mượn thiết bị, Đặt phòng, Tri thức; không có Hàng đợi xử lý, Phòng và thiết bị, Thống kê, Danh mục.
- [X] `lecturer1`: cùng bộ menu như sinh viên.
- [X] `tech1`: thêm Hàng đợi xử lý, Phòng và thiết bị, Thống kê; không có Danh mục.
- [X] `admin1`: có đủ 9 mục, kể cả Danh mục.
- [X] `student1` gõ thẳng `/queue` hoặc `/catalog` trên thanh địa chỉ: báo "Không đủ quyền".
- [X] Bấm chuông **Thông báo**: hộp thông báo mở, danh sách chỉ của tài khoản đang đăng nhập.
- [X] Đăng xuất rồi đăng nhập tài khoản khác: không còn thấy dữ liệu của tài khoản trước.

## 2. Yêu cầu hỗ trợ (ticket)

### 2.1 Gửi ticket và gợi ý

Đăng nhập `student1`.

- [ ] **Yêu cầu hỗ trợ** → **Báo sự cố mới** mở form.
- [ ] Gõ tiêu đề "Máy chiếu phòng A101 không lên hình" và mô tả; ngừng gõ khoảng nửa giây thì hộp **Gợi ý** hiện.
- [ ] Hộp gợi ý có loại sự cố (máy chiếu), mức ưu tiên, từ khóa khớp, lý do.
- [ ] Hộp gợi ý có tối đa 3 bài tri thức liên quan.
- [ ] Bấm **Dùng gợi ý này**: loại và ưu tiên trong form được điền theo gợi ý.
- [ ] Gõ thêm "đang thi" hoặc "cả phòng": ưu tiên gợi ý được nâng lên.
- [ ] Đính kèm 1 ảnh PNG hoặc JPEG dưới 5 MiB: tệp hiện trong danh sách đính kèm.
- [ ] Đính kèm tệp loại khác (ví dụ `.zip`) hoặc tệp quá 5 MiB: bị từ chối với thông báo lỗi.
- [ ] Đính kèm quá 5 tệp: bị từ chối.
- [ ] **Gửi yêu cầu**: ticket có mã dạng `TK-<năm>-<số>`, trạng thái `NEW`.
- [ ] Chi tiết ticket hiện hạn phản hồi và hạn xử lý đúng với SLA của mức ưu tiên (xem 8.2).
- [ ] Mattermost `http://localhost:8065`, kênh kỹ thuật viên: có tin ticket mới.

### 2.2 Nhận việc và xử lý (luồng chính)

Đăng nhập `tech1`.

- [ ] **Hàng đợi xử lý** → tab **Chưa có người nhận** có ticket vừa tạo.
- [ ] **Nhận việc**: ticket sang `ASSIGNED`, tab đó không còn ticket này.
- [ ] `student1`: chuông có thông báo "Yêu cầu được tiếp nhận".
- [ ] `tech1` mở ticket → **Thao tác** → **Bắt đầu xử lý**: `IN_PROGRESS`.
- [ ] `tech1` viết một phản hồi công khai trong **Trao đổi**: `student1` nhận thông báo "Có phản hồi mới".
- [ ] `tech1` viết một ghi chú ở ô **Nội bộ**: `student1` không thấy ghi chú này, `tech2` và `admin1` thấy.
- [ ] `student1` trả lời trong **Trao đổi**: `tech1` nhận thông báo "Có phản hồi mới".
- [ ] `tech1` **Hoàn tất xử lý** để trống cách xử lý hoặc nguyên nhân gốc: bị chặn, báo thiếu trường bắt buộc.
- [ ] `tech1` **Hoàn tất xử lý** điền đủ cách xử lý và nguyên nhân gốc: `RESOLVED`.
- [ ] `student1`: nhận thông báo yêu cầu đã xử lý xong.
- [ ] `student1` **Xác nhận đã xử lý xong**, chọn 4 sao, thêm nhận xét: `CLOSED`.
- [ ] **Lịch sử xử lý** cuối chi tiết liệt kê đủ các bước theo thứ tự và không có nút sửa hoặc xóa.
- [ ] Ticket `CLOSED`: không thêm được tệp đính kèm nữa.

### 2.3 Chờ người gửi bổ sung

Tạo ticket mới bằng `student1`, `tech1` nhận việc và bắt đầu xử lý.

- [ ] `tech1` chọn **Chờ người gửi bổ sung** để trống câu hỏi: bị chặn.
- [ ] `tech1` nhập câu hỏi: ticket `WAITING_USER`; câu hỏi hiện thành phản hồi công khai.
- [ ] `student1` nhận thông báo "Cần bạn bổ sung thông tin".
- [ ] `student1` trả lời trong **Trao đổi**: ticket tự về `IN_PROGRESS`.

### 2.4 Mở lại

Dùng ticket đã `RESOLVED` (hoàn tất bằng `tech1`).

- [ ] `student1` **Chưa xong, mở lại** để trống vấn đề: bị chặn.
- [ ] `student1` mở lại có ghi vấn đề còn lại: về `IN_PROGRESS`.
- [ ] Mở lại lần thứ 3 vẫn được.
- [ ] Mở lại lần thứ 4: bị từ chối, yêu cầu tạo ticket mới.

### 2.5 Huỷ và từ chối

- [ ] `student1` tạo ticket rồi bấm **Huỷ yêu cầu** khi còn `NEW`: `CANCELLED`.
- [ ] Ticket đã `ASSIGNED`: `student1` không còn nút **Huỷ yêu cầu**.
- [ ] `tech1` mở ticket `NEW` hoặc `ASSIGNED`: không có nút **Từ chối yêu cầu**.
- [ ] `admin1` **Từ chối yêu cầu** để trống lý do: bị chặn.
- [ ] `admin1` từ chối có lý do: `REJECTED`; `student1` nhận thông báo kèm lý do.

### 2.6 Phân công bởi quản trị

- [ ] `admin1` mở ticket `NEW`, chọn kỹ thuật viên ở ô **Giao cho kỹ thuật viên…** → **Phân công**: `ASSIGNED`.
- [ ] Kỹ thuật viên được giao nhận thông báo "Bạn được gán yêu cầu".
- [ ] `admin1` gán lại cho kỹ thuật viên khác khi ticket `ASSIGNED` hoặc `IN_PROGRESS`: được.
- [ ] Danh sách kỹ thuật viên chỉ gồm người đã từng nhận ticket (hành vi số 6 ở `LAB_FLOWS.md` mục 5).

### 2.7 Hai người nhận cùng lúc

- [ ] `tech1` và `tech2` cùng mở tab **Chưa có người nhận**, cùng thấy một ticket `NEW`.
- [ ] `tech1` bấm **Nhận việc** trước, `tech2` bấm sau: `tech2` báo xung đột, ticket thuộc về `tech1`.

### 2.8 Ai thấy gì và bộ lọc

- [ ] `student1` chỉ thấy ticket của mình; `student2` không thấy ticket của `student1`.
- [ ] `student2` mở thẳng đường dẫn ticket của `student1`: báo không tồn tại.
- [ ] `tech1` và `admin1` thấy mọi ticket.
- [ ] Bộ lọc trạng thái, loại, ưu tiên cho danh sách đúng.
- [ ] Bộ lọc **Quá hạn** chỉ giữ ticket quá hạn xử lý.
- [ ] Nhân sự có thêm bộ lọc **Chưa có người nhận**; sinh viên không có.
- [ ] Danh sách phân trang 10 dòng mỗi trang trong **Hàng đợi xử lý**.

## 3. Mượn và trả thiết bị

### 3.1 Sinh viên mượn, giảng viên duyệt

Đăng nhập `student1`.

- [ ] **Mượn thiết bị**: danh sách chỉ cho chọn thiết bị `AVAILABLE`.
- [ ] Hạn trả quá 7 ngày kể từ hôm nay: bị từ chối.
- [ ] Hạn trả trong 7 ngày, có mục đích: phiếu `PENDING`.
- [ ] Thiết bị vẫn `AVAILABLE` khi phiếu còn chờ duyệt (xem ở `tech1`, **Phòng và thiết bị**).
- [ ] `student1` không có nút **Duyệt** trên phiếu của mình.
- [ ] `lecturer1` mở phiếu → **Duyệt**: `APPROVED`.
- [ ] Thiết bị sang `ON_LOAN`.
- [ ] `student1` nhận thông báo phiếu được duyệt kèm hạn trả.

### 3.2 Từ chối và huỷ

- [ ] `student1` tạo phiếu thứ hai; `lecturer1` **Từ chối** để trống lý do: bị chặn.
- [ ] `lecturer1` từ chối có lý do: `REJECTED`; `student1` nhận thông báo kèm lý do; thiết bị vẫn `AVAILABLE`.
- [ ] `student1` tạo phiếu rồi **Huỷ phiếu** khi còn `PENDING`: `CANCELLED`.
- [ ] Phiếu đã `APPROVED`: `student1` không còn nút **Huỷ phiếu**.
- [ ] `admin1` huỷ được phiếu `PENDING` của người khác.

### 3.3 Ai duyệt ai

- [ ] `lecturer1` mượn thiết bị: `lecturer1` không duyệt được phiếu của chính mình.
- [ ] `tech1` duyệt được phiếu của `lecturer1`.
- [ ] `tech1` mượn thiết bị: `tech2` không duyệt được; `admin1` duyệt được.
- [ ] `admin1` mượn thiết bị: `admin1` không tự duyệt được phiếu của mình.
- [ ] `tech1` không duyệt được phiếu của `student1` (chỉ giảng viên hoặc quản trị).

### 3.4 Giới hạn

- [ ] Một người có 2 phiếu đang chờ hoặc chưa trả: tạo phiếu thứ 3 bị từ chối.
- [ ] Hai sinh viên tạo phiếu chờ cho cùng một thiết bị: cả hai tạo được.
- [ ] Duyệt phiếu thứ nhất thành công; duyệt phiếu thứ hai báo `DEVICE_NOT_AVAILABLE`.

### 3.5 Trả thiết bị

- [ ] `tech1` mở phiếu `APPROVED` → **Nhận lại thiết bị** → **Bình thường**: phiếu `RETURNED`, thiết bị về `AVAILABLE`.
- [ ] Làm lại với phiếu khác, chọn **Hư hỏng** để trống mô tả: bị chặn.
- [ ] **Hư hỏng** có mô tả: phiếu `RETURNED`, thiết bị `UNDER_REPAIR`.
- [ ] Nhật ký bảo trì của thiết bị có thêm một mục sửa chữa đang mở.
- [ ] `student1` không có nút **Nhận lại thiết bị**.

### 3.6 Ai thấy gì

- [ ] `student1` chỉ thấy phiếu của mình.
- [ ] `lecturer1`, `tech1`, `admin1` thấy mọi phiếu; ô **Chỉ phiếu của tôi** lọc đúng.
- [ ] Phiếu quá hạn (dữ liệu demo có sẵn) có nhãn **Quá hạn**.

## 4. Đặt phòng

### 4.1 Lịch phòng và chọn giờ

Đăng nhập `lecturer1`.

- [ ] **Đặt phòng** → **Lịch phòng**: chọn phòng, lưới tuần chạy từ 06:00 đến 24:00.
- [ ] Đổi tuần trước và sau: lưới cập nhật.
- [ ] Ô đã qua bị làm mờ và không chọn được.
- [ ] Ô đã có lịch hiện tên và trạng thái.
- [ ] Bấm một ô trống: giờ bắt đầu được chọn và điền vào form.
- [ ] Bấm thêm một ô trống phía dưới cùng ngày: khoảng giờ được tô màu, "đến giờ" điền theo.
- [ ] Bấm ô cách xa quá 4 giờ hoặc bị ô đã đặt chắn giữa: không kéo dài được.
- [ ] Dòng tóm tắt hiện dạng "Thứ tư, 21/10/2026 · 10:00 – 12:00 (2 giờ)".
- [ ] Chọn ngày ở form: lưới tuần nhảy sang tuần chứa ngày đó.

### 4.2 Nhập giờ ở form (hệ 24 giờ)

- [ ] Ô "từ giờ" liệt kê từ 06:00 đến 23:30, bước 30 phút, không có AM hoặc PM.
- [ ] Chọn từ 10:00: ô "đến giờ" liệt kê từ 10:30 đến 14:00.
- [ ] Chọn từ 20:00: ô "đến giờ" có 24:00.
- [ ] Đặt 10:00 – 12:00 ngày mai, phòng đủ sức chứa, có mục đích: gửi được, lịch `PENDING`.
- [ ] Lịch vừa gửi hiện ngay trong lưới tuần và giữ khung giờ.

### 4.3 Quy tắc

- [ ] Số người vượt sức chứa phòng: bị từ chối.
- [ ] Đặt ngày hôm nay nhưng giờ đã qua: không chọn được hoặc bị từ chối.
- [ ] Đặt xa quá 30 ngày: không chọn được hoặc bị từ chối.
- [ ] Đặt phòng A103 (đang bảo trì): bị từ chối.
- [ ] Đặt chồng giờ với lịch `PENDING` hoặc `APPROVED` cùng phòng: báo `ROOM_SLOT_TAKEN`.
- [ ] Đặt liền kề (kết thúc 12:00, bắt đầu 12:00): được.
- [ ] `student1` mở **Đặt phòng**: xem được lịch nhưng không có form đặt.

### 4.4 Duyệt, từ chối, huỷ

- [ ] `tech1` mục **Yêu cầu đặt phòng** thấy lịch `PENDING` của `lecturer1`.
- [ ] `tech1` **Duyệt**: `APPROVED`; `lecturer1` nhận thông báo.
- [ ] `tech1` **Từ chối** để trống lý do: bị chặn.
- [ ] `tech1` từ chối có lý do: `REJECTED`, khung giờ được nhả (đặt lại đúng giờ đó được), `lecturer1` nhận thông báo kèm lý do.
- [ ] `lecturer1` **Huỷ lịch** khi `PENDING`: `CANCELLED`, khung giờ nhả.
- [ ] `lecturer1` **Huỷ lịch** khi `APPROVED`: `CANCELLED`, khung giờ nhả.
- [ ] `lecturer1` không có nút **Duyệt** hoặc **Từ chối**.

### 4.5 Ai thấy gì

- [ ] `lecturer1` chỉ thấy lịch do mình đặt trong danh sách yêu cầu.
- [ ] `tech1` và `admin1` thấy mọi lịch.
- [ ] Trong lưới tuần, tên người đặt hiện với nhân sự và với chính người đặt; `student1` chỉ thấy khung bận, không thấy tên.

### 4.6 Hai người đặt cùng lúc

- [ ] `lecturer1` và `tech1` cùng mở form, cùng chọn một phòng và khung giờ, bấm gửi gần như đồng thời: một bên thành công, bên kia báo `ROOM_SLOT_TAKEN`.

## 5. Quản lý phòng và thiết bị

Đăng nhập `tech1`, mở **Phòng và thiết bị**.

### 5.1 Phòng

- [ ] **Thêm phòng** với mã, tòa nhà, tên, loại, sức chứa hợp lệ: phòng mới hiện trong danh sách.
- [ ] Thêm phòng trùng mã: bị từ chối.
- [ ] **Sửa** đổi sức chứa: lưu được, form đặt phòng áp dụng sức chứa mới.
- [ ] Phòng có lịch `APPROVED` trong tương lai: **Chuyển sang bảo trì** thành công.
- [ ] Biểu ngữ cảnh báo liệt kê các lịch còn hiệu lực của phòng đó; các lịch không bị huỷ.
- [ ] Phòng bảo trì không còn nhận đặt mới.
- [ ] **Mở lại phòng**: nhận đặt trở lại.

### 5.2 Thiết bị

- [ ] **Thêm thiết bị** đủ mã tài sản, loại, model, serial, ngày mua, hạn bảo hành, phòng: thiết bị mới `AVAILABLE`.
- [ ] Thêm thiết bị trùng mã tài sản: bị từ chối.
- [ ] Lọc theo phòng, trạng thái, loại cho kết quả đúng.
- [ ] **Sửa** thiết bị: lưu được.
- [ ] Mở dòng thiết bị: thấy lịch sử mượn của riêng thiết bị đó.

### 5.3 Bảo trì

- [ ] Mở dòng thiết bị `AVAILABLE`, chọn **Sửa chữa**, nhập nội dung → **Ghi nhật ký và đặt đang sửa**: thiết bị `UNDER_REPAIR`.
- [ ] Chọn **Thay thế**: thiết bị cũng sang `UNDER_REPAIR`.
- [ ] Chọn **Kiểm tra**: thiết bị giữ nguyên trạng thái.
- [ ] Thiết bị đang `UNDER_REPAIR`: không ghi thêm đợt bảo trì thứ hai.
- [ ] **Hoàn tất sửa chữa**, ghi kết quả → **Hoàn tất**: thiết bị về `AVAILABLE`.
- [ ] Thiết bị `ON_LOAN`: không bảo trì và không thanh lý được.
- [ ] **Thanh lý** thiết bị `AVAILABLE`: `RETIRED`, vẫn xem được lịch sử.
- [ ] Thiết bị `RETIRED` không xuất hiện trong danh sách chọn ở **Mượn thiết bị**.

## 6. Hỏi đáp quy chế

Đăng nhập bất kỳ vai nào, mở **Tri thức**.

- [ ] Giao diện có một ô nhập câu hỏi một dòng, nút **Hỏi** và các câu gợi ý bấm được.
- [ ] Bấm một câu gợi ý: tự gửi, nút hiện "Đang tìm…" rồi ra kết quả.
- [ ] Gõ "Đặt lại mật khẩu tài khoản sinh viên như thế nào" rồi **Hỏi**: có tối đa 4 đoạn trích.
- [ ] Mỗi đoạn là một thẻ có tên tài liệu và "Độ liên quan %".
- [ ] Đoạn có bước đánh số hiện thành danh sách số, không lặp tiêu đề.
- [ ] Phần **Trích từ** hiện tên tài liệu, đơn vị soạn, giấy phép.
- [ ] Tài liệu chưa duyệt có nhãn **Chưa chuyên gia duyệt**.
- [ ] Tên tài liệu ở **Trích từ** không phải đường dẫn bấm được và không mở GitHub.
- [ ] Hỏi một câu ngoài phạm vi ("giá vàng hôm nay"): hiện gợi ý hỏi cụ thể hơn hoặc gửi ticket, không bịa câu trả lời.
- [ ] Nội dung đoạn trích khớp nguyên văn tài liệu, không phải văn bản sinh mới.
- [ ] Dừng AI Service (`docker compose stop ai`) rồi hỏi: báo lỗi 503, trang không vỡ; bật lại sau khi kiểm.
- [ ] Trên màn hình hẹp (thu cửa sổ), thẻ và ô hỏi vẫn đọc được, không tràn ngang.

## 7. Thông báo, nhắc việc và chat

### 7.1 Hộp thông báo

- [ ] Sau mỗi bước ở các mục 2 đến 4, chuông tài khoản liên quan tăng số chưa đọc trong tối đa 60 giây (hoặc ngay khi tải lại trang).
- [ ] Bấm một thông báo: được đánh dấu đã đọc, số chưa đọc giảm 1.
- [ ] Nút đánh dấu tất cả đã đọc: số chưa đọc về 0.
- [ ] Thông báo của người này không xuất hiện ở hộp của người khác.

### 7.2 Cảnh báo SLA

Đăng nhập `admin1` → **Danh mục** → **Chính sách SLA**.

- [ ] Đặt hạn xử lý của mức ưu tiên Thường xuống 10 phút, lưu.
- [ ] `student1` tạo ticket ưu tiên Thường, `tech1` nhận việc.
- [ ] Sau khoảng 8 phút (80%) và một chu kỳ 5 phút: `tech1` nhận thông báo "Yêu cầu sắp quá hạn".
- [ ] Qua 10 phút và một chu kỳ 5 phút: `tech1` nhận thông báo "Yêu cầu quá hạn xử lý"; ticket có nhãn **Quá hạn**; kênh Mattermost kỹ thuật viên và kênh quản trị có tin.
- [ ] Mỗi loại thông báo chỉ đến một lần cho mỗi ticket dù đợi thêm vài chu kỳ.
- [ ] Ticket quá hạn khi chưa ai nhận: chỉ có tin Mattermost và bộ lọc **Quá hạn**, không có thông báo trong app.
- [ ] Sau khi kiểm, trả hạn xử lý về 3 ngày (4320 phút).

### 7.3 Nhắc trả thiết bị

Việc chạy lúc 07:00 hằng ngày nên khó kiểm trong ngày.

- [ ] Phiếu quá hạn trong dữ liệu demo hiện nhãn **Quá hạn**.
- [ ] Sau 07:00 ngày hôm sau: người mượn có thông báo "Sắp đến hạn trả thiết bị" hoặc "Quá hạn trả thiết bị" tùy phiếu.

### 7.4 Chat

- [ ] Ticket mới: kênh kỹ thuật viên có tin.
- [ ] Phiếu mượn và lịch đặt phòng mới: không có tin chat.

## 8. Thống kê và danh mục

### 8.1 Tổng quan và thống kê

- [ ] Tổng quan của `tech1`: có bộ đếm chưa có người nhận, giao cho tôi, quá hạn, lịch chờ duyệt, phiếu chờ duyệt; con số khớp với danh sách tương ứng.
- [ ] Danh sách ticket quá hạn ở Tổng quan khớp bộ lọc **Quá hạn**.
- [ ] Tổng quan của `admin1`: có thêm tổng quan 30 ngày.
- [ ] Tổng quan của `student1`: lối tắt, ticket gần đây, thiết bị đang mượn.
- [ ] Tổng quan của `lecturer1`: thêm phiếu chờ duyệt và lịch đặt phòng của mình.
- [ ] **Thống kê**: mặc định 30 ngày, có số ticket theo trạng thái và loại, tỷ lệ đúng hạn SLA, thời gian phản hồi và xử lý trung bình, số quá hạn, điểm hài lòng, tải kỹ thuật viên, mức dùng phòng, thiết bị theo trạng thái, phiếu mượn quá hạn.
- [ ] Đổi khoảng ngày: số liệu đổi theo.
- [ ] Khoảng quá 366 ngày: bị từ chối.
- [ ] Điểm hài lòng phản ánh đúng sao đã chấm ở mục 2.2.
- [ ] `admin1` bấm **Mở dashboard Superset**: mở `http://localhost:9081`, thấy dashboard Lab.

### 8.2 Danh mục (chỉ quản trị)

- [ ] **Loại sự cố**: có 8 loại mặc định.
- [ ] Thêm loại mới với tên, ưu tiên mặc định, từ khóa: loại xuất hiện trong form ticket.
- [ ] Thêm từ khóa cho một loại: gõ từ khóa đó vào tiêu đề ticket thì gợi ý trả về loại này.
- [ ] Tắt một loại: không còn chọn được ở form ticket mới.
- [ ] **Chính sách SLA**: bảng hiện đúng 4 mức (Khẩn 1 giờ/4 giờ, Cao 4 giờ/1 ngày, Thường 1 ngày/3 ngày, Thấp 2 ngày/7 ngày).
- [ ] Sửa SLA rồi tạo ticket mới: hạn tính theo giá trị mới; ticket cũ giữ hạn cũ.
- [ ] **Lớp và bộ môn**: tạo đơn vị mới được và đơn vị xuất hiện trong danh sách.

## 9. Hành vi cần chốt

Các hành vi ở `LAB_FLOWS.md` mục 5 đang đúng như mô tả, nhưng bạn cần quyết định giữ hay đổi.
Đánh dấu `[x]` khi đã chấp nhận hành vi hiện tại.

- [ ] Kỹ thuật viên tự duyệt được lịch đặt phòng do chính mình gửi.
- [ ] Chỉ quản trị mới từ chối được ticket.
- [ ] Thời gian ticket chờ người gửi vẫn tính vào SLA.
- [ ] Chuyển phòng sang bảo trì không huỷ lịch đã đặt.
- [ ] Tối đa 4 giờ mỗi lần đặt phòng.
- [ ] Sinh viên chỉ xem lịch phòng, không đặt được.
- [ ] Hỏi đáp chỉ trích tài liệu tự soạn, không liên kết ra văn bản gốc.
