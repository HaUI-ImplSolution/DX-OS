# Test plan — Kiểm ảnh lúa trước chẩn đoán

## 1. Phạm vi và môi trường

Nhánh `tung/cv-rice-image-validation`, từ `Khuong/main` tại `4fb1169`.
Người dùng yêu cầu tích hợp ứng viên vào sản phẩm để thử dù model còn nhận nhầm cây ngoài miền. Đã đọc skill AI (AGENTS, verification-before-completion), Backend (code-quality, spring-boot, jpa-patterns) và plan Phase 6/template trước tích hợp.

Hợp đồng, preprocessing, artifact và cách tải nằm tại [CV README](../../../ai/app/cv/README.md#phân-loại-lúa-trước-chẩn-đoán). File plan tạm đã được chuyển vào tài liệu module và file này.

Môi trường local hiện tại:

- Android: Expo Go, cùng Wi-Fi với máy phát triển; mở `exp://192.168.1.13:8083`, reload để nhận code mới. Không dùng APK.
- Portal kỹ sư: `http://localhost:39080/portal/`; trên điện thoại: `http://192.168.1.13:39080/portal/`.
- Farmer: `farmer1 / farmer123`; engineer: `engineer1 / engineer123`; admin: `admin1 / admin123`.
- Chọn vụ đang canh tác đã duyệt. Farmer chụp bằng app; kỹ sư kiểm tra báo cáo trên web.
- Rice threshold **0.80**, margin **0.20**. Code confidence bệnh **0.70**; container local tạm **0.00** theo yêu cầu hạ ngưỡng để thử trước đó. Kết quả chẩn đoán local với 0.00 không dùng để nghiệm thu policy 0.70.
- Trang thử riêng classifier: `http://localhost:39083/` hoặc `http://192.168.1.13:39083/`; slider chỉ tác động trang thử, không đổi policy sản phẩm.

Các địa chỉ trên chỉ là môi trường thử, không đưa vào config được quản lý bằng Git.
Người dùng đã thử lại và phản hồi “có vẻ ổn” ngày 2026-10-07. Chưa có kết quả riêng từng case nên các dòng thủ công bên dưới vẫn chờ xác nhận cụ thể.

## 2. Kịch bản người dùng tự thử

| ID | Bước thực hiện | Kết quả cần kiểm tra | Người chạy / trạng thái |
|---|---|---|---|
| CV-M01 | Chụp lá lúa rõ, đủ sáng, lá ở giữa khung hình; gửi trên vụ đã duyệt | Precheck đạt, gửi được một báo cáo; kỹ sư thấy báo cáo. Nếu chặn nhầm, ghi ảnh/giờ gửi để đối chiếu | Người dùng / chờ |
| CV-M02 | Chụp rõ người, đồ vật, bàn phím hoặc cây khác | Nếu classifier từ chối: thông báo **chưa được nhận diện là lúa**, không ghi là ảnh mờ; nút gửi bị chặn | Người dùng / chờ |
| CV-M03 | Chụp ảnh lúa mất nét, ảnh tối hoặc ảnh cháy sáng | Lý do tương ứng mờ/tối/sáng; không gửi báo cáo. Bước chất lượng chạy trước classifier | Người dùng / chờ |
| CV-M04 | Chụp ngô, cỏ và lúa mì rõ nét, đặt giữa khung hình | Mục tiêu là từ chối. Model hiện có thể cho qua; nếu lọt ghi **Fail — nhận nhầm**, không coi đây là đạt vì confidence cao | Người dùng / chờ |
| CV-M05 | Tắt mạng rồi chụp/gửi ảnh; bật mạng và đồng bộ hàng đợi | Server kiểm cả chất lượng và cây trồng khi đồng bộ. 422 thành lỗi cần chụp lại, không retry tự động; lỗi kết nối/dịch vụ được phép thử lại | Người dùng / chờ |
| CV-M06 | Kỹ sư yêu cầu chụp lại một báo cáo; farmer chụp ảnh thay thế đạt gate | Giữ liên kết ảnh trước/sau; không tạo trùng khi gửi lại. Ảnh thay thế không đạt không làm mất báo cáo cũ | Người dùng / chờ |
| CV-M07 | Kỹ sư chạy AI lại cho báo cáo cũ có ảnh ngoài lúa | Nếu gate từ chối, chẩn đoán cũ giữ nguyên; không phát sinh kết quả AI mới | Người dùng / chờ |
| CV-M08 | So sánh cùng ảnh trên trang classifier và app | Trang riêng có 10 xác suất + chất lượng. Ảnh đạt Rice nhưng mờ vẫn bị app chặn; slider trang riêng không tác động app | Người dùng / chờ |

Ghi giờ gửi, nội dung thông báo và ảnh minh họa cho ca lỗi. Không lấy ảnh chụp màn hình/ảnh tổng hợp làm bằng chứng nhận đúng lúa thực địa.

## 3. Kiểm chứng kỹ thuật

| ID | Kiểm tra | Kết quả / người chạy |
|---|---|---|
| CV-A01 | Loader: thiếu/hash sai, mapping/shape sai; nạp một lần và giải phóng hai model | AI suite **61 test qua** + Ruff/pip check / Codex, 2026-10-07 |
| CV-A02 | Rice thấp, top1 cây khác, output NaN, runtime lỗi; quality chạy trước crop | AI suite **61 test qua** + Ruff/pip check / Codex, 2026-10-07 |
| CV-A03 | Precheck/predict/diagnose cùng gate; 422 không gọi detector/facts/RAG; lỗi dịch vụ 503 | AI suite **61 test qua** + Ruff/pip check / Codex, 2026-10-07 |
| CV-A04 | Bỏ qua precheck, gửi thẳng ảnh không được nhận là lúa | Backend test: 422, số report không đổi, storage.store không được gọi. Live qua gateway: ảnh người trả 422, vẫn 3 báo cáo / Codex |
| CV-A05 | Hồi quy role, workflow, retake/idempotency, PHI | Backend `formatter:format verify`: **79 test qua, 0 bỏ qua** / Codex, 2026-10-07 |
| CV-A06 | Mobile typecheck và export Android | Qua; Web production build cũng qua. Export Android chỉ ở thư mục local được ignore / Codex, 2026-10-07 |

Live qua gateway ngày 2026-10-07: ảnh lúa HF `rice_sample.jpg` đạt (sharpness 55.689); ảnh người `zidane.jpg` không đạt Rice dù sharpness 33.668. Precheck đều HTTP 200, lý do cây trồng riêng với ảnh mờ. Gửi thẳng ảnh người trả 422, số báo cáo giữ nguyên 3. Hai ảnh tải được xử lý trong bộ nhớ, không lưu file hay MinIO. Gateway được restart để bỏ DNS container cũ sau rebuild. Readiness cả hai model true; bộ nhớ tại lúc kiểm tra khoảng AI 426 MiB và Backend 417 MiB (snapshot, không phải benchmark).

CI trên GitHub chưa chạy vì nhánh chưa push. Clone sạch phải chạy script tải ONNX trước Compose; workflow E2E có bước tải và kiểm hash. Ba biến thể ảnh tổng hợp của demo qua gate trong kiểm tra local, chỉ dùng để kiểm workflow.

## 4. Bằng chứng về giới hạn model

Ứng viên: [revision cố định](https://huggingface.co/RaiyanKhaan/KrishokTech-Models/tree/c339988a6eceb5e22943ee27e4731a1a1dc9160a/01_crop_classifier).
Lần thử trước tích hợp ngày 2026-10-07 có 15 ảnh: 5 ảnh nguồn mô tả là lúa, 10 ảnh ngoài lúa; chưa phải ground truth do kỹ sư xác nhận.
Classifier cho qua 3 ảnh âm; 2 ảnh vẫn qua kiểm chất lượng (ngô/lúa mì), cỏ bị bước độ nét chặn.
Đối chiếu PyTorch 8.4.116 và ONNX trên cùng tensor của 5 ảnh: cùng top1, sai lệch xác suất tối đa < `5e-7`.

| Mẫu | P(Rice), PyTorch đối chiếu | Hành vi |
|---|---:|---|
| Ảnh lúa mẫu HF | 100% | Qua |
| Lúa mì mẫu HF | 99.9749% | Nhận nhầm |
| [Lá ngô, Commons](https://commons.wikimedia.org/wiki/File:Underside_of_a_Corn_Leaf,_2020-07-25.jpg) | 100% | Nhận nhầm, chất lượng đạt |
| [Cỏ, Unsplash](https://unsplash.com/photos/a-close-up-of-a-blade-of-grass-VrMdAkQcUAM) | 100% | Nhận nhầm; chất lượng từ chối |
| [Người, Ultralytics](https://ultralytics.com/images/zidane.jpg) | Khoảng 0.000029% | Từ chối; top1 Tomato vẫn là phân loại sai chủ thể |

Ảnh tải thử và checkpoint PyTorch đối chiếu đã xóa. Chỉ giữ ONNX được ignore và bằng chứng văn bản.
**Tình trạng:** tích hợp để thử; chưa nghiệm thu bài toán chặn ảnh ngoài miền. Cần model/dữ liệu âm phù hợp để xử lý các ca Rice gần 100%, không giải quyết bằng tăng threshold.

## 5. Rà soát bàn giao — 2026-10-07

- Fetch mới: `HEAD` và `origin/Khuong/main` cùng `4fb1169`; không có commit cha mới hoặc conflict cần xử lý.
- Chạy lại: AI 61 test + Ruff/pip check, Backend `verify` 79 test (0 bỏ qua), Mobile typecheck và Web build đều qua. Compose template hợp lệ và từ chối secret bắt buộc bị bỏ trống.
- Kiểm qua Backend: lúa mẫu đạt, ảnh người bị gate cây trồng chặn; gửi thẳng trả 422, số báo cáo giữ nguyên 4 (dữ liệu local đã có thêm báo cáo người dùng thử).
- Sửa Changelog cũ còn mô tả precheck chỉ kiểm chất lượng; bổ sung bước tải ONNX vào hướng dẫn local/Compose. README tổng không sửa.
- Dọn export Android, Web dist, JAR build thử dư và script gọi thử local. Giữ artifact/model, cấu hình và dữ liệu phục vụ môi trường đang chạy; các file local này được ignore. Không có APK/AAB trong phạm vi mã nguồn đã rà.
- Các file mới để review gồm classifier, script tải artifact, test hồi quy và test plan này. Không commit/push; CI GitHub chưa chạy.
