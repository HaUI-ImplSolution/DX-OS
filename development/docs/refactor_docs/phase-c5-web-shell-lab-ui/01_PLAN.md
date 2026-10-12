# Plan thực hiện: Phase C5

Plan tổng quan theo từng work package (WP).
Việc chi tiết nằm ở [`02_CHECKLIST.md`](./02_CHECKLIST.md).

## Thứ tự và phụ thuộc

WPC5-01 → WPC5-02 → WPC5-03.
WPC5-04, WPC5-05, WPC5-06 làm song song được sau WPC5-02, theo tiến độ API của C4.

## WPC5-01: Vỏ web và nạp module theo manifest

- Mục tham chiếu: RD2.
- Mục tiêu: một image web, nạp module app theo `GET /api/v1/app`.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Menu và route | Viết cứng trong `App.tsx` | Vỏ dựng từ manifest; module app khai báo route và trang |
| Vai | `ROLES` cố định trong `auth.ts` | Lấy từ manifest |
| Cấu trúc thư mục | `pages/`, `components/` phẳng | `shell/` (dùng chung), `apps/farm/`, `apps/lab/` |

**Hướng làm**

- Module app xuất một đối tượng: id, danh sách route, trang, nhãn.
- Nạp lười (`import()`) theo `app.id`; id lạ hiện trang lỗi rõ.
- Core/App: vỏ không import trực tiếp thư mục `apps/*`; registry ánh xạ id sang hàm nạp.

**Phạm vi ảnh hưởng**

- Module: `frontend`.
- File chính: `App.tsx`, `auth.ts`, `api.ts`, `types.ts`, `labels.ts`, `main.tsx`, `Dockerfile`.
- Endpoint, bảng DB, biến môi trường: dùng `GET /api/v1/app`.
- Role và client bị ảnh hưởng: mọi vai web.
- Tương thích ngược và dữ liệu cũ: Farm chạy như trước.

**Kết quả đầu ra**

- Với Farm, web giống trước phase; id app lạ có thông báo lỗi.

**Phụ thuộc, lưu ý**: cần C2, C3.

## WPC5-02: Trang dùng chung và tách module Farm

- Mục tham chiếu: đề thi HaUI mục 3 (tầng 2, 3).
- Mục tiêu: trang dùng chung chạy cho mọi app; trang Farm nằm trong module Farm.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Hộp duyệt, chi tiết case, timeline | `Requests`, `CaseDetail`, `Timeline` gắn với loại case Farm | Dùng `CaseSummaryProvider` và nhãn trạng thái từ manifest |
| Danh sách đơn vị | `Households` của Farm | Farm giữ `Households` trong `apps/farm`; trang `Workspaces` dùng chung cho Lab |
| Tri thức | `Knowledge` Farm | Dùng chung theo namespace của app |
| Trang nghiệp vụ Farm | `ParcelPanel`, `CropSeasonPanel`, `DiseaseReportPanel`, `DiagnosisPanel`, `ReferencePanel`, `DemoFlow`, `Queue` | Chuyển vào `apps/farm/` |

**Hướng làm**

- Trạng thái không biết hiển thị nguyên chuỗi với màu trung tính.
- Rà từng file `pages/` và `components/` để quyết định dùng chung hay của Farm.
- Core/App: thành phần dùng chung không biết thửa, vụ, bệnh.

**Phạm vi ảnh hưởng**

- Module: `frontend`.
- File chính: toàn bộ `pages/`, `components/`, `labels.ts`, `errorMessages.ts`.
- Endpoint, bảng DB, biến môi trường: không.
- Role và client bị ảnh hưởng: mọi vai web.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- `tsc` đạt; Farm đủ trang như trước.

**Phụ thuộc, lưu ý**: cần WPC5-01.

## WPC5-03: Giao diện Lab: ticket

- Mục tham chiếu: đề thi HaUI mục 4.3, 9.
- Mục tiêu: người dùng báo sự cố và theo dõi; kỹ thuật viên xử lý có công cụ đủ dùng.
- Ước lượng: L.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Báo sự cố | Không có | Form có gợi ý loại, ưu tiên và bài tri thức liên quan khi gõ mô tả; đính kèm ảnh |
| Theo dõi | Không có | Danh sách có lọc theo trạng thái, loại, ưu tiên, quá hạn; chi tiết có timeline, bình luận, đồng hồ SLA |
| Trao đổi | Không có | Luồng bình luận công khai; kỹ thuật viên có thêm bình luận nội bộ |
| Kỹ thuật viên | Không có | Hàng chờ: nhận, bắt đầu, chờ người dùng, xử lý (ghi chú, nguyên nhân gốc), kèm số ticket đang xử lý của từng người |
| Hoàn tất | Không có | Người gửi xác nhận kèm đánh giá sao hoặc mở lại |
| Thông báo | Không có | Hộp thông báo của người dùng |

**Hướng làm**

- Tái dùng `Timeline`, `Pagination`, `Badge`, `Section`, `DecisionActions` từ vỏ.
- Nhãn trạng thái, ưu tiên, nguyên nhân gốc và lỗi bằng tiếng Việt.
- Core/App: module Lab chỉ gọi API Lab và API chung.

