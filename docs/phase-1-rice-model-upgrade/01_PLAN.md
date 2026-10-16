# Plan thực hiện: Phase 1

Việc chi tiết ở [02_CHECKLIST.md](./02_CHECKLIST.md).
Thứ tự: Spec → xác nhận hợp đồng API giữ nguyên → Code.
Không thêm endpoint nên không phát sinh Swagger hoặc DTO Backend mới.

## Thứ tự và phụ thuộc

WP1-01 → WP1-02 → WP1-03 → WP1-04.
Mỗi thay đổi phải phục vụ trực tiếp việc thay model bệnh.

## WP1-01: Chốt checkpoint và mapping

- Mục tham chiếu: `00_OVERVIEW.md`, mục 4 và 6.
- Mục tiêu: xác định chính xác artifact, sáu lớp và payload đang được các consumer dùng.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Lớp Healthy | Fallback, confidence 0 khi không có detection | Lớp 0 được classifier dự đoán |
| Mapping class | Năm lớp bệnh, ID 0–4 | Healthy và năm bệnh, ID 0–5 |

**Hướng làm**

- Dùng metadata sẵn trong checkpoint: architecture, `class_names`, `class_to_idx`, `img_size`, mean/std và `state_dict`.
- Cập nhật `data/structured/class_contract.json` làm nguồn mapping duy nhất.
- Metadata runtime: `DX-Farm-EfficientNetV2-M`, phiên bản `4.0.0`, ảnh 480×480; giữ `model_profile.py` hiện có.
- Giữ trường `fallback` để các consumer hiện có còn đọc được; đó là metadata tương thích, không dùng để kết luận Healthy khi suy luận lỗi.
- Không tạo catalog/manifest thứ hai và không sửa symptom service, graph hoặc dữ liệu RAG.

| Class ID | Nhãn checkpoint | Mã bệnh hiện có |
|---|---|---|
| 0 | Healthy | `Rice___healthy` |
| 1 | Blast | `Rice___Blast` |
| 2 | Brown Spot | `Rice___Brown_spot` |
| 3 | Leaf Smut | `Rice___Leaf_smut` |
| 4 | Rice Tungro | `Rice___Tungro` |
| 5 | Sheath Blight | `Rice___Sheath_blight` |

**Phạm vi ảnh hưởng**

- File chính: `data/structured/class_contract.json`, `ai/app/cv/model_profile.py`.
- Bệnh canonical và rule hiện có không đổi; không migration.
- Payload chẩn đoán không đổi; danh sách class và metadata phản ánh model mới.

**Kết quả đầu ra**

- Có mapping sáu lớp khớp checkpoint và mã bệnh trong danh mục hiện tại.

**Phụ thuộc, lưu ý**: artifact lấy từ phần trọng số đã lưu trên nhánh cũ; không lấy code triển khai cũ.

## WP1-02: Thay nạp và suy luận trong CV hiện có

- Mục tham chiếu: luồng CV ở `ai/app/cv/README.md` và WP1-01.
- Mục tiêu: đổi model phía sau `PlantDiseaseService` và giữ đầu ra hiện tại.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Nạp model | Ultralytics YOLO, task detect | Torchvision EfficientNetV2-M, sáu logits |
| Suy luận | Ảnh 640, boxes, confidence/IoU | Ảnh 480×480, softmax, top-1 |
| Kết quả dưới ngưỡng | Gate workflow 0,70 | Cùng gate, dùng confidence chưa làm tròn |

**Hướng làm**

- Sửa trực tiếp `loader.py`, `model_profile.py` và phần suy luận trong `PlantDiseaseService`.
- Nạp `torch.load(..., weights_only=True)`, dựng model với `weights=None`, kiểm metadata và `load_state_dict(..., strict=True)`.
- Tái sử dụng kiểm file/checksum, chọn CPU/CUDA và nạp một lần khi khởi động.
- Tiền xử lý theo recipe checkpoint: RGB/EXIF hiện có, resize toàn ảnh 480×480 bicubic có antialias, chuẩn hóa ImageNet.
- Kiểm đầu ra sáu lớp và confidence hữu hạn; trả `disease_code`, `disease_name`, `confidence`, `model_name`, `model_version` như trước.
- Giữ quality gate, crop classifier, ngưỡng Rice 0,80 và workflow 0,70.
- Bỏ riêng logic boxes/IoU và dependency Ultralytics của model bệnh; khai báo Torchvision trực tiếp theo phiên bản tương thích trong lock.
- Không thêm provider registry, capabilities API, schema mới hoặc luật chẩn đoán/điều trị.

**Phạm vi ảnh hưởng**

- File chính: `ai/app/cv/loader.py`, `ai/app/cv/model_profile.py`, `ai/app/services/plant_disease_service.py`, đường dẫn trong `ai/app/core/config.py`; docstring module ở `ai/app/cv/__init__.py` chỉ đổi tên thư viện/model cho đúng.
- `ai/app/cv/policy.py` chỉ bỏ hằng detection/IoU không còn caller; giữ mọi ngưỡng còn dùng.
- Comment trong `ai/app/core/lifespan.py` chỉ bỏ tên thư viện cũ; giữ nguyên toàn bộ logic startup/shutdown.
- Dependency: `ai/requirements.txt`, `ai/requirements.lock`, `sbom/ai.cdx.json`; không nâng phiên bản thư viện ngoài nhu cầu model.
- Role, client, API Backend, RAG và dữ liệu cũ giữ nguyên.

