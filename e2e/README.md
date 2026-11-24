# Kiểm tự động trên stack đang chạy

- `api/`: các script Python `check_*.py` kiểm API, phục hồi, hồ sơ, đổi mô hình, quy trình, đặt chỗ và kịch bản trình diễn, cùng `kc.py` giữ các hàm dùng chung; thứ tự chạy và cách dùng ở [`docs/TESTING.md`](../docs/TESTING.md).
- `fixtures/invalid-models/`: tệp mô hình sai mẫu mà các script đó gửi lên để kiểm lỗi.
- `web/`: dự án Playwright kiểm giao diện web (`tests/`), mô tả dưới đây.

## Kiểm giao diện trên trình duyệt

Bài kiểm Playwright của bảng điều khiển, chạy trên một stack đang chạy.
`web/tests/console.spec.ts` đăng nhập bằng tài khoản `ui_admin`, dùng một mẫu trong thư viện mẫu, xem kế hoạch, áp dụng, thấy mô hình chạy và đi qua các tab của bảng điều khiển.
`web/tests/phone.spec.ts` chạy ở cỡ điện thoại 390 px và kiểm bảng điều khiển cùng hộp việc không rộng hơn màn hình.
`web/tests/stale-decision.spec.ts` có bốn ca: cán bộ mở một hồ sơ Một cửa với luồng sự kiện bị chặn, rồi hồ sơ (hoặc mô hình) bị đổi ở nơi khác, rồi cán bộ bấm Duyệt, Nhận việc hoặc Duyệt hàng loạt.
Mỗi ca kiểm hồ sơ không bị duyệt hay giao, màn hình báo hồ sơ vừa đổi (hoặc mô hình vừa đổi thì báo tải lại trang) và hồ sơ hiện nội dung mới.
Bài này gọi `web/helpers/case.py` để áp dụng mẫu Một cửa nếu chưa có, tạo hai tài khoản `stu1` và `off1`, tạo hồ sơ, sửa hồ sơ và áp dụng một phiên bản mới của mô hình; cần `python3` và `deploy/.env` ở thư mục gốc repository, cùng `KEYCLOAK_ADMIN_CONSOLE=true` như bước tạo tài khoản ở dưới.

### Chạy

Cần một stack rỗng đã lên (xem [`docs/TESTING.md`](../docs/TESTING.md), mục 3) và tài khoản `ui_admin` vai `ADMIN`:

```bash
python3 -c "import sys; sys.path.insert(0, 'e2e/api'); import kc; kc.ensure_user('ui_admin', ['ADMIN'])"
cd e2e/web
npm ci
npx playwright install chromium
npx playwright test
```

### Cấu hình

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DXOS_BASE_URL` | `http://localhost:9080` | Địa chỉ gateway của stack cần kiểm |
| `E2E_USER` | `ui_admin` | Tài khoản đăng nhập |
| `E2E_PASSWORD` | mật khẩu thử của `e2e/api/kc.py` | Mật khẩu của tài khoản đó |

### Kết quả

- `web/screenshots/`: ảnh chụp từng bước.
- `web/test-results/`: vết của bài hỏng.
- Cả hai thư mục không vào git.

CI chạy bài này ở job `Browser end-to-end` của workflow `Platform`, trên stack dựng từ commit đang kiểm.
