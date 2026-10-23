# Bộ trả lời câu hỏi hội đồng

Thời lượng: 10 phút demo và tối đa 5 phút trả lời.
Mười câu đầu theo đúng thứ tự câu hỏi định hướng ở mục 13 của đề; hai câu cuối là câu hỏi phụ hay gặp.
Sơ đồ và ranh giới ở [`ARCHITECTURE.md`](../ARCHITECTURE.md).

## 1. Open-Core nằm ở đâu? Thành phần nào chạy headless và ứng dụng nào dùng dịch vụ đó?

- Tầng 1 gồm `dx-core` (kèm `dx-core-api`) của Backend, Keycloak (SSO), gateway APISIX, PostgreSQL và MinIO.
  Không thành phần nào có giao diện cho người dùng cuối; giao diện nằm ở `frontend/` và `mobile/` (tầng 2 và 3).
- Hai ứng dụng dùng chung Tầng 1: **DX-Farm** (`app-farm`) và **DX-Lab** (`app-lab`), mỗi lần chạy một ứng dụng chọn bằng `DXOS_APP`.
- Ranh giới là ràng buộc lúc biên dịch: ứng dụng chỉ có `dx-core-api` trên classpath.
- Dẫn: `ARCHITECTURE.md` mục 1 và 2; `backend/README.md`.

## 2. Vì sao chọn kiến trúc và stack này? Thành phần nào là nguồn mở, giấy phép gì?

- Chọn thành phần nguồn mở có sẵn cho Tầng 1 thay vì tự viết lại: Keycloak (OIDC), APISIX (gateway), PostgreSQL cùng pgvector, MinIO (S3), Superset (BI).
  Phần tự viết là `dx-core` (workflow, workspace, thông báo, cổng AI) và các ứng dụng.
- Backend Java và Spring Boot vì ranh giới Core/ứng dụng cần kiểm tra lúc biên dịch bằng module Maven; AI viết bằng Python vì hệ sinh thái CV và RAG.
- Mã tự viết Apache-2.0.
  Dependency chính: Spring, Keycloak, APISIX, PostgreSQL, Superset, Ollama (Apache-2.0 hoặc MIT); PyTorch/Torchvision (BSD-3-Clause); MinIO, Mattermost và crop classifier là AGPL-3.0; phần fine-tune model bệnh của nhóm theo Apache-2.0.
- Dẫn: `LICENSES.md` (bảng đầy đủ, nguồn và vai trò), `sbom/` (CycloneDX).

## 3. Nếu thay đổi ứng dụng nghiệp vụ, Open-Core có phải thay đổi theo không? Vì sao?

- Không: Core không biết ứng dụng nào đang chạy; vai, loại quy trình, thông báo, view BI, nguồn tri thức do ứng dụng khai báo qua interface.
- Bằng chứng:
  - DX-Lab được thêm vào sau DX-Farm mà không sửa `dx-core`, `dx-core-api` hay `app-farm`: Pull Request #93 (merge `e759b3b`) không có dòng nào đổi trong ba module đó (`git diff --stat e759b3b^1 e759b3b -- backend/dx-core backend/dx-core-api backend/app-farm` trống).
  - Một ứng dụng mẫu rỗng (chỉ có `AppModule`) khởi động được trên Core nguyên bản và trả `GET /api/v1/system/status` đúng id của nó.
  - Chuyển ứng dụng là đổi một biến rồi chạy `deploy/start.sh <app>`; web, gateway, BI và tri thức đổi theo.
- Những gì không thuộc Core vẫn có thể đổi theo ứng dụng: module giao diện, route giới hạn tốc độ ở gateway, mục BI, dữ liệu demo.
- Dẫn: `CONTRIBUTING.md` mục "Thêm một ứng dụng mới", `ARCHITECTURE.md` mục 3.

## 4. Identity/SSO, API Gateway, Data và Workflow được cung cấp thành dịch vụ dùng chung thế nào?

