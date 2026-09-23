# Plan thực hiện: Phase C4

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).
Thứ tự làm: Spec → Swagger → Code.
Phase chỉ làm phạm vi đã cắt gọn ở `00_OVERVIEW.md` mục 4; các mục nâng cao nằm ở backlog.

## Thứ tự và phụ thuộc

WPC4-01 → WPC4-02 → WPC4-03.
WPC4-04, WPC4-05 làm song song được sau WPC4-02.
WPC4-06 cần WPC4-03, WPC4-04, WPC4-05.
WPC4-07 cần WPC4-03; WPC4-08 làm cuối.

## WPC4-01: Khung `app-lab` và cấu hình nạp

- Mục tham chiếu: RD1, RD2.
- Mục tiêu: `DXOS_APP=lab` nạp được module, `farm` không nạp.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| `DXOS_APP=lab` | Giá trị không hợp lệ | Nạp `app-lab` |
| Nạp entity, repository, Flyway | Chỉ Farm | Theo app được chọn |

**Hướng làm**

- Auto-configuration có điều kiện `dxos.app=lab`; `AppModule` (id `lab`, tên, menu); `application-lab.yaml`.
- Core/App: không chỉnh `dx-core`.

**Phạm vi ảnh hưởng**

- Module: `app-lab`, `dx-host`.
- File chính: pom, auto-configuration, `application-lab.yaml`.
- Endpoint, bảng DB, biến môi trường: `GET /api/v1/app` trả `lab`.
- Role và client bị ảnh hưởng: không.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Khởi động với `lab` lên được; `GET /api/v1/app` trả manifest của Lab.

**Phụ thuộc, lưu ý**: cần C1, C2.

## WPC4-02: Danh mục nền

- Mục tham chiếu: đề thi HaUI mục 4.3.
- Mục tiêu: phòng, thiết bị, loại sự cố, chính sách SLA có sẵn để các chức năng sau dùng.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Phòng | Không có | `Room`: mã, tòa, tên, loại (phòng máy, phòng học, phòng họp), sức chứa, trạng thái (sẵn sàng, bảo trì) |
| Thiết bị | Không có | `Device`: mã tài sản, loại, mẫu, số serial, phòng hiện tại, trạng thái (sẵn sàng, đang mượn, đang sửa, thanh lý), ngày mua, hạn bảo hành |
| Loại sự cố | Không có | `TicketCategory`: mã, tên, ưu tiên mặc định, từ khóa gợi ý |
| SLA | Không có | `SlaPolicy` theo ưu tiên: hạn phản hồi, hạn xử lý; ADMIN chỉnh được |

**Hướng làm**

- TECHNICIAN và ADMIN quản lý phòng, thiết bị; ADMIN quản lý loại sự cố và SLA; mọi vai đọc được danh mục.
- Core/App: chỉ dùng facade ở `dx-core-api`.

**Phạm vi ảnh hưởng**

- Module: `app-lab`.
- File chính: entity, repository, service, controller, DTO.
- Endpoint, bảng DB, biến môi trường: `/api/v1/rooms`, `/api/v1/devices`, `/api/v1/ticket-categories`, `/api/v1/sla-policies`; bảng `rooms`, `devices`, `ticket_categories`, `sla_policies`.
- Role và client bị ảnh hưởng: mọi vai Lab (web).
- Tương thích ngược và dữ liệu cũ: không có dữ liệu cũ.

**Kết quả đầu ra**

- Quản lý được danh mục; đọc được theo vai.

**Phụ thuộc, lưu ý**: cần WPC4-01; Q6.

## WPC4-03: Ticket hỗ trợ CNTT

