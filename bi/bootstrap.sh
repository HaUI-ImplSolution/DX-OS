#!/bin/bash
# Bootstrap Superset: migrate metadata DB, seed admin, đăng ký kết nối Postgres, import
# dashboard mẫu - idempotent, chạy lại an toàn mỗi lần container khởi động.
set -e

# authlib không có sẵn trong image gốc, cần cho AUTH_OAUTH (superset_config.py) - không build lại
# image, cài thẳng lúc container khởi động.
pip install --no-cache-dir authlib

superset db upgrade

superset fab create-admin \
  --username "${SUPERSET_ADMIN_USERNAME:-admin}" \
  --firstname Admin --lastname DXFarm \
  --email "${SUPERSET_ADMIN_EMAIL:-admin@dxfarm.local}" \
  --password "${SUPERSET_ADMIN_PASSWORD}" \
  || echo "[bootstrap] admin user already exists, skip"

superset init

# Chạy server nền để bootstrap datasource gọi được REST API, rồi wait làm tiến trình chính.
/usr/bin/run-server.sh &
SERVER_PID=$!

python3 /app/pythonpath/bootstrap_datasources.py \
  || echo "[bootstrap] datasource/dashboard bootstrap thất bại - Superset vẫn chạy, xem log để chạy tay lại"

wait "$SERVER_PID"
