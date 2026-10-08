# Phase 7: Dữ liệu, BI và tri thức

| Mục | Giá trị |
|---|---|
| Trạng thái | Xong (hoãn test thời tiết T7-04, T7-05; việc chưa làm và chưa chốt ở mục 11) |
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

- Demo được: Superset chỉ hiện dữ liệu đã duyệt; có 5 chart mới (báo cáo theo bệnh theo tuần, điểm nóng, thửa đang cách ly, lịch thu hoạch, tỷ lệ AI đúng/sai); trang hỏi đáp trả lời có trích nguồn từ corpus RAG; nguồn thời tiết hoạt động, có snapshot khi API ngoài ngừng.
- Sản phẩm bàn giao: migration `V9__bi_views.sql` (sáu view), quyền `superset_ro` theo danh sách trong seeder, `bi/dxfarm_dashboard_export.zip` cập nhật (commit theo đúng quy trình ở `bi/README.md`), trang web hỏi đáp mới, script/endpoint snapshot thời tiết.
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

- [x] `README.md`, `CHANGELOG.md` (chỉ ghi trạng thái cuối, tick mục "RAG trả lời có trích nguồn").
- [x] `bi/README.md` (view mới, chart mới, nền bản đồ).
- [x] `bi/dxfarm_dashboard_export.zip` (export lại từ Superset đang chạy).

## 11. Việc chưa làm và chưa chốt

**Chưa làm**

- Test thủ công thời tiết T7-04 và T7-05: endpoint chưa có màn hình nên chưa thử qua UI.
- Dữ liệu mẫu cho chart "Thửa đang cách ly" và "Tỷ lệ AI đúng/sai" (thuộc P8); hiện hai chart này hiện "No results".
- Thời tiết chưa có màn hình web hay mobile, chỉ có API.
- Chưa có chart thời tiết trong dashboard.
- Trang `/knowledge` chỉ có trên web (kỹ sư, admin); mobile của nông dân chưa có.
- Kho RAG chỉ có 6 tài liệu bệnh lúa (IRRI, LSU); chưa có tài liệu VietGAP hay quy trình canh tác.
- Snapshot thời tiết chụp ngày 07/10/2026 và chỉ có 5 điểm ĐBSCL; cần chụp lại nếu muốn dữ liệu dự phòng mới hơn.
- `knowledge-sources.json` của Backend sao từ `data/rag/source_registry.json` bằng tay, chưa có kiểm tra lệch.
- Export dashboard chưa có `query_context` cho từng chart (tạo qua API); dashboard hiển thị bình thường.

**Chưa chốt**

- Nền bản đồ: đang giữ nền trắng (WP7-03-DOC-01), chờ người phụ trách xác nhận.
- Tài khoản `engineer1` (role Gamma) vào Superset thấy danh sách dashboard rỗng; chưa xác định nguyên nhân (nghi thiếu quyền dataset của Gamma) và chưa quyết định cho kỹ sư xem dashboard hay chỉ admin.
- Cách nhận biết chẩn đoán do AI trong `vw_ai_diagnosis_outcomes` (`symptom_text` rỗng và `model_version` khác `manual-input-v1`): dễ sai nếu kỹ sư đổi tên model; phương án chắc hơn là thêm cột nguồn vào `diagnoses`.
- Ngưỡng `KNOWLEDGE_MIN_SCORE=0.40` chọn theo vài câu hỏi thử, chưa hiệu chỉnh trên bộ câu hỏi thật; điểm câu hỏi đúng chủ đề khoảng 0.45 đến 0.50.
- Giới hạn gateway 60 câu hỏi mỗi phút cho mỗi địa chỉ IP là số tạm.
- Hệ quả của việc đóng quyền mặc định: bảng danh mục mới phải được thêm tay vào `READABLE_RELATIONS`.
