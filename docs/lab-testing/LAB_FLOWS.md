# DX-Lab: chức năng và luồng đầu cuối

Tài liệu mô tả những gì DX-Lab làm được và từng luồng nghiệp vụ từ người gửi đến người xử lý, để kiểm tay và rà soát.
Quy tắc chi tiết và cấu hình ở [`backend/app-lab/README.md`](../backend/app-lab/README.md); kịch bản kiểm thử theo mã L1 đến L17 ở [`TESTING.md`](./TESTING.md) mục 6.

## 1. Chạy và đăng nhập

```bash
./deploy/start.sh lab        # dựng stack Lab và nạp dữ liệu demo
```

Web ở `http://localhost:9080`, đăng nhập bằng SSO Keycloak.

| Username | Mật khẩu | Vai | Đơn vị |
|---|---|---|---|
| `student1`, `student2` | `student123` | Sinh viên | Lớp CNTT K15A |
| `lecturer1` | `lecturer123` | Giảng viên | Bộ môn Công nghệ phần mềm |
| `tech1`, `tech2` | `tech123` | Kỹ thuật viên | |
| `admin1` | `admin123` | Quản trị viên | |

Dữ liệu demo gồm 5 phòng (A103 đang bảo trì), 7 thiết bị, 12 ticket ở đủ trạng thái, 5 phiếu mượn và 5 lịch đặt phòng; xem [`deploy/demo/seed_lab.py`](../deploy/demo/seed_lab.py).

## 2. Vai và menu

Menu lấy từ backend (`LabAppModule`); mục không thuộc vai thì không hiện và gõ đường dẫn trực tiếp sẽ báo "Không đủ quyền".
Kỹ thuật viên làm việc từ **Hàng đợi xử lý**; họ không báo sự cố, không mượn thiết bị và không đặt phòng, chỉ duyệt phiếu mượn, nhận lại thiết bị và duyệt lịch đặt phòng.

| Menu | Đường dẫn | Sinh viên | Giảng viên | Kỹ thuật viên | Quản trị |
|---|---|---|---|---|---|
| Tổng quan | `/` | có | có | có | có |
| Yêu cầu hỗ trợ | `/tickets` | có | có | | có |
| Mượn thiết bị | `/device-loans` | có | có | duyệt và nhận lại | có |
| Đặt phòng | `/room-bookings` | chỉ xem lịch | có | duyệt | có |
| Tri thức | `/knowledge` | có | có | có | có |
| Hàng đợi xử lý | `/queue` | | | có | có |
| Phòng và thiết bị | `/assets` | | | có | có |
| Thống kê | `/stats` | | | có | có |
| Danh mục | `/catalog` | | | | có |

Chuông **Thông báo** ở đầu trang mở hộp thông báo của chính tài khoản; số chưa đọc tự cập nhật mỗi 60 giây.

## 3. Bản đồ chức năng

| Chức năng | Người dùng chính | Luồng |
|---|---|---|
| Yêu cầu hỗ trợ (ticket) | Mọi vai gửi; kỹ thuật viên xử lý | 4.1 |
| Mượn và trả thiết bị | Sinh viên, giảng viên mượn; giảng viên, kỹ thuật viên duyệt | 4.2 |
| Đặt phòng | Giảng viên, quản trị đặt; kỹ thuật viên duyệt | 4.3 |
| Quản lý phòng và thiết bị | Kỹ thuật viên, quản trị | 4.4 |
| Hỏi đáp quy chế | Mọi vai | 4.5 |
| Thông báo, nhắc việc, chat | Hệ thống | 4.6 |
| Thống kê | Kỹ thuật viên, quản trị | 4.7 |
| Danh mục: loại sự cố, SLA, lớp và bộ môn | Quản trị | 4.8 |

## 4. Luồng đầu cuối

### 4.1 Yêu cầu hỗ trợ