- Mục tham chiếu: đề thi HaUI mục 4.2 (P - Process), 4.3.
- Mục tiêu: vòng đời ticket đầy đủ, có trao đổi, SLA và đánh giá.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Báo sự cố | Không có | `POST /api/v1/tickets`: tiêu đề, mô tả, loại, ưu tiên, phòng, thiết bị liên quan (tùy chọn), tệp đính kèm |
| Mã ticket | Không có | `TK-{năm}-{số}` |
| Hạn xử lý | Không có | Tính từ `SlaPolicy` theo ưu tiên: hạn phản hồi và hạn xử lý |
| Trao đổi | Không có | `TicketComment`: công khai hoặc nội bộ (chỉ kỹ thuật viên và quản trị thấy) |
| Phân công | Không có | Kỹ thuật viên tự nhận hoặc ADMIN gán; danh sách kèm số ticket đang xử lý của từng người |
| Hoàn tất | Không có | Xử lý xong bắt buộc ghi chú và nguyên nhân gốc; người gửi xác nhận kèm đánh giá 1-5 sao hoặc mở lại |

**Hướng làm**

- Vòng đời (Q3): NEW → ASSIGNED → IN_PROGRESS → WAITING_USER ↔ IN_PROGRESS → RESOLVED → CLOSED; REJECTED, CANCELLED; RESOLVED → IN_PROGRESS khi mở lại (tối đa 3 lần).
- Ai được chuyển: STUDENT, LECTURER hủy ticket NEW của mình, xác nhận hoặc mở lại khi RESOLVED; TECHNICIAN nhận, bắt đầu, chờ người dùng, xử lý; ADMIN gán, từ chối, xem tất cả.
- Thời điểm phản hồi đầu tiên ghi lại khi người xử lý bắt đầu hoặc trả lời công khai đầu tiên.
- Tệp đính kèm qua `ObjectStorage`; `FileProcessor` giới hạn loại và dung lượng.
- Tạo comment của người gửi khi ticket ở WAITING_USER tự chuyển lại IN_PROGRESS.
- Phạm vi xem: người gửi xem ticket của mình; TECHNICIAN và ADMIN xem tất cả.
- Core/App: chỉ dùng facade; người dùng và workspace qua `UserContext`, `WorkspaceDirectory`.

**Phạm vi ảnh hưởng**

- Module: `app-lab`.
- File chính: `Ticket`, `TicketComment`, `TicketAttachment`, service, controller, handler workflow, `CaseTypeDefinition` của `TICKET`.
- Endpoint, bảng DB, biến môi trường: `/api/v1/tickets`, `/{id}`, `/{id}/comments`, `/{id}/assign`, `/start`, `/wait`, `/resolve`, `/confirm`, `/reopen`, `/cancel`, `/reject`; bảng `tickets`, `ticket_comments`, `ticket_attachments`.
- Role và client bị ảnh hưởng: STUDENT, LECTURER, TECHNICIAN, ADMIN (web).
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Ticket đi đủ vòng đời; lịch sử trong `workflow_event`; hạn SLA tính đúng; hai người xử lý cùng ticket thì một bên 409.

**Phụ thuộc, lưu ý**: cần C2 (`CaseTypeDefinition` cấu hình được); Q3, Q6.

## WPC4-04: Thiết bị: nhật ký bảo trì và mượn trả

- Mục tham chiếu: đề thi HaUI mục 4.3 (quản lý thiết bị).
- Mục tiêu: biết tình trạng từng thiết bị và cho mượn trả có kiểm soát.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Sửa chữa, kiểm tra | Không có | `DeviceMaintenance`: loại (sửa, kiểm tra, thay thế), ghi chú, ticket liên quan, người thực hiện; bắt đầu sửa đặt thiết bị ở trạng thái đang sửa, hoàn tất trả về sẵn sàng |
| Mượn thiết bị | Không có | `DeviceLoan`: thiết bị, người mượn, mục đích, ngày mượn, hạn trả |
| Vòng đời mượn | Không có | `DEVICE_LOAN`: PENDING → APPROVED → RETURNED; REJECTED, CANCELLED |
| Trả thiết bị | Không có | Ghi tình trạng khi trả; hỏng thì mở nhật ký sửa chữa và thiết bị chuyển trạng thái đang sửa |

**Hướng làm**

