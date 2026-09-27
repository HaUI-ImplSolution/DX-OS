# Nhật ký hoàn thiện DB seed — 26–27/09/2026

> **Mục đích:** Ghi chi tiết việc kiểm tra, triển khai và xác minh trong hai ngày để hoàn thành [`DB_SEED_UNIFICATION_PLAN.md`](./DB_SEED_UNIFICATION_PLAN.md).
> `CHANGELOG.md` chỉ nhận bản tóm tắt kết quả cuối cùng.

## Phạm vi và bất biến

- Chỉ xử lý dữ liệu nghiệp vụ trong Backend DB `dxfarm`: `diseases`, `pesticides`, `treatment_rules` và `disease_management_policies`.
- Không thay đổi AI Vector DB `dx_farm_ai_vector_db`, hai bảng `documents`/`document_chunks`, embedding hoặc job ingest RAG.
- `data/structured/*.json` là nguồn sinh seed Backend; `data/rag/*` chỉ phục vụ RAG và audit nguồn.
- Chỉ mở eligibility khi có quyết định được ghi rõ. Nhóm dự án cho phép `TR-CAND-004` dưới dạng `E2E_ASSUMPTION`; không bịa thêm thuốc, liều hoặc PHI và không coi đây là phê duyệt production.
- Không thêm Flyway/Liquibase và không thay đổi các `*Seeder` hạ tầng trong lượt này.
- Mọi kết luận hoàn thành phải có lệnh kiểm tra và kết quả mới.

## Audit trạng thái ban đầu — 26/09/2026

Các số liệu trong mục này là ảnh chụp **trước** khi nhóm cho phép `TR-CAND-004` làm giả định E2E; trạng thái cuối nằm ở mục “Nhật ký kết quả”.

| Mục đã tích trong plan | Kết quả audit | Bằng chứng hoặc giới hạn |
|---|---|---|
| Generator liệt kê rule bị chặn và lý do | Đạt | Sinh ra đúng 16 rule bị chặn; cả 16 có `blocked_reason`; không có rule nào được tự mở cờ. |
| Entity/bảng `disease_management_policies` đúng field | Đạt | Entity có quan hệ duy nhất tới `Disease` và đủ các field đã nêu trong plan; Backend compile thành công bằng JDK 21. |
| Seed 13 policy qua generator | Đạt | Database sạch nhận đúng 13 policy và 13 `disease_id` khác nhau. |
| Policy chỉ dùng để chặn/tra cứu, không tự sinh hóa chất | Đạt một phần | Không có service nào sinh hóa chất từ policy, nhưng repository hiện chưa được service/API sử dụng nên chức năng chặn/tra cứu chưa chạy ở runtime. |
| Generator chạy độc lập và deterministic | Đạt | `--check` thành công; hai lần gọi `generate()` cho output giống hệt nhau. |
| `data.sql` không chứa seed cũ ngoài nguồn hiện tại | Đạt ở mức file sinh | `data.sql` khớp output generator. Tuy nhiên database đã có dữ liệu seed cũ chưa hội tụ về nguồn mới sau restart. |
| Hai cơ chế seed đã được ghi thành quy ước | Đạt | README gốc và `data/README.md` phân biệt `data.sql` cho dữ liệu JPA với `*Seeder` cho hạ tầng PostgreSQL. |
| Database sạch start hai lần không trùng | Đạt | Cả hai lần đều có 38 disease, 6 pesticide, 0 treatment rule và 13 policy; số lượng bằng số khóa nghiệp vụ phân biệt. |

Các mục chưa tích trong plan vẫn chưa hoàn thành:

- Quyết định seed/không seed từng treatment rule chưa được ghi đầy đủ trong PR.
- E2E ba role trên bộ seed hiện tại chưa được chạy lại.

## Phát hiện cần xử lý

### 1. Database cũ chưa hội tụ về nguồn structured

Khi giả lập database cũ có `AGROVERTIN_50EC`, `THUMB_0_5SL` và rule `Squash___Powdery_mildew`–`THUMB_0_5SL`, restart Backend vẫn giữ 8 pesticide và rule Thumb cũ.
Nguyên nhân là generator chỉ đưa candidate pesticide còn eligibility vào danh sách dọn stale rule và không có bước dọn pesticide mất eligibility.

### 2. Tài liệu đang mâu thuẫn với dữ liệu sinh thật

- `DB_SEED_UNIFICATION_PLAN.md` mô tả `data/structured/*.json` nhưng lại nói phạm vi này gồm cả `rag/`.
- `data/README.md` vừa ghi cả 16 treatment rule đều bị chặn, vừa ghi `TR-CAND-004` đang được seed cho E2E.
- `AI_WEB_E2E_DEMO_README.md` còn kết quả của bộ seed cũ có treatment rule; không phải bằng chứng cho trạng thái hiện tại 0 rule.
- Phần “hiện trạng” của plan còn nói Backend thiếu entity policy dù entity đã tồn tại.

### 3. Test tự động chưa bảo vệ luồng seed

Backend hiện chỉ có một unit test cho `TreatmentRule`.
Chưa có regression test cho generator, eligibility, tính deterministic, số lượng seed, khởi động lặp hoặc hội tụ từ database cũ.

### 4. Policy mới chỉ được lưu, chưa tham gia decision flow

`DiseaseManagementPolicyRepository` tồn tại nhưng chưa có caller runtime.
Việc nối policy vào internal API/AI là thay đổi contract riêng; không tự mở rộng trong lượt seed nếu chưa được chốt phạm vi.