**Trạng thái:** `NEW` → `ASSIGNED` → `IN_PROGRESS` ↔ `WAITING_USER`; `IN_PROGRESS` → `RESOLVED` → `CLOSED` hoặc mở lại về `IN_PROGRESS`; `NEW`, `ASSIGNED` → `REJECTED`; `NEW` → `CANCELLED`.

| Bước | Ai | Màn hình và thao tác | Kết quả |
|---|---|---|---|
| 1 | Người gửi | **Yêu cầu hỗ trợ** → **Báo sự cố mới** → nhập tiêu đề và mô tả | Sau khoảng nửa giây ngừng gõ, hộp **Gợi ý** hiện loại sự cố và các bài tri thức liên quan, gom theo từng tài liệu |
| 2 | Người gửi | Bấm **Dùng gợi ý này** hoặc tự chọn loại, ưu tiên, phòng (hoặc **Phòng khác…** rồi nhập tay), thiết bị; đính kèm tối đa 5 ảnh JPEG hoặc PNG, mỗi ảnh tối đa 5 MiB; **Gửi yêu cầu** | Ticket mã `TK-<năm>-<số>`, trạng thái `NEW`, hạn phản hồi và hạn xử lý tính theo SLA của mức ưu tiên; kênh chat kỹ thuật viên nhận tin ticket mới kèm ảnh đầu tiên và liên kết mở ticket |
| 3 | Kỹ thuật viên | **Hàng đợi xử lý** → tab **Chưa có người nhận** → **Nhận việc** | `ASSIGNED`; người gửi nhận thông báo "Yêu cầu được tiếp nhận"; hai người nhận cùng lúc thì một bên báo xung đột (409) |
| 3' | Quản trị | Trong chi tiết ticket `NEW`, chọn kỹ thuật viên ở ô **Giao cho kỹ thuật viên…** → **Phân công**; với ticket `ASSIGNED` hoặc `IN_PROGRESS` thì ô **Giao lại cho…** → **Giao lại** | `ASSIGNED`, người được giao nhận thông báo; lịch sử ghi một bước "Phân công" kèm người được giao |
| 4 | Người nhận việc | Chi tiết ticket → **Thao tác** → **Bắt đầu xử lý** | `IN_PROGRESS`; ghi mốc phản hồi đầu tiên |
| 5 | Hai bên | Mục **Trao đổi**: viết phản hồi; kỹ thuật viên có thêm ô **Nội bộ** | Phản hồi công khai gửi thông báo cho bên kia; ghi chú nội bộ chỉ nhân sự thấy. Kỹ thuật viên trả lời công khai đầu tiên cũng tính là phản hồi đầu tiên |
| 6 | Người nhận việc | Cần thêm thông tin → **Chờ người gửi bổ sung** và nhập câu hỏi (bắt buộc) | `WAITING_USER`; câu hỏi thành phản hồi công khai; người gửi nhận thông báo |
| 7 | Người gửi | Trả lời trong **Trao đổi** | Ticket tự về `IN_PROGRESS` |
| 8 | Người nhận việc | **Hoàn tất xử lý**, nhập cách đã xử lý và nguyên nhân gốc (đều bắt buộc) | `RESOLVED`; người gửi nhận thông báo yêu cầu xác nhận |
| 9a | Người gửi | **Xác nhận đã xử lý xong**, chọn 1 đến 5 sao, có thể thêm nhận xét | `CLOSED`, điểm được ghi |
| 9b | Người gửi | **Chưa xong, mở lại**, ghi vấn đề còn lại | Về `IN_PROGRESS`; mở lại tối đa 3 lần, sau đó phải tạo ticket mới |
| 10a | Người gửi | **Huỷ yêu cầu** (nút đỏ dưới cùng, có hộp xác nhận) khi ticket còn `NEW` | `CANCELLED` |
| 10b | Quản trị | **Từ chối yêu cầu** khi ticket `NEW` hoặc `ASSIGNED`, nhập lý do (bắt buộc) | `REJECTED`; người gửi nhận thông báo kèm lý do |

