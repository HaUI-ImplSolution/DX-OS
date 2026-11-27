# Ứng dụng duyệt đơn

Ví dụ nhỏ nhất cho đường "có code" của [hướng dẫn bắt đầu nhanh](../../docs/QUICKSTART.md): một ứng dụng chạy trong Docker Compose, nộp một đơn rồi duyệt nó qua API của DX-OS.

| Tệp | Việc |
|---|---|
| [`dxos.yaml`](./dxos.yaml) | Mô hình `order_approval`: vai `REQUESTER` và `APPROVER`, một thực thể `order_request` có quy trình duyệt |
| [`app.py`](./app.py) | Ứng dụng: lấy token của hai vai, nộp đơn, duyệt đơn; chỉ cần Python chuẩn |
| [`docker-compose.yml`](./docker-compose.yml) | Chạy `app.py` trong container, nạp biến từ `.env` (`env_file`) và cho container gọi `host.docker.internal` (`extra_hosts`) |

## Chạy thử

Trên máy chạy DX-OS (đã `dxos start`), trong thư mục này:

```bash
dxos apply dxos.yaml
dxos connect order_approval --role REQUESTER --role APPROVER
docker compose run --rm app
```

`dxos connect` đăng ký hai client và ghi `DXOS_BASE_URL` cùng các biến của chúng vào `.env`, xem [`docs/QUICKSTART.md`](../../docs/QUICKSTART.md#bước-3-nối-ứng-dụng-với-dx-os).

Kết quả mong đợi:

```
filed order 1, status submitted
approved order 1, status approved
```

Tệp `.env` giữ hai secret của client; `.gitignore` của repository đã bỏ qua nó.
Dọn dẹp: `dxos client remove requester` và `dxos client remove approver`.

## Giấy phép

Apache-2.0, như cả dự án DX-OS; toàn văn ở `LICENSE` ở gốc repository.
