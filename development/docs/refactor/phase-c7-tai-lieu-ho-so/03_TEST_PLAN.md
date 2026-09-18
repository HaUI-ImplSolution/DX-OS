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
| TC7-01 | WPC7-01 | Tài liệu | Tìm `docs/ARCHITECTURE_OPEN_CORE`, `docs/PARCEL_SEASON_CODES`, `improvement_docs/_template`, `backend/DX-Farm` | Không còn đường dẫn cũ | | |
| TC7-02 | WPC7-02 | Happy path | Làm theo README trên clone sạch, chạy `farm` | Hệ thống lên, đăng nhập được | | |
| TC7-03 | WPC7-02 | Happy path | Làm theo hướng dẫn thêm app, tạo app mẫu rỗng | App nạp được khi đặt `DXOS_APP`, core không sửa | | |
| TC7-04 | WPC7-03 | Rà soát | Đối chiếu 10 câu hỏi mục 13 với `COUNCIL_QA.md` | Mỗi câu có câu trả lời và tham chiếu | | |
| TC7-05 | WPC7-04 | Bảo mật | Quét gitleaks toàn repo | Không có phát hiện | | |
| TC7-06 | WPC7-04 | Tài liệu | Tìm `dxfarm`, `DX-Farm` | Chỉ còn ngoại lệ đã ghi | | |
| TC7-07 | WPC7-03 | Tài liệu | Kịch bản demo chạy thử trong 10 phút | Đủ hai app và thao tác chuyển | | |

## 3. Hồi quy (không được hỏng)

**Dùng chung (mọi app)**

- [ ] README dựng được hệ thống từ clone sạch.
- [ ] Mọi link nội bộ trong tài liệu mở được.
- [ ] Dựng từ clone sạch bằng `docker compose up -d --build` lên đủ dịch vụ healthy với từng giá trị `DXOS_APP`.

## 4. Tiêu chí đạt của phase (exit criteria)

- TC7-01 đến TC7-07 đạt.
- Không còn lỗi nghiêm trọng mở.
- Các tiêu chí ở `00_OVERVIEW.md` mục 6 đều có bằng chứng.

## 5. Bằng chứng và báo lỗi

- Bằng chứng: kết quả tìm kiếm, báo cáo gitleaks, ghi chú làm theo README lưu ở {đường dẫn}.
- Lỗi phát hiện: tạo issue, ghi mã vào cột "Kết quả" (ví dụ `Fail #123`).