| Dịch vụ | Cách cung cấp | Ứng dụng dùng |
|---|---|---|
| Identity/SSO | Keycloak realm `dxos`; core tạo vai do ứng dụng khai báo và tài khoản workspace qua Admin API; ghi sự kiện đăng nhập và khóa tạm khi sai mật khẩu nhiều lần | Farm: FARMER, ENGINEER, ADMIN; Lab: STUDENT, LECTURER, TECHNICIAN, ADMIN |
| API Gateway | APISIX là cửa duy nhất: kiểm JWT, giới hạn tốc độ, route theo app, log truy cập JSON kèm `X-Request-Id`; backend kiểm JWT lần hai | Giới hạn upload ảnh và chẩn đoán (Farm), tạo ticket và đặt phòng (Lab) |
| Dữ liệu cấu trúc | PostgreSQL, Flyway: `db/migration/core` dùng chung và `db/migration/<app>` | Bảng riêng của từng ứng dụng, dùng chung `workspaces`, `notifications` |
| Dữ liệu phi cấu trúc | MinIO riêng tư, client nhận link có hạn; tệp qua `FileProcessor` của ứng dụng | Ảnh lá lúa (Farm), tệp đính kèm ticket (Lab) |
| Workflow | `core.workflow`: loại case cấu hình được, lịch sử `workflow_event` chỉ thêm, người phụ trách, hộp duyệt | Farm 5 loại duyệt; Lab `TICKET`, `DEVICE_LOAN`, `ROOM_BOOKING` |

- Dẫn: `ARCHITECTURE.md` mục 2, 3 và 4; `sso/README.md`; `deploy/apisix/`.

## 5. Điểm nào thể hiện H-P-D-I thay vì chỉ là một dashboard CRUD?

| | DX-Farm | DX-Lab |
|---|---|---|
| **H** | Nông dân chỉ thấy hộ mình, chỉ vào bằng mobile; kỹ sư và admin theo vai | Sinh viên chỉ thấy yêu cầu của mình; ghi chú nội bộ ẩn với người yêu cầu |
| **P** | Duyệt thửa, vụ, chẩn đoán, khai phun; khóa thu hoạch theo PHI (poka-yoke chặn ở backend) | Ticket có SLA và lịch cảnh báo, mượn thiết bị có duyệt theo vai, đặt phòng chống trùng giờ ở cơ sở dữ liệu |
| **D** | View đã duyệt cho Superset, thời tiết có snapshot dự phòng | View `vw_lab_*`, thống kê SLA, tải kỹ thuật viên, mức dùng phòng |
| **I** | CV, RAG, LangGraph; kỹ sư chốt bệnh và điều trị | Luật phân loại giải thích được (từ khóa và lý do), RAG quy chế có trích nguồn |

- Trạng thái và quyết định là sự kiện chỉ thêm, có người phụ trách; hai người xử lý cùng một việc thì một bên nhận 409.
- Dẫn: `SUBMISSION.md` mục 2, `backend/app-farm/README.md`, `backend/app-lab/README.md`.

## 6. AI/ML được dùng ở đâu? Dữ liệu đầu vào, mô hình, đánh giá và giới hạn là gì?

- Farm, CV: EfficientNetV2-M (checkpoint `DX-Farm-EfficientNetV2-M` 4.0.0, Apache-2.0 cho phần fine-tune của nhóm), dữ liệu RiceLeafDiseaseBD v3 (CC BY 4.0), Healthy và 5 bệnh lá lúa.
  Trước model bệnh có crop classifier YOLO26s-cls (ONNX) kiểm ảnh là lúa; trọng số nằm trong `ai/weights/`, model bệnh được ghép vào image khi build.
