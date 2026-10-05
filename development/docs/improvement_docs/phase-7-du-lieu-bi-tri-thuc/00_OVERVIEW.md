# Phase 7: Dữ liệu, BI và tri thức

| Mục | Giá trị |
|---|---|
| Trạng thái | Sẵn sàng code (plan + Q1 đã chốt, chưa bắt đầu implement) |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | B |
| Ước lượng | 4-5 ngày |
| Người phụ trách | {điền tên} |
| Bắt đầu, dự kiến xong | {dd/mm/yyyy}, {dd/mm/yyyy} |
| Phụ thuộc | Phase 4, Phase 6 |

## 1. Tham chiếu

| Mục `PROJECT_IMPROVEMENTS.md` | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| mục 14 | BI / Superset và dữ liệu mở | Quan trọng | Toàn bộ |
| mục 21 | Bốn không gian H-P-D-I, phần D và một phần I | Quan trọng | D (dữ liệu/BI) toàn bộ; I (hỏi đáp RAG có trích nguồn) |

## 2. Lý do

- Vấn đề đang gặp: `superset_ro` hiện có `GRANT SELECT ON ALL TABLES IN SCHEMA public` (`SupersetReadOnlyRoleSeeder.java`), nghĩa là Superset đọc được cả bản ghi chưa duyệt, kèm `ALTER DEFAULT PRIVILEGES` nên bảng mới tạo sau này cũng tự động được quyền đọc.
  Dashboard mẫu hiện chỉ có 2 chart (bản đồ điểm, pie vụ mùa theo trạng thái, theo `bi/README.md`).
  Nền bản đồ chưa có — `bi/README.md` đã ghi nhận đã cân nhắc Mapbox/OSM/Google Maps và loại cả 3 vì vấn đề nhãn Hoàng Sa/Trường Sa hoặc chi phí, hiện chọn nền trắng.
  RAG đã có khả năng trả về `source_ids` (`ai/app/rag/retriever.py`) nhưng chưa có giao diện hỏi đáp nào cho người dùng cuối.
- Vì sao làm ở phase này: cần dữ liệu đã chuẩn hoá từ P4 (mã thửa/vụ, GPS bắt buộc) và nhãn huấn luyện từ P6 trước khi làm chart có ý nghĩa; làm sớm hơn sẽ phải sửa lại dataset/chart khi dữ liệu nguồn đổi.

## 3. Tổng quan sửa đổi

| Lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| Backend | Có | Endpoint hỏi đáp RAG (passthrough), endpoint snapshot thời tiết |
| Frontend (web) | Có | Trang tài liệu/hỏi đáp (knowledge hub) |
| Mobile | Không | |
| AI | Không | Dùng nguyên `/knowledge/retrieve` đã có |
| Data / Seed / Migration | Có | VIEW approved-only, snapshot dữ liệu thời tiết |
| Infra / Deploy / CI | Không | |
| Docs / PoF | Có | Cập nhật `bi/README.md`, dashboard export mới |

## 4. Phạm vi

- Trong phạm vi: WP7-01 đến WP7-05 ở `01_PLAN.md` (VIEW approved-only + đổi quyền, thêm chart, chốt giải pháp nền bản đồ, nguồn dữ liệu mở Open-Meteo, trang tài liệu/hỏi đáp RAG).
- Ngoài phạm vi (không làm ở phase này): tự host vector tile map (đã đánh giá trong `bi/README.md` là "không tương xứng quy mô demo", giữ nguyên quyết định đó).
- Giả định: D10 (phase 0, Open-Meteo + snapshot dự phòng) đã chốt; mã thửa/vụ chuẩn hoá (P4) và nhãn huấn luyện AI (P6) đã có.

## 5. Ưu điểm và hạn chế

- Ưu điểm: đóng đúng lỗ hổng "Superset đọc được dữ liệu chưa duyệt"; có thêm nguồn dữ liệu mở sống (không chỉ file tĩnh); phần Intelligence có giao diện demo được ngoài chẩn đoán ảnh.
- Hạn chế: không giải quyết được vấn đề nhãn bản đồ tranh chấp bằng giải pháp miễn phí hoàn chỉnh (đã đánh giá kỹ trong `bi/README.md`, chấp nhận giới hạn).
- Trade-off lớn: xem `04_TRADEOFFS_RISKS.md` (đổi quyền `superset_ro` có thể làm vỡ dashboard cũ nếu chart đang query thẳng bảng gốc).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Demo được: Superset chỉ hiện dữ liệu đã duyệt; có ít nhất 3 chart mới (báo cáo theo bệnh theo thời gian, thửa đang cách ly, tỷ lệ AI đúng/sai); trang hỏi đáp trả lời có trích nguồn từ corpus RAG; nguồn thời tiết hoạt động, có snapshot khi API ngoài ngừng.
- Sản phẩm bàn giao: migration VIEW + đổi quyền, `bi/dxfarm_dashboard_export.zip` cập nhật (commit theo đúng quy trình ở `bi/README.md`), trang web hỏi đáp mới, script/endpoint snapshot thời tiết.
- Tiêu chí đo được: `superset_ro` không còn `SELECT` được trực tiếp trên bảng gốc chứa bản ghi chưa duyệt; mục "RAG trả lời có trích nguồn" trong README chuyển từ chưa tick sang đã tick.
- Bằng chứng: ảnh chụp dashboard mới, log test T7-xx, ảnh chụp màn hỏi đáp có trích nguồn.

## 7. Phụ thuộc và thứ tự

- Cần từ phase trước: mã thửa/vụ chuẩn hoá và GPS bắt buộc (P4); nhãn huấn luyện AI đã lưu (P6, cho chart tỷ lệ AI đúng/sai).
- Phase sau cần từ phase này: P8 dùng chart/dashboard làm bằng chứng demo phần D; mục 24 (carbon, để sau) có thể dùng nguồn thời tiết đã tích hợp ở đây.

## 8. Ảnh hưởng đến thi (PoF và sản phẩm)

- Điểm rubric liên quan: đề phần D (nguồn dữ liệu mở có giấy phép, tìm kiếm/lọc/thống kê, API REST); đề mục 21 (không gian D, I).
- Dùng làm bằng chứng cho: câu hỏi hội đồng "nguồn dữ liệu/API ngừng thì sao?" (mục 20 `PROJECT_IMPROVEMENTS.md`) — trả lời bằng snapshot dự phòng đã có cho cả AI (`evidence_fallback`) và thời tiết (WP7-04).

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Snapshot thời tiết lưu dạng nào | File JSON tĩnh trong repo, cập nhật tay định kỳ / Bảng DB, cronjob tự lưu snapshot mới nhất | 05/10/2026 | File JSON tĩnh trong repo — khớp quy ước hiện có của dự án (`data/structured`, `dxfarm_dashboard_export.zip`), không cần thêm hạ tầng cronjob |

## 10. Tài liệu cần cập nhật khi xong

- [ ] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối, tick mục "RAG trả lời có trích nguồn").
- [ ] `bi/README.md` (VIEW mới, chart mới, cập nhật phần "Dashboard mẫu hiện có").
- [ ] `bi/dxfarm_dashboard_export.zip` (export lại và commit theo đúng quy trình đã có).
