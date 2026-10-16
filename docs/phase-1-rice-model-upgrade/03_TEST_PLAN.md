# Kế hoạch kiểm thử: Phase 1

Theo template, mặc định kiểm cú pháp/compile và đọc diff.
Chỉ chạy test, build/khởi động ứng dụng hoặc gọi endpoint khi người dùng yêu cầu rõ.
Đã triển khai và kiểm tra AI, stack Farm/Lab theo phạm vi được duyệt ngày 16/10/2026.
Người dùng yêu cầu chạy nốt smoke và chỉ ghi nhận lỗi ngoài phạm vi, không sửa các module đó.

## 1. Chiến lược

- Mức kiểm tra: cú pháp, unit CV gốc, tương thích contract và smoke luồng chẩn đoán khi được yêu cầu.
- Môi trường runtime: checkout riêng của lần tích hợp mới, cấu hình theo README main; không thay dữ liệu đang dùng của người dùng.
- Test CV sửa trong bộ `ai/tests` hiện có; không thêm benchmark, ảnh fixture riêng hoặc thư mục test trong deploy.
- Dữ liệu: ảnh người dùng cho phép dùng ngoài Git; mock trong test gốc; tài khoản demo hiện có.
- Không huấn luyện hoặc chấm lại accuracy; chỉ số 89,43% là báo cáo đã cung cấp.
- Các ô kết quả và người chạy để trống cho đến khi kiểm tra thật trên code mới.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| T1-01 | WP1-01 | Contract | So class_names/class_to_idx checkpoint với contract và disease.code | Sáu lớp đúng thứ tự, không tạo mã bệnh mới | Codex, 16/10/2026 | Pass: checkpoint thật, mapping sáu lớp và 5 test dữ liệu. |
| T1-02 | WP1-02 | Happy path | Nạp checkpoint thật và đối chiếu recipe trên một ảnh được phép dùng | RGB/EXIF, 480×480 bicubic, normalization và top-1 đúng recipe | Codex, 16/10/2026 | Pass: recipe unit và ảnh thật qua API/image offline; Brown Spot 93,57%. |
| T1-03 | WP1-02 | Ngưỡng | Mock confidence 0,6999; 0,70; 0,91 trong test gốc | Chỉ hai giá trị sau được đi tiếp, không đổi ngưỡng | Codex, 16/10/2026 | Pass unit: 0,6999 bị chặn; 0,70 và 0,91 đi tiếp. |
| T1-04 | WP1-02 | Healthy | Classifier trả lớp Healthy qua diagnose | Mã Healthy đúng, không tự duyệt hoặc tự tạo rule thuốc | Codex, 16/10/2026 | Pass unit: Healthy đi theo contract/review_required, không tự sinh rule. |
| T1-05 | WP1-02 | Âm tính | Ảnh hỏng/mờ/tối hoặc crop classifier không nhận lúa | Giữ quy tắc từ chối, không chạy suy luận bệnh/Backend/RAG sau khi bị chặn | Codex, 16/10/2026 | Pass unit: chất lượng ảnh và crop gate chặn trước suy luận bệnh. |
| T1-06 | WP1-02 | Artifact/output lỗi | Thiếu file, checksum sai, class order sai, logits lỗi hoặc không hữu hạn | Bị từ chối theo lỗi phù hợp; không trả Healthy thay lỗi | Codex, 16/10/2026 | Pass unit: checksum, metadata, shape/dtype và logits không hợp lệ bị từ chối. |
| T1-07 | WP1-02 | API | Đối chiếu predict/diagnose/health với schema cũ | Endpoint và cấu trúc cũ; metadata model mới; service key vẫn bắt buộc như main | Codex, 16/10/2026 | Pass: giữ schema, contract diagnose; API thật có health/6 lớp, thiếu key trả 401. |
| T1-08 | WP1-03 | Đóng gói | Build image từ checkout mới, kiểm file trong image và restart AI khi không có mạng | Ghép đủ checkpoint, không tải model bệnh, volume embedding không che file | Codex, 16/10/2026 | Pass: build image, checkpoint ngoài volume weights, API chạy --network none. |
| T1-09 | WP1-03 | Cấu hình/CI | Dùng `.env.example`, script env CI và job AI hiện có | Path/checksum khớp artifact mới; không hard-code/in checksum vào log | Codex, 16/10/2026 | Pass make_ci_env/config full và reduced cho Farm/Lab; mọi workflow CI khớp origin/main; chưa chạy trên GitHub. |
| T1-10 | WP1-04 | Farm | Upload → kỹ sư chẩn đoán → duyệt → nông dân xem | Giữ phân quyền, ẩn kết luận chờ duyệt; rule/liều/PHI do Backend quyết định | Codex, 16/10/2026 | Pass: Farm E2E 10/10; ảnh thật qua Backend trả Brown Spot 93,51%, PENDING bị ẩn, sau duyệt nông dân xem được; không có rule chưa được duyệt. |
| T1-11 | WP1-04 | Ranh giới | Chuyển Farm/Lab theo lệnh gốc khi được yêu cầu smoke | App hoạt động như main, không đổi core hoặc thêm cách chạy | Codex, 16/10/2026 | Không đạt cold start: seed upload trước AI ready trả 503. Sau AI healthy, Farm 10/10 và restart/predict offline đạt; chuyển Lab theo script gốc đạt, Lab 16/16 với nguồn tri thức. Không sửa startup/seed ngoài phạm vi. |
| T1-12 | WP1-04 | Kiểm tra phạm vi | Đọc diff theo danh sách file của `01_PLAN.md`, dò file thừa và secret | Chỉ có thay model và phụ thuộc trực tiếp; không còn công cụ tích hợp tạm | Codex, 16/10/2026 | Pass: diff đúng phạm vi; gitleaks không allowlist và đối chiếu secret/checksum riêng không có phát hiện. |

