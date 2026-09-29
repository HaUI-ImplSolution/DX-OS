# Kế hoạch hoàn thiện Web E2E

> **Cập nhật:** 24/09/2026.

## 1. Giả định đã chốt

- Luồng hiện có là baseline: FARMER báo bệnh, ENGINEER chạy diagnosis, ENGINEER/ADMIN duyệt và ghi nhận xử lý.
- Backend tiếp tục là nguồn sự thật cho `TreatmentRule`, liều lượng, PHI và trạng thái duyệt.
- Seed 38 bệnh, 4 thuốc và 8 rule; RAG ingest; role authorization; kiểm tra khoảng liều; build và contract self-check hiện có không được regression.

## 2. Hiện trạng

- Internal API trả được nhiều rule hoặc không có rule theo disease code.
- Luồng CV → Backend facts → semantic RAG → Ollama đã chạy và lưu diagnosis để duyệt.
- Carbon/AWD + IoT simulator, GraphRAG/multi-agent/workflow phức tạp và IoT auto-confirm chưa được triển khai.

## 3. Quyết định phạm vi

Kế hoạch này chỉ theo dõi các công việc còn lại liên quan đến behavior diagnosis, metadata diagnosis và cấu hình secret. Các phần chưa đạt được được ghi ở mục 5 và không có checklist trong kế hoạch này.

## 4. Các công việc cần thực hiện

- [ ] Ảnh có confidence dưới ngưỡng được lưu diagnosis `review_required`; Backend không chọn `TreatmentRule` và frontend hiển thị lý do cần kỹ sư xem xét.
- [ ] Diagnosis có nhiều rule hoặc không có rule từ Internal API chuyển `review_required`; hệ thống không chọn rule theo thứ tự ID.
- [ ] Mỗi diagnosis từ AI lưu được `request_id`, CV model/version, rule/version, nguồn RAG và thời điểm xử lý; Backend–AI contract self-check không regression.
- [ ] Diagnosis `review_required` có reason code cho confidence thấp, nhiều rule hoặc không có rule; frontend hiển thị đúng reason code.
- [ ] Backend, AI và Mattermost đọc secret từ cấu hình triển khai; source, log và response không chứa secret/token thật.

## 5. Các phần chưa đạt được

- `DiseaseManagementPolicy` và seed data.
- Deterministic selector theo ngữ cảnh nông học.
- Carbon/AWD Estimator và IoT simulator.
- GraphRAG, multi-agent và workflow phức tạp.
- Tự động xác nhận đã phun từ IoT.

## 6. Bất biến

- Không thay đổi quyền FARMER/ENGINEER/ADMIN, seed hiện có, RAG ingest hiện có, kiểm tra khoảng liều hoặc khóa PHI khi chưa có quyết định tương ứng.
- Không để AI tự sinh hóa chất, liều lượng hoặc PHI ngoài `TreatmentRule`.
- Các phần ở mục 5 không là điều kiện hoàn tất Web E2E.

## 7. Definition of done

Toàn bộ hạng mục ở mục 4 hoàn tất; baseline ở mục 2 không regression. Các phần ở mục 5 không chặn hoàn tất Web E2E.