**Kết quả đầu ra**

- Model mới chạy qua facade và hợp đồng cũ, không còn nhánh suy luận YOLO bệnh.

**Phụ thuộc, lưu ý**: Healthy là dự đoán sáu lớp; không suy ra Healthy từ lỗi hoặc thiếu kết quả.

## WP1-03: Đóng gói trọng số theo cách chạy gốc

- Mục tham chiếu: yêu cầu trọng số có sẵn sau checkout và cách khởi động của main.
- Mục tiêu: build image AI chứa checkpoint và tránh thêm bước ghép/tải model trên host.
- Ước lượng: M.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Artifact Git | Một `best.pt` nhỏ, mount read-only | Ba phần checkpoint, mỗi phần dưới 100 MiB |
| Image AI | Nhận checkpoint qua mount | Ghép lúc build vào `/opt/dxos/models/efficientnetv2m_rice_disease.pth` |
| Khởi động/restart | Nạp file cục bộ | Nạp file trong image, không tải model bệnh |

**Hướng làm**

- Tái sử dụng đúng byte ba phần checkpoint đã có; một script ghép bằng stdlib kiểm thứ tự/kích thước và dừng khi artifact không hợp lệ.
- Chỉ thêm stage ghép trọng số vào `ai/Dockerfile`; giữ các phần chạy ứng dụng hiện có.
- Đặt checkpoint ngoài `/app/weights` để volume embedding hiện có không che mất model mới.
- Trong Compose chỉ chỉnh service `ai`: truyền đường dẫn model mới và bỏ bind-mount `best.pt` cũ; giữ mount crop classifier và các volume khác.
- Tái sử dụng tên biến `AI_CV_WEIGHTS_PATH`, `AI_CV_WEIGHTS_SHA256`; path mặc định đặt trong `.env.example`, biến cần có dùng `${VAR:?}` tại chỗ sửa.
- Ghi rõ `AI_CV_WEIGHTS_PATH` chuyển từ đường dẫn host của model cũ sang đường dẫn trong image; override bên ngoài cần một mount tương ứng.
- Giữ cách người dùng chuẩn bị `.env` của main; chỉ hướng dẫn tính checksum cho artifact mới, không thêm script cấu hình chung hoặc ghi giá trị checksum vào Git.
- Chỉ sửa `deploy/demo/make_ci_env.py` để tính checksum từ checkpoint mới; giữ nguyên mọi workflow CI như main theo yêu cầu người dùng.
- Ghép checkpoint trong Docker build hiện có; workflow E2E gốc vẫn kiểm build image.
- Sau khi thay thế được xác nhận, xóa `ai/weights/best.pt` cùng đường dẫn tham chiếu cũ thuộc model bệnh; không để hai model bệnh chạy dự phòng.

**Phạm vi ảnh hưởng**

- Artifact: `ai/weights/dx-farm-efficientnetv2m/` gồm ba phần, metadata kích thước/thứ tự và nguồn/giấy phép ngắn.
- Build: `ai/Dockerfile`, `ai/.dockerignore`, một script ghép trong `ai/scripts/`.
- Deploy: chỉ phần model bệnh trong `deploy/docker-compose.yml`, `deploy/.env.example`, `deploy/demo/make_ci_env.py`; không sửa `.github/workflows/`.
- `.gitattributes` chỉ được thêm rule binary cho các phần trọng số nếu cần bảo đảm nguyên byte; không chỉnh ignore toàn dự án.
- Không sửa script start/switch/reset, Backend Dockerfile, BI, SSO hoặc healthcheck của dịch vụ khác.

**Kết quả đầu ra**

- Clone/build bằng quy trình gốc dùng được model mới; restart không cần mạng để lấy model bệnh.

**Phụ thuộc, lưu ý**: LLM/embedding vẫn theo cơ chế của main; không tối ưu hoặc sửa cache của các model đó trong phase này.

## WP1-04: Kiểm tra tương thích, tài liệu và dọn release

- Mục tham chiếu: `00_OVERVIEW.md` mục 6, [03_TEST_PLAN.md](./03_TEST_PLAN.md).
- Mục tiêu: kiểm chứng phạm vi model và không để lại công cụ tích hợp dùng một lần.
- Ước lượng: S.

**Hành vi thay đổi**

| Tình huống | Hiện tại | Sau thay đổi |
|---|---|---|
| Test CV gốc | Mock YOLO và detection | Điều chỉnh cùng bộ test để kiểm classifier sáu lớp |
| Công cụ đã có | Một số chỗ gắn `best.pt` hoặc ID YOLO | Sửa đúng caller phụ thuộc model mới |
| Tài liệu | Model bệnh YOLO, năm lớp và Healthy fallback | Mô tả model mới, sáu lớp và số liệu do người huấn luyện cung cấp |

