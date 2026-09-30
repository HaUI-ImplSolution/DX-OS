# Kế hoạch chuyển đổi dữ liệu và mô hình bệnh lá lúa

## 1. Mục tiêu

Thay bộ dữ liệu bệnh cây và service CV hiện tại bằng:

- Dữ liệu bệnh lá lúa được chuẩn hóa từ `development/.local/RICE_DATA_COLLECTION_TEMPLATE.xlsx`.
- Trọng số YOLOv8n tại `development/.local/best.pt`.
- Quy ước lớp và nghiệp vụ trong `development/.local/RICE_LEAF_DISEASE_BD_DATASET_SPEC.md`.

Việc chuyển đổi phải giữ ổn định luồng hiện có từ upload ảnh, CV, backend, RAG đến khuyến nghị. Không mở rộng API hoặc thay đổi schema nếu không thực sự cần thiết cho việc thay model và data.

## 2. Nguyên tắc bắt buộc

- Giữ nguyên contract của `POST /api/v1/disease/predict` và cấu trúc `cv_result` trong luồng chẩn đoán.
- Response CV chỉ gồm `disease_code`, `disease_name`, `confidence`, `model_name`, `model_version`.
- Bounding box và danh sách detection chỉ dùng nội bộ, không đưa thêm vào API hoặc UI.
- Không tạo endpoint mới.
- Không thay đổi topology của workflow, schema database, embedding hoặc vector store nếu luồng hiện tại vẫn đáp ứng được.
- Không tự động tải checkpoint từ Internet. Trọng số phải được cung cấp qua đường dẫn cấu hình và mount read-only khi chạy container.
- Không tự nâng trạng thái dữ liệu từ `BLOCKED` hoặc `IN_REVIEW` thành `APPROVED`.
- Không seed thuốc, liều dùng hoặc quy tắc điều trị chưa đủ điều kiện phê duyệt.
- `data.sql` chỉ được sinh bằng script hiện có, không chỉnh tay.
- Thực hiện theo từng giai đoạn; chỉ chuyển bước sau khi đạt điều kiện nghiệm thu của giai đoạn trước.

## 3. Quy ước lớp chuẩn

| YOLO class ID | Tên lớp model | `disease_code` | Tên hiển thị |
|---:|---|---|---|
| 0 | Blast | `Rice___Blast` | Bệnh đạo ôn trên lúa |
| 1 | Brown Spot | `Rice___Brown_spot` | Bệnh đốm nâu trên lúa |
| 2 | Leaf Smut | `Rice___Leaf_smut` | Bệnh than lá lúa |
| 3 | Rice Tungro | `Rice___Tungro` | Bệnh tungro trên lúa |
| 4 | Sheath Blight | `Rice___Sheath_blight` | Bệnh khô vằn trên lúa |
| Không có detection đạt ngưỡng | Healthy fallback | `Rice___healthy` | Lá lúa không thấy triệu chứng bệnh |

Quy tắc suy luận:

- Ngưỡng confidence mặc định: `0.25`.
- Ngưỡng IoU mặc định: `0.45`.
- Nếu có nhiều detection đạt ngưỡng, chọn detection có confidence cao nhất làm chẩn đoán chính.
- Nếu không có detection đạt ngưỡng, trả về lớp healthy fallback.
- Model phải được kiểm tra task, số lớp và thứ tự lớp khi service khởi động.

## 4. Phạm vi file dự kiến thay đổi

### Dữ liệu chuẩn

- `data/structured/diseases.json`
- `data/structured/class_contract.json`
- `data/structured/pesticides_candidates_master.json`
- `data/structured/treatment_rules_candidates_master.json`
- `data/structured/disease_management_policies_master.json`
- `data/rag/processed/disease_knowledge.json`
- `data/rag/source_registry.json`
- Seed SQL được sinh từ `data/scripts/generate_seed_sql.py`

### Service AI/CV

- Loader và inference của service CV.
- Cấu hình đường dẫn trọng số, confidence và IoU.
- Dependency phục vụ Ultralytics YOLO.
- Docker image/Compose mount checkpoint read-only.
- Test và mock liên quan đến kết quả CV.

### Tài liệu và cấu hình vận hành

- `README.md`
- `data/README.md`
- README của service AI/CV.
- File cấu hình mẫu và Compose liên quan trực tiếp đến model mới.
- Tài liệu license/nguồn dữ liệu nếu cần.

Các file cụ thể chỉ được sửa sau khi audit xác nhận chúng đang tham gia luồng chạy thực tế.

## 5. Kế hoạch triển khai theo giai đoạn

### Giai đoạn 0 — Chốt baseline và phạm vi ảnh hưởng