Người gửi bấm **Chỉnh sửa** để đổi tiêu đề, mô tả, phòng và thêm hoặc xoá ảnh khi ticket chưa xử lý xong; mỗi ticket tối đa 5 ảnh.
Kỹ thuật viên và quản trị không sửa được nội dung báo cáo.
Panel **Trao đổi** nằm ở cột phải và cuộn riêng; ticket đã đóng, bị từ chối hoặc huỷ không nhận thêm trao đổi.
Mục **Lịch sử xử lý** ở cuối chi tiết liệt kê từng bước đã ghi, không sửa hay xóa được.

**Ai thấy gì**

- Sinh viên và giảng viên chỉ thấy ticket do mình gửi; mở ticket của người khác báo không tồn tại.
- Kỹ thuật viên và quản trị thấy mọi ticket và mọi ghi chú nội bộ.
- Danh sách có bộ lọc trạng thái, loại, ưu tiên, **Quá hạn**; nhân sự có thêm **Chưa có người nhận**.

**SLA:** ticket mở có hạn xử lý cố định lúc tạo; xem 4.6 cho cảnh báo và đánh dấu quá hạn.

### 4.2 Mượn và trả thiết bị

**Trạng thái phiếu:** `PENDING` → `APPROVED` → `RETURNED`; `PENDING` → `REJECTED` hoặc `CANCELLED`.
**Trạng thái thiết bị:** `AVAILABLE` → `ON_LOAN` → `AVAILABLE` hoặc `UNDER_REPAIR`.

| Bước | Ai | Màn hình và thao tác | Kết quả |
|---|---|---|---|
| 1 | Sinh viên hoặc giảng viên | **Mượn thiết bị** → chọn thiết bị đang sẵn sàng, mục đích, hạn trả → gửi | Phiếu `PENDING`; thiết bị chưa bị giữ |
| 2 | Người duyệt | Danh sách phiếu → mở dòng → **Duyệt** hoặc **Từ chối** (lý do bắt buộc) | Duyệt: `APPROVED`, thiết bị sang `ON_LOAN`, người mượn nhận thông báo kèm hạn trả. Từ chối: `REJECTED`, thông báo kèm lý do |
| 3 | Người mượn | **Huỷ phiếu** khi phiếu còn `PENDING` (quản trị huỷ được phiếu của người khác) | `CANCELLED` |
| 4 | Kỹ thuật viên hoặc quản trị | Mở dòng phiếu `APPROVED` → **Nhận lại thiết bị**, chọn **Bình thường** hoặc **Hư hỏng** | Bình thường: `RETURNED`, thiết bị về `AVAILABLE`. Hư hỏng (bắt buộc mô tả): `RETURNED`, thiết bị sang `UNDER_REPAIR` và có mục sửa chữa trong nhật ký bảo trì |

**Ai duyệt ai**

| Người mượn | Người duyệt |
|---|---|
| Sinh viên | Giảng viên (hoặc quản trị) |
| Giảng viên | Kỹ thuật viên (hoặc quản trị) |
| Kỹ thuật viên, quản trị | Chỉ quản trị khác; không ai duyệt phiếu của chính mình |

**Quy tắc:** hạn trả trong vòng 7 ngày từ hôm nay; mỗi người tối đa 2 phiếu đang chờ hoặc chưa trả; chỉ mượn thiết bị `AVAILABLE`.
Hai phiếu chờ duyệt cho cùng một thiết bị đều tạo được; khi duyệt phiếu thứ hai, thiết bị đã `ON_LOAN` nên báo `DEVICE_NOT_AVAILABLE`.
Sinh viên chỉ thấy phiếu của mình; giảng viên, kỹ thuật viên và quản trị thấy mọi phiếu (ô **Chỉ phiếu của tôi** để lọc).
Phiếu quá hạn có nhãn **Quá hạn**; nhắc hạn ở 4.6.

### 4.3 Đặt phòng

**Trạng thái:** `PENDING` → `APPROVED`; `PENDING` → `REJECTED`; `PENDING`, `APPROVED` → `CANCELLED`.