## Kế hoạch ngày 1 — 26/09/2026

- [x] Đọc plan, README và toàn bộ skill Backend/verification liên quan.
- [x] Audit từng checkbox đã tích bằng code và dữ liệu thật.
- [x] Xác minh generator deterministic, đủ 16 lý do chặn và `data.sql` khớp nguồn.
- [x] Build và chạy test Backend trong Maven/JDK 21: 1 test, 0 failure/error.
- [x] Dựng database sạch tạm, start Backend hai lần và đối chiếu số lượng/khóa duy nhất.
- [x] Mô phỏng database cũ và tái hiện lỗi không hội tụ.
- [x] Chốt quy tắc dọn dữ liệu mất eligibility: vô hiệu hóa rule trước; chỉ xóa rule/pesticide khi không còn dữ liệu nghiệp vụ tham chiếu.
- [x] Sửa generator tại nguồn và sinh lại `data.sql`.
- [x] Bổ sung regression check cho deterministic output, authorization và cleanup/convergence.

## Kế hoạch ngày 2 — dự kiến 27/09/2026, hoàn thành sớm ngày 26/09

- [x] Kiểm tra database sạch start hai lần sau khi sửa: ổn định ở 38 disease, 6 pesticide, 1 active treatment rule và 13 policy.
- [x] Kiểm tra database cũ hội tụ an toàn về tập seed hiện hành.
- [x] Chạy toàn bộ test/build Backend bằng JDK 21: 1 test, 0 failure/error.
- [x] Chạy E2E FARMER/ENGINEER/ADMIN trên trạng thái seed hiện hành bằng endpoint chẩn đoán thủ công dành cho testing/demo.
- [x] Xác minh luồng treatment thành công chỉ với rule mang `E2E_ASSUMPTION`; không mô tả nó là rule đã được chuyên gia duyệt.
- [x] Ghi quyết định seed/không seed của từng candidate bằng dữ liệu có thể review trong PR.
- [x] Sửa các trạng thái và số liệu cũ trong plan, `data/README.md` và tài liệu E2E.
- [x] Cập nhật `CHANGELOG.md` theo ngày bằng một dòng kết quả.
- [x] Kiểm tra diff, file tạm/rác và xác nhận không có thay đổi ngoài phạm vi.

## Nhật ký kết quả

### 26/09/2026

- Hoàn thành audit checklist hiện hữu và ghi nhận bằng chứng mới cho generator, build/test và idempotency trên database sạch.
- Xác định database cũ không tự loại bỏ hai pesticide mất eligibility và rule Thumb cũ.
- Xác định tài liệu seed/E2E còn số liệu mâu thuẫn với trạng thái 6 pesticide, 0 treatment rule và 13 policy.
- Nhóm dự án cho phép riêng `TR-CAND-004` bằng `E2E_ASSUMPTION`; 15 candidate khác vẫn bị chặn.
- Generator bắt buộc authorization/version cho rule eligible, sinh 1 active rule và đối soát dữ liệu stale an toàn.
- Database sạch start hai lần ổn định ở 38/6/1/13; database cũ tự nâng schema, loại dữ liệu không tham chiếu và giữ rule lịch sử ở trạng thái inactive.
- Internal API chỉ trả active rule: bệnh có rule lịch sử inactive trả danh sách rỗng, còn `Tomato___Early_blight` trả đúng rule E2E và version hiện hành.
- E2E ba role qua trên database sạch: FARMER bị chặn chẩn đoán (403), ENGINEER tạo diagnosis PENDING với đúng rule, ADMIN duyệt; liều 1.49 bị chặn (409), liều 1.50 được lưu với PHI 7 ngày.
- Kiểm tra E2E này không gọi CV/RAG/Ollama và không được dùng làm bằng chứng chất lượng mô hình.
- Loại 7 artifact dẫn xuất không có consumer khỏi `data/`; giữ nguyên contract CV, master seed/candidate, corpus RAG và source registry.

### 27/09/2026

- Hoàn thành validation ảnh cho Web E2E: frontend chặn file vượt 10 MiB, không phải ảnh hoặc không decode được; Backend kiểm tra chữ ký/nội dung ảnh, format, kích thước và giới hạn 24 triệu pixel trước khi lưu.
- Hoàn thành diagnosis job chạy nền: `POST .../diagnosis/ai` trả `202 Accepted` cùng job ID; frontend polling trạng thái và lưu job trong local storage để lấy lại sau khi rời trang/mất mạng. Job `QUEUED` được chạy lại, job `RUNNING` được đưa về queue khi Backend khởi động lại.
- Chuẩn hóa upload theo pipeline AI: chỉ nhận JPEG/PNG có nội dung giải mã hợp lệ; frontend kiểm tra MIME/đuôi file, dung lượng và decode để cải thiện UX, Backend vẫn xác định format theo nội dung thực. Request vượt giới hạn 10 MiB trả `413` có kiểm soát.
- Xác minh thay đổi phạm vi làm ngay bằng `mvnw.cmd test` (4 tests, 0 failure/error), `npm run build`, ba AI self-check và Docker Compose config đều pass.

## Điều kiện kết thúc worklog

Chỉ kết thúc khi các mục còn lại trong plan có bằng chứng kiểm tra tương ứng, tài liệu phản ánh đúng trạng thái cuối, `CHANGELOG.md` đã được cập nhật ngắn gọn và không có thay đổi nào tới AI Vector DB.