**Hướng làm**

- Thay test CV cũ tại vị trí `ai/tests/`; có thể đổi tên `test_cv_yolo.py` thành `test_cv_model.py`, không tách thêm nhiều file test/helper/fixture.
- Chỉ điều chỉnh test gốc khác nếu thật sự phụ thuộc contract/model cũ; `test_rice_workflow.py` kiểm biên 0,70 và `check_diagnose_contract.py` dùng metadata mới trong mock, giữ nguyên logic nghiệp vụ/bảo mật.
- Sửa kiểm tra mapping trong `data/scripts/test_generate_seed_sql.py` và `validate_rice_workbook_export.py` theo sáu lớp; không sửa workbook hoặc dữ liệu nguồn để giả khớp ID YOLO lịch sử.
- `ai/scripts/evaluate_cv.py` đang có cần cập nhật đường dẫn và cách đếm Healthy nếu giữ sử dụng; không mở đợt benchmark hoặc tạo script đánh giá mới.
- Cập nhật đúng đoạn model trong `ai/README.md`, `ai/app/cv/README.md`, `data/README.md`, `docs/AI_EVALUATION.md`, `docs/COUNCIL_QA.md`, `docs/BACKLOG.md`; `docs/TESTING.md` và `deploy/demo/README.md` chỉ đổi tên artifact đã thay.
- `LICENSES.md`, `NOTICE` và các dòng giấy phép liên quan ở README chỉ đổi theo model/dependency; giữ giấy phép mã nguồn AI hiện có.
- Phần fine-tune của nhóm dùng Apache-2.0 theo quyết định đã có; giữ điều khoản pretrained Torchvision và CC BY 4.0 của dữ liệu, không đổi giấy phép crop classifier.
- Chỉ bổ sung CHANGELOG khi có kết quả cuối; không chép lịch sử làm việc vào README và không xóa log cũ.
- Chạy case theo quy ước `03_TEST_PLAN.md`; ghi bằng chứng thực tế rồi dọn file tạm, ảnh và log trước merge.

**Phạm vi ảnh hưởng**

- Chỉ test/công cụ gốc và các đoạn tài liệu trực tiếp mô tả model vừa thay.
- Không sửa UI, test Java để đổi tên mock, công cụ demo, cấu trúc `deploy/demo` hoặc template PR.

**Kết quả đầu ra**

- Mọi file trong diff giải thích được bằng việc nâng cấp model bệnh; không có thay đổi sản phẩm ngoài phạm vi.

**Phụ thuộc, lưu ý**: mọi file ngoài danh sách trên phải có lý do trực tiếp và được cập nhật/duyệt lại kế hoạch trước khi sửa.

## Thay đổi hợp đồng (API, schema, cấu hình)

| Loại | Đối tượng | Thay đổi | Phá tương thích? |
|---|---|---|---|
| Endpoint/DTO | Predict, diagnose, precheck, health | Giữ endpoint và cấu trúc; cập nhật giá trị metadata model | Không đổi cấu trúc |
| Class contract | `classes` và `fallback` | Sáu lớp đúng checkpoint, giữ trường fallback cho consumer gốc | Class ID thay đổi có chủ đích; mã bệnh giữ nguyên |
| Interface/DB | Core, app, migration, seed | Không đổi | Không |
| Cấu hình | `AI_CV_WEIGHTS_PATH` | Đường dẫn model mới trong image | Có đổi ý nghĩa path; cập nhật đúng dòng hướng dẫn |
| Cấu hình | `AI_CV_WEIGHTS_SHA256` | Checksum riêng của checkpoint mới | Phải cấu hình theo artifact mới, không công khai giá trị |

## Dữ liệu, migration và rollback

- Không có migration và không cập nhật dữ liệu bệnh/thuốc hoặc chẩn đoán lịch sử.
- Chẩn đoán mới ghi metadata model mới; dữ liệu lịch sử giữ nguyên tên/phiên bản đã lưu.
- Rollback bằng revert commit nâng cấp model, dùng lại image/trọng số và cấu hình model của main trước đó.
- Không xóa database hoặc volume; không thêm fallback chạy hai model.

## Quyết định thiết kế đã chốt

| # | Quyết định | Lý do ngắn |
|---|---|---|
| D1 | Nền `origin/Khuong/main`, nhánh kế hoạch riêng | Bỏ phạm vi mở rộng của PR cũ |
| D2 | Thay trực tiếp trong cấu trúc CV gốc | Một model bệnh, chưa cần abstraction đa provider |
| D3 | Dùng class contract và metadata checkpoint hiện có | Tránh hai nguồn mapping và phải sửa các consumer |
| D4 | Ngưỡng 0,70, quality/crop gate và workflow giữ nguyên | Chỉ thay model bệnh |
| D5 | Ghép trọng số vào image AI, người dùng đã duyệt | Artifact lớn, checkout có đủ trọng số, không thêm bước chạy trên host |
