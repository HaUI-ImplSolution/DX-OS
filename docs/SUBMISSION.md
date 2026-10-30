# Hồ sơ nộp bài và kịch bản demo

Đối chiếu với mục 10 của đề: repository, release, README, LICENSE và bảng dependency, CHANGELOG, báo cáo kỹ thuật, slide, video.
Việc còn lại của hồ sơ nằm ở [`BACKLOG.md`](./BACKLOG.md); câu trả lời hội đồng ở [`COUNCIL_QA.md`](./COUNCIL_QA.md).

## 1. Hồ sơ theo mục 10 của đề

| Yêu cầu | Nơi có |
|---|---|
| Repository công khai, lịch sử commit | GitHub, nhánh `main` |
| Release và tag có phiên bản | GitHub Releases (`v1.0.0` đến `v1.0.3`); thay đổi theo phiên bản ở [`CHANGELOG.md`](../CHANGELOG.md) |
| `README.md`: kiến trúc 3 tầng, H-P-D-I, tính năng, cài đặt, cấu hình, chạy thử, API, tài khoản demo | [`README.md`](../README.md) |
| `LICENSE` và bảng dependency, license | [`LICENSE`](../LICENSE), [`COPYRIGHT.txt`](../COPYRIGHT.txt), [`sbom/`](../sbom/README.md) |
| `CHANGELOG.md`, issue tracker, `CONTRIBUTING.md` | [`CHANGELOG.md`](../CHANGELOG.md), GitHub Issues, [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Sơ đồ kiến trúc và luồng gọi dịch vụ | [`ARCHITECTURE.md`](../ARCHITECTURE.md) |
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Sườn báo cáo ở [`report/README.md`](./report/README.md) |
| Video demo 3-5 phút | Mục 3, dựa trên kịch bản ở mục 2 |

## 2. Kịch bản demo 10 phút

Chuỗi trình bày: người dùng, Open-Core, quy trình, dữ liệu, trí tuệ, kết quả.
Một lõi, hai ứng dụng: DX-Farm trước, đổi sang DX-Lab bằng một lệnh, rồi DX-Lab.

### Chuẩn bị trước

1. Dựng sạch và nạp dữ liệu cả hai ứng dụng, kết thúc ở Farm:

   ```bash
   ./deploy/start.sh lab && ./deploy/start.sh farm
   ```

   Chạy `./deploy/start.sh lab` một lần trước để corpus tri thức và dữ liệu Lab đã có; lúc demo lệnh đổi sang Lab chỉ mất khoảng một phút.
   Có ảnh lá lúa thật thì dựng Farm không nạp dữ liệu rồi nạp kèm ảnh để chẩn đoán qua AI: `./deploy/switch-app.sh farm` rồi `python3 deploy/demo/seed_demo.py --images DIR`.
2. Mở sẵn các tab:

   | Tab | Địa chỉ | Tài khoản |
   |---|---|---|
   | Portal | http://localhost:9080/portal/ | không cần |
   | Web | http://localhost:9080 | `engineer1`, `admin1` (Farm); `student1`, `tech1`, `lecturer1`, `admin1` (Lab); mỗi vai một cửa sổ ẩn danh |
   | Superset | http://localhost:9081 | `admin1` |
   | Mattermost | http://localhost:8065 | tài khoản trong `deploy/.env` |
   | Sơ đồ | [`ARCHITECTURE.md`](../ARCHITECTURE.md) | |

3. Mở sẵn một terminal ở thư mục gốc repo để gõ lệnh đổi ứng dụng.
4. Mobile của nông dân (`farmer1`) chạy trên điện thoại hoặc emulator; không bắt buộc cho kịch bản này.

### Các bước

| Phút | Việc làm | Nói và chỉ vào |
|---|---|---|
| 0:00-1:00 | Mở sơ đồ 3 tầng, rồi portal | "Tầng 1 là Core headless: Keycloak, APISIX, workflow, dữ liệu. Tầng 2 là H-P-D-I. Tầng 3 là hai ứng dụng chạy trên cùng Core." Portal hiện app đang chạy và trạng thái từng dịch vụ; mở nhanh `/monitor/` bằng `admin1` để chỉ lớp giám sát chạy riêng, không phụ thuộc Backend |
| 1:00-2:00 | **Farm**, web admin: hàng chờ duyệt, duyệt thửa mới, mở timeline | [H] SSO một lần, vai theo realm. [P] workflow dùng chung: mỗi quyết định là một sự kiện chỉ thêm, có người phụ trách; Mattermost nhận thông báo |
| 2:00-3:30 | **Farm**, web kỹ sư: mở báo cáo, chạy chẩn đoán AI, duyệt; khai phun và khóa thu hoạch | [I] CV, độ tin cậy, nguồn RAG; kỹ sư chốt bệnh. Liều và PHI chỉ lấy từ dữ liệu có cấu trúc. Thu hoạch bị chặn `POKA_YOKE_VIETGAP_VIOLATION`; bệnh chưa có rule ở `review_required`, không có liều hay PHI |
| 3:30-4:30 | **Farm**, Superset: dashboard 11 chart, kể cả nhiệt độ và lượng mưa theo ngày; web: trang Tài liệu, hỏi một câu | [D] chỉ đọc view của bản ghi đã duyệt, không có dữ liệu cá nhân. Hỏi đáp trích nguyên văn kèm nguồn và giấy phép |
| 4:30-5:30 | **Đổi ứng dụng**: gõ `./deploy/start.sh lab`, trong lúc chạy chỉ ra `docker compose ps` | "Một biến `DXOS_APP`. Keycloak, PostgreSQL, SeaweedFS, APISIX không khởi động lại; chỉ gateway lấy route của Lab, backend nạp `app-lab`, Superset đăng ký view Lab." Core không đổi. Tải lại web: menu và trang là của Lab |
| 5:30-6:30 | **Lab**, `student1`: mô tả "máy chiếu phòng A2 không lên hình", xem gợi ý, gửi yêu cầu kèm ảnh | [I] luật phân loại giải thích được: loại, ưu tiên, từ khóa khớp, bài tri thức liên quan. Mã `TK-<năm>-<số>`, hạn phản hồi và hạn xử lý theo SLA |
| 6:30-7:30 | **Lab**, `tech1`: hàng đợi, nhận yêu cầu, trả lời công khai, ghi chú nội bộ, hoàn tất (ghi chú và nguyên nhân gốc); `student1` xác nhận 5 sao | [P] cùng workflow với Farm, loại case khác. Hai kỹ thuật viên nhận cùng lúc thì một bên 409. Sinh viên không thấy ghi chú nội bộ. Timeline đủ bước |
| 7:30-8:30 | **Lab**, `lecturer1`: đặt phòng máy bằng ô trên lịch tuần, thử đặt chồng giờ; `tech1` duyệt | Chồng giờ bị từ chối với lý do tiếng Việt; chống trùng ở cơ sở dữ liệu (`EXCLUDE USING gist`) nên hai người đặt cùng lúc vẫn chỉ một lịch hiệu lực |
| 8:30-9:30 | **Lab**, `admin1`: trang Thống kê; mượn thiết bị và trả hỏng; hỏi đáp "quy định sử dụng máy tính trong thư viện" | [D] SLA đạt, thời gian xử lý, tải kỹ thuật viên, mức dùng phòng từ cùng PostgreSQL; view `vw_lab_*` cho BI. Hỏi đáp trích văn bản của HaUI, bấm nguồn mở trang gốc hoặc bản PDF |
| 9:30-10:00 | Tổng kết | Một Core, hai ứng dụng, đổi bằng một biến; kiểm chứng bằng E2E trong CI cho cả hai (Farm 10 bước, Lab 16 bước) |

### Phương án dự phòng

| Sự cố | Xử lý |
|---|---|
| Lệnh đổi ứng dụng chậm hoặc lỗi | Chuẩn bị sẵn hai cửa sổ: kết quả `start.sh lab` đã chạy trước, mở web Lab ở tab thứ hai; nói rõ đây là kết quả của cùng lệnh |
| AI hoặc Ollama chưa sẵn sàng | Portal hiện AI ngừng; Farm chuyển sang chẩn đoán nhập thủ công (`POST .../diagnosis`) và nói rõ đây là đường dự phòng; gợi ý phân loại của Lab vẫn chạy bằng luật |
| Mạng không có | Thời tiết trả snapshot (`isSnapshot=true`); mobile gửi lại khi có mạng |
| Điện thoại không kết nối | Bỏ đoạn mobile, hoặc dùng emulator hoặc video quay sẵn |
| Máy yếu không chạy được Qwen (VPS hiện tại) | Đặt `AI_ANSWER_GENERATION=false`: chatbot web trả tài liệu tham khảo mở sẵn; chẩn đoán giữ CV/rule/evidence và ẩn phần Qwen |
| Superset trống | Chạy lại `python3 deploy/demo/seed_demo.py`; chart "Thửa đang cách ly" và "Tỷ lệ AI đúng/sai" cần rule được duyệt và chẩn đoán AI |
| Token cũ báo 401 sau khi dựng sạch | Đăng xuất rồi đăng nhập lại |

### Bằng chứng đi kèm

- Log workflow `E2E` trên GitHub, hai job `farm` và `lab`.
- Kết quả `./mvnw verify` và `pytest`.
- Kết quả đánh giá truy xuất RAG và thử tải retrieval ở `docs/TESTING.md` mục 3; chạy lại bằng `./ai/evaluate.sh all`.
- Kết quả kiểm gateway, log, audit, màn hình giám sát và cấu hình Cloudflare ở `docs/TESTING.md` mục 8 và 10.
- Video quay thử toàn bộ kịch bản.

## 3. Video 3-5 phút

Quay từ bản chạy thật theo kịch bản ở mục 2, rút gọn: sơ đồ, một chuỗi Farm (duyệt, chẩn đoán, khóa thu hoạch), lệnh đổi ứng dụng, một chuỗi Lab (yêu cầu, xử lý, đặt phòng), thống kê.
Quay thử toàn bộ một lần trước khi quay bản nộp.
