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

Chạy trên stack mới dựng (`deploy/reset.sh`, đợi đến khi báo mọi service sẵn sàng) và cần `deploy/.env`.
Script đọc `.env` và gọi `docker compose exec postgres`, nên chạy trên máy đang chạy stack.

### Dữ liệu được tạo

**Tài khoản.** `farmer1`, `engineer1`, `admin1` có sẵn từ realm Keycloak.
Script tạo thêm `demo.hogd2` và `demo.hogd3`; mật khẩu tạm in ra một lần ở cuối lúc chạy.

**Nông hộ.**

| Hộ | Tài khoản | Cách tạo |
|---|---|---|
| Nguyễn Văn Hậu (Cần Thơ) | `farmer1` | Thêm một hàng `households` bằng `psql`, rồi gắn tài khoản qua API |
| Trần Thị Mai (Tháp Mười, Đồng Tháp) | `demo.hogd2` | Admin tạo |
| Lê Văn Phước (Vĩnh Thuận, Kiên Giang) | `demo.hogd3` | Admin tạo |

**Thửa và vụ mùa.**

| Thửa | Hộ | Diện tích | Trạng thái | Vụ |
|---|---|---|---|---|
| Phong Điền, Cần Thơ | `farmer1` | 1,20 ha | Đã duyệt | Đang canh tác (bắt đầu 45 ngày trước, Thu Đông) |
| Mỹ Xuyên, Sóc Trăng | `farmer1` | 0,85 ha | Đã duyệt | Đã thu hoạch, 7.200 kg |
| Châu Phú, An Giang | `farmer1` | 2,00 ha | Chờ duyệt | Không có |
| Tháp Mười, Đồng Tháp | `demo.hogd2` | 1,50 ha | Đã duyệt | Đang canh tác |
| Vĩnh Thuận, Kiên Giang | `demo.hogd3` | 1,10 ha | Đã duyệt | Đang canh tác |

- Thửa của `farmer1` đi qua luồng nông dân gửi và admin duyệt, kèm GPS có sai số và `Idempotency-Key`.
  Thửa và vụ của hai hộ còn lại do admin tạo nên được duyệt ngay.
- Mọi vụ dùng giống đầu tiên trong danh mục giống; mã thửa và mã vụ do backend sinh.

**Báo cáo sâu bệnh** trên vụ đang canh tác của `farmer1`:

1. Đã chẩn đoán và kỹ sư đã duyệt.
2. Đã chẩn đoán, chờ kỹ sư duyệt.
3. Kỹ sư yêu cầu chụp lại, lý do "Ảnh chưa thấy rõ vết bệnh, hãy chụp lại gần lá hơn."

**Kết quả trên giao diện.**

- Hàng chờ có hai việc: thửa An Giang (admin) và chẩn đoán số 2 (kỹ sư).
- `farmer1` có thông báo từ các lần duyệt thửa, vụ, chẩn đoán và yêu cầu chụp lại.
- Superset có dữ liệu cho 4 thửa đã duyệt, 4 vụ và 1 chẩn đoán đã duyệt.
  Hai chart "Thửa đang cách ly" và "Tỷ lệ AI đúng/sai" trống cho tới khi có rule được duyệt và chẩn đoán AI.

Script không tạo thuốc, treatment rule, tài liệu RAG hay danh mục giống; các dữ liệu này đến từ seed của Backend và job `rag-ingest` lúc dựng stack.

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

Checkout có sẵn `best.pt` và `crop_classifier.onnx` trong `ai/weights/`. Stack mount read-only và nạp trực tiếp; E2E không có bước download classifier.

Workflow `E2E` trên GitHub chạy chuỗi: checkout, dựng stack rút gọn, `seed_demo.py`, thêm rule thử nghiệm của CI, `e2e.py --require-rule`.
Rule này nằm trong database tạm của CI, không thuộc dữ liệu demo hay seed.
