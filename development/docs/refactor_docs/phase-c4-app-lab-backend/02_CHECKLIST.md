# Checklist: Phase C4

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).
Mã dòng: `WPC4-{xx}-{lớp}-{nn}`.
Dòng BE và DATA ghi module trong ngoặc vuông.
Chỉ làm phạm vi đã cắt gọn; các mục nâng cao ở backlog.

## WPC4-01: Khung `app-lab` và cấu hình nạp

Kết quả đầu ra: `DXOS_APP=lab` lên được; `GET /api/v1/app` trả manifest của Lab.

**Backend**

- [ ] WPC4-01-BE-01 [lab] pom `app-lab` chỉ phụ thuộc `dx-core-api`.
- [ ] WPC4-01-BE-02 [lab] Auto-configuration có điều kiện `dxos.app=lab`; giới hạn quét entity, repository, Flyway.
- [ ] WPC4-01-BE-03 [lab] `AppModule` (id `lab`, tên, menu theo vai).
- [ ] WPC4-01-BE-04 [host] Thêm `app-lab` vào `dx-host`; `application-lab.yaml`.
- [ ] WPC4-01-BE-05 [lab] `application-lab.yaml` không nạp seed của Farm; khởi động `lab` trên DB sạch không lỗi.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-01-TEST-01 Khởi động `lab` và `farm`, kiểm bảng được tạo (TC4-01, TC4-02).

## WPC4-02: Danh mục nền

Kết quả đầu ra: quản lý được phòng, thiết bị, loại sự cố, SLA; đọc được theo vai.

**Spec và tài liệu**

- [ ] WPC4-02-DOC-01 Cập nhật spec và Swagger cho các endpoint danh mục.

**Backend**

- [ ] WPC4-02-BE-01 [lab] Entity, repository, service, controller: `Room`, `Device`, `TicketCategory`, `SlaPolicy`.
- [ ] WPC4-02-BE-02 [lab] Quyền: TECHNICIAN, ADMIN quản lý phòng và thiết bị; ADMIN quản lý loại sự cố và SLA; mọi vai đọc.
- [ ] WPC4-02-BE-03 [lab] Mã tài sản duy nhất; chặn thanh lý thiết bị đang mượn.

**Data, seed, migration**

- [ ] WPC4-02-DATA-01 [lab] `db/migration/lab`: bảng, ràng buộc duy nhất, khóa ngoại nội bộ Lab.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-02-TEST-01 Danh mục và quyền (TC4-03, TC4-04).

## WPC4-03: Ticket hỗ trợ CNTT

Kết quả đầu ra: ticket đi đủ vòng đời; SLA tính đúng; 409 khi xử lý đồng thời.

**Spec và tài liệu**

- [ ] WPC4-03-DOC-01 Cập nhật spec và Swagger cho `/api/v1/tickets`.

**Backend**

- [ ] WPC4-03-BE-01 [lab] Entity: `Ticket`, `TicketComment`, `TicketAttachment`; mã `TK-{năm}-{số}`.
- [ ] WPC4-03-BE-02 [lab] Tạo ticket: tính hạn phản hồi và hạn xử lý từ `SlaPolicy`.
- [ ] WPC4-03-BE-03 [lab] `CaseTypeDefinition` của `TICKET` (trạng thái, bước chuyển, vai).
- [ ] WPC4-03-BE-04 [lab] Handler cập nhật `Ticket`: thời điểm phản hồi đầu tiên, hoàn tất, đóng, số lần mở lại.
- [ ] WPC4-03-BE-05 [lab] Bình luận công khai và nội bộ; người gửi trả lời khi WAITING_USER thì tự chuyển IN_PROGRESS.
- [ ] WPC4-03-BE-06 [lab] Phân công: tự nhận, ADMIN gán; danh sách kỹ thuật viên kèm số ticket đang xử lý.
- [ ] WPC4-03-BE-07 [lab] `resolve` bắt buộc ghi chú và nguyên nhân gốc; `confirm` kèm đánh giá 1-5; `reopen` tối đa 3 lần.
- [ ] WPC4-03-BE-08 [lab] Đính kèm tệp qua `ObjectStorage`; `FileProcessor` giới hạn loại và dung lượng.
- [ ] WPC4-03-BE-09 [lab] Phạm vi xem theo vai; `CaseSummaryProvider` cho hộp duyệt.
- [ ] WPC4-03-BE-10 [lab] Danh sách có lọc theo trạng thái, loại, ưu tiên, người xử lý, quá hạn; phân trang.