## 3. Hồi quy (không được hỏng)

**Dùng chung**

- [x] Payload diagnose và mã bệnh canonical vẫn được Backend đọc được.
- [x] Xác thực service key, chất lượng ảnh và crop classifier giữ hành vi hiện tại.
- [x] Các dịch vụ RAG, Ollama, SSO, BI và chat không bị sửa cấu hình để phục vụ đổi model.
- [x] Cách chạy và chuyển app giống main; không thêm script chuẩn bị env hoặc host dependency mới.

**Farm**

- [x] Confidence dưới 0,70 bị chặn trước Backend facts/RAG/LLM.
- [x] Chẩn đoán chưa duyệt vẫn bị ẩn với nông dân (đã kiểm qua API với model thật).
- [x] Healthy không tự sinh thuốc; PHI và rule điều trị tiếp tục do Backend quản lý.
- [x] Chẩn đoán lịch sử và dữ liệu hiện có không bị sửa hoặc backfill.

## 4. Tiêu chí đạt của phase (exit criteria)

- Các case được yêu cầu đã có người chạy và kết quả; không tự tick các case chưa được chạy.
- Các tiêu chí bàn giao trong `00_OVERVIEW.md` có bằng chứng mới và không có lỗi chặn tích hợp.
- Bất kỳ thay đổi phạm vi nào cần xem lại kế hoạch trước khi sửa tiếp.
- Không sử dụng kết quả test từ nhánh/PR cũ để chứng minh lần tích hợp mới đạt.

## 5. Bằng chứng và báo lỗi

- Ghi tóm tắt lệnh, người chạy, ngày và kết quả vào bảng trên hoặc nội dung PR khi thực hiện.
- Log/ảnh tạm nằm ngoài repository và được dọn sau kiểm tra; không thêm báo cáo test phát sinh vào release.
- Che credential/checksum riêng trước khi chia sẻ log; số liệu accuracy phải ghi đúng nguồn người huấn luyện.
- Lỗi mới ghi vào issue hoặc BACKLOG đúng phạm vi, không sửa module ngoài phase để làm xanh kiểm tra.

Kết quả ngày 16/10/2026: `pytest tests -q -p no:cacheprovider` đạt 100/100 trên image vừa build; 5 test dữ liệu và `generate_seed_sql.py --check` đạt; `check_diagnose_contract` đạt.
Ảnh thực chạy bằng image, không mount code và tắt mạng: Brown Spot 93,57%, CPU mặc định 4 luồng, một lượt predict 0,65 giây; đây không phải benchmark accuracy/hiệu năng.

Smoke ngày 16/10/2026 dùng checkout tạm, `.env` mới và project Compose riêng; không dùng database hay dữ liệu người dùng.
Chạy `./deploy/start.sh farm --build`, `./deploy/start.sh lab`, seed lại và E2E gốc (`farm --require-rule`, `lab --require-knowledge`).
Cache embedding/Ollama được sao chép sang volume thử; checkpoint bệnh lấy từ image vừa build.

Ghi nhận ngoài phạm vi, không sửa mã hoặc cấu hình sản phẩm:

- Lượt đầu upload trả 500 do MinIO `XMinioStorageFull`: máy chỉ còn khoảng 1,5 GB, thấp hơn yêu cầu 40 GB trống trong README.
  Smoke tiếp theo chuyển riêng thư mục MinIO của stack thử sang RAM; kết quả trên không chứng minh khởi động được khi ổ đĩa thiếu dung lượng.
- Superset tự cài Authlib 1.9.1 kéo cryptography 50.0.2, ngoài khoảng `>=42.0.4,<43.0.0` của Superset 3.1.3.
  Stack vẫn healthy trong lượt thử, nhưng pip báo xung đột; không sửa BI theo giới hạn người dùng.