- Poka-yoke: chỉ mượn thiết bị đang sẵn sàng và không trùng khoảng thời gian với phiếu đã duyệt; thời hạn mượn tối đa 7 ngày; mỗi người tối đa 2 phiếu đang mở.
- STUDENT mượn thì LECTURER duyệt; LECTURER mượn thì TECHNICIAN duyệt; TECHNICIAN và ADMIN quản lý thiết bị.
- Core/App: dùng facade; trạng thái thiết bị do handler cập nhật cùng transaction với quyết định.

**Phạm vi ảnh hưởng**

- Module: `app-lab`.
- File chính: `DeviceMaintenance`, `DeviceLoan`, service, controller, handler, `CaseTypeDefinition` của `DEVICE_LOAN`.
- Endpoint, bảng DB, biến môi trường: `/api/v1/devices/{id}/maintenance`, `/api/v1/device-loans`, `/{id}/approve`, `/reject`, `/return`, `/cancel`; bảng `device_maintenance`, `device_loans`.
- Role và client bị ảnh hưởng: STUDENT, LECTURER, TECHNICIAN, ADMIN.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Mượn trả đủ vòng đời; thiết bị đổi trạng thái đúng; mượn thiết bị đang mượn bị từ chối.

**Phụ thuộc, lưu ý**: cần WPC4-02.

## WPC4-05: Đặt phòng máy và phòng thực hành

- Mục tham chiếu: đề thi HaUI mục 4.3 (phòng học).
- Mục tiêu: đặt phòng theo khung giờ, không bao giờ trùng giờ.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Đặt phòng | Không có | `RoomBooking`: phòng, người đặt, học phần hoặc sự kiện, số người, giờ bắt đầu và kết thúc |
| Xung đột | Không có | Hai yêu cầu chồng giờ cùng phòng: một bên bị từ chối ngay cả khi gửi cùng lúc |
| Duyệt | Không có | `ROOM_BOOKING`: PENDING → APPROVED hoặc REJECTED; PENDING, APPROVED → CANCELLED |
| Lịch | Không có | `GET /api/v1/rooms/{id}/schedule?from=&to=` trả các khung giờ đã đặt theo tuần |

**Hướng làm**

- Chống xung đột bằng ràng buộc loại trừ của PostgreSQL trên (`room_id`, khoảng thời gian) cho bản ghi còn hiệu lực (cột `active`, do handler cập nhật cùng transaction với quyết định).
- Quy tắc (Q7): bước 30 phút, tối đa 4 giờ mỗi lần, đặt trước tối đa 30 ngày, không đặt vào quá khứ, số người không vượt sức chứa, phòng đang bảo trì không nhận đặt; STUDENT không tự đặt phòng.
- Đặt phòng sang trạng thái bảo trì khi đã có lịch duyệt: trả danh sách lịch bị ảnh hưởng để người quản lý xử lý.
- Core/App: dùng facade; extension DB tạo trong migration của Lab.

**Phạm vi ảnh hưởng**

- Module: `app-lab`.
- File chính: `RoomBooking`, service, controller, handler, `CaseTypeDefinition` của `ROOM_BOOKING`, migration có ràng buộc loại trừ.
- Endpoint, bảng DB, biến môi trường: `/api/v1/room-bookings`, `/{id}/approve`, `/reject`, `/cancel`, `/api/v1/rooms/{id}/schedule`; bảng `room_bookings`; extension `btree_gist`.
- Role và client bị ảnh hưởng: LECTURER, TECHNICIAN, ADMIN đặt hoặc duyệt; STUDENT chỉ xem lịch.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Đặt, duyệt, hủy hoạt động; không thể có hai lịch hiệu lực chồng giờ cùng phòng.

**Phụ thuộc, lưu ý**: cần WPC4-02; Q7; kiểm tra vai chủ DB tạo được extension `btree_gist` trên image PostgreSQL đang dùng.

## WPC4-06: Thông báo, nhắc việc và SLA theo lịch

