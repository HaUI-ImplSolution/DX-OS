# Hồ sơ nộp bài, kịch bản demo và dàn ý báo cáo

Đối chiếu với mục 10 của đề: repository, release, README, LICENSE và bảng dependency, CHANGELOG, báo cáo kỹ thuật, slide, video.
Việc còn lại của hồ sơ nằm ở [`BACKLOG.md`](./BACKLOG.md); câu trả lời hội đồng ở [`COUNCIL_QA.md`](./COUNCIL_QA.md).

## 1. Hồ sơ theo mục 10 của đề

| Yêu cầu | Nơi có |
|---|---|
| Repository công khai, lịch sử commit | GitHub, nhánh `main` |
| Release và tag có phiên bản | GitHub Releases |
| `README.md`: kiến trúc 3 tầng, H-P-D-I, tính năng, cài đặt, cấu hình, chạy thử, API, tài khoản demo | [`README.md`](../README.md) |
| `LICENSE` và bảng dependency, license | [`LICENSE`](../LICENSE), [`LICENSES.md`](../LICENSES.md), [`sbom/`](../sbom/README.md) |
| `CHANGELOG.md`, issue tracker, `CONTRIBUTING.md` | [`CHANGELOG.md`](../CHANGELOG.md), GitHub Issues, [`CONTRIBUTING.md`](../CONTRIBUTING.md) |
| Sơ đồ kiến trúc và luồng gọi dịch vụ | [`ARCHITECTURE.md`](../ARCHITECTURE.md) |
| Báo cáo kỹ thuật 8-10 trang, slide tối đa 12 | Dàn ý ở mục 3 |
| Video demo 3-5 phút | Kịch bản ở mục 2 |

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
| 0:00-1:00 | Mở sơ đồ 3 tầng, rồi portal | "Tầng 1 là Core headless: Keycloak, APISIX, workflow, dữ liệu. Tầng 2 là H-P-D-I. Tầng 3 là hai ứng dụng chạy trên cùng Core." Portal hiện app đang chạy và trạng thái từng dịch vụ |
| 1:00-2:00 | **Farm**, web admin: hàng chờ duyệt, duyệt thửa mới, mở timeline | [H] SSO một lần, vai theo realm. [P] workflow dùng chung: mỗi quyết định là một sự kiện chỉ thêm, có người phụ trách; Mattermost nhận thông báo |
| 2:00-3:30 | **Farm**, web kỹ sư: mở báo cáo, chạy chẩn đoán AI, duyệt; khai phun và khóa thu hoạch | [I] CV, độ tin cậy, nguồn RAG; kỹ sư chốt bệnh. Liều và PHI chỉ lấy từ dữ liệu có cấu trúc. Thu hoạch bị chặn `POKA_YOKE_VIETGAP_VIOLATION`; bệnh chưa có rule ở `review_required`, không có liều hay PHI |
| 3:30-4:30 | **Farm**, Superset: dashboard 9 chart; web: trang Tài liệu, hỏi một câu | [D] chỉ đọc view của bản ghi đã duyệt, không có dữ liệu cá nhân. Hỏi đáp trích nguyên văn kèm nguồn và giấy phép |
| 4:30-5:30 | **Đổi ứng dụng**: gõ `./deploy/start.sh lab`, trong lúc chạy chỉ ra `docker compose ps` | "Một biến `DXOS_APP`. Keycloak, PostgreSQL, MinIO, APISIX không khởi động lại; chỉ gateway lấy route của Lab, backend nạp `app-lab`, Superset đăng ký view Lab." Core không đổi. Tải lại web: menu và trang là của Lab |
| 5:30-6:30 | **Lab**, `student1`: mô tả "máy chiếu phòng A2 không lên hình", xem gợi ý, gửi yêu cầu kèm ảnh | [I] luật phân loại giải thích được: loại, ưu tiên, từ khóa khớp, bài tri thức liên quan. Mã `TK-<năm>-<số>`, hạn phản hồi và hạn xử lý theo SLA |
| 6:30-7:30 | **Lab**, `tech1`: hàng đợi, nhận yêu cầu, trả lời công khai, ghi chú nội bộ, hoàn tất (ghi chú và nguyên nhân gốc); `student1` xác nhận 5 sao | [P] cùng workflow với Farm, loại case khác. Hai kỹ thuật viên nhận cùng lúc thì một bên 409. Sinh viên không thấy ghi chú nội bộ. Timeline đủ bước |
| 7:30-8:30 | **Lab**, `lecturer1`: đặt phòng máy bằng ô trên lịch tuần, thử đặt chồng giờ; `tech1` duyệt | Chồng giờ bị từ chối với lý do tiếng Việt; chống trùng ở cơ sở dữ liệu (`EXCLUDE USING gist`) nên hai người đặt cùng lúc vẫn chỉ một lịch hiệu lực |
| 8:30-9:30 | **Lab**, `admin1`: trang Thống kê; mượn thiết bị và trả hỏng; hỏi đáp "quy định khi sử dụng phòng máy" | [D] SLA đạt, thời gian xử lý, tải kỹ thuật viên, mức dùng phòng từ cùng PostgreSQL; view `vw_lab_*` cho BI. Hỏi đáp có nguồn và giấy phép CC BY 4.0 |
| 9:30-10:00 | Tổng kết | Một Core, hai ứng dụng, đổi bằng một biến; kiểm chứng bằng E2E trong CI cho cả hai (Farm 10 bước, Lab 16 bước) |

