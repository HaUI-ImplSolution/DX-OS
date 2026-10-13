# SPDX-License-Identifier: Apache-2.0
"""Farm demo data: sample accounts, households, parcels, seasons and disease reports.

Called by seed_demo.py when the running app is Farm. Everything goes through the same endpoints
the apps use, so the approval workflow, quality check and audit columns are exercised for real.
Photos come from --images (real leaf photos); without it a synthetic photo is used, which is
enough for the screens but not for a meaningful AI diagnosis.
"""
import datetime
import pathlib
import sys
import uuid

import dxos_client as dx

DEMO_MARK = "Demo:"
MOBILE_CLIENT = ("farm-mobile", "dxfarm://callback")

ACCOUNTS = {
    "admin": dx.Account("admin1", "admin123", dx.WEB_CLIENT, "ADMIN", "Quản Trị", "Viên"),
    "engineer": dx.Account("engineer1", "engineer123", dx.WEB_CLIENT, "ENGINEER", "Trần", "Kỹ Sư"),
    "farmer": dx.Account("farmer1", "farmer123", MOBILE_CLIENT, "FARMER", "Nguyễn Văn", "Nông"),
}

# Approximate field positions in the Mekong Delta; areas in hectares.
PARCELS = [
    {"location": "Demo: Phong Điền, Cần Thơ", "latitude": "10.0050", "longitude": "105.6830", "area": "1.20"},
    {"location": "Demo: Mỹ Xuyên, Sóc Trăng", "latitude": "9.5800", "longitude": "105.9900", "area": "0.85"},
    {"location": "Demo: Châu Phú, An Giang", "latitude": "10.5700", "longitude": "105.2300", "area": "2.00"},
]
OTHER_HOUSEHOLDS = [
    {"fullName": "Trần Thị Mai", "username": "demo.hogd2", "address": "Tháp Mười, Đồng Tháp",
     "parcel": {"location": "Demo: Tháp Mười, Đồng Tháp", "latitude": "10.5600", "longitude": "105.8400", "area": "1.50"}},
    {"fullName": "Lê Văn Phước", "username": "demo.hogd3", "address": "Vĩnh Thuận, Kiên Giang",
     "parcel": {"location": "Demo: Vĩnh Thuận, Kiên Giang", "latitude": "9.5000", "longitude": "105.2300", "area": "1.10"}},
]
FARMER_HOUSEHOLD_NAME = "Nguyễn Văn Hậu"


def leaf_photo(variant=0):
    """Synthetic leaf-like PNG that passes the image-quality check (no model weights needed)."""

    def paint(draw):
        for x in range(-40 - variant * 7, 680, 80):
            draw.polygon([(x, 480), (x + 24, 0), (x + 66, 480)], fill=(35, 105, 38))
            draw.line([(x + 25, 0), (x + 32, 480)], fill=(145, 180, 80), width=3)
            for y in range(20, 480, 14):
                draw.line([(x + 31, y), (x + 54, y + 10)], fill=(90, 150, 55), width=2)
                draw.ellipse((x + 30, y + 3, x + 39, y + 11), fill=(165, 105, 55))

    return dx.png_image(paint, color=(95 + variant % 5 * 3, 125, 65))


def upload_report(api, season_id, image, key=None, filename="leaf"):
    png = image.startswith(b"\x89PNG")
    content, content_type = dx.multipart(
        "image", f"{filename}.{'png' if png else 'jpg'}", image, "image/png" if png else "image/jpeg")
    headers = {"Content-Type": content_type, "Idempotency-Key": key or str(uuid.uuid4())}
    return api.ok("POST", f"/crop-seasons/{season_id}/disease-reports", headers=headers, raw=content)


def ensure_farmer_household(admin):
    households = [h for h in admin.ok("GET", "/households") if h["fullName"] == FARMER_HOUSEHOLD_NAME]
    if households and households[0]["accountLinked"]:
        return
    if not households:
        dx.psql("WITH n AS (INSERT INTO workspace_code_counter (type, last_number) "
                "VALUES ('HOUSEHOLD', 1) ON CONFLICT (type) "
                "DO UPDATE SET last_number = workspace_code_counter.last_number + 1 "
                "RETURNING last_number) "
                "INSERT INTO workspaces (code, type, name, address, created_at, updated_at) "
                "SELECT 'H' || lpad(last_number::text, 4, '0'), 'HOUSEHOLD', "
                f"'{FARMER_HOUSEHOLD_NAME}', 'Cần Thơ', now(), now() FROM n")
        households = [h for h in admin.ok("GET", "/households") if h["fullName"] == FARMER_HOUSEHOLD_NAME]
    admin.ok("PATCH", f"/households/{households[0]['code']}/account", {"username": "farmer1"})
    print(f"Linked farmer1 to household {households[0]['code']}")


def approve(admin, case_type, entity_id):
    case = dx.case_for(admin, case_type, entity_id)
    admin.ok("PATCH", f"/workflow-cases/{case['id']}/approve")


def parcel_body(spec, **extra):
    return {"areaHectares": spec["area"], "location": spec["location"], "latitude": spec["latitude"],
            "longitude": spec["longitude"], **extra}


