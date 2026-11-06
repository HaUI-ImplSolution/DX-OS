# Kiểm giao diện trên trình duyệt

Bài kiểm Playwright của bảng điều khiển, chạy trên một stack đang chạy.
`tests/console.spec.ts` đăng nhập bằng tài khoản `ui_admin`, dùng một mẫu trong thư viện mẫu, xem kế hoạch, áp dụng, thấy mô hình chạy và đi qua các tab của bảng điều khiển.

## Chạy

Cần một stack rỗng đã lên (xem [`docs/TESTING.md`](../docs/TESTING.md), mục 3) và tài khoản `ui_admin` vai `ADMIN`:

```bash
python3 -c "import sys; sys.path.insert(0, 'deploy/dev'); import kc; kc.ensure_user('ui_admin', ['ADMIN'])"
cd e2e
npm ci
npx playwright install chromium
npx playwright test
```

## Cấu hình

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `DXOS_BASE_URL` | `http://localhost:9080` | Địa chỉ gateway của stack cần kiểm |
| `E2E_USER` | `ui_admin` | Tài khoản đăng nhập |
| `E2E_PASSWORD` | mật khẩu thử của `deploy/dev/kc.py` | Mật khẩu của tài khoản đó |

## Kết quả

- `screenshots/`: ảnh chụp từng bước.
- `test-results/`: vết của bài hỏng.
- Cả hai thư mục không vào git.

CI chạy bài này ở job `Browser end-to-end` của workflow `Platform`, trên stack dựng từ commit đang kiểm.