**Data, seed, migration**

- [ ] WPC4-03-DATA-01 [lab] Migration `tickets`, `ticket_comments`, `ticket_attachments`, chỉ mục theo trạng thái và hạn xử lý.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-03-TEST-01 Vòng đời, SLA, 409, phân quyền (TC4-05 đến TC4-12).

## WPC4-04: Thiết bị: nhật ký bảo trì và mượn trả

Kết quả đầu ra: thiết bị đổi trạng thái đúng; mượn thiết bị đang mượn bị từ chối.

**Backend**

- [ ] WPC4-04-BE-01 [lab] `DeviceMaintenance` và API; bắt đầu sửa đặt thiết bị đang sửa, hoàn tất trả về sẵn sàng.
- [ ] WPC4-04-BE-02 [lab] `DeviceLoan` và `CaseTypeDefinition` của `DEVICE_LOAN`.
- [ ] WPC4-04-BE-03 [lab] Poka-yoke: thiết bị sẵn sàng, không trùng phiếu đã duyệt, tối đa 7 ngày, tối đa 2 phiếu mở mỗi người.
- [ ] WPC4-04-BE-04 [lab] Quy tắc duyệt: STUDENT mượn thì LECTURER duyệt; LECTURER mượn thì TECHNICIAN duyệt.
- [ ] WPC4-04-BE-05 [lab] Trả thiết bị ghi tình trạng; hỏng thì tạo nhật ký sửa chữa.
- [ ] WPC4-04-BE-06 [lab] `CaseSummaryProvider` cho phiếu mượn.

**Data, seed, migration**

- [ ] WPC4-04-DATA-01 [lab] Migration `device_maintenance`, `device_loans`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-04-TEST-01 Mượn trả và bảo trì (TC4-13 đến TC4-16).

## WPC4-05: Đặt phòng máy và phòng thực hành

Kết quả đầu ra: không thể có hai lịch hiệu lực chồng giờ cùng phòng.

**Backend**

- [ ] WPC4-05-BE-01 [lab] `RoomBooking`, `CaseTypeDefinition` của `ROOM_BOOKING`.
- [ ] WPC4-05-BE-02 [lab] Quy tắc: bước 30 phút, tối đa 4 giờ, tối đa 30 ngày, không quá khứ, không vượt sức chứa, phòng bảo trì không nhận đặt, STUDENT không tự đặt.
- [ ] WPC4-05-BE-03 [lab] Handler duyệt, từ chối, hủy; cập nhật cột `active` cùng transaction.
- [ ] WPC4-05-BE-04 [lab] `GET /api/v1/rooms/{id}/schedule` theo khoảng ngày.
- [ ] WPC4-05-BE-05 [lab] Chuyển phòng sang bảo trì: trả danh sách lịch bị ảnh hưởng.
- [ ] WPC4-05-BE-06 [lab] `CaseSummaryProvider` cho đặt phòng.

**Data, seed, migration**

- [ ] WPC4-05-DATA-01 [lab] Migration `room_bookings`: extension `btree_gist`, ràng buộc loại trừ trên (`room_id`, khoảng thời gian) khi `active`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-05-TEST-01 Đặt phòng, xung đột đồng thời (TC4-17 đến TC4-20).

## WPC4-06: Thông báo, nhắc việc và SLA theo lịch

Kết quả đầu ra: thông báo đúng người, đúng lúc, không trùng.

**Backend**

