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

- [X] **Yêu cầu hỗ trợ** → **Báo sự cố mới** mở form.
- [X] Gõ tiêu đề "Máy chiếu phòng A101 không lên hình" và mô tả; ngừng gõ khoảng nửa giây thì hộp **Gợi ý** hiện.
- [X] Hộp gợi ý chỉ hiện loại sự cố (máy chiếu) và nút **Dùng gợi ý này**; không có chữ "ưu tiên", từ khóa khớp hay lý do.
- [X] Các bài tri thức liên quan gom theo từng tài liệu, mỗi tài liệu một thẻ, các bước đánh số hiện thành danh sách.
- [X] Bấm **Dùng gợi ý này**: loại và ưu tiên trong form được điền theo gợi ý.
- [X] Gõ thêm "đang thi" hoặc "cả phòng": ưu tiên gợi ý được nâng lên.
- [X] Ô đính kèm ghi "Ảnh minh hoạ" và chỉ chọn được JPEG hoặc PNG; chọn 1 ảnh dưới 5 MiB thì nhận.
- [X] Chọn tệp khác ảnh (ví dụ `.pdf`, `.zip`) hoặc ảnh quá 5 MiB: bị từ chối với thông báo lỗi.
- [X] Chọn quá 5 ảnh: bị từ chối.
- [X] Ô **Phòng** có thêm lựa chọn **Phòng khác…**; chọn thì hiện ô nhập tên hoặc vị trí phòng, và chi tiết ticket hiện đúng chữ đã nhập.
- [X] **Gửi yêu cầu**: ticket có mã dạng `TK-<năm>-<số>`, trạng thái `NEW`.
- [X] Chi tiết ticket hiện hạn phản hồi và hạn xử lý đúng với SLA của mức ưu tiên (xem 8.2).
- [X] Mattermost `http://localhost:8065`, kênh kỹ thuật viên: có tin ticket mới, kèm ảnh nếu có ảnh, bấm tiêu đề mở đúng ticket.

### 2.2 Nhận việc và xử lý (luồng chính)

Đăng nhập `tech1`.

- [X] **Hàng đợi xử lý** → tab **Chưa có người nhận** có ticket vừa tạo.
- [X] **Nhận việc**: ticket sang `ASSIGNED`, tab đó không còn ticket này.
- [X] `student1`: chuông có thông báo "Yêu cầu được tiếp nhận".
- [X] `tech1` mở ticket → **Thao tác** → **Bắt đầu xử lý**: `IN_PROGRESS`.
- [X] `tech1` viết một phản hồi công khai trong **Trao đổi**: `student1` nhận thông báo "Có phản hồi mới".
- [X] `tech1` viết một ghi chú ở ô **Nội bộ**: `student1` không thấy ghi chú này, `tech2` và `admin1` thấy.
- [X] `student1` trả lời trong **Trao đổi**: `tech1` nhận thông báo "Có phản hồi mới".
- [X] `tech1` **Hoàn tất xử lý** để trống cách xử lý hoặc nguyên nhân gốc: bị chặn, báo thiếu trường bắt buộc.
- [X] `tech1` **Hoàn tất xử lý** điền đủ cách xử lý và nguyên nhân gốc: `RESOLVED`.
- [X] `student1`: nhận thông báo yêu cầu đã xử lý xong.
- [X] `student1` **Xác nhận đã xử lý xong**, chọn 4 sao, thêm nhận xét: `CLOSED`.
- [X] **Lịch sử xử lý** cuối chi tiết liệt kê đủ các bước theo thứ tự và không có nút sửa hoặc xóa.
- [X] Ticket `CLOSED`: không thêm được tệp đính kèm nữa.

### 2.3 Chờ người gửi bổ sung

Tạo ticket mới bằng `student1`, `tech1` nhận việc và bắt đầu xử lý.

- [X] `tech1` chọn **Chờ người gửi bổ sung** để trống câu hỏi: bị chặn.
- [X] `tech1` nhập câu hỏi: ticket `WAITING_USER`; câu hỏi hiện thành phản hồi công khai.
- [X] `student1` nhận thông báo "Cần bạn bổ sung thông tin".
- [X] `student1` trả lời trong **Trao đổi**: ticket tự về `IN_PROGRESS`.

### 2.4 Mở lại

Dùng ticket đã `RESOLVED` (hoàn tất bằng `tech1`).

- [X] `student1` **Chưa xong, mở lại** để trống vấn đề: bị chặn.
- [X] `student1` mở lại có ghi vấn đề còn lại: về `IN_PROGRESS`.
- [X] Mở lại lần thứ 3 vẫn được.
- [X] Mở lại lần thứ 4: bị từ chối, yêu cầu tạo ticket mới.