- RAG: pgvector, embedding `paraphrase-multilingual-MiniLM-L12-v2` (Apache-2.0); 6 tài liệu IRRI và LSU cho Farm, 12 bài trích từ nội quy, quy định và hướng dẫn chính thức của HaUI cho Lab, mỗi corpus một namespace.
- LLM: Qwen2.5-1.5B (Apache-2.0) chỉ diễn đạt câu trả lời hỏi đáp từ tài liệu đã tra, không sinh số; chẩn đoán không dùng LLM.
- Lab, luật phân loại: từ khóa theo loại và luật nâng ưu tiên (`classifier-rules.json`), trả kèm từ khóa khớp và lý do; người dùng quyết định.
- Giới hạn:
  - Ngưỡng chuyển cho người xem `0.70` chưa có căn cứ đo trên ảnh ngoài ruộng.
  - Crop classifier nhận 6/15 ảnh ngô và 15/15 ảnh mía là lúa, và chặn 8/50 ảnh lúa nền ruộng.
  - Sâu hại chưa có mô hình.
  - Accuracy model bệnh 89,43%, macro-F1 88,13% trên 1.466 ảnh theo báo cáo người huấn luyện, chưa đo toàn pipeline hoặc riêng ngưỡng `0.70`.
  - Chưa có đánh giá trên ảnh ruộng thật có nhãn kỹ sư.
  - Luật của Lab là luật tay, chưa đo độ chính xác trên dữ liệu thật.
- Kỹ sư luôn là người chốt bệnh và điều trị; nhãn kỹ sư chốt được lưu có cấu trúc cho dữ liệu huấn luyện về sau.
- Dẫn: `ai/README.md`, `ai/app/cv/README.md`, `AI_EVALUATION.md`, `BACKLOG.md` mục "Giới hạn của AI và dữ liệu".

## 7. Nếu một nguồn dữ liệu hoặc API ngừng hoạt động, hệ thống xử lý thế nào?

- Thời tiết: Open-Meteo không gọi được thì trả điểm tham chiếu gần nhất trong `weather-snapshot.json`, đánh dấu `isSnapshot=true`.
  Snapshot chụp ngày 07/10/2026, chỉ có 5 điểm ĐBSCL.
- Thời tiết cho Superset: mỗi giờ Backend ghi số đo thật của từng vị trí thửa đã duyệt vào `weather_observations`; snapshot dự phòng không được ghi.
- AI: LLM không sẵn sàng thì hỏi đáp trả nguyên văn tài liệu; Backend có circuit breaker khi gọi AI.
- Lab: gợi ý phân loại vẫn trả kết quả của luật khi AI không gọi được (`knowledgeAvailable=false`); ticket và đặt phòng không phụ thuộc AI.
- Chat: thông báo gửi bất đồng bộ, Mattermost lỗi hoặc chưa có webhook thì ứng dụng vẫn chạy.
- Mobile giữ ảnh trong hàng đợi và gửi lại khi dịch vụ phục hồi.
- Portal hiện trạng thái từng dịch vụ; `GET /api/v1/system/status` là công khai.

## 8. Đội có build toàn bộ hệ thống từ source trên một máy mới được không?

- Được: `cp deploy/.env.example deploy/.env`, điền biến, `./deploy/start.sh farm` (hoặc `lab`); lệnh dựng stack, chờ đủ dịch vụ và nạp dữ liệu demo.
  `deploy/reset.sh` dựng lại từ đầu.
- Workflow `E2E` dựng stack trên runner sạch và chạy kịch bản đầu-cuối cho cả hai ứng dụng.
- Bản dựng nặng lần đầu: image AI khoảng 3 GB, Ollama khoảng 9 GB cùng model; MinIO build từ mã nguồn Go vì image chính thức không còn phát hành.
  Máy đã có image dựng sạch trong khoảng hai phút.
- Phiên bản Python khóa ở `ai/requirements.lock`; SBOM ở `sbom/`; Maven wrapper và npm lockfile khóa phần còn lại.
- Dẫn: `README.md` mục "Cài đặt và chạy", `deploy/demo/README.md`, `.github/workflows/e2e.yml`.

