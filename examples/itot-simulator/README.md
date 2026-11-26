# Simulator IT/OT

Simulator đóng vai hệ thống giám sát thiết bị ở nhà máy.
Nó nằm ngoài lõi: chỉ đăng nhập bằng tài khoản dịch vụ riêng và gọi API dữ liệu như mọi ứng dụng khác.
Khi chỉ số của một thiết bị vượt ngưỡng, simulator tạo một sự cố trong mô hình `plant` để kỹ thuật viên xử lý.
Mỗi lần đo có một mã quan sát không trùng, nên gửi lại cùng một lần đo thì lõi từ chối và không tạo sự cố thứ hai.
Sự cố đã xử lý xong được simulator xác nhận khi chỉ số trở lại bình thường, hoặc mở lại khi chỉ số vẫn cao.

Simulator chỉ cần Python 3.9 trở lên, không cần thư viện ngoài.
Quy trình chung để cấp quyền, kiểm thử, đóng gói và nâng cấp dịch vụ ngoài nằm ở [`docs/EXTENSIONS.md`](../../docs/EXTENSIONS.md#dịch-vụ-riêng-gọi-api).
Lệnh `setup` dưới đây gọi thẳng Keycloak để ví dụ tự chạy, nên cần `KEYCLOAK_ADMIN_CONSOLE=true` trong `deploy/.env` (xem [Keycloak Admin Console](../../docs/OPERATIONS.md#keycloak-admin-console)); bản triển khai thật nên đăng ký client bằng `dxos client add`.

## Chạy thử

1. Áp dụng mẫu "Sự cố thiết bị" ở bảng điều khiển (tệp `backend/dx-engine/src/main/resources/templates/plant_incident.yaml`).
2. Tạo tài khoản dịch vụ cho simulator, trên máy chạy hệ thống:

   ```
   python3 examples/itot-simulator/simulator.py setup
   ```

   Lệnh này đọc tài khoản quản trị Keycloak trong `deploy/.env`, tạo client `dxos-itot-plant`, cấp vai `SENSOR` của mô hình cho tài khoản dịch vụ của client đó, và giữ secret ở `~/.local/state/dxos-itot-plant.json`.
3. Chạy simulator:

   ```
   python3 examples/itot-simulator/simulator.py run --devices 3 --interval 5
   ```

4. Ở giao diện, cấp vai `TECHNICIAN` của mô hình `plant` cho một người dùng; người đó thấy sự cố trong hộp việc, nhận xử lý rồi bấm Xử lý xong.
5. Gửi lại lần đo cuối để thấy lõi chặn trùng:

   ```
   python3 examples/itot-simulator/simulator.py replay
   ```

   Lệnh in `duplicate` và thoát với mã 0 khi lõi từ chối.

## Qua MQTT

Với `--transport mqtt`, simulator publish mọi số đo lên broker của [cầu nối MQTT](../../docs/INTEGRATIONS.md#cầu-nối-mqtt-tùy-chọn) như một thiết bị thật, và cầu nối mới là bên tạo sự cố.

1. Bật cầu nối theo [`docs/INTEGRATIONS.md`](../../docs/INTEGRATIONS.md#cầu-nối-mqtt-tùy-chọn): đặt `deploy/mqtt.env`, đăng ký client `itot-mqtt` vai `SENSOR`, đặt `COMPOSE_FILE` rồi `dxos start`.
2. Chạy `python3 examples/itot-simulator/simulator.py setup` như ở trên: simulator vẫn dùng tài khoản này để xác nhận hoặc mở lại sự cố đã xử lý.
3. Chạy simulator qua MQTT:

   ```
   python3 examples/itot-simulator/simulator.py run --transport mqtt --devices 3 --interval 5
   ```

   Mỗi số đo đi tới chủ đề `plant/<thiết bị>/reading` kèm `metric`, `reading` và mã quan sát `observation`.
   Tuyến mặc định của cầu nối (`deploy/mqtt/bridge.json`) chỉ tạo sự cố khi `reading` vượt 80 và thiết bị chưa có sự cố đang mở; đổi `--threshold` thì đổi ngưỡng trong tuyến cho khớp.
4. Gửi lại bản tin cuối:

   ```
   python3 examples/itot-simulator/simulator.py replay --transport mqtt
   ```

   Lệnh publish lại đúng bản tin và thoát với mã 0; log của cầu nối (`docker compose logs mqtt-bridge`) cho thấy bản tin không tạo hồ sơ mới.

Broker mặc định ở `localhost:1883` (`--mqtt-host`, `--mqtt-port`); tài khoản lấy từ `DXOS_MQTT_USER` và `DXOS_MQTT_PASSWORD`, hoặc từ `MQTT_USER` và `MQTT_PASSWORD` của `deploy/mqtt.env` (đổi tệp bằng `DXOS_MQTT_ENV`).
Chủ đề đổi được bằng `--topic-prefix`.

## Thiết lập

| Biến môi trường | Mặc định | Ý nghĩa |
|---|---|---|
| `DXOS_BASE_URL` | `http://localhost:9080` | Địa chỉ gateway |
| `DXOS_ENV_FILE` | `deploy/.env` của repo | Tệp chứa tài khoản quản trị Keycloak, chỉ lệnh `setup` đọc |
| `DXOS_ITOT_STATE` | `~/.local/state/dxos-itot-<mô hình>.json` | Nơi giữ secret và lần đo cuối |

`python3 examples/itot-simulator/simulator.py run --help` liệt kê các tùy chọn của lần chạy: số thiết bị, ngưỡng, khả năng vọt chỉ số và khoảng cách giữa hai lần đo.

## Giấy phép

Apache-2.0, như cả dự án DX-OS; toàn văn ở `LICENSE` ở gốc repository.