### 2.5 Huỷ và từ chối

- [X] `student1` tạo ticket rồi bấm **Huỷ yêu cầu** khi còn `NEW`: `CANCELLED`.
- [X] Ticket đã `ASSIGNED`: `student1` không còn nút **Huỷ yêu cầu**.
- [X] `tech1` mở ticket `NEW` hoặc `ASSIGNED`: không có nút **Từ chối yêu cầu**.
- [X] `admin1` **Từ chối yêu cầu** để trống lý do: bị chặn.
- [X] `admin1` từ chối có lý do: `REJECTED`; `student1` nhận thông báo kèm lý do.

### 2.6 Phân công bởi quản trị

- [X] `admin1` mở ticket `NEW`, chọn kỹ thuật viên ở ô **Giao cho kỹ thuật viên…** → **Phân công**: `ASSIGNED`.
- [X] Kỹ thuật viên được giao nhận thông báo "Bạn được gán yêu cầu".
- [X] `admin1` gán lại cho kỹ thuật viên khác khi ticket `ASSIGNED` hoặc `IN_PROGRESS`: được.
- [X] Danh sách kỹ thuật viên chỉ gồm người đã từng nhận ticket (hành vi số 6 ở `LAB_FLOWS.md` mục 5).

### 2.7 Hai người nhận cùng lúc

- [X] `tech1` và `tech2` cùng mở tab **Chưa có người nhận**, cùng thấy một ticket `NEW`.
- [X] `tech1` bấm **Nhận việc** trước, `tech2` bấm sau: `tech2` báo xung đột, ticket thuộc về `tech1`.

### 2.8 Ai thấy gì và bộ lọc

- [X] `student1` chỉ thấy ticket của mình; `student2` không thấy ticket của `student1`.
- [X] `student2` mở thẳng đường dẫn ticket của `student1`: báo không tồn tại.
- [X] `tech1` và `admin1` thấy mọi ticket.
- [X] Bộ lọc trạng thái, loại, ưu tiên cho danh sách đúng.
- [X] Bộ lọc **Quá hạn** chỉ giữ ticket quá hạn xử lý.
- [X] Nhân sự có thêm bộ lọc **Chưa có người nhận**; sinh viên không có.
- [X] Danh sách phân trang 10 dòng mỗi trang trong **Hàng đợi xử lý**.

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
- [ ] `tech1` không còn form **Gửi yêu cầu mượn** ở trang **Mượn thiết bị**, chỉ có danh sách phiếu để duyệt và nhận lại.
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

### 4.1 Lịch phòng và chọn ô

Đăng nhập `lecturer1`.

- [ ] **Đặt phòng** → **Lịch phòng**: chọn phòng; lưới tuần có các hàng giờ từ 07:00 đến 21:00, mỗi ô một giờ.
- [ ] Đổi tuần trước, tuần này, tuần sau: lưới cập nhật và dòng ngày đầu tuần đổi theo.
- [ ] Ô đã có lịch hiện mục đích và trạng thái, tô màu khác ô trống.
- [ ] Ô trống có màu chọn được; bấm một ô thì ô đó được tô đậm, ô **Bắt đầu** điền giờ đó, ô **Kết thúc** điền sau đó 1 giờ.
- [ ] Bấm ô đã có lịch: không chọn được.
- [ ] Phòng đang bảo trì: lưới không cho chọn ô.
- [ ] `student1` mở **Đặt phòng**: lưới chỉ để xem, không có ô nào chọn được.

### 4.2 Nhập giờ ở form

Hai ô **Bắt đầu** và **Kết thúc** là ô ngày giờ của trình duyệt, bước 30 phút.
Cách hiện giờ (24 giờ hay AM/PM) theo ngôn ngữ của trình duyệt.

- [ ] Gõ hoặc chọn tay được giờ ngoài lưới (ví dụ 22:00) và giờ lẻ 30 phút (ví dụ 10:30).
- [ ] Đặt 10:00 đến 12:00 ngày mai, phòng đủ sức chứa, có mục đích: gửi được, lịch `PENDING`.
- [ ] Lịch vừa gửi hiện ngay trong lưới tuần và giữ khung giờ.
- [ ] Đặt 10:00 đến 24:00 (14 giờ): bị từ chối sau khi bấm gửi với thông báo "Mỗi lần đặt tối đa 4 giờ" (`BOOKING_TOO_LONG`), lịch không được tạo. Đây là hành vi hiện tại của giới hạn 4 giờ (xem mục 10).
- [ ] Đặt 20:00 đến 00:00 hôm sau (4 giờ): gửi được.
- [ ] Giờ kết thúc không sau giờ bắt đầu: bị từ chối (`BOOKING_RANGE_INVALID`).
- [ ] Giờ lẻ không theo bước 30 phút (ví dụ 10:15): trình duyệt hoặc backend (`BOOKING_STEP_INVALID`) từ chối.