- Mục tham chiếu: đề thi HaUI mục 4.2 (P - Process: cơ chế theo dõi yêu cầu chờ xử lý).
- Mục tiêu: người liên quan luôn biết việc cần làm; ticket không bị bỏ quên.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Thông báo trong app | Không có | Loại: ticket được nhận, có trả lời, chờ phản hồi, đã xử lý, quá hạn SLA; phiếu mượn được duyệt, bị từ chối, sắp đến hạn, quá hạn; đặt phòng được duyệt, bị từ chối |
| Kênh chat | Không có | Ticket mới gửi tới kênh kỹ thuật viên |
| Nhắc SLA | Không có | Lịch chạy định kỳ: cảnh báo khi đã dùng 80% hạn xử lý; quá hạn thì đánh dấu và báo cho người xử lý và ADMIN |
| Nhắc mượn trả | Không có | Hằng ngày: sắp đến hạn và quá hạn trả |

**Hướng làm**

- Lịch của Lab dùng cấu hình lịch của core; tham số trong `application-lab.yaml`.
- Khóa chống trùng theo ticket, mốc và ngày để nhắc không lặp.
- Core/App: loại thông báo và kênh do Lab đăng ký.

**Phạm vi ảnh hưởng**

- Module: `app-lab`.
- File chính: `NotificationTypeDefinition`, `NotificationChannel`, lịch SLA, lịch nhắc mượn trả, `CaseSummaryProvider`.
- Endpoint, bảng DB, biến môi trường: tham số `lab.sla.*`, `lab.reminder.*`; cột đánh dấu quá hạn trên `tickets`.
- Role và client bị ảnh hưởng: mọi vai Lab.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Thông báo đúng người, đúng lúc, không trùng; ticket quá hạn được đánh dấu và báo cho người liên quan.

**Phụ thuộc, lưu ý**: cần WPC4-03, 04, 05.

## WPC4-07: Tri thức và chức năng thông minh

- Mục tham chiếu: đề thi HaUI mục 4.2 (I - Intelligence), mục 8.
- Mục tiêu: hỗ trợ người dùng tự giải quyết và giúp kỹ thuật viên ưu tiên, có giải thích.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Chọn loại và ưu tiên | Không có | `POST /api/v1/tickets/suggest` trả loại, ưu tiên gợi ý, từ khóa khớp và lý do theo luật; người dùng quyết định |
| Gợi ý bài liên quan | Không có | Cùng phản hồi trả tối đa 3 đoạn tri thức liên quan kèm nguồn |
| Hỏi đáp quy chế | Không có | Hỏi đáp RAG namespace `lab` qua `KnowledgeGateway` |

**Hướng làm**

- Luật phân loại: từ khóa và mẫu câu theo loại, đặt trong tệp cấu hình; ưu tiên tăng khi mô tả nhắc ảnh hưởng cả phòng hoặc đang diễn ra kỳ thi.
- Tri thức: 8 đến 10 tài liệu ngắn tự soạn (quy chế sử dụng phòng máy, quy trình mượn thiết bị và đặt phòng, Wi-Fi và VPN, đặt lại mật khẩu, xử lý lỗi máy chiếu và máy in, an toàn thông tin cơ bản), cấp phép CC BY 4.0, danh mục nguồn kèm giấy phép; nạp vào RAG namespace `lab` bằng job nạp hiện có.
- Core/App: dùng `KnowledgeGateway`; luật và ngưỡng nằm ở `app-lab`.

**Phạm vi ảnh hưởng**

- Module: `app-lab`, `ai/` (nạp dữ liệu), `data/rag`.
- File chính: `TicketClassifier`, tệp luật, `KnowledgeNamespace` của Lab, tài liệu tri thức.
- Endpoint, bảng DB, biến môi trường: `/api/v1/tickets/suggest`; tham số `lab.classifier.*` (đường dẫn tệp luật).
- Role và client bị ảnh hưởng: mọi vai Lab.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Gợi ý có giải thích; hỏi đáp trả đoạn trích có nguồn và giấy phép.

**Phụ thuộc, lưu ý**: cần WPC2-06 (namespace); Q5.

## WPC4-08: Thống kê, BI, dữ liệu mẫu và tài liệu