### Phương án dự phòng

| Sự cố | Xử lý |
|---|---|
| Lệnh đổi ứng dụng chậm hoặc lỗi | Chuẩn bị sẵn hai cửa sổ: kết quả `start.sh lab` đã chạy trước, mở web Lab ở tab thứ hai; nói rõ đây là kết quả của cùng lệnh |
| AI hoặc Ollama chưa sẵn sàng | Portal hiện AI ngừng; Farm chuyển sang chẩn đoán nhập thủ công (`POST .../diagnosis`) và nói rõ đây là đường dự phòng; gợi ý phân loại của Lab vẫn chạy bằng luật |
| Mạng không có | Thời tiết trả snapshot (`isSnapshot=true`); mobile gửi lại khi có mạng |
| Điện thoại không kết nối | Bỏ đoạn mobile, hoặc dùng emulator hoặc video quay sẵn |
| Máy yếu không chạy Ollama | Hỏi đáp dùng nhánh `evidence_fallback` của AI |
| Superset trống | Chạy lại `python3 deploy/demo/seed_demo.py`; chart "Thửa đang cách ly" và "Tỷ lệ AI đúng/sai" cần rule được duyệt và chẩn đoán AI |
| Token cũ báo 401 sau khi dựng sạch | Đăng xuất rồi đăng nhập lại |

### Bằng chứng đi kèm

- Log workflow `E2E` trên GitHub, hai job `farm` và `lab`.
- Kết quả `./mvnw verify` và `pytest`.
- Video quay thử toàn bộ kịch bản.

## 3. Dàn ý báo cáo kỹ thuật (8-10 trang) và slide (tối đa 12)

Mỗi mục nêu nội dung cần viết và nơi lấy số liệu, bằng chứng.

| Mục báo cáo | Trang | Nội dung | Nguồn |
|---|---|---|---|
| 1. Bài toán và phạm vi | 1 | DX-OS Open-Core, hai ứng dụng minh họa (DX-Farm, DX-Lab), người dùng, luồng xương sống của từng ứng dụng | `README.md` |
| 2. Kiến trúc Open-Core 3 tầng | 1-2 | Sơ đồ 3 tầng, module và ranh giới biên dịch, những gì ứng dụng khai báo, cách chọn ứng dụng lúc chạy | `ARCHITECTURE.md` mục 1 đến 4 |
| 3. Dịch vụ dùng chung của Core | 1 | Identity/SSO, API Gateway, dữ liệu, workflow, thông báo, cổng AI: ai cung cấp, ứng dụng khai báo gì | `ARCHITECTURE.md` mục 3, `sso/README.md`, `deploy/apisix/` |
| 4. H-P-D-I qua hai ứng dụng | 2 | Farm: vai và phạm vi hộ, duyệt và khóa PHI, view BI, CV và RAG. Lab: vai, ba loại case, SLA, view BI, luật phân loại và RAG | `backend/app-farm/README.md`, `backend/app-lab/README.md` |
| 5. Trí tuệ nhân tạo | 1 | CV, RAG, LangGraph, LLM, luật phân loại; ngưỡng, giới hạn, vai trò kỹ sư; dữ liệu và nguồn gốc | `ai/README.md`, `COUNCIL_QA.md` câu 6 |
| 6. Kiểm thử và kiểm chứng | 1 | Test backend, E2E hai ứng dụng trên stack sạch (Farm 10 bước, Lab 16 bước), bằng chứng Core không đổi khi thêm ứng dụng, ứng dụng mẫu rỗng | log workflow `E2E`, `deploy/demo/README.md`, `TESTING.md` |
| 7. Mã nguồn mở và giấy phép | 1 | Bảng license, SBOM, lockfile, SPDX, CONTRIBUTING và hướng dẫn thêm ứng dụng, issue tracker | `LICENSES.md`, `sbom/`, `CONTRIBUTING.md` |
| 8. Giới hạn và hướng phát triển | 0.5 | Rule thuốc được duyệt mới có hai bệnh, điểm nghẽn mở rộng, mục chưa làm của Lab (mobile, dashboard Superset, ticket tương tự), giấy phép của `ai/` | `COUNCIL_QA.md`, `BACKLOG.md` |

### Slide (12)

1. Bài toán và người dùng: hai ứng dụng trên một nền tảng
2. Sơ đồ 3 tầng và ranh giới Core / ứng dụng
3. Dịch vụ dùng chung: Identity/SSO, Gateway, Data, Workflow
4. H: danh tính và phân quyền theo vai
5. P: workflow cấu hình được và vòng đời (Farm, Lab)
6. Poka-yoke: khóa PHI, GPS, SLA, chống trùng giờ đặt phòng
7. I: CV, RAG, luật phân loại và vai trò con người
8. D: dữ liệu, BI, thời tiết
9. Demo: chuỗi Farm, đổi ứng dụng, chuỗi Lab
10. Kiểm thử: E2E hai ứng dụng và test lõi
11. Mã nguồn mở: license, SBOM, đóng góp, thêm ứng dụng mới
12. Giới hạn trung thực và hướng phát triển

### Video 3-5 phút

Quay từ bản chạy thật theo kịch bản ở mục 2, rút gọn: sơ đồ, một chuỗi Farm (duyệt, chẩn đoán, khóa thu hoạch), lệnh đổi ứng dụng, một chuỗi Lab (yêu cầu, xử lý, đặt phòng), thống kê.
Quay thử toàn bộ một lần trước khi quay bản nộp.
