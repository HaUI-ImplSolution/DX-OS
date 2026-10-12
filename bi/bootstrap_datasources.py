"""Đăng ký kết nối Postgres (DX-Farm, role chỉ-đọc) + dataset qua REST API công khai của
Superset. Idempotent - tìm theo tên trước khi tạo mới. Import dashboard mẫu ở cuối nếu có
export sẵn trong repo.
"""

import json
import os
import time
import zipfile
from urllib.parse import urlparse

import requests

BASE_URL = "http://localhost:8088"
ADMIN_USERNAME = os.environ.get("SUPERSET_ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ["SUPERSET_ADMIN_PASSWORD"]
DXOS_DB_URI = os.environ["DXOS_DB_URI"]
DASHBOARD_EXPORT_PATH = "/app/pythonpath/farm_dashboard_export.zip"

# Views of approved/settled records only (V9__bi_views.sql); superset_ro cannot read the tables.
DATASET_TABLES = [
    "vw_parcels_approved",
    "vw_crop_seasons_approved",
    "vw_disease_reports_processed",
    "vw_diagnoses_approved",
    "vw_treatment_applications_confirmed",
    "vw_ai_diagnosis_outcomes",
]


def wait_until_healthy(timeout_seconds: int = 180) -> None:
    deadline = time.time() + timeout_seconds
    while time.time() < deadline:
        try:
            resp = requests.get(f"{BASE_URL}/health", timeout=5)
            if resp.status_code == 200:
                return
        except requests.RequestException:
            pass
        time.sleep(2)
    raise SystemExit("[bootstrap] Superset không sẵn sàng sau khi chờ, dừng bootstrap datasource")


def login() -> str:
    resp = requests.post(
        f"{BASE_URL}/api/v1/security/login",
        json={
            "username": ADMIN_USERNAME,
            "password": ADMIN_PASSWORD,
            "provider": "db",
            "refresh": True,
        },
        timeout=15,
    )
    resp.raise_for_status()
    return resp.json()["access_token"]


def build_session(access_token: str) -> tuple[dict, requests.cookies.RequestsCookieJar]:
    csrf_resp = requests.get(
        f"{BASE_URL}/api/v1/security/csrf_token/",
        headers={"Authorization": f"Bearer {access_token}"},
        timeout=15,
    )
    csrf_resp.raise_for_status()
    headers = {
        "Authorization": f"Bearer {access_token}",
        "X-CSRFToken": csrf_resp.json()["result"],
        "Referer": BASE_URL,
    }
    return headers, csrf_resp.cookies


def find_one(headers, cookies, endpoint: str, filter_col: str, filter_value: str):
    query = f'(filters:!((col:{filter_col},opr:eq,value:{filter_value})))'
    resp = requests.get(
        f"{BASE_URL}{endpoint}", headers=headers, cookies=cookies, params={"q": query}, timeout=15
    )
    resp.raise_for_status()
    results = resp.json().get("result", [])
    return results[0] if results else None


def ensure_database(headers, cookies) -> int | None:
    """Trả về id nếu đã có sẵn hoặc tạo mới thành công; None nếu chưa xong, để main() lặp lại
    toàn bộ chu trình đăng nhập + kiểm tra + tạo mới."""
    existing = find_one(headers, cookies, "/api/v1/database/", "database_name", "DX-Farm")
    if existing:
        print(f"[bootstrap] database 'DX-Farm' đã tồn tại, id={existing['id']}")
        return existing["id"]

    payload = {
        "database_name": "DX-Farm",
        "sqlalchemy_uri": DXOS_DB_URI,
        "expose_in_sqllab": True,
        "allow_run_async": False,
    }
    resp = requests.post(
        f"{BASE_URL}/api/v1/database/", headers=headers, cookies=cookies, json=payload, timeout=15
    )
    if resp.status_code < 300:
        db_id = resp.json()["id"]
        print(f"[bootstrap] tạo mới database 'DX-Farm', id={db_id}")
        return db_id
    print(f"[bootstrap] thử tạo database 'DX-Farm' thất bại: {resp.status_code} {resp.text[:300]}")
    return None


def ensure_dataset(headers, cookies, database_id: int, table_name: str) -> int:
    existing = find_one(headers, cookies, "/api/v1/dataset/", "table_name", table_name)
    if existing:
        print(f"[bootstrap] dataset '{table_name}' đã tồn tại, id={existing['id']}")
        return existing["id"]

    payload = {"database": database_id, "schema": "public", "table_name": table_name}
    resp = requests.post(
        f"{BASE_URL}/api/v1/dataset/", headers=headers, cookies=cookies, json=payload, timeout=15
    )
    resp.raise_for_status()
    dataset_id = resp.json()["id"]
    print(f"[bootstrap] tạo mới dataset '{table_name}', id={dataset_id}")
    return dataset_id


def has_dashboard_export() -> bool:
    return os.path.exists(DASHBOARD_EXPORT_PATH) and os.path.getsize(DASHBOARD_EXPORT_PATH) > 0


def import_dashboard_bundle(headers, cookies) -> bool:
    """Import database+dataset+chart+dashboard từ file export đã build sẵn (xem README trong
    thư mục này), khớp theo UUID, overwrite=true nên chạy lại nhiều lần an toàn. Truyền mật
    khẩu Postgres thật qua tham số `passwords` khớp đường dẫn file trong zip vì Superset mask
    mật khẩu lúc export."""
    with zipfile.ZipFile(DASHBOARD_EXPORT_PATH) as zf:
        db_yaml_paths = [n for n in zf.namelist() if n.endswith("/databases/DX-Farm.yaml")]

    real_password = urlparse(DXOS_DB_URI).password or ""
    # Superset khớp mật khẩu theo đường dẫn đã bỏ thư mục gốc của zip.
    passwords = {path.split("/", 1)[1]: real_password for path in db_yaml_paths}

    with open(DASHBOARD_EXPORT_PATH, "rb") as f:
        files = {"formData": ("farm_dashboard_export.zip", f.read(), "application/zip")}
    data = {"overwrite": "true", "passwords": json.dumps(passwords)}
    resp = requests.post(
        f"{BASE_URL}/api/v1/dashboard/import/",
        headers={k: v for k, v in headers.items() if k != "Referer"} | {"Referer": BASE_URL},
        cookies=cookies,
        files=files,
        data=data,
        timeout=60,
    )
    if resp.status_code >= 300:
        print(f"[bootstrap] import dashboard thất bại: {resp.status_code} {resp.text[:500]}")
        return False
    print("[bootstrap] import dashboard mẫu (database+dataset+chart+dashboard) thành công")
    return True


def main() -> None:
    wait_until_healthy()

    # Lặp lại toàn bộ chu trình đăng nhập + kiểm tra + tạo mới (không chỉ retry bước POST) vì
    # request đầu tiên ngay sau khi Superset vừa khởi động đôi khi thất bại thoáng qua.
    retries, delay_seconds = 8, 10
    use_export = has_dashboard_export()
    ok = False
    for attempt in range(1, retries + 1):
        try:
            access_token = login()
            headers, cookies = build_session(access_token)
        except requests.RequestException as exc:
            print(f"[bootstrap] đăng nhập lần {attempt}/{retries} thất bại: {exc}")
            time.sleep(delay_seconds)
            continue

        if use_export:
            # Bundle export là nguồn sự thật duy nhất khi đã có (database+dataset+chart+
            # dashboard khớp UUID cố định).
            ok = import_dashboard_bundle(headers, cookies)
        else:
            database_id = ensure_database(headers, cookies)
            if database_id is not None:
                for table in DATASET_TABLES:
                    ensure_dataset(headers, cookies, database_id, table)
                ok = True

        if ok:
            break
        print(f"[bootstrap] chu trình đăng nhập+kết nối lần {attempt}/{retries} chưa thành công")
        time.sleep(delay_seconds)

    if not ok:
        raise SystemExit(f"[bootstrap] bootstrap datasource/dashboard thất bại sau {retries} lần")


if __name__ == "__main__":
    main()
