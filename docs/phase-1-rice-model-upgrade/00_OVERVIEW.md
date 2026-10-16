# Phase 1: Nâng cấp model nhận diện bệnh lúa

| Mục | Giá trị |
|---|---|
| Trạng thái | Đã duyệt kế hoạch, chưa triển khai |
| Mức độ | Quan trọng |
| Nhóm ưu tiên | A |
| Ứng dụng và module chính | AI/CV của Farm |
| Ước lượng | 1–2 ngày triển khai và kiểm tra, sau khi duyệt |
| Người phụ trách | Nhóm AI |
| Bắt đầu, dự kiến xong | 16/10/2026; chốt đóng phase sau kiểm tra ứng dụng |
| Phụ thuộc | Checkpoint và metadata DX-Farm EfficientNetV2-M đã được cung cấp |

## 1. Tham chiếu

| Nguồn yêu cầu | Nội dung | Mức độ | Phạm vi trong phase này |
|---|---|---|---|
| Yêu cầu ngày 16/10/2026 | Làm lại từ origin, chỉ nâng cấp model và tuân thủ `_template` | Quan trọng | Toàn bộ |
| [AI/CV](../../ai/app/cv/README.md) | Loader, class contract, quality/crop gate, ngưỡng workflow | Quan trọng | Chỉ phần model bệnh |
| [Kiến trúc gốc](../../ARCHITECTURE.md) | AI tư vấn, Backend quản lý dữ liệu và phê duyệt | Quan trọng | Giữ nguyên ranh giới |
| [Đánh giá AI](../AI_EVALUATION.md) | Hạn chế của detector hiện tại | Quan trọng | Thay model, không mở đợt đánh giá mới |

## 2. Lý do

- Detector hiện tại nhận diện năm bệnh và dùng Healthy như fallback khi không có detection.
- Checkpoint mới đã được người huấn luyện chấm trên tập test của dự án, có lớp Healthy riêng.
- PR trước mở rộng sang deploy, BI, SSO và công cụ phụ; lần này dựa trên `origin/Khuong/main` và giới hạn vào model bệnh.

## 3. Tổng quan sửa đổi

Chốt mapping và metadata của checkpoint sáu lớp.
Thay phần nạp và suy luận ngay trong cấu trúc CV hiện có.
Đóng gói trọng số cùng mã nguồn, ghép vào image AI khi build.
Giữ payload trả Backend, quality/crop gate và ngưỡng workflow 0,70.
Chỉ cập nhật kiểm tra, cấu hình và thông tin tài liệu trực tiếp phụ thuộc model.

| Module hoặc lớp | Có thay đổi | Ghi chú ngắn |
|---|---|---|
| `dx-core-api` | Không | Giữ interface và DTO |
| `dx-core` | Không | Giữ cài đặt dùng chung |
| `app-farm` | Không | Giữ API, workflow, treatment rule và phê duyệt |
| `app-lab` | Không | Giữ toàn bộ nghiệp vụ |
| `dx-host` | Không | Giữ cách chọn app và khởi tạo dữ liệu |
| Frontend (web) | Không | Đọc kết quả theo payload hiện có |
| Mobile | Không | Giữ upload, offline queue và đăng nhập |
| AI | Có | Loader, metadata và suy luận model bệnh |
| Data / Seed / Migration | Một phần | Chỉ mapping class và kiểm tra mapping; không đổi bệnh, thuốc, seed hoặc migration |
| Infra / Deploy / CI | Một phần | Chỉ đóng gói, đường dẫn và checksum trọng số bệnh trong AI/CI |
| Docs | Một phần | Chỉ các chỗ mô tả model, dependency và giấy phép tương ứng |

## 4. Phạm vi

- Trong phạm vi: DX-Farm EfficientNetV2-M, sáu lớp, recipe tiền xử lý, mapping, đóng gói offline và các phụ thuộc trực tiếp liệt kê ở `01_PLAN.md`.
- Ngoài phạm vi: Backend, web, mobile, SSO, BI, Mattermost, thời tiết, RAG/LLM, crop classifier và thuật toán kiểm chất lượng ảnh.
- Giữ nguyên `deploy/start.sh`, `deploy/switch-app.sh`, `deploy/reset.sh` và các bước chạy của README gốc.
- Không đưa lại `setup-env.py`, provider framework, model manifest riêng hoặc endpoint capabilities từ PR cũ.
- Giả định: dùng đúng checkpoint đã cung cấp; phần trọng số trong nhánh cũ chỉ là nguồn artifact, không lấy lại các thay đổi code của nhánh đó.
- Ranh giới Core/App: không thêm hoặc đổi interface; AI tiếp tục trả mã bệnh canonical để Backend tra rule và quản lý duyệt.