- Mục tham chiếu: đề thi HaUI mục 4.2 (D - Data), 7, 8, 9.
- Mục tiêu: có số liệu cho trang thống kê và demo có ý nghĩa.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Thống kê trong app | Không có | `GET /api/v1/lab-stats/summary?from=&to=`: ticket theo trạng thái và loại, tỉ lệ đạt SLA, thời gian phản hồi và xử lý trung bình, tải và số ticket đã đóng của từng kỹ thuật viên, điểm hài lòng, mức sử dụng phòng, thiết bị theo trạng thái, phiếu quá hạn |
| View BI | Không có | View `vw_lab_*` cho Superset; dashboard Superset của Lab để backlog, trang thống kê trong web thay thế |
| Dữ liệu mẫu | Không có | Dữ liệu giả lập đủ để các biểu đồ có số liệu |
| Quyền Superset | Chỉ view Farm | `BiDatasetProvider` của Lab đăng ký view Lab |

**Hướng làm**

- View không chứa nội dung mô tả tự do hay thông tin cá nhân không cần thiết (dùng id và nhóm).
- Dữ liệu mẫu: 8 loại sự cố, 4 chính sách SLA, 6 phòng, khoảng 30 thiết bị, khoảng 25 ticket ở mọi trạng thái trải trong 60 ngày, khoảng 10 lịch đặt phòng, 6 phiếu mượn; ngày tính tương đối so với lúc nạp; seed qua API để đi qua poka-yoke (thực hiện ở C6).
- Core/App: tên view và quyền khai báo ở Lab.

**Phạm vi ảnh hưởng**

- Module: `app-lab`; `bi/`, `data/`.
- File chính: `LabStatsService`, migration view, `BiDatasetProvider`, tệp dữ liệu mẫu, `app-lab/README.md`, `LICENSES.md`.
- Endpoint, bảng DB, biến môi trường: `/api/v1/lab-stats/summary`; các view `vw_lab_*`.
- Role và client bị ảnh hưởng: TECHNICIAN, ADMIN (thống kê); Superset.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Thống kê trả số liệu đúng trên dữ liệu mẫu; vai chỉ đọc chỉ đọc được view Lab.

**Phụ thuộc, lưu ý**: cần WPC2-07; seed ở C6.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | `/api/v1/rooms`, `/devices`, `/ticket-categories`, `/sla-policies`, `/tickets`, `/device-loans`, `/room-bookings`, `/lab-stats/summary` | Mới (chỉ khi `DXOS_APP=lab`) | Không |
| Bảng DB | `rooms`, `devices`, `ticket_categories`, `sla_policies`, `tickets`, `ticket_comments`, `ticket_attachments`, `device_maintenance`, `device_loans`, `room_bookings`, các view `vw_lab_*` | Mới | Không |
| Extension DB | `btree_gist` | Tạo trong migration Lab | Không |
| Interface | `dx-core-api` | Bổ sung nếu Lab phát hiện thiếu | Không |
| Biến môi trường | `DXOS_APP=lab`; `lab.sla.*`, `lab.reminder.*`, `lab.classifier.*` | Mới | Không |

## Dữ liệu, migration và rollback

- Migration: `db/migration/lab` từ V1.
- Dữ liệu cũ: không.
- Rollback: bỏ giá trị `lab`; bảng Lab không được tạo khi chạy Farm.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Trạng thái ticket, phiếu mượn, đặt phòng nằm ở workflow; entity giữ `workflowCaseId` | Một nguồn sự thật cho trạng thái và lịch sử |
| D2 | Phân loại và cảnh báo bằng luật cấu hình được, có giải thích | Đề cho phép rule-based; kiểm chứng và giải thích được |
| D3 | Chống xung đột đặt phòng bằng ràng buộc DB | An toàn khi đồng thời, không dựa vào kiểm tra ở mã |
| D4 | Ticket tương tự, cảnh báo bất thường, leo thang, tự đóng, dashboard Superset của Lab chuyển sang backlog | Giữ phạm vi vừa phải, ưu tiên chức năng cốt lõi chạy ổn |
| D5 | Dữ liệu mẫu và tri thức tự soạn, nạp qua API | Nguồn gốc rõ; đi qua kiểm tra nghiệp vụ |