1. Ghi nhận trạng thái Git trước khi sửa để không ghi đè thay đổi không liên quan.
2. Xác định chính xác đường đi hiện tại của:
   - Dữ liệu nguồn đến JSON và seed SQL.
   - Upload ảnh đến response CV.
   - `disease_code` từ CV đến backend, RAG và khuyến nghị.
3. Chạy các test hiện có liên quan đến data, CV, backend và E2E để lập baseline.
4. Kiểm tra checkpoint cục bộ: tồn tại, checksum, task, class count và class names.
5. Đối chiếu workbook với dataset spec; ghi nhận rõ các mục đang `BLOCKED` hoặc `IN_REVIEW`.

Điều kiện hoàn thành:

- Có danh sách file thực sự cần sửa.
- Có baseline test và các lỗi tồn tại từ trước được tách riêng.
- Checkpoint khớp đúng năm lớp bệnh theo thứ tự đã chốt.

### Giai đoạn 1 — Thay dữ liệu chuẩn sang miền bệnh lá lúa

1. Chuyển sáu điều kiện chuẩn, gồm năm bệnh và healthy fallback, vào `diseases.json`.
2. Cập nhật `class_contract.json` để ràng buộc chính xác class ID, tên lớp model và `disease_code`.
3. Chuyển các dữ liệu quản lý bệnh đủ điều kiện từ workbook vào các file structured tương ứng.
4. Giữ nguyên trạng thái review của dữ liệu chưa được phê duyệt; không đưa các mục này vào seed vận hành.
5. Cập nhật provenance và source registry để truy vết được nguồn workbook/spec.
6. Chạy validation JSON, uniqueness, foreign key, class mapping và provenance.

Điều kiện hoàn thành:

- Sáu mã bệnh/healthy nhất quán giữa các file.
- Không còn liên kết nghiệp vụ mới đến mã bệnh cũ.
- Không có dữ liệu `BLOCKED` hoặc `IN_REVIEW` bị đưa vào tập `APPROVED`.
- Toàn bộ validator dữ liệu đạt.

### Giai đoạn 2 — Cập nhật seed và bảo vệ database hiện có

1. Điều chỉnh generator để tạo seed cho dữ liệu lúa từ nguồn chuẩn.
2. Sinh lại `data.sql` bằng `data/scripts/generate_seed_sql.py`.
3. Bảo đảm database mới nhận đúng dữ liệu lúa.
4. Với database đã có dữ liệu:
   - Xóa dữ liệu cũ chỉ khi không được diagnosis/history tham chiếu.
   - Giữ bản ghi cũ đang được tham chiếu để bảo toàn lịch sử.
   - Đánh dấu hoặc loại bản ghi cũ khỏi lookup mới mà không phá foreign key.
5. Kiểm tra seed có thể chạy lặp lại an toàn theo cơ chế hiện hành.

Điều kiện hoàn thành:

- Fresh database khởi tạo thành công với dữ liệu lúa.
- Database có lịch sử cũ migrate mà không mất diagnosis/history và không lỗi foreign key.
- Lookup mới chỉ trả dữ liệu thuộc miền lúa đang hoạt động.

### Giai đoạn 3 — Thay inference CV bằng YOLOv8n

1. Thay loader/classifier cũ bằng Ultralytics YOLO sử dụng checkpoint cấu hình được.
2. Ánh xạ output model qua `class_contract.json`; không hard-code hai nguồn mapping độc lập.
3. Chọn detection có confidence cao nhất; áp dụng healthy fallback khi không có detection đạt ngưỡng.
4. Giữ nguyên response schema và mã lỗi hiện tại.
5. Kiểm tra model khi startup:
   - File tồn tại và đọc được.
   - Checksum đúng nếu được cấu hình.
   - Task phù hợp.
   - Đúng số lớp và đúng thứ tự/tên lớp.
6. Cập nhật Docker/Compose để mount `best.pt` read-only và không đóng gói hoặc tải ngầm model ngoài chủ đích.

Điều kiện hoàn thành:

- Service load được `best.pt` và health check đạt.
- Invalid image vẫn trả hành vi tương thích với contract cũ.
- Model/class mismatch làm startup thất bại rõ ràng.
- Test xác nhận quy tắc highest-confidence và healthy fallback.
- Contract test chứng minh response không có trường mới.

### Giai đoạn 4 — Đồng bộ RAG và logic khuyến nghị

1. Thay knowledge corpus cũ bằng nội dung cho sáu điều kiện lúa, dùng đúng `disease_code` chuẩn.
2. Cập nhật mock/fixture CV trong các test workflow sang mã bệnh lúa.
3. Giữ nguyên graph và cơ chế truy hồi hiện tại.
4. Không suy diễn thuốc, liều dùng, PHI hoặc hướng dẫn điều trị từ nội dung RAG.
5. Nếu không có treatment rule đã `APPROVED`, kết quả phải đi theo nhánh `review_required` hiện có.

