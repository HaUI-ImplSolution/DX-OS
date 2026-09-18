# Phase C0: Chốt thiết kế và nền

| Mục | Giá trị |
|---|---|
| Trạng thái | Đang làm (quyết định đã chốt; còn baseline) |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | Dùng chung (chưa đụng code) |
| Ước lượng | 1 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | Không |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề thi HaUI mục 3, 4.1 | Kiến trúc 3 tầng, Open-Core headless, tách biệt UI khỏi core | Nghiêm trọng | Toàn bộ |
| Đề thi HaUI mục 12, 13 | Lỗi bị trừ điểm "không chứng minh được Open-Core"; câu hỏi hội đồng về ranh giới core và app | Nghiêm trọng | Toàn bộ |
| `ARCHITECTURE_OPEN_CORE.md` mục 2 | Ranh giới hiện chỉ là quy ước package | Nghiêm trọng | Toàn bộ |

## 2. Lý do

- Vấn đề đang gặp: core chưa tách khỏi app ở mức biên dịch, `Household` mang nghĩa nông nghiệp, chưa có app thứ hai để chứng minh core dùng chung.
- Vì sao làm ở phase này: các phase sau đều đổi cấu trúc backend; quyết định sai về tên, module hay trạng thái workflow sẽ phải đổi lại nhiều lần.

## 3. Tổng quan sửa đổi

Phase chỉ làm tài liệu.
Chốt các quyết định mở, hoàn thiện bản đồ module và danh mục interface, ghi lại baseline để so sánh sau mỗi phase.
Không đổi code.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` | Không | Chỉ mô tả |
| `dx-core` | Không | Chỉ mô tả |
| `app-farm` | Không | Chỉ mô tả |
| `app-lab` | Không | Chỉ mô tả |
| `dx-host` | Không | Chỉ mô tả |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Không | |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | `refactor/` |

## 4. Phạm vi

- Trong phạm vi: chốt RD8 đến RD10 và các câu hỏi mục 9; hoàn thiện `MODULE_MAP.md`, `SPI_CATALOG.md`; ghi baseline.
- Ngoài phạm vi: mọi thay đổi code, cấu hình, migration.
- Giả định: RD1 đến RD7 ở `README.md` đã chốt.
- Ranh giới Core/App: chốt nguyên tắc "app chỉ phụ thuộc `dx-core-api`, core không phụ thuộc app".

## 5. Ưu điểm và hạn chế

- Ưu điểm: các phase sau không phải đoán; baseline cho phép phát hiện hành vi lệch sau C1.
- Hạn chế: thêm một ngày chưa ra code.
- Trade-off lớn: không có; quyết định khó nằm ở `04_TRADEOFFS_RISKS.md` của C1 và C2.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: đọc `README.md` của `refactor/` là hiểu đích đến, thứ tự phase và các quyết định đã chốt.
- Sản phẩm bàn giao: `README.md`, `MODULE_MAP.md`, `SPI_CATALOG.md`, tám folder phase, bản ghi baseline.
- Tiêu chí đo được: RD8 đến RD12 có trạng thái "Đã chốt"; mục 9 không còn câu hỏi mở.
- Bằng chứng: `README.md` cập nhật, bản ghi baseline trong `03_TEST_PLAN.md`.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: không.
- Phase sau cần từ phase này: C1 cần RD8, RD9; C2 cần RD10; C3 cần tên realm.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: kiến trúc 3 tầng, Open-Core headless có vai trò thực (10 điểm).
- Dùng làm bằng chứng cho: câu hỏi định hướng 1, 2, 3, 4 và 10 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Tên package và artifact (RD8) | A: `HaUI.ImplSolution.dxos.{core.api,core,farm,lab,host}` với artifact `dx-core-api`, `dx-core`, `app-farm`, `app-lab`, `dx-host`, parent `dx-os-parent`. B: giữ `DX_Farm` làm gốc | Trước C1 | Chốt A (09/10/2026) |
| Q2 | Schema DB (RD9) | A: giữ `public`, tách Flyway theo location và bảng lịch sử riêng. B: schema `core`, `farm`, `lab` | Trước C1 | Chốt A (09/10/2026) |
| Q3 | Trạng thái workflow (RD10) | A: giữ enum 4 trạng thái, app tự giữ trạng thái riêng. B: trạng thái do `CaseTypeDefinition` khai báo | Trước C2 | Chốt B (09/10/2026) |
| Q4 | Tên realm trung tính (RD4) | `dxos` hoặc tên khác | Trước C3 | Chốt `dxos` (09/10/2026) |
| Q5 | Phạm vi domain của Lab (RD11) | Ticket, tài sản và mượn trả, đặt phòng, tri thức, thống kê; đủ sâu để dùng thật | Trước C4 | Chốt mở rộng (09/10/2026); thứ tự ưu tiên ở C4 |
| Q6 | Đường dẫn API (RD12) | A: giữ `/api/v1/...` cho app đang chạy, không prefix theo app. B: prefix `/api/v1/{app}/...` | Trước C1 | Chốt A (09/10/2026) |
| Q7 | Tên repository và sản phẩm | Giữ `DX---Farm` hay đổi sang tên trung tính | Trước C7 | Chốt giữ tên `DX---Farm` (09/10/2026); đổi tên repository để sau |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md` của `refactor/`: trạng thái quyết định RD8 đến RD12.
- [ ] `MODULE_MAP.md`, `SPI_CATALOG.md` khớp quyết định.
