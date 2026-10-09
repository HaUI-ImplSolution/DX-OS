# Bộ trả lời câu hỏi hội đồng

Thời lượng: 10 phút demo và tối đa 5 phút trả lời.

## 1. Open-Core nằm ở đâu, thay ứng dụng có phải đổi Core không?

- Core gồm `core.identity`, `core.workflow`, `core.storage` và gateway APISIX; DX-Farm là package ứng dụng bên trên.
- Đổi ứng dụng thì thêm package ứng dụng và loại yêu cầu mới cho workflow; Core không đổi.
- Chưa có ứng dụng thứ hai chạy thật; bằng chứng là ranh giới package và sơ đồ.
- Dẫn: `development/docs/ARCHITECTURE_OPEN_CORE.md`, `CONTRIBUTING.md` mục "Thêm một quy trình hoặc ứng dụng mới".

## 2. Nguồn dữ liệu hoặc API ngừng thì sao?

- Thời tiết: Open-Meteo không gọi được thì trả điểm tham chiếu gần nhất trong `weather-snapshot.json`, đánh dấu `isSnapshot=true`.
  Snapshot chụp ngày 07/10/2026, chỉ có 5 điểm ĐBSCL.
- AI: LLM không sẵn sàng thì AI dùng nhánh `evidence_fallback`, trả nguyên văn tài liệu; Backend có circuit breaker khi gọi AI.
- Mobile giữ ảnh trong hàng đợi và gửi lại khi dịch vụ phục hồi.
- Portal hiện trạng thái từng dịch vụ.

## 3. Build toàn bộ từ source trên máy mới được không?

- Được: `cp .env.example .env`, điền biến, `docker compose up -d --build`; `deploy/reset.sh` dựng lại từ đầu.
- Workflow `E2E` dựng stack trên runner sạch rồi chạy kịch bản đầu-cuối.
- Bản dựng nặng: image AI khoảng 3 GB, Ollama khoảng 9 GB cùng model; MinIO build từ mã nguồn Go vì image chính thức không còn phát hành.
- Phiên bản Python khóa ở `ai/requirements.lock`; SBOM ở `sbom/`.

## 4. Từ 100 lên 100.000 người dùng thì sao?

Hướng mở rộng:

- Backend không giữ trạng thái phiên, chạy nhiều bản sau gateway.
- Postgres tách đọc/ghi, index và phân trang đã có ở các danh sách quản lý.
- Ảnh nằm ở object storage (MinIO tương thích S3), không phụ thuộc đĩa của backend.
- Gọi AI chạy ngoài transaction; suy luận AI chuyển sang hàng đợi và nhiều worker.
- Keycloak chạy cụm.

Điểm nghẽn hiện có, nói thẳng:

- Superset dùng SQLite cho metadata.
- AI Service là một tiến trình, không có hàng đợi.
- Hạn mức tốc độ tính theo địa chỉ IP ở gateway là số tạm.
- Chưa có thử tải.

## 5. Quy trình đóng góp một module mới?

- `CONTRIBUTING.md`: quy trình nhánh và Pull Request, kiểm tra theo từng phần, quy ước mã, cách thêm quy trình hoặc ứng dụng mới.
- Có mẫu issue và pull request ở `.github/`; CI chạy riêng cho backend, AI, web, mobile, hạ tầng và E2E.

## 6. AI dùng ở đâu, đánh giá, giới hạn?

- CV: YOLOv8n (checkpoint `YOLOv8n-RiceLeafBD` 3.1.0, AGPL-3.0), dữ liệu RiceLeafDiseaseBD v3 (CC BY 4.0), 5 bệnh lá lúa. Trước detector có crop classifier YOLO26s-cls (ONNX) kiểm ảnh là lúa; hai trọng số nằm trong `ai/weights/`.
- RAG: pgvector, embedding `paraphrase-multilingual-MiniLM-L12-v2` (Apache-2.0), 6 tài liệu IRRI và LSU.
- LLM: Qwen2.5-1.5B (Apache-2.0) chỉ diễn đạt dữ kiện đã tra, không sinh số.
- Giới hạn: ngưỡng chuyển cho người xem `0.70` chưa có căn cứ đo trên ảnh ngoài ruộng; crop classifier còn nhận nhầm ngô, cỏ, lúa mì thành lúa, chưa nghiệm thu khả năng chặn mọi ảnh ngoài miền; sâu hại chưa có mô hình; chưa có báo cáo đánh giá độc lập (test set, chỉ số theo lớp).
- Kỹ sư luôn là người chốt bệnh và điều trị; nhãn kỹ sư chốt được lưu có cấu trúc cho dữ liệu huấn luyện về sau.

## 7. Vì sao không có thuốc và liều trong dữ liệu seed?

- Nguyên tắc dự án: số liệu thuốc, liều, PHI phải do chuyên gia duyệt, không tự bịa.
- Rule và thuốc ứng viên nằm ở `data/structured/` với trạng thái chưa được phép seed; có quyết định duyệt thì chạy lại generator và luồng khóa PHI mở ra.
- Khóa PHI đã được kiểm chứng bằng test đơn vị, test tích hợp và E2E (CI dùng một rule thử nghiệm riêng, không phải dữ liệu thật).

## 8. Giấy phép

- Mã tự viết Apache-2.0, SPDX header ở file Java và Python.
- AGPL-3.0: Ultralytics, PyMuPDF, MinIO, checkpoint CV (detector bệnh và crop classifier); Mattermost Team Edition chạy nguyên bản, không đụng `server/enterprise/`.
- Bảng đầy đủ: `LICENSES.md`.