Lượt dựng sạch sau khi người dùng yêu cầu dọn Docker ngày 16/10/2026:

- Dọn image/cache không dùng thu hồi khoảng 49,4 GB; còn khoảng 51 GB trước thử. Giữ dữ liệu/volume người dùng.
- Snapshot nguồn riêng, env từ `.env.example` điền đủ giá trị, volume dự án rỗng, không sao chép cache embedding/Ollama, MinIO dùng ổ đĩa thường.
- Chạy đúng `./deploy/start.sh`, không sửa script hoặc Compose cho test: build/tải xong nhưng seed thoát mã 1 với `503 ERR_AI_UNAVAILABLE`.
  Upload lỗi lúc 09:13:03 UTC; Uvicorn bắt đầu startup lúc 09:13:07, AI startup complete lúc 09:13:09.
- Sau khi AI healthy, E2E Farm gốc `--require-rule` đạt 10/10 với rule đi kèm sản phẩm, không chèn CI fixture; ảnh thật qua Backend trả Brown Spot 93,51%, chờ duyệt bị ẩn và sau duyệt xem được.
- Ngắt mạng container, restart AI và predict ảnh thật đạt Brown Spot 93,57%; checksum khớp env riêng, payload giữ 5 trường. Nối lại mạng sau thử.
- Seed Farm chạy lại báo đã có dữ liệu dù lượt đầu bị ngắt; guard theo thửa demo khiến phần báo cáo/nông hộ còn thiếu bị bỏ qua. Không coi seed lặp lại này là bằng chứng dữ liệu demo đầy đủ.
- `./deploy/start.sh lab` và seed lại đạt; E2E Lab `--require-knowledge` đạt 16/16 trên cùng stack dựng sạch.
- Web, JavaScript của web, portal, Swagger và system status trả HTTP 200 sau khi chuyển Lab.
- Rà soát cuối: workflow/module được bảo vệ khớp main, mã runtime khớp snapshot đã test; gitleaks không allowlist và đối chiếu checksum/credential riêng không có phát hiện trong 740 file văn bản đề xuất.
- Workflow CI và start/switch/reset giữ nguyên origin/main. Hai lỗi startup/seed và cảnh báo dependency BI được ghi ở BACKLOG, không sửa module ngoài model.

Chưa đạt tiêu chí khởi động một lệnh nạp đủ demo trên lần đầu; không đóng phase hoặc ghi toàn stack Pass để che lỗi ngoài phạm vi.

Sau kiểm tra đã hạ stack thử, xóa volume thử, snapshot, env riêng, ảnh và log tạm; giữ nguyên dữ liệu/volume người dùng.
Mã nguồn và trọng số vẫn chưa commit/push.

Cập nhật nền ngày 16/10/2026 theo yêu cầu người dùng:

- Đã fetch và rebase nhánh lên đúng `origin/Khuong/main` mới nhất; không có conflict Git. Không merge/cherry-pick code tích hợp từ feature cũ, chỉ tái sử dụng checkpoint.
- Giữ các cập nhật bản đồ và thời tiết của upstream; diff riêng của nâng cấp model không sửa Backend, BI, web/mobile, core hoặc workflow CI.
- Bộ trọng số đã vào Git index; checkout nguyên byte đủ ba phần, không có LFS pointer, từng phần dưới 100 MiB. Chưa commit/push.
- Kiểm lại: 100/100 test AI (pytest 9.1.1), 5/5 test dữ liệu, generated seed và contract diagnose đạt.
- Stack E2E rút gọn theo workflow CI gốc: seed Farm hai lần, E2E 10/10 và ảnh thật qua Backend trả Brown Spot 93,51%; quyền xem trước/sau duyệt và rule giữ đúng.
- Dừng lượt thử khi người dùng yêu cầu không chạy thêm Docker; không chạy lại E2E Lab sau rebase, không ghi 16/16 cũ thành kết quả mới.
  Mã Lab, core, web/mobile, CI và script khởi động đã đối chiếu không đổi so với nền trước cập nhật. Kết quả Lab 16/16 ở trên thuộc lượt trước.
- Render Compose đầy đủ hợp lệ: model path mới, crop classifier và mount basemap upstream đều đúng; bước này không khởi động dịch vụ.
- Đã hạ container, xóa volume và image tag QA vừa tạo; không tiếp tục dựng stack/image mới.

Rà soát cuối sau cập nhật nền: không có conflict hoặc thay đổi ở module được bảo vệ; gitleaks chạy native không có phát hiện, đối chiếu credential/checksum riêng đạt trên 746 file văn bản.
Bộ trọng số và hai file mới cần cho model đã theo dõi trong index; chưa commit/push.
