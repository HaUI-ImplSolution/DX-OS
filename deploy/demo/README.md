# Dữ liệu demo và kiểm thử đầu-cuối

Các script dùng Python 3 (thư viện chuẩn, thêm `pillow` để tạo ảnh tổng hợp) và gọi API qua gateway `http://localhost:9080`.
Đăng nhập bằng tài khoản demo (`admin1`, `engineer1`, `farmer1`) qua luồng Authorization Code + PKCE của Keycloak, nên token mang đúng client như app thật (`farmer1` đi qua client mobile).

| File | Việc làm |
|---|---|
| `seed_demo.py` | Nạp dữ liệu demo |
| `e2e.py` | Chạy kịch bản đầu-cuối, thoát mã 0 khi mọi bước đạt |
| `dxfarm_client.py` | Đăng nhập và gọi API dùng chung |
| `docker-compose.e2e.yml` | Stack rút gọn cho CI: không BI, chat, nạp RAG, Ollama |
| `ci_fixture_rule.sql` | Một rule thử nghiệm riêng cho CI |
| `make_ci_env.py` | Sinh `deploy/.env` cho stack CI |

## Nạp dữ liệu demo

```bash
python3 -m pip install pillow
python3 deploy/demo/seed_demo.py [--base-url URL] [--images DIR]
```

Chạy trên stack mới dựng (`deploy/reset.sh`).
Script tạo:

- Nông hộ gắn tài khoản `farmer1` (thêm một hàng `households` bằng `docker compose exec postgres psql`, rồi gắn qua API).
- Hai thửa của `farmer1` ở Cần Thơ và Sóc Trăng đi qua yêu cầu và duyệt của admin, một thửa ở An Giang để chờ duyệt.
- Một vụ đang canh tác và một vụ đã thu hoạch.
- Ba báo cáo trên vụ đang canh tác: một đã chẩn đoán và duyệt, một chờ kỹ sư duyệt, một bị yêu cầu chụp lại.
- Hai nông hộ có tài khoản (`demo.hogd2`, `demo.hogd3`) ở Đồng Tháp và Kiên Giang, mật khẩu tạm in ra một lần.

Chạy lại khi đã có dữ liệu demo thì không tạo thêm.

**Ảnh.** Không có `--images`, script dùng ảnh tổng hợp: đủ cho các màn hình và kiểm tra chất lượng ảnh, không đủ cho chẩn đoán AI có ý nghĩa; chẩn đoán khi đó nhập thủ công (`Rice___Blast`, `manual-input-v1`).
Có `--images DIR` (các file `.jpg` ảnh lá lúa thật), script gọi chẩn đoán AI cho báo cáo.

**Treatment rule.** Script không tạo thuốc hay rule.
Chưa có rule được duyệt cho bệnh đó thì chẩn đoán ở trạng thái chỉ xem xét, không có liều và PHI.
Có rule được duyệt thì script ghi thêm bản khai phun thuốc và xác nhận, vụ vào trạng thái cách ly.

## Kiểm thử đầu-cuối

```bash
python3 deploy/demo/e2e.py [--base-url URL] [--require-rule]
```

Các bước: trạng thái hệ thống công khai, engineer bị chặn tạo thửa, GPS giả lập bị từ chối, thửa chờ duyệt và chưa tạo được vụ, chỉ admin duyệt, thu hoạch trước ngày dự kiến bị chặn, upload idempotent, ảnh trắng không qua kiểm tra chất lượng, chẩn đoán chờ rồi được duyệt.
Khi có treatment rule: bản khai phun chặn thu hoạch tới khi kỹ sư xác nhận, sau đó khóa cách ly chặn thu hoạch tới `harvestLockedUntil`.
Không có rule, kịch bản kiểm chẩn đoán ở trạng thái chỉ xem xét; `--require-rule` coi việc thiếu rule là lỗi.

Workflow `E2E` trên GitHub chạy chuỗi: dựng stack rút gọn, `seed_demo.py`, thêm rule thử nghiệm của CI, `e2e.py --require-rule`.
Rule này nằm trong database tạm của CI, không thuộc dữ liệu demo hay seed.
