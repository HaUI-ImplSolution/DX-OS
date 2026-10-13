# Kế hoạch kiểm thử: Phase C7

Phase chỉ có tài liệu; kiểm tra bằng tìm kiếm và đọc lại.
Chạy stack hoặc thử hướng dẫn trên máy sạch chỉ khi người dùng yêu cầu rõ; ghi ai chạy và khi nào ở cột "Người chạy".

## 1. Chiến lược

- Mức kiểm thử áp dụng: tìm kiếm link và tên cũ, quét secret, đọc lại, làm theo hướng dẫn trên clone sạch.
- Môi trường: clone sạch, `DXOS_APP` lần lượt `farm` và `lab`.
- Dữ liệu và tài khoản test: tài khoản demo của từng app.

## 2. Test case

| ID | WP | Loại | Mô tả và bước | Kết quả mong đợi | Người chạy | Kết quả |
|---|---|---|---|---|---|---|
| TC7-01 | WPC7-01 | Tài liệu | Tìm `backend/DX-Farm` ngoài `improvement_docs` và các mục lịch sử của `CHANGELOG.md` | Không còn đường dẫn cũ | Claude, 13/10/2026 | Đạt: `backend/DX-Farm` chỉ còn ở `improvement_docs`, 3 mục lịch sử của `CHANGELOG.md` và các tài liệu phase `refactor_docs` mô tả việc đổi đường dẫn; 0 link nội bộ gãy trên toàn bộ `.md` |
| TC7-02 | WPC7-02 | Happy path | Làm theo README trên clone sạch, chạy `farm` | Hệ thống lên, đăng nhập được | Claude, 13/10/2026 | Đạt: clone sạch, `cp deploy/.env`, `./deploy/start.sh farm` 113 s, E2E Farm 10/10; `start.sh lab` 56 s, E2E Lab 16/16, mọi dịch vụ healthy |
| TC7-03 | WPC7-02 | Happy path | Làm theo hướng dẫn thêm app, tạo app mẫu rỗng | App nạp được khi đặt `DXOS_APP`, core không sửa | Claude, 13/10/2026 | Đạt: làm đúng các bước của CONTRIBUTING với app mẫu rỗng (`AppModule`, nạp có điều kiện, `KNOWN_APPS`): build 7 module, backend lên với `DXOS_APP=sample`, `system/status` trả id `sample`, chỉ 14 endpoint của core; không sửa `dx-core`; bước tri thức rút ra thêm: thiếu corpus thì `rag-ingest` lỗi, đã ghi vào hướng dẫn |
| TC7-04 | WPC7-03 | Rà soát | Đối chiếu 10 câu hỏi mục 13 với `COUNCIL_QA.md` | Mỗi câu có câu trả lời và tham chiếu | Claude, 13/10/2026 | Đạt: `COUNCIL_QA.md` có 10 câu theo thứ tự mục 13 của đề, mỗi câu có tham chiếu tài liệu hoặc mã |
| TC7-05 | WPC7-04 | Bảo mật | Quét gitleaks toàn repo | Không có phát hiện | Claude, 13/10/2026 | Đạt: gitleaks v8.24.3 trên 336 commit và trên cây làm việc: không có phát hiện |
| TC7-06 | WPC7-04 | Tài liệu | Tìm `dxfarm`, `DX-Farm` | Chỉ còn ngoại lệ đã ghi | Claude, 13/10/2026 | Đạt: ngoại lệ có chủ đích là scheme `dxfarm://` và bundle `vn.dxfarm.mobile` của mobile, database `dx_farm_ai_vector_db`, allowlist `.gitleaks.toml`, tài liệu lịch sử (`CHANGELOG`, `improvement_docs`, `legacy_docs`, `pending_docs`, `refactor_docs`); tên `DX-Farm` còn lại là tên ứng dụng Farm |
| TC7-07 | WPC7-03 | Tài liệu | Kịch bản demo chạy thử trong 10 phút | Đủ hai app và thao tác chuyển | Người dùng, 13/10/2026 | Đạt theo xác nhận của người dùng: chạy `start.sh` và `switch-app.sh`, hai app lên ổn định; kịch bản dùng đúng hai lệnh này. Thời lượng 10 phút là ước tính của kịch bản, chưa đo bằng bấm giờ |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [x] README dựng được hệ thống từ clone sạch.
- [x] Mọi link nội bộ trong tài liệu mở được.
- [x] Dựng từ clone sạch lên đủ dịch vụ healthy với `farm` (`start.sh farm`) và `lab` (`start.sh lab`).

## 4. Tiêu chí đạt của phase (exit criteria)

- TC7-01 đến TC7-07 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả tìm kiếm, báo cáo gitleaks, ghi chú làm theo README lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
