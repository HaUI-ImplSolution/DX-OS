#!/bin/bash
# Bootstrap Superset: migrate metadata DB, seed admin, đăng ký kết nối Postgres, import
# dashboard mẫu - idempotent, chạy lại an toàn mỗi lần container khởi động.
set -e

# authlib không có sẵn trong image gốc, cần cho AUTH_OAUTH (superset_config.py) - không build lại
# image, cài thẳng lúc container khởi động. Bản 1.3.2 giữ cryptography trong khoảng Superset 3.1.3 yêu cầu.
python3 -c "import authlib; assert authlib.__version__ == '1.3.2'" 2>/dev/null \
  || pip install --no-cache-dir "authlib==1.3.2" "cryptography>=42.0.4,<43.0.0"

superset db upgrade

superset fab create-admin \
  --username "${SUPERSET_ADMIN_USERNAME:-admin}" \
  --firstname Admin --lastname DXOS \
  --email "${SUPERSET_ADMIN_EMAIL:-admin@dxos.local}" \
  --password "${SUPERSET_ADMIN_PASSWORD}" \
  || echo "[bootstrap] admin user already exists, skip"

superset init

# Chạy server nền để bootstrap datasource gọi được REST API, rồi wait làm tiến trình chính.
/usr/bin/run-server.sh &
SERVER_PID=$!

python3 /app/pythonpath/bootstrap_datasources.py \
  || echo "[bootstrap] datasource/dashboard bootstrap thất bại - Superset vẫn chạy, xem log để chạy tay lại"

python3 /app/pythonpath/grant_engineer_access.py \
  || echo "[bootstrap] cấp quyền dataset cho kỹ sư thất bại - kỹ sư chưa thấy dashboard, xem log"

wait "$SERVER_PID"