### 4.3 Quy tắc

- [ ] Số người vượt sức chứa phòng: bị từ chối.
- [ ] Đặt vào giờ đã qua: bị từ chối khi gửi (`BOOKING_IN_THE_PAST`).
- [ ] Đặt xa quá 30 ngày: bị từ chối khi gửi.
- [ ] Đặt quá 4 giờ liền: bị từ chối khi gửi.
- [ ] Đặt phòng A103 (đang bảo trì): bị từ chối.
- [ ] Đặt chồng giờ với lịch `PENDING` hoặc `APPROVED` cùng phòng: báo `ROOM_SLOT_TAKEN`.
- [ ] Đặt liền kề (kết thúc 12:00, bắt đầu 12:00): được.
- [ ] `student1` mở **Đặt phòng**: xem được lịch nhưng không có form đặt.
- [ ] `tech1` mở **Đặt phòng**: có lịch phòng và mục **Yêu cầu đặt phòng**, không có form đặt.

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
- [ ] Lưới tuần hiện mục đích và trạng thái của khung bận; tên người đặt chỉ có trong danh sách yêu cầu, và `student1` không có danh sách đó.

### 4.6 Hai người đặt cùng lúc

- [ ] `lecturer1` và `admin1` cùng mở form, cùng chọn một phòng và khung giờ, bấm gửi gần như đồng thời: một bên thành công, bên kia báo `ROOM_SLOT_TAKEN`.

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

- [ ] Giao diện có khung **Câu hỏi** nhiều dòng và nút **Hỏi**; nút bị khoá khi khung trống.
- [ ] Gõ "Đặt lại mật khẩu tài khoản sinh viên như thế nào" rồi **Hỏi**: nút hiện "Đang tìm…", sau đó có tối đa 4 đoạn trích.
- [ ] Mỗi đoạn nằm trong một khung riêng: tên tài liệu in đậm, nội dung giữ nguyên xuống dòng, dòng "Độ liên quan %".
- [ ] Dưới mỗi đoạn có mục **Nguồn**: tên tài liệu, đơn vị soạn (sau dấu gạch), nhãn giấy phép.
- [ ] Tài liệu chưa duyệt có nhãn **Chưa chuyên gia duyệt**.
- [ ] Tên nguồn là liên kết mở tab mới; hiện trỏ tới tệp tri thức trên GitHub (xem mục 10).
- [ ] Hỏi một câu ngoài phạm vi ("giá vàng hôm nay"): hiện gợi ý hỏi cụ thể hơn hoặc gửi ticket, không bịa câu trả lời.
- [ ] Nội dung đoạn trích khớp nguyên văn tài liệu, không phải văn bản sinh mới.
- [ ] Dừng AI Service (`docker compose stop ai`) rồi hỏi: báo lỗi 503, trang không vỡ; bật lại sau khi kiểm.

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
- [ ] Trang **Thống kê** không còn nút mở Superset.

### 8.2 Danh mục (chỉ quản trị)

- [ ] **Loại sự cố**: có 8 loại mặc định.
- [ ] Thêm loại mới với tên, ưu tiên mặc định, từ khóa: loại xuất hiện trong form ticket.
- [ ] Thêm từ khóa cho một loại: gõ từ khóa đó vào tiêu đề ticket thì gợi ý trả về loại này.
- [ ] Tắt một loại: không còn chọn được ở form ticket mới.
- [ ] **Chính sách SLA**: bảng hiện đúng 4 mức (Khẩn 1 giờ/4 giờ, Cao 4 giờ/1 ngày, Thường 1 ngày/3 ngày, Thấp 2 ngày/7 ngày).
- [ ] Sửa SLA rồi tạo ticket mới: hạn tính theo giá trị mới; ticket cũ giữ hạn cũ.
- [ ] **Lớp và bộ môn**: tạo đơn vị mới được và đơn vị xuất hiện trong danh sách.

## 9. Kiểm lại sau đợt chỉnh sửa giao diện ticket

### 9.1 Vai kỹ thuật viên