Điều kiện hoàn thành:

- RAG truy hồi được tài liệu theo từng mã bệnh lúa.
- Luồng khuyến nghị không dùng dữ liệu chưa phê duyệt.
- Không thay đổi schema state hoặc response của workflow.

### Giai đoạn 5 — Kiểm thử tích hợp và E2E

Thực hiện tối thiểu các nhóm kiểm thử sau:

1. Data validation: JSON schema, mapping, foreign key, provenance và approval status.
2. CV unit/contract: ảnh lỗi, mapping sai, highest-confidence, healthy fallback và schema response.
3. Backend integration: seed mới, lookup mới và bảo toàn lịch sử cũ.
4. Docker smoke test: mount checkpoint, startup, health check và một lần inference.
5. E2E: upload ảnh → CV → backend context → RAG → recommendation → diagnosis.
6. Kiểm thử ngữ nghĩa bằng mẫu ảnh thật đại diện cho từng lớp khi có bộ mẫu phù hợp.

Lưu ý: việc model load thành công chỉ xác nhận tích hợp kỹ thuật, không chứng minh độ chính xác của model.

Điều kiện hoàn thành:

- Các test bắt buộc đạt hoặc có lỗi baseline được ghi nhận rõ.
- Không có regression trên contract và luồng E2E hiện hữu.
- Kết quả chẩn đoán lưu đúng mã bệnh lúa và tiếp tục đi qua RAG/khuyến nghị bình thường.

### Giai đoạn 6 — Hoàn thiện tài liệu và bàn giao

1. Cập nhật tài liệu setup data/model, biến môi trường và cách mount checkpoint.
2. Ghi rõ nguồn dữ liệu, model, checksum, class contract và giới hạn đánh giá.
3. Tạo/cập nhật file tiến độ riêng theo style ngắn gọn của `CHANGELOG.md` sau mỗi giai đoạn.
4. Chỉ chắt lọc các thay đổi đã hoàn tất và được xác minh vào `CHANGELOG.md`.
5. Lưu kết quả test và các rủi ro còn lại để bàn giao.

Điều kiện hoàn thành:

- Tài liệu đủ để dựng lại môi trường mà không tải model ngoài ý muốn.
- Tiến độ phản ánh đúng thay đổi đã thực hiện, không ghi trước kết quả chưa xác minh.

## 6. Thứ tự thực hiện và điểm dừng kiểm tra

Thứ tự bắt buộc:

1. Baseline và audit.
2. Dữ liệu chuẩn.
3. Seed và migration database.
4. Service CV.
5. RAG và workflow fixtures.
6. Integration/E2E.
7. Tài liệu và bàn giao.

Sau mỗi giai đoạn:

- Cập nhật file tiến độ.
- Chạy test đúng phạm vi vừa thay đổi.
- Kiểm tra diff để loại bỏ thay đổi ngoài phạm vi.
- Dừng để review trước khi chuyển sang giai đoạn kế tiếp.

## 7. Ngoài phạm vi

- Thêm bounding box, ảnh annotate hoặc danh sách detection vào API/UI.
- Thêm endpoint CV mới.
- Huấn luyện lại hoặc tinh chỉnh model.
- Lưu nhiều bệnh cho một lần chẩn đoán.
- Tự động phê duyệt pesticide hoặc treatment rule.
- Thiết kế lại RAG, embedding, vector database hoặc workflow graph.
- Thay đổi frontend ngoài nội dung tên bệnh cần thiết để hiển thị dữ liệu mới.
- Các hạng mục không liên quan như SSO, BI, chat hoặc mở rộng nghiệp vụ khác.

## 8. Tiêu chí hoàn tất toàn bộ kế hoạch

Kế hoạch chỉ được coi là hoàn tất khi:

- Dữ liệu vận hành đã chuyển sang sáu điều kiện bệnh lá lúa theo contract chuẩn.
- Service CV dùng đúng checkpoint YOLOv8n được cung cấp và không phụ thuộc checkpoint tải từ xa.
- API và workflow contract không thay đổi.
- Fresh database và database có lịch sử cũ đều hoạt động an toàn.
- RAG và khuyến nghị dùng đúng mã bệnh lúa, đồng thời tôn trọng trạng thái phê duyệt dữ liệu.
- Test unit, integration, Docker smoke và E2E bắt buộc đạt.
- Tài liệu và nhật ký tiến độ phản ánh đúng trạng thái đã xác minh.