def photos(images_dir, count):
    if images_dir:
        files = sorted(p for p in pathlib.Path(images_dir).iterdir()
                       if p.suffix.lower() in (".jpg", ".jpeg"))
        if files:
            return [files[i % len(files)].read_bytes() for i in range(count)]
    return [leaf_photo(i) for i in range(count)]


def diagnose(engineer, report_id, use_ai):
    if use_ai:
        engineer.ok("POST", f"/disease-reports/{report_id}/diagnosis/ai")
    else:
        engineer.ok("POST", f"/disease-reports/{report_id}/diagnosis",
                    {"diseaseCode": "Rice___Blast", "confidence": 0.85, "modelVersion": "manual-input-v1"})


def seed(base_url, images=None):
    dx.ensure_accounts(base_url, ACCOUNTS.values())
    admin = dx.sign_in(base_url, ACCOUNTS["admin"])
    engineer = dx.sign_in(base_url, ACCOUNTS["engineer"])
    farmer = dx.sign_in(base_url, ACCOUNTS["farmer"])
    ensure_farmer_household(admin)

    if any(p["location"].startswith(DEMO_MARK) for p in farmer.ok("GET", "/parcels")):
        print("Demo data already present, nothing to do.")
        return

    varieties = admin.ok("GET", "/rice-varieties")
    if not varieties:
        sys.exit("No rice variety in the catalogue.")
    variety = varieties[0]
    today = datetime.date.today()

    # Two parcels and their seasons go through the farmer path: request, then administrator approval.
    parcels = []
    for spec in PARCELS[:2]:
        parcel = farmer.ok("POST", "/parcels", parcel_body(spec, gpsAccuracyMeters="8", mockedLocation=False),
                           headers={"Idempotency-Key": str(uuid.uuid4())})
        parcels.append(parcel)
    for parcel in parcels:
        approve(admin, "PARCEL_APPROVAL", parcel["id"])

    active_start = today - datetime.timedelta(days=45)
    harvested_start = today - datetime.timedelta(days=variety["growthDaysMax"] + 5)
    active = farmer.ok("POST", f"/parcels/{parcels[0]['code']}/crop-seasons",
                       {"riceVarietyCode": variety["code"], "startDate": active_start.isoformat(),
                        "seasonType": "THU_DONG"})
    finished = farmer.ok("POST", f"/parcels/{parcels[1]['code']}/crop-seasons",
                         {"riceVarietyCode": variety["code"], "startDate": harvested_start.isoformat(),
                          "seasonType": "HE_THU"})
    approve(admin, "CROP_SEASON_APPROVAL", active["id"])
    approve(admin, "CROP_SEASON_APPROVAL", finished["id"])
    farmer.ok("POST", f"/crop-seasons/{finished['id']}/harvest",
              {"yieldKg": "7200", "actualHarvestDate": today.isoformat()})

    # A third parcel stays pending so the approval queue has an entry.
    farmer.ok("POST", "/parcels", parcel_body(PARCELS[2], gpsAccuracyMeters="12", mockedLocation=False),
              headers={"Idempotency-Key": str(uuid.uuid4())})

    # Reports on the active season: one diagnosed and approved, one waiting, one sent back for a retake.
    reports = [upload_report(farmer, active["id"], image, filename=f"leaf{i}")
               for i, image in enumerate(photos(images, 3))]
    diagnose(engineer, reports[0]["id"], use_ai=bool(images))
    approved = engineer.ok("POST", f"/disease-reports/{reports[0]['id']}/diagnosis/approve")
    diagnose(engineer, reports[1]["id"], use_ai=bool(images))
    engineer.ok("PATCH", f"/disease-reports/{reports[2]['id']}/request-retake",
                {"reason": "Ảnh chưa thấy rõ vết bệnh, hãy chụp lại gần lá hơn."})

    rule = approved.get("treatmentRule")
    if rule:
        farmer.ok("POST", f"/disease-reports/{reports[0]['id']}/treatment-application/self-report",
                  {"doseAmount": rule["doseMin"], "appliedAt": today.isoformat()})
        engineer.ok("POST", f"/disease-reports/{reports[0]['id']}/treatment-application/confirm")
        print("Treatment application confirmed: the season is under pesticide isolation.")
    else:
        print("No approved treatment rule for this disease: the diagnosis stays review-only.")

    # Households without a login in the demo: their parcels and seasons are started by the administrator.
    existing = {h["fullName"] for h in admin.ok("GET", "/households")}
    for item in OTHER_HOUSEHOLDS:
        if item["fullName"] in existing:
            continue
        created = admin.ok("POST", "/households", {k: item[k] for k in ("fullName", "username", "address")})
        household = created["household"]
        parcel = admin.ok("POST", "/parcels", parcel_body(item["parcel"], householdCode=household["code"]))
        admin.ok("POST", f"/parcels/{parcel['code']}/crop-seasons",
                 {"riceVarietyCode": variety["code"], "startDate": active_start.isoformat(),
                  "seasonType": "THU_DONG"})
        print(f"Household {household['code']} {item['fullName']}: account {created['username']}, "
              f"temporary password {created['temporaryPassword']}")

    print("Demo data created.")
