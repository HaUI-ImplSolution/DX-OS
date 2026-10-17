"""Đăng ký kết nối Postgres (role chỉ-đọc) + dataset của app đang chạy (DXOS_APP) qua REST API
công khai của Superset. Idempotent - tìm theo tên trước khi tạo mới. App có bundle dashboard
export sẵn trong repo thì import bundle đó thay cho việc khai báo dataset từng view.
"""

import hashlib
import json
import os
import time
import zipfile
from urllib.parse import urlparse

import requests
import yaml

from chart_query_context import build_query_context, datasource_id_of, to_payload

BASE_URL = "http://localhost:8088"
ADMIN_USERNAME = os.environ.get("SUPERSET_ADMIN_USERNAME", "admin")
ADMIN_PASSWORD = os.environ["SUPERSET_ADMIN_PASSWORD"]
DXOS_DB_URI = os.environ["DXOS_DB_URI"]

# Per app: the Superset connection name, the read-only views to expose (superset_ro cannot read the
# tables) and the dashboard bundle to import (None: datasets only).
APPS = {
    "farm": {
        "database": "DX-Farm",
        "datasets": [
            "vw_parcels_approved",
            "vw_crop_seasons_approved",
            "vw_disease_reports_processed",
            "vw_diagnoses_approved",
            "vw_treatment_applications_confirmed",
            "vw_ai_diagnosis_outcomes",
            "vw_weather_daily",
        ],
        "dashboard": "/app/pythonpath/farm_dashboard_export.zip",
    },
    "lab": {
        "database": "DX-Lab",
        "datasets": [
            "vw_lab_rooms",
            "vw_lab_devices",
            "vw_lab_tickets",
            "vw_lab_device_loans",
            "vw_lab_room_bookings",
        ],
        "dashboard": None,
    },
}
APP_ID = os.environ.get("DXOS_APP", "farm")
if APP_ID not in APPS:
    raise SystemExit(f"[bootstrap] DXOS_APP={APP_ID} không hợp lệ, chọn một trong {sorted(APPS)}")
APP = APPS[APP_ID]
DATABASE_NAME = APP["database"]
DASHBOARD_EXPORT_PATH = APP["dashboard"]
STATIC_ASSETS_DIR = "/app/superset/static/assets"


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
    existing = find_one(headers, cookies, "/api/v1/database/", "database_name", DATABASE_NAME)
    if existing:
        print(f"[bootstrap] database '{DATABASE_NAME}' đã tồn tại, id={existing['id']}")
        return existing["id"]

    payload = {
        "database_name": DATABASE_NAME,
        "sqlalchemy_uri": DXOS_DB_URI,
        "expose_in_sqllab": True,
        "allow_run_async": False,
    }
    resp = requests.post(
        f"{BASE_URL}/api/v1/database/", headers=headers, cookies=cookies, json=payload, timeout=15
    )
    if resp.status_code < 300:
        db_id = resp.json()["id"]
        print(f"[bootstrap] tạo mới database '{DATABASE_NAME}', id={db_id}")
        return db_id
    print(f"[bootstrap] thử tạo database '{DATABASE_NAME}' thất bại: {resp.status_code} {resp.text[:300]}")
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
    return DASHBOARD_EXPORT_PATH is not None and os.path.exists(DASHBOARD_EXPORT_PATH) and os.path.getsize(DASHBOARD_EXPORT_PATH) > 0


def import_dashboard_bundle(headers, cookies) -> bool:
    """Import database+dataset+chart+dashboard từ file export đã build sẵn (xem README trong
    thư mục này), khớp theo UUID, overwrite=true nên chạy lại nhiều lần an toàn. Truyền mật
    khẩu Postgres thật qua tham số `passwords` khớp đường dẫn file trong zip vì Superset mask
    mật khẩu lúc export."""
    with zipfile.ZipFile(DASHBOARD_EXPORT_PATH) as zf:
        db_yaml_paths = [n for n in zf.namelist() if n.endswith(f"/databases/{DATABASE_NAME}.yaml")]

    real_password = urlparse(DXOS_DB_URI).password or ""
    # Superset khớp mật khẩu theo đường dẫn đã bỏ thư mục gốc của zip.
    passwords = {path.split("/", 1)[1]: real_password for path in db_yaml_paths}

    with open(DASHBOARD_EXPORT_PATH, "rb") as f:
        files = {"formData": (os.path.basename(DASHBOARD_EXPORT_PATH), f.read(), "application/zip")}
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