## 9. Từ 100 lên 100.000 người dùng thì kiến trúc thay đổi thế nào?

Hướng mở rộng:

- Backend không giữ trạng thái phiên, chạy nhiều bản sau gateway; ghi đồng thời an toàn nhờ khóa phiên bản và ràng buộc ở cơ sở dữ liệu (đặt phòng dùng `EXCLUDE USING gist`).
- Postgres tách đọc và ghi, index và phân trang đã có ở các danh sách quản lý.
- Ảnh và tệp nằm ở object storage tương thích S3, không phụ thuộc đĩa của backend.
- Gọi AI chạy ngoài transaction; suy luận AI chuyển sang hàng đợi và nhiều worker.
- Keycloak chạy cụm.

Điểm nghẽn hiện có, nói thẳng:

- Superset dùng SQLite cho metadata.
- AI Service là một tiến trình, không có hàng đợi.
- Lịch chạy nền (kiểm SLA, nhắc mượn trả, nhắc thu hoạch, nhắc xác nhận khai phun, thu thập thời tiết) giữ khóa advisory của PostgreSQL nên hai bản backend không chạy cùng một việc cùng lúc.
  Thông báo trong app có khóa chống trùng; tin chat nhắc có thể lặp nếu hai bản chạy lệch giờ.
- Hạn mức tốc độ tính theo địa chỉ IP ở gateway là số tạm.
- Chưa có thử tải.

## 10. Nếu cộng đồng muốn đóng góp một module mới, quy trình contribution là gì?

- `CONTRIBUTING.md`: quy trình nhánh và Pull Request, kiểm tra theo từng phần, quy ước mã.
- Có hướng dẫn từng bước thêm ứng dụng mới (module, `AppModule`, nạp có điều kiện, vai, migration, interface cần cài, gateway, BI, tri thức, giao diện), lấy `app-lab` làm mẫu.
  Hướng dẫn đã được kiểm bằng một ứng dụng mẫu rỗng: làm theo thì ứng dụng nạp được khi đặt `DXOS_APP`, không sửa core.
- Có mẫu issue và pull request ở `.github/`; CI chạy riêng cho backend, AI, web, mobile, hạ tầng và E2E hai ứng dụng.

## Câu hỏi phụ

### A. Vì sao có ba rule thuốc trong dữ liệu seed?

- Nguyên tắc dự án: số liệu thuốc, liều, PHI phải do chuyên gia duyệt, không tự bịa.
- Rule đạo ôn và khô vằn với `NEWTEC_300SC` đã được chuyên gia dự án xác nhận ngày 2026-10-05 nên được seed.
- Rule đốm nâu (Help 400SC) được mở với trạng thái `EXPERT_APPROVED` theo yêu cầu chủ dự án ngày 2026-10-20; `seed_authorization` ghi căn cứ và người xác nhận ở vai trò chủ dự án, chưa kèm văn bản chuyên môn riêng.
- Khóa PHI đã được kiểm chứng bằng test đơn vị, test tích hợp và E2E (CI thêm một rule thử nghiệm riêng, không phải dữ liệu thật).

### B. Giấy phép

- Mã tự viết Apache-2.0, SPDX header ở file Java và Python.
- AGPL-3.0: MinIO, crop classifier và mã nguồn `ai/`; Mattermost Team Edition chạy nguyên bản, không đụng `server/enterprise/`. Phần fine-tune checkpoint bệnh do nhóm phát hành theo Apache-2.0, giữ điều khoản pretrained Torchvision.
- Thư mục `ai/` giữ AGPL-3.0 (`ai/LICENSE`); phần còn lại giữ Apache-2.0.
  AI Service là tiến trình riêng giao tiếp với Backend qua HTTP, nên Backend không là tác phẩm phái sinh của `ai/`.
- Bảng đầy đủ: `LICENSES.md`.