| Bước | Ai | Màn hình và thao tác | Kết quả |
|---|---|---|---|
| 1 | Mọi vai | **Đặt phòng** → mục **Lịch phòng**: chọn phòng, đổi tuần | Lưới tuần từ 07:00 đến 21:00, mỗi ô một giờ; ô đã có lịch hiện mục đích và trạng thái |
| 2 | Giảng viên, quản trị | Bấm một ô trống | Ô được tô đậm; form điền sẵn giờ bắt đầu là giờ của ô và giờ kết thúc sau đó 1 giờ |
| 3 | Người đặt | Mục **Đặt phòng**: phòng, mục đích, số người, **Bắt đầu**, **Kết thúc** (ô ngày giờ của trình duyệt, bước 30 phút) → **Gửi yêu cầu đặt phòng** | Lịch `PENDING`, giữ khung giờ ngay từ lúc gửi |
| 4 | Kỹ thuật viên hoặc quản trị | Mục **Yêu cầu đặt phòng** → **Duyệt** hoặc **Từ chối** (lý do bắt buộc) | Duyệt: `APPROVED`, người đặt nhận thông báo. Từ chối: `REJECTED`, nhả khung giờ, thông báo kèm lý do |
| 5 | Người đặt hoặc nhân sự | **Huỷ lịch** khi `PENDING` hoặc `APPROVED` | `CANCELLED`, nhả khung giờ |

**Quy tắc:** bước 30 phút; tối đa 4 giờ mỗi lần; đặt trước tối đa 30 ngày; không đặt vào quá khứ; số người không vượt sức chứa; phòng `MAINTENANCE` không nhận đặt.
Hai lịch còn hiệu lực không thể chồng giờ cùng phòng, kể cả khi hai yêu cầu gửi cùng lúc (cơ sở dữ liệu chặn, báo `ROOM_SLOT_TAKEN`).
Sinh viên xem được lịch phòng nhưng không đặt được.
Giảng viên chỉ thấy lịch do mình đặt trong danh sách; kỹ thuật viên và quản trị thấy tất cả.
Dữ liệu lịch phòng chỉ gồm tên người đặt cho nhân sự và chính người đặt; lưới tuần không hiển thị tên.

### 4.4 Quản lý phòng và thiết bị

Menu **Phòng và thiết bị**, dành cho kỹ thuật viên và quản trị.

| Việc | Thao tác | Kết quả |
|---|---|---|
| Thêm, sửa phòng | **Thêm phòng**, **Sửa**: mã, tòa nhà, tên, loại (phòng máy, phòng học, phòng họp), sức chứa | Mã phòng không trùng |
| Bảo trì phòng | **Chuyển sang bảo trì** / **Mở lại phòng** | Phòng bảo trì không nhận lịch mới; các lịch còn hiệu lực không bị huỷ và được liệt kê trong biểu ngữ cảnh báo để người quản lý tự xử lý |
| Thêm, sửa thiết bị | **Thêm thiết bị**, **Sửa**: mã tài sản, loại, model, serial, ngày mua, hạn bảo hành, phòng | Mã tài sản không trùng; lọc theo phòng, trạng thái, loại |
| Nhật ký bảo trì | Mở dòng thiết bị → chọn **Sửa chữa**, **Kiểm tra** hoặc **Thay thế**, nhập nội dung → **Ghi nhật ký và đặt đang sửa** | Sửa chữa và thay thế đưa thiết bị sang `UNDER_REPAIR`; kiểm tra không đổi trạng thái (API còn cho gắn ticket vào mục nhật ký, giao diện chưa có ô này) |
| Hoàn tất bảo trì | **Hoàn tất sửa chữa** trên mục đang mở, ghi kết quả tùy chọn rồi **Hoàn tất** | Thiết bị `UNDER_REPAIR` về `AVAILABLE` |
| Thanh lý | **Thanh lý** | `RETIRED`, vẫn giữ lịch sử; thiết bị đang cho mượn thì chưa thanh lý được |
| Lịch sử mượn | Mở dòng thiết bị | Các phiếu của thiết bị đó |

