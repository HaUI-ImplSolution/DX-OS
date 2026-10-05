# Checklist: Phase 7

Mã dòng: `WP7-{xx}-{lớp}-{nn}`. Lớp gồm DOC, BE, FE, DATA, TEST.
Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ (ghi lý do).

## WP7-01: VIEW chỉ bản ghi đã duyệt, đổi quyền `superset_ro`

Kết quả đầu ra: `superset_ro` không còn đọc trực tiếp được bảng gốc chứa bản ghi chưa duyệt.

**Data, seed, migration**

- [ ] WP7-01-DATA-01 Migration tạo `vw_parcels_approved`, `vw_crop_seasons_approved`, `vw_disease_reports_processed`, `vw_diagnoses_approved`.
- [ ] WP7-01-DATA-02 Migration `REVOKE SELECT` trên 5 bảng gốc, `GRANT SELECT` trên view mới cho `superset_ro`.

**Backend**

- [ ] WP7-01-BE-01 `SupersetReadOnlyRoleSeeder.java`: cập nhật script idempotent để khớp với migration (tránh seeder ghi đè lại quyền cũ mỗi lần khởi động).

**Infra, deploy, CI**

- [ ] WP7-01-INFRA-01 `bi/bootstrap_datasources.py`: đổi 5 dataset sang trỏ view mới.

**Test**

- [ ] WP7-01-TEST-01 Kết nối `psql` bằng role `superset_ro`, thử `SELECT` trên bảng gốc → bị từ chối (T7-01).
- [ ] WP7-01-TEST-02 `SELECT` trên view mới → thành công, chỉ thấy bản ghi đã duyệt (T7-02).

## WP7-02: Thêm chart mới cho dashboard

Kết quả đầu ra: dashboard có đủ 5 chart mới, trỏ đúng view đã lọc approved.

**Tài liệu / BI**

- [ ] WP7-02-DOC-01 Build chart "báo cáo theo bệnh theo thời gian" qua UI Superset.
- [ ] WP7-02-DOC-02 Build chart "điểm nóng dịch bệnh" (deck.gl Heatmap).
- [ ] WP7-02-DOC-03 Build chart "thửa đang cách ly".
- [ ] WP7-02-DOC-04 Build chart "lịch thu hoạch dự kiến".
- [ ] WP7-02-DOC-05 Build chart "tỷ lệ AI đúng/sai" (dùng nhãn huấn luyện từ P6).
- [ ] WP7-02-DOC-06 Export dashboard, đè `bi/dxfarm_dashboard_export.zip`, cập nhật `bi/README.md` mục "Dashboard mẫu hiện có".

**Test**

- [ ] WP7-02-TEST-01 `docker compose up -d --force-recreate superset` từ clone sạch → dashboard hiện đủ 7 chart, không chart nào nhân đôi (T7-03).

## WP7-03: Chốt giải pháp nền bản đồ

Kết quả đầu ra: có câu trả lời chuẩn bị sẵn cho câu hỏi hội đồng về nền bản đồ.

**Tài liệu**

- [ ] WP7-03-DOC-01 Xác nhận với người phụ trách: giữ nền trắng hoặc đổi phương án.
- [ ] WP7-03-DOC-02 Viết sẵn câu trả lời ngắn cho câu hỏi hội đồng, dẫn lý do từ `bi/README.md`.

## WP7-04: Nguồn dữ liệu mở thứ hai — Open-Meteo

Kết quả đầu ra: endpoint thời tiết hoạt động với dữ liệu thật, có fallback snapshot khi API ngoài lỗi.

**Backend**

- [ ] WP7-04-BE-01 Thêm `service/WeatherService.java`, gọi Open-Meteo theo toạ độ, cache ngắn hạn.
- [ ] WP7-04-BE-02 Thêm endpoint `GET /api/v1/parcels/{code}/weather`.
- [ ] WP7-04-BE-03 Fallback đọc snapshot JSON tĩnh khi gọi API ngoài lỗi, trả kèm cờ `isSnapshot`.

**Data, seed, migration**

- [ ] WP7-04-DATA-01 Chuẩn bị file snapshot JSON cho các thửa demo.

**Test**

- [ ] WP7-04-TEST-01 Gọi endpoint cho thửa có GPS → trả dữ liệu thật (T7-04).
- [ ] WP7-04-TEST-02 Giả lập Open-Meteo lỗi → trả snapshot, không 500 (T7-05).

## WP7-05: Trang tài liệu và hỏi đáp RAG có trích nguồn

Kết quả đầu ra: hỏi đáp trả lời kèm nguồn trích dẫn xác định được.

**Backend**

- [ ] WP7-05-BE-01 Thêm `controller/KnowledgeController.java`, endpoint `POST /api/v1/knowledge/ask` (passthrough sang AI, kèm `X-Service-Api-Key`).
- [ ] WP7-05-BE-02 Map `source_ids` sang tên tài liệu hiển thị được.

**Frontend (web)**

- [ ] WP7-05-FE-01 Trang `/knowledge`: ô nhập câu hỏi, hiển thị trả lời + danh sách nguồn.

**Test**

- [ ] WP7-05-TEST-01 Nhập câu hỏi về VietGAP → trả lời kèm ít nhất 1 nguồn xác định được (T7-06).

## Đóng phase

- [ ] `./mvnw compile` đạt (backend), `npx tsc --noEmit` đạt (web) và đã đọc lại diff.
- [ ] Comment mới trong diff ngắn, bằng tiếng Anh.
- [ ] Swagger khớp code.
- [ ] `.env.example`, seed, migration chạy được trên clone sạch.
- [ ] Không có secret, mật khẩu hay dữ liệu thật trong diff.
- [ ] README (tick "RAG trả lời có trích nguồn"), CHANGELOG, `bi/README.md` cập nhật (chỉ trạng thái cuối).
- [ ] Mọi tiêu chí ở `00_OVERVIEW.md` mục 6 đã đạt và có bằng chứng.
- [ ] Cập nhật trạng thái phase ở `improvement_docs/README.md`.