- [ ] WPC4-06-BE-01 [lab] `NotificationTypeDefinition` cho toàn bộ loại ở `01_PLAN.md`; gửi qua `NotificationPublisher`.
- [ ] WPC4-06-BE-02 [lab] `NotificationChannel` cho kỹ thuật viên và quản trị; lời nhắn dựng ở Lab.
- [ ] WPC4-06-BE-03 [lab] Lịch SLA: cảnh báo ở 80% hạn xử lý; quá hạn thì đánh dấu và báo cho người xử lý và ADMIN.
- [ ] WPC4-06-BE-04 [lab] Nhắc mượn trả hằng ngày: sắp đến hạn, quá hạn.
- [ ] WPC4-06-BE-05 [lab] Khóa chống trùng theo đối tượng, mốc và ngày.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-06-TEST-01 Thông báo và lịch (TC4-21, TC4-22).

## WPC4-07: Tri thức và chức năng thông minh

Kết quả đầu ra: gợi ý có giải thích; hỏi đáp có nguồn và giấy phép.

**Backend**

- [ ] WPC4-07-BE-01 [lab] `TicketClassifier` theo luật, tệp luật cấu hình được; trả loại, ưu tiên, từ khóa khớp, lý do.
- [ ] WPC4-07-BE-02 [lab] `POST /api/v1/tickets/suggest` kèm tối đa 3 đoạn tri thức liên quan.
- [ ] WPC4-07-BE-03 [lab] `KnowledgeNamespace` của Lab và danh mục nguồn kèm giấy phép.

**AI**

- [ ] WPC4-07-AI-01 Nạp tài liệu tri thức Lab vào namespace `lab` bằng job nạp hiện có.

**Data, seed, migration**

- [ ] WPC4-07-DATA-01 [lab] Soạn 8 đến 10 tài liệu tri thức; ghi giấy phép CC BY 4.0 và nguồn.
- [ ] WPC4-07-DATA-03 [lab] Đặt tài liệu tri thức ở `data/rag/` theo namespace `lab`; ghi nguồn vào registry cùng định dạng `source_registry.json`.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-07-TEST-01 Phân loại, gợi ý, hỏi đáp (TC4-23 đến TC4-25).

## WPC4-08: Thống kê, BI, dữ liệu mẫu và tài liệu

Kết quả đầu ra: thống kê đúng trên dữ liệu mẫu; vai chỉ đọc chỉ đọc view Lab.

**Backend**

- [ ] WPC4-08-BE-01 [lab] `LabStatsService` và `GET /api/v1/lab-stats/summary`.
- [ ] WPC4-08-BE-02 [lab] `BiDatasetProvider` đăng ký view Lab.

**Data, seed, migration**

- [ ] WPC4-08-DATA-01 [lab] View `vw_lab_*` không chứa nội dung tự do hay thông tin cá nhân không cần thiết.
- [ ] WPC4-08-DATA-02 [lab] Tệp dữ liệu mẫu: 8 loại sự cố, 4 chính sách SLA, 6 phòng, khoảng 30 thiết bị, khoảng 25 ticket, khoảng 10 lịch đặt phòng, 6 phiếu mượn; ngày tương đối.

**Spec và tài liệu**

- [ ] WPC4-08-DOC-01 `app-lab/README.md`: domain, vai, interface đã cài, quy tắc SLA và đặt phòng.
- [ ] WPC4-08-DOC-02 `LICENSES.md`: nguồn dữ liệu mẫu và tri thức.

**Test** (chi tiết ở `03_TEST_PLAN.md`)

- [ ] WPC4-08-TEST-01 Thống kê và quyền đọc BI (TC4-26, TC4-27).

## Đóng phase

- [ ] `./mvnw compile` đạt cho toàn bộ module (backend) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, chỉ nêu hành vi hiện tại, bằng tiếng Anh.
- [ ] Swagger khớp code, `@Operation` bằng tiếng Anh, không nhắc nhãn phase.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch với `DXOS_APP=lab` và `DXOS_APP=farm`.
- [ ] `app-lab` chỉ import `dx-core-api`; không sửa `dx-core`, `app-farm` ngoài việc bổ sung interface.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] Dọn dữ liệu và ảnh test không còn dùng.
- [ ] README, CHANGELOG, sơ đồ cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở bảng chỉ mục của `README.md`.