Không bảo trì được thiết bị đang cho mượn hoặc đã thanh lý, và mỗi thiết bị chỉ có một đợt bảo trì mở.

### 4.5 Hỏi đáp quy chế

| Bước | Ai | Màn hình và thao tác | Kết quả |
|---|---|---|---|
| 1 | Mọi vai | **Tri thức** → nhập câu hỏi → **Hỏi** | Tối đa 4 đoạn trích từ kho tài liệu, mỗi đoạn có tên tài liệu và độ liên quan |
| 2 | | Đọc mục **Nguồn** dưới mỗi đoạn | Tên tài liệu (liên kết mở tab mới, hiện trỏ tới tệp tri thức trên GitHub), đơn vị soạn, giấy phép, và nhãn **Chưa chuyên gia duyệt** khi tài liệu chưa được duyệt |

Đoạn trả lời được trích nguyên văn từ tài liệu, không do mô hình sinh ra.
Không đoạn nào đủ liên quan thì hiện gợi ý hỏi cụ thể hơn hoặc gửi ticket; AI Service dừng thì báo lỗi 503.
Kho tài liệu có 10 bài tự soạn về nội quy phòng máy, mượn thiết bị, đặt phòng, Wi-Fi và VPN, đặt lại mật khẩu, máy chiếu, máy in, an toàn thông tin, ticket và SLA, hỗ trợ kỳ thi.
Cùng kho này cấp bài liên quan cho hộp gợi ý ở bước 1 của luồng 4.1.

### 4.6 Thông báo, nhắc việc và chat

**Thông báo trong app** (chuông, đánh dấu đã đọc từng cái hoặc tất cả):

| Loại | Gửi cho | Khi nào |
|---|---|---|
| Yêu cầu được tiếp nhận | Người gửi | Kỹ thuật viên nhận ticket |
| Bạn được gán yêu cầu | Người được giao | Quản trị gán ticket |
| Có phản hồi mới | Bên còn lại | Có phản hồi công khai trong ticket |
| Cần bạn bổ sung thông tin | Người gửi | Ticket chuyển `WAITING_USER` |
| Yêu cầu đã xử lý xong / bị từ chối | Người gửi | Ticket `RESOLVED` hoặc `REJECTED` |
| Yêu cầu sắp quá hạn | Người nhận việc | Đã dùng 80% thời gian xử lý |
| Yêu cầu quá hạn xử lý | Người nhận việc | Qua hạn xử lý |
| Phiếu mượn được duyệt / bị từ chối | Người mượn | Quyết định của người duyệt |
| Sắp đến hạn trả thiết bị | Người mượn | Hạn trả trong vòng 1 ngày |
| Quá hạn trả thiết bị | Người mượn | Quá hạn trả, nhắc mỗi ngày |
| Lịch đặt phòng được duyệt / bị từ chối | Người đặt | Quyết định của kỹ thuật viên |

**Công việc định kỳ** (múi giờ Asia/Ho_Chi_Minh):

- Kiểm tra SLA mỗi 5 phút: cảnh báo ở 80% thời gian và đánh dấu quá hạn khi qua hạn xử lý; mỗi thông báo gửi một lần cho mỗi ticket.
- Nhắc trả thiết bị lúc 07:00 hằng ngày.

**Chat Mattermost** (`http://localhost:8065`): kênh kỹ thuật viên nhận tin khi có ticket mới; kênh kỹ thuật viên và kênh quản trị nhận tin khi ticket quá hạn xử lý.
Phiếu mượn và đặt phòng không gửi sang chat.

### 4.7 Thống kê

