# Bên nhận giữ hash lịch sử

Lịch sử hồ sơ nối thành một chuỗi hash, và `dxos history verify` tính lại chuỗi đó trên chính hệ thống.
Người sửa được cả cơ sở dữ liệu lẫn kho đối tượng có thể sửa một dòng rồi tính lại cả chuỗi và các điểm neo, nên phép tự kiểm vẫn báo nguyên vẹn.
Bên nhận webhook giữ `eventId` và `hash` của từng tin đã nhận, nên có chỗ để so: hash họ giữ không đổi theo.

Công cụ này nằm ngoài lõi, chỉ đọc `GET /api/v1/events` và không cần thư viện ngoài (Python 3.9 trở lên).
Quy trình chung cho bên nhận, chữ ký, khử trùng, đọc bù và nâng cấp nằm ở [`docs/EXTENSIONS.md`](../../docs/EXTENSIONS.md#bên-nhận-webhook).

## Cách dùng

Tệp của bên nhận có mỗi dòng một cặp `eventId hash`; bên nhận có thể tự ghi từ log webhook của mình.
Để thử, lệnh `save` đọc các sự kiện hiện có và ghi những cái chưa có trong tệp:

```
export DXOS_BASE_URL=http://localhost:9080
export DXOS_WITNESS_TOKEN=<access token của một ADMIN>
python3 examples/history-witness/witness.py save witness.txt
```

Về sau, `compare` đọc lại các sự kiện trong khoảng của tệp và so:

```
python3 examples/history-witness/witness.py compare witness.txt
```

Kết quả là số dòng khớp, số dòng đã đổi hash và số dòng không còn; mã thoát là 1 khi có dòng đổi hoặc mất.
Thay `DXOS_WITNESS_TOKEN` bằng `DXOS_WITNESS_CLIENT_ID` và `DXOS_WITNESS_CLIENT_SECRET` của một client credentials mà tài khoản dịch vụ giữ vai `ADMIN` thì công cụ tự lấy token (`DXOS_REALM_URL` mặc định là `<base>/realms/dxos`).

## Giới hạn

- Hash của mỗi dòng tính cả `note` và `detail`, mà tin webhook và `GET /events` không mang hai trường đó, nên bên ngoài không tính lại được hash; chỉ so được hash đã giữ.
- Công cụ chỉ có giá trị khi tệp nằm ở nơi người sửa hệ thống không với tới; chạy nó và giữ tệp ngay trên máy chủ thì không chứng minh được gì.
- Dòng thêm vào sau lần `save` cuối không có trong tệp nên không được kiểm.
