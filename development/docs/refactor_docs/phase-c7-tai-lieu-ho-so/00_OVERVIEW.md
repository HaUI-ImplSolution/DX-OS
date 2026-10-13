# Phase C7: Tài liệu và hồ sơ nộp

| Mục | Giá trị |
|---|---|
| Trạng thái | Hoàn tất |
| Mức độ | Nghiêm trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | Dùng chung (tài liệu, hồ sơ) |
| Ước lượng | 2 ngày |
| Người phụ trách | {chưa phân công} |
| Bắt đầu, dự kiến xong | {chưa chốt} |
| Phụ thuộc | C6 (một số mục làm dần ở các phase trước) |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Đề thi HaUI mục 5, 10 | README, LICENSE, bảng dependency, CHANGELOG, CONTRIBUTING, báo cáo kỹ thuật, slide, video | Nghiêm trọng | Toàn bộ |
| Đề thi HaUI mục 13 | Câu hỏi định hướng của hội đồng | Nghiêm trọng | Câu trả lời mới theo kiến trúc host + plugin |
| `REMAINING_WORK.md` | Việc còn lại trước khi nộp | Quan trọng | Cập nhật theo trạng thái mới |

## 2. Lý do

- Vấn đề đang gặp: tài liệu và hồ sơ viết cho một ứng dụng (Farm); `ARCHITECTURE_OPEN_CORE.md` ghi ranh giới chỉ là quy ước package; nhiều link trỏ đường dẫn cũ sau khi chuyển file; chưa có hướng dẫn thêm app mới.
- Vì sao làm ở phase này: sản phẩm nộp là core dùng chung hai app, hồ sơ phải chứng minh điều đó.

## 3. Tổng quan sửa đổi

Cập nhật kiến trúc, README, CONTRIBUTING (kể cả hướng dẫn thêm app mới), LICENSES, CHANGELOG, các tài liệu nộp bài (hỏi đáp hội đồng, kịch bản demo, dàn ý báo cáo) và danh sách việc còn lại.
Sửa các link gãy do chuyển file.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` (interface, DTO) | Không | |
| `dx-core` (cài đặt dùng chung) | Không | |
| `app-farm` | Không | |
| `app-lab` | Không | |
| `dx-host` (chạy, chọn app) | Không | |
| Frontend (web) | Không | |
| Mobile | Không | |
| AI | Không | |
| Data / Seed / Migration | Không | |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | Toàn bộ |

## 4. Phạm vi

- Trong phạm vi: tài liệu kiến trúc và vận hành, hướng dẫn thêm app, tài liệu nộp bài, link, danh sách việc còn lại.
- Ngoài phạm vi: giấy phép AGPL cho `ai/` (RD7, xử lý riêng sau phase này); tag release và chuyển repository sang public (theo `REMAINING_WORK.md`); video và slide cuối.
- Giả định: C1 đến C6 hoàn tất; ảnh chụp và log có sẵn.
- Ranh giới Core/App: tài liệu mô tả đúng ranh giới biên dịch của `dx-core-api`.

## 5. Ưu điểm và hạn chế

- Ưu điểm: hồ sơ khớp sản phẩm; hội đồng có sơ đồ và hướng dẫn cụ thể để kiểm chứng.
- Hạn chế: cần chụp lại ảnh và kịch bản demo sau khi đổi tên.
- Trade-off lớn: không có.

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: người đọc README dựng được hệ thống, chạy từng app bằng `DXOS_APP`, làm theo hướng dẫn thêm một app giả.
- Sản phẩm bàn giao: tài liệu kiến trúc mới, README, CONTRIBUTING, LICENSES, CHANGELOG, hỏi đáp hội đồng, kịch bản demo, dàn ý báo cáo, `REMAINING_WORK.md`.
- Tiêu chí đo được:
  - Không còn link gãy trong repo (tìm đường dẫn cũ ở `00_OVERVIEW.md` mục 9).
  - Hướng dẫn thêm app liệt kê đủ bước: module, `AppModule`, vai, migration, interface cần cài, web.
  - Mọi câu hỏi mục 13 của đề có câu trả lời tham chiếu sơ đồ hoặc mã.
- Bằng chứng: kết quả tìm link gãy, bản đọc lại tài liệu.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: C6; các mục từng phần làm dần ở C1 đến C6.
- Phase sau cần từ phase này: không.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: README, CHANGELOG, issue và chất lượng tài liệu (10 điểm); license, dependency và nguồn gốc (10 điểm); Open-Core (10 điểm); phong cách trình diễn (10 điểm).
- Dùng làm bằng chứng cho: báo cáo, slide, video, câu hỏi định hướng 1 đến 10 ở mục 13 của đề.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Tên repository và sản phẩm | Giữ `DX---Farm` hay đổi sang tên trung tính | Đầu phase | Chốt giữ tên `DX---Farm` (09/10/2026); đổi tên repository để sau |
| Q2 | Nơi đặt hướng dẫn thêm app | `CONTRIBUTING.md` hay tài liệu riêng trong `development/docs` | Đầu phase | Chốt `CONTRIBUTING.md` kèm liên kết (09/10/2026) đề xuất `CONTRIBUTING.md` kèm liên kết |
| Q3 | Đường dẫn cũ cần sửa | `backend/DX-Farm` → `backend/` và module đích (README, CONTRIBUTING, LICENSES, `data/README.md`, `sbom/README.md`, link tới file Java); các link tài liệu đã chuyển (`submission/`, `legacy_docs/`, `_template`) đã đúng | Đầu phase | Danh sách cố định, không cần chốt thêm |

## 10. Tài liệu cần cập nhật khi xong

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối).
- [x] Swagger (springdoc) nếu còn lệch.
- [x] Sơ đồ kiến trúc, `LICENSES.md`, `.env.example`.
