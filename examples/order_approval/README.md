# Ứng dụng duyệt đơn

Ví dụ nhỏ nhất cho đường "có code" của [hướng dẫn bắt đầu nhanh](../../docs/QUICKSTART.md): một ứng dụng chạy trong Docker Compose, nộp một đơn rồi duyệt nó qua API của DX-OS.

| Tệp | Việc |
|---|---|
| [`dxos.yaml`](./dxos.yaml) | Mô hình `order_approval`: vai `REQUESTER` và `APPROVER`, một thực thể `order_request` có quy trình duyệt |
| [`app.py`](./app.py) | Ứng dụng: lấy token của hai vai, nộp đơn, duyệt đơn; chỉ cần Python chuẩn |
| [`docker-compose.yml`](./docker-compose.yml) | Chạy `app.py` trong container và truyền năm biến cấu hình |

## Chạy thử

Trên máy chạy DX-OS (đã `dxos start`, và `PUBLIC_HOST` là IP hoặc tên máy chủ chứ không phải `localhost`, xem [`docs/OPERATIONS.md`](../../docs/OPERATIONS.md#biến-cấu-hình-env)), trong thư mục này:

```bash
dxos apply dxos.yaml
echo "DXOS_BASE_URL=http://<PUBLIC_HOST>:9080" > .env
dxos client add requester --model order_approval --role REQUESTER --env DXOS_REQUESTER >> .env
dxos client add approver  --model order_approval --role APPROVER  --env DXOS_APPROVER  >> .env
docker compose run --rm app
```

Kết quả mong đợi:

```
filed order 1, status submitted
approved order 1, status approved
```

Tệp `.env` giữ hai secret của client; `.gitignore` của repository đã bỏ qua nó.
Dọn dẹp: `dxos client remove requester` và `dxos client remove approver`.