**Phạm vi ảnh hưởng**

- Module: `frontend/src/apps/lab`.
- File chính: trang tạo ticket, danh sách, chi tiết, hàng chờ kỹ thuật viên, hộp thông báo, `api` và `labels` của Lab.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: STUDENT, LECTURER, TECHNICIAN, ADMIN.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Tạo, trao đổi, xử lý, xác nhận một ticket hoàn chỉnh qua web.

**Phụ thuộc, lưu ý**: cần C4 WPC4-03, WPC4-06, WPC4-07.

## WPC5-04: Giao diện Lab: thiết bị và mượn trả

- Mục tham chiếu: đề thi HaUI mục 4.3 (quản lý thiết bị).
- Mục tiêu: xem tình trạng thiết bị, mượn trả, ghi bảo trì.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Danh sách thiết bị | Không có | Lọc theo loại, phòng, trạng thái; lịch sử bảo trì và mượn |
| Mượn trả | Không có | Gửi yêu cầu, duyệt, từ chối, trả kèm tình trạng |
| Bảo trì | Không có | Mở và hoàn tất nhật ký sửa chữa (TECHNICIAN, ADMIN) |

**Hướng làm**

- Phiếu mượn đi qua hộp duyệt dùng chung cho người duyệt.
- Core/App: không thêm API vào core.

**Phạm vi ảnh hưởng**

- Module: `frontend/src/apps/lab`.
- File chính: trang thiết bị, mượn trả, bảo trì, menu Lab.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: mọi vai Lab.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Mượn, duyệt, trả, ghi bảo trì qua web.

**Phụ thuộc, lưu ý**: cần C4 WPC4-04.

## WPC5-05: Giao diện Lab: đặt phòng

- Mục tham chiếu: đề thi HaUI mục 4.3 (phòng học), 9.
- Mục tiêu: xem lịch phòng và đặt phòng không trùng.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Xem lịch | Không có | Lịch tuần theo phòng, ô đã đặt hiển thị học phần và trạng thái |
| Đặt phòng | Không có | Form chọn phòng và khung giờ; báo lỗi rõ khi trùng giờ, vượt sức chứa, phòng bảo trì |
| Duyệt, hủy | Không có | Hộp duyệt cho người duyệt; người đặt hủy được |

**Hướng làm**

- Chọn ô trên lịch điền sẵn giờ bắt đầu và kết thúc.
- Thông báo lỗi theo mã lỗi của API bằng tiếng Việt.
- Core/App: không thêm API vào core.

**Phạm vi ảnh hưởng**

- Module: `frontend/src/apps/lab`.
- File chính: trang lịch phòng, form đặt phòng.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: LECTURER, TECHNICIAN, ADMIN; STUDENT chỉ xem lịch.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Đặt phòng và duyệt qua web; lịch phản ánh đúng.

**Phụ thuộc, lưu ý**: cần C4 WPC4-05.

## WPC5-06: Giao diện Lab: tổng quan, thống kê, hỏi đáp

- Mục tham chiếu: đề thi HaUI mục 4.2 (D, I), 9.
- Mục tiêu: trang chủ có ý nghĩa với từng vai; có chỗ xem số liệu và hỏi đáp.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hành vi hiện tại | Hành vi sau thay đổi |
|---|---|---|
| Trang chủ | Không có | Theo vai: STUDENT, LECTURER xem việc của mình; TECHNICIAN xem hàng chờ và ticket quá hạn; ADMIN xem tổng quan |
| Thống kê | Không có | Biểu đồ SLA, thời gian xử lý, tải kỹ thuật viên, sử dụng phòng, hài lòng (TECHNICIAN, ADMIN) |
| Hỏi đáp quy chế | Không có | Trang tri thức dùng chung với namespace `lab`, hiện nguồn và giấy phép |
| Liên kết BI | Không có | Liên kết tới Superset cho ADMIN; thống kê chính nằm ở trang thống kê trong web |

**Hướng làm**

- Biểu đồ nhẹ, vẽ từ số liệu `lab-stats/summary`.
- Core/App: không thêm API vào core.

**Phạm vi ảnh hưởng**

- Module: `frontend/src/apps/lab`.
- File chính: trang chủ theo vai, trang thống kê, menu Lab.
- Endpoint, bảng DB, biến môi trường: không đổi.
- Role và client bị ảnh hưởng: mọi vai Lab.
- Tương thích ngược và dữ liệu cũ: không.

**Kết quả đầu ra**

- Kịch bản demo Lab đầu-cuối chạy qua web theo từng vai.

**Phụ thuộc, lưu ý**: cần C4 WPC4-07, WPC4-08.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint | Không | Dùng API đã có | Không |
| Biến môi trường | `VITE_KEYCLOAK_*` | Giá trị `dxos` (C3) | Có với `.env` cũ |
| Cấu trúc | `frontend/src` | `shell/`, `apps/farm`, `apps/lab` | Không |

## Dữ liệu, migration và rollback

- Migration: không.
- Dữ liệu cũ: không.
- Rollback: quay lại nhánh trước phase.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Một image web, nạp module theo manifest lúc chạy | Đổi app không build lại web |
| D2 | Vỏ không import module app | Giữ ranh giới như phía backend |