def bundle_dashboard_titles() -> list[str]:
    with zipfile.ZipFile(DASHBOARD_EXPORT_PATH) as zf:
        names = [n for n in zf.namelist() if "/dashboards/" in n and n.endswith(".yaml")]
        return [yaml.safe_load(zf.read(n))["dashboard_title"] for n in names]


def versioned_static_url(style: str) -> str:
    """URL dưới `/static/assets/` được gắn `?v=<hash nội dung file>` để trình duyệt tải lại
    style khi file đổi. URL khác hoặc file không có thì giữ nguyên."""
    url_prefix = "/static/assets/"
    if not style.startswith(url_prefix):
        return style
    base = style.split("?", 1)[0]
    path = os.path.join(STATIC_ASSETS_DIR, base[len(url_prefix):])
    if not os.path.isfile(path):
        return style
    with open(path, "rb") as f:
        return f"{base}?v={hashlib.sha256(f.read()).hexdigest()[:8]}"


def bundle_map_styles() -> dict[str, str]:
    """Nền bản đồ (`mapbox_style`) của từng chart bản đồ trong bundle, theo tên chart.
    Import không cập nhật chart đã tồn tại nên giá trị này được ghi lại sau import."""
    styles: dict[str, str] = {}
    with zipfile.ZipFile(DASHBOARD_EXPORT_PATH) as zf:
        for name in zf.namelist():
            if "/charts/" in name and name.endswith(".yaml"):
                chart = yaml.safe_load(zf.read(name))
                style = (chart.get("params") or {}).get("mapbox_style")
                if style:
                    styles[chart["slice_name"]] = versioned_static_url(style)
    return styles


def restore_query_contexts(headers, cookies) -> None:
    """Superset xóa query_context của chart mỗi lần import, nên sinh lại cho các chart của
    dashboard trong bundle để API dữ liệu chart, xuất CSV và báo cáo định kỳ chạy được."""
    titles = set(bundle_dashboard_titles())
    map_styles = bundle_map_styles()
    resp = requests.get(
        f"{BASE_URL}/api/v1/dashboard/",
        headers=headers,
        cookies=cookies,
        params={"q": "(columns:!(id,dashboard_title),page_size:100)"},
        timeout=15,
    )
    resp.raise_for_status()
    for dashboard in resp.json().get("result", []):
        if dashboard["dashboard_title"] not in titles:
            continue
        charts = requests.get(
            f"{BASE_URL}/api/v1/dashboard/{dashboard['id']}/charts",
            headers=headers,
            cookies=cookies,
            timeout=15,
        )
        charts.raise_for_status()
        for chart in charts.json().get("result", []):
            restore_chart_query_context(
                headers, cookies, chart["id"], chart["slice_name"], map_styles.get(chart["slice_name"])
            )


def restore_chart_query_context(
    headers, cookies, chart_id: int, chart_name: str, map_style: str | None = None
) -> None:
    resp = requests.get(
        f"{BASE_URL}/api/v1/chart/{chart_id}", headers=headers, cookies=cookies, timeout=15
    )
    resp.raise_for_status()
    form_data = json.loads(resp.json()["result"]["params"])
    payload = {}
    if map_style and form_data.get("mapbox_style") != map_style:
        form_data["mapbox_style"] = map_style
        payload["params"] = json.dumps(form_data)
    datasource_id = datasource_id_of(form_data)
    query_context = (
        build_query_context(form_data, datasource_id) if datasource_id is not None else None
    )
    if query_context is None:
        print(f"[bootstrap] chart '{chart_name}' ({form_data.get('viz_type')}) chưa hỗ trợ query_context, bỏ qua")
    else:
        payload |= to_payload(query_context)
    if not payload:
        return
    resp = requests.put(
        f"{BASE_URL}/api/v1/chart/{chart_id}",
        headers=headers,
        cookies=cookies,
        json=payload,
        timeout=15,
    )
    resp.raise_for_status()
    print(f"[bootstrap] chart '{chart_name}' đã cập nhật ({', '.join(payload)})")


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
            if ok:
                try:
                    restore_query_contexts(headers, cookies)
                except requests.RequestException as exc:
                    print(f"[bootstrap] sinh query_context cho chart thất bại, dashboard vẫn hiển thị: {exc}")
        else:
            database_id = ensure_database(headers, cookies)
            if database_id is not None:
                for table in APP["datasets"]:
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