## 5. Ưu điểm và hạn chế

- Tận dụng loader, `model_profile.py`, `class_contract.json` và `PlantDiseaseService` đang có.
- Có Healthy được huấn luyện và giữ năm mã bệnh hiện tại.
- Accuracy 89,43% và macro-F1 88,13% là số liệu người huấn luyện cung cấp trên 1.466 ảnh, không phải kết quả đo mới của phase.
- Chưa có đánh giá độc lập trên ảnh ruộng thật hoặc số liệu toàn pipeline tại ngưỡng 0,70.
- Đổi detector thành classifier và cách đóng gói trọng số được phân tích ở [04_TRADEOFFS_RISKS.md](./04_TRADEOFFS_RISKS.md).

## 6. Kết quả đầu ra mong đợi (Definition of Done)

- Farm nhận ảnh qua luồng hiện tại, trả đúng mã bệnh, confidence và tên/phiên bản model mới; kỹ sư vẫn duyệt kết luận.
- Mapping đúng thứ tự Healthy, Blast, Brown Spot, Leaf Smut, Rice Tungro, Sheath Blight.
- `confidence < 0.70` bị chặn, kể cả 0,6999; không làm tròn điểm trước khi so ngưỡng.
- Input hỏng, output không hợp lệ hoặc thiếu trọng số không được chuyển thành kết luận Healthy.
- Không đổi endpoint, DTO chẩn đoán, dữ liệu nghiệp vụ, thuốc, liều, PHI hoặc luật phê duyệt.
- Trọng số có trong Git, không có file vượt giới hạn 100 MiB; build/restart không tải model bệnh từ mạng.
- Giữ lệnh và bước cấu hình của main; không thêm yêu cầu tự cài công cụ để ghép model trên host.
- Farm và Lab giữ khả năng khởi động/chuyển app theo tài liệu gốc; core không đổi.
- Diff chỉ có những file thuộc danh sách ở `01_PLAN.md`; mọi phát sinh phải quay lại duyệt phạm vi.
- Bằng chứng mới ghi vào `03_TEST_PLAN.md` khi thực sự chạy; không lấy kết quả PR cũ làm kết quả của lần tích hợp này.

## 7. Phụ thuộc và thứ tự

- WP1-01 chốt class contract và checkpoint trước WP1-02.
- WP1-02 và WP1-03 hoàn tất trước kiểm tra, tài liệu và đóng phase ở WP1-04.
- Chưa triển khai code trước khi kế hoạch được người dùng duyệt.

## 8. Ảnh hưởng đến thi

- Liên quan chức năng chẩn đoán ảnh và sử dụng dữ liệu bệnh có cấu trúc của Farm.
- Không mở rộng hạng mục demo hoặc thay đổi kiến trúc để lấy thêm tính năng.

## 9. Câu hỏi mở và quyết định cần chốt

| # | Câu hỏi | Lựa chọn | Hạn chốt | Kết luận |
|---|---|---|---|---|
| Q1 | Phạm vi và cách đóng gói ở `01_PLAN.md` đã đủ hẹp để triển khai? | Duyệt / điều chỉnh kế hoạch | Trước code | Người dùng đã duyệt ngày 16/10/2026 |

## 10. Tài liệu cần cập nhật khi xong

- [ ] Các đoạn model trong `ai/README.md`, `ai/app/cv/README.md` và tài liệu đánh giá hiện có khớp model mới.
- [ ] `LICENSES.md`, `NOTICE`, `sbom/ai.cdx.json` khớp dependency/trọng số thực tế.
- [ ] README gốc chỉ đổi thông tin model/giấy phép nếu cần; giữ nguyên hướng dẫn khởi động.
- [ ] CHANGELOG chỉ bổ sung kết quả cuối; không xóa log cũ.
- [ ] BACKLOG chỉ cập nhật đúng việc liên quan model; giữ hạn chế chưa được kiểm chứng.
