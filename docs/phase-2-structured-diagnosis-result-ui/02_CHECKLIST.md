# Checklist: Phase 2

Ký hiệu: `[ ]` chưa làm, `[~]` đang làm, `[x]` xong, `[-]` bỏ và phải ghi lý do.
Checklist mô tả phần việc còn lại từ code hiện tại của nhánh.
Confidence, treatment card và responsive CSS hiện có là baseline cần giữ hồi quy.

## WP2-01: Contract AI evidence

Kết quả đầu ra: AI trả evidence đầy đủ và không tạo summary ghép CV/rule/RAG.

**Spec và tài liệu**

- [ ] WP2-01-DOC-01 Chốt ví dụ response AI có evidence và evidence rỗng.
- [ ] WP2-01-DOC-02 Cập nhật OpenAPI: metadata evidence mới, `summary` nullable/deprecated.

**AI**

- [ ] WP2-01-AI-01 Mở rộng `DiagnoseRagEvidence` bằng chunk/document ID, title và version.
- [ ] WP2-01-AI-02 Map metadata sẵn có từ retriever vào diagnosis response.
- [ ] WP2-01-AI-03 Ngừng gọi recommendation writer và không ghép summary trong diagnosis path.
- [ ] WP2-01-AI-04 Giữ rule selection, engineer approval và fallback evidence rỗng.

**Test**

- [ ] WP2-01-TEST-01 Viết/chạy T2-01 đến T2-03.

## WP2-02: Persistence và Backend response

Kết quả đầu ra: evidence được lưu, reload và trả đúng quyền; treatment rule vẫn là nguồn nghiệp vụ.

**Backend**

- [ ] WP2-02-BE-01 [farm] Mở rộng `AiDiagnoseResponse.RagEvidence`.
- [ ] WP2-02-BE-02 [farm] Thêm field JSONB evidence vào entity `Diagnosis`.
- [ ] WP2-02-BE-03 [farm] Validate giới hạn payload evidence trước khi lưu.
- [ ] WP2-02-BE-04 [farm] Lưu evidence và cho AI diagnosis mới để `recommendationText` null.
- [ ] WP2-02-BE-05 [farm] Thêm `KnowledgeEvidenceResponse` vào `DiagnosisResponse`.
- [ ] WP2-02-BE-06 [farm] Resolve source metadata qua `FarmKnowledgeNamespace`.
- [ ] WP2-02-BE-07 [farm] Giữ nguyên disease/rule/version validation.
- [ ] WP2-02-BE-08 [farm] Cập nhật masking FARMER và mapper lịch sử diagnosis.

**Data và migration**

- [ ] WP2-02-DATA-01 [farm] Thêm `knowledge_evidence jsonb NOT NULL DEFAULT '[]'`.
- [ ] WP2-02-DATA-02 [farm] Cho phép `recommendation_text` null mà không sửa dữ liệu cũ.
- [ ] WP2-02-DATA-03 [farm] Kiểm tra migration trên database sạch và database có diagnosis cũ.

**Test**

- [ ] WP2-02-TEST-01 Viết/chạy T2-04 đến T2-09.

## WP2-03: Hiển thị evidence trên web

Kết quả đầu ra: web hiện treatment rule và RAG evidence thành hai khối riêng, không đưa summary trở lại.

**Frontend web**

- [ ] WP2-03-FE-01 Thêm type `knowledgeEvidence` và source vào `Diagnosis`.
- [ ] WP2-03-FE-02 Tạo knowledge evidence card dưới treatment card hiện có.
- [ ] WP2-03-FE-03 Hiển thị document title, content, “Độ liên quan của tài liệu” và `SourceList` cho từng evidence.
- [ ] WP2-03-FE-04 Ẩn toàn bộ knowledge card khi evidence rỗng.
- [ ] WP2-03-FE-05 Giữ confidence, treatment card, responsive và điều kiện nút nghiệp vụ hiện có.
- [ ] WP2-03-FE-06 Không render `recommendationText`, HTML từ content hoặc field điều trị suy ra từ evidence.
- [ ] WP2-03-FE-07 Áp dụng thứ tự, nhãn tiếng Việt và trạng thái ngoại lệ trong `05_EXPECTED_OUTPUT_FORMAT.md`.
- [ ] WP2-03-FE-08 Ghi rõ “Nguồn phác đồ” và “Nguồn tài liệu”; không dùng một nhãn nguồn chung.
- [ ] WP2-03-FE-09 Không gọi confidence là “tỷ lệ chính xác” và không trình bày similarity như xác suất mắc bệnh.
- [ ] WP2-03-FE-10 Chỉ hiện phương pháp áp dụng từ field cấu trúc đã xác thực; nếu contract chưa có thì ẩn dòng, không parse `notes`.

**Test**

- [ ] WP2-03-TEST-01 Viết/chạy T2-10 đến T2-14.

## WP2-04: Kiểm thử và đóng phase

Kết quả đầu ra: contract, migration, UI và hồi quy nghiệp vụ trực tiếp đều có bằng chứng.

**Test**

- [ ] WP2-04-TEST-01 Chạy test AI liên quan đến diagnosis contract.
- [ ] WP2-04-TEST-02 Chạy Backend integration test cho migration/persistence/masking/rule validation.
- [ ] WP2-04-TEST-03 Chạy `npm run build` trong `frontend`.
- [ ] WP2-04-TEST-04 Kiểm tra thủ công web với evidence có/rỗng và màn hình hẹp.
- [ ] WP2-04-TEST-05 Chạy `git diff --check` và đọc toàn bộ diff.

**Tài liệu**

- [ ] WP2-04-DOC-01 Cập nhật AI/Backend OpenAPI theo code cuối.
- [ ] WP2-04-DOC-02 Cập nhật README/CHANGELOG chỉ khi maintainer yêu cầu.
- [ ] WP2-04-DOC-03 Ghi mobile/section hóa vào backlog nếu được duyệt làm follow-up.

## Đóng phase

- [ ] AI trả evidence có metadata đầy đủ và không còn summary ghép chuỗi cho diagnosis mới.
- [ ] Backend lưu/reload evidence; dữ liệu cũ và manual diagnosis vẫn đọc được.
- [ ] Backend không lấy thuốc, liều hoặc PHI từ evidence.
- [ ] Rule ID/version sai vẫn bị từ chối như trước.
- [ ] FARMER không thấy evidence khi diagnosis chưa được phép hiển thị.
- [ ] Web hiện knowledge card khi có evidence và ẩn đúng khi rỗng.
- [ ] Web không render `recommendationText` hoặc lặp thuốc/liều/PHI từ evidence.
- [ ] Web dùng đúng format đầu ra, thuật ngữ confidence/similarity và nhãn nguồn theo `05_EXPECTED_OUTPUT_FORMAT.md`.
- [ ] Duyệt, từ chối, ghi nhận phun và khóa thu hoạch theo PHI không hỏng.
- [ ] Không đổi corpus, ingestion, embedding, Knowledge, symptom suggestion, Lab hoặc mobile.
- [ ] Test/build bắt buộc và `git diff --check` đạt.
- [ ] Không có secret, dữ liệu người dùng thật hoặc artifact test thừa trong diff.
- [ ] Cập nhật trạng thái cuối trong README phase.