- [X] Menu của `tech1` không có **Yêu cầu hỗ trợ**; có Tổng quan, Mượn thiết bị, Đặt phòng, Tri thức, Hàng đợi xử lý, Phòng và thiết bị, Thống kê.
- [X] Tổng quan của `tech1` có lối tắt **Hàng đợi xử lý** và **Phòng và thiết bị**, không có **Báo sự cố**, **Mượn thiết bị**, **Đặt phòng**.
- [X] Trang **Đặt phòng** của `tech1` không có form đặt phòng; vẫn có lịch phòng và mục **Yêu cầu đặt phòng** để duyệt.
- [X] Từ **Hàng đợi xử lý** mở được chi tiết ticket; nút quay lại dẫn về hàng đợi.
- [X] `tech1` không thêm được ảnh vào ticket của người khác (không có ô thêm ảnh, API trả 403).

### 9.2 Chi tiết ticket

- [X] Bố cục hai cột: thông tin và thao tác bên trái, panel **Trao đổi** bên phải.
- [X] Panel **Trao đổi** có thanh cuộn riêng; trao đổi dài không đẩy phần thông tin ticket đi.
- [X] Gửi tin mới thì panel tự cuộn xuống cuối; Ctrl + Enter gửi được.
- [X] Ticket `CLOSED`, `REJECTED`, `CANCELLED`: panel **Trao đổi** không còn ô nhập.
- [X] `student1` thấy nút **Chỉnh sửa** khi ticket chưa xử lý xong; `tech1` không thấy.
- [X] Ở chế độ chỉnh sửa, đổi được tiêu đề, mô tả, phòng; sau khi lưu, **Trao đổi** có dòng "Người gửi đã cập nhật nội dung yêu cầu."
- [X] Ảnh đính kèm chỉ thêm và xoá được ở chế độ chỉnh sửa; ngoài chế độ này không có ô thêm ảnh.
- [X] Nút **Huỷ yêu cầu** màu đỏ nằm dưới cùng, chỉ hiện khi ticket `NEW`; bấm thì hiện hộp xác nhận, chọn huỷ bỏ thì ticket giữ nguyên.
- [X] Ticket `RESOLVED`: `tech1` không còn nút thao tác nào; ticket đóng: không còn nút làm đổi ticket.
- [X] Ô nhập khi bấm **Chờ người gửi bổ sung** đủ rộng, placeholder "Cần bổ sung gì?".

### 9.3 Lịch sử xử lý

- [X] `tech1` nhận việc: **Lịch sử xử lý** chỉ thêm một dòng "Nhận việc — tech1".
- [X] `admin1` phân công ticket `NEW` cho `tech1`: một dòng "Phân công — admin1" kèm "Giao cho: tech1".
- [X] `admin1` mở ticket `ASSIGNED` hoặc `IN_PROGRESS`: có ô **Giao lại cho…** (không liệt kê người đang xử lý) và nút **Giao lại**.
- [X] Sau khi giao lại, người xử lý mới hiện ở chi tiết, **Lịch sử xử lý** có dòng "Được giao việc — <người mới>", người mới nhận thông báo.

### 9.4 Thông báo và portal

- [X] Bấm thông báo về ticket: mở đúng chi tiết ticket đó và thông báo chuyển sang đã đọc.
- [X] Bấm thông báo về phiếu mượn hoặc lịch đặt phòng: mở trang **Mượn thiết bị** hoặc **Đặt phòng**.
- [X] Cổng `http://localhost:9080/portal/`: với Lab chỉ còn thẻ Mattermost và Mattermost Admin; không còn thẻ BI Dashboard, Superset Admin và dòng trạng thái BI.

## 10. Hành vi cần chốt

Các hành vi ở `LAB_FLOWS.md` mục 5 đang đúng như mô tả, nhưng bạn cần quyết định giữ hay đổi.
Đánh dấu `[x]` khi đã chấp nhận hành vi hiện tại.

- [ ] Kỹ thuật viên tự duyệt được lịch đặt phòng do chính mình gửi.
- [ ] Chỉ quản trị mới từ chối được ticket.
- [ ] Thời gian ticket chờ người gửi vẫn tính vào SLA.
- [ ] Chuyển phòng sang bảo trì không huỷ lịch đã đặt.
- [ ] Tối đa 4 giờ mỗi lần đặt phòng, chỉ báo lỗi sau khi gửi; ô chọn giờ không giới hạn trước.
- [ ] Chọn giờ đặt phòng bằng ô ngày giờ của trình duyệt (có thể hiện AM/PM), lưới tuần chỉ từ 07:00 đến 21:00.
- [ ] Sinh viên chỉ xem lịch phòng, không đặt được.
- [ ] Hỏi đáp chỉ trích tài liệu tự soạn, và nguồn là liên kết tới tệp tri thức trên GitHub, chưa có văn bản hay trang quy chế chính thức.