| Nơi | Nội dung |
|---|---|
| **Tổng quan** (nhân sự) | Bộ đếm: ticket chưa có người nhận, đang giao cho tôi, quá hạn, lịch đặt phòng chờ duyệt, phiếu mượn chờ duyệt; danh sách ticket quá hạn; quản trị có thêm tổng quan 30 ngày |
| **Tổng quan** (sinh viên, giảng viên) | Lối tắt, ticket gần đây, thiết bị đang mượn; giảng viên có thêm phiếu chờ duyệt và lịch đặt phòng của mình |
| **Thống kê** | Chọn khoảng ngày (mặc định 30 ngày, tối đa 366): số ticket theo trạng thái và loại, tỷ lệ đúng hạn SLA, thời gian phản hồi và xử lý trung bình, số quá hạn, điểm hài lòng, tải kỹ thuật viên, mức dùng từng phòng, thiết bị theo trạng thái, phiếu mượn quá hạn |

Mức dùng phòng tính trên số giờ mở cửa mỗi ngày (mặc định 10 giờ), chỉ tính lịch đã duyệt.

### 4.8 Danh mục (quản trị)

| Phần | Việc |
|---|---|
| **Loại sự cố** | Thêm, sửa, bật tắt loại; mỗi loại có tên, ưu tiên mặc định và từ khóa bổ sung cho gợi ý |
| **Chính sách SLA** | Sửa hạn phản hồi và hạn xử lý (phút) cho từng mức ưu tiên; chỉ áp dụng cho ticket tạo sau |
| **Lớp và bộ môn** | Tạo đơn vị người dùng thuộc về (lớp, bộ môn) |

Mặc định có 8 loại sự cố (mạng, tài khoản, máy chiếu, máy in, máy tính, phần mềm, hỗ trợ thi, khác) và SLA:

| Ưu tiên | Phản hồi | Xử lý |
|---|---|---|
| Khẩn | 1 giờ | 4 giờ |
| Cao | 4 giờ | 1 ngày |
| Thường | 1 ngày | 3 ngày |
| Thấp | 2 ngày | 7 ngày |

Luật gợi ý ở [`classifier-rules.json`](../backend/app-lab/src/main/resources/lab/classifier-rules.json): từ khóa theo loại, và các luật nâng ưu tiên (đang trong giờ thi, cả phòng hoặc nhiều máy, đang dạy học, an toàn).

## 5. Điều cần để ý khi kiểm tay

Đây là hành vi hiện tại của hệ thống, ghi lại để đối chiếu với kỳ vọng.

| # | Hành vi |
|---|---|
| 1 | Kỹ thuật viên tự duyệt được lịch đặt phòng do chính mình gửi; phiếu mượn thì không ai tự duyệt được phiếu của mình |
| 2 | Phiếu mượn của kỹ thuật viên chỉ quản trị duyệt được |
| 3 | Chỉ quản trị mới từ chối được ticket; kỹ thuật viên chỉ nhận, xử lý hoặc hoàn tất |
| 4 | Thời gian ticket chờ người gửi vẫn tính vào SLA |
| 5 | Cảnh báo SLA và quá hạn chỉ gửi cho người nhận việc; ticket quá hạn khi chưa có ai nhận chỉ lên kênh chat và bộ lọc **Quá hạn** |
| 6 | Danh sách kỹ thuật viên để quản trị giao việc gồm những người đã từng nhận ticket, không phải toàn bộ tài khoản có vai kỹ thuật viên |
| 7 | Chuyển phòng sang bảo trì không huỷ lịch đã đặt |
| 8 | Phiếu mượn chờ duyệt không giữ thiết bị; chỉ khi duyệt thiết bị mới sang `ON_LOAN` |
| 9 | Giảng viên thấy mọi phiếu mượn (để duyệt phiếu của sinh viên), nhưng chỉ thấy lịch đặt phòng của mình |
| 10 | Menu **Đặt phòng** hiện với cả sinh viên nhưng chỉ để xem lịch |
| 11 | Hộp thông báo chỉ cập nhật khi mở trang hoặc sau 60 giây; không có đẩy thông báo |
| 12 | Nội dung hỏi đáp là tài liệu tự soạn đang chờ khoa duyệt, chưa phải văn bản chính thức |
