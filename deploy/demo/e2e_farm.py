# SPDX-License-Identifier: Apache-2.0
"""Farm end-to-end scenario: household scope, poka-yoke on GPS and harvest date, approval workflow
and its roles, idempotent upload, image quality check, diagnosis approval and the pesticide harvest
lock. The lock step needs an approved treatment rule for Rice___Blast; without one the run checks
that the diagnosis stays review-only, unless require_rule asks for the full chain.
"""
import datetime
import io
import uuid

import dxos_client as dx
import seed_farm
from dxos_client import error_code, expect

DISEASE = "Rice___Blast"


def run(base_url, steps, require_rule=False, **_):
    accounts = seed_farm.ACCOUNTS
    admin = dx.sign_in(base_url, accounts["admin"])
    engineer = dx.sign_in(base_url, accounts["engineer"])
    farmer = dx.sign_in(base_url, accounts["farmer"])
    seed_farm.ensure_farmer_household(admin)
    state = {}
    today = datetime.date.today()

    @steps("system status is public and reports the backend up")
    def _():
        status, body = dx.Api(base_url, "").call("GET", "/system/status")
        expect(status == 200, f"status {status}")
        services = {s["name"]: s["status"] for s in body["data"]["services"]}
        expect(services.get("backend") == "UP", services)
        expect(body["data"]["app"]["id"] == "farm", body["data"]["app"])

    @steps("engineer cannot create a parcel (403, ERR_FORBIDDEN)")
    def _():
        body = engineer.refused("POST", "/parcels", 403, {
            "areaHectares": "1.0", "latitude": "10.0", "longitude": "105.7", "householdCode": "H0001"})
        expect(error_code(body) == "ERR_FORBIDDEN", body)

    @steps("farmer parcel with a mocked GPS fix is refused (GPS_MOCKED)")
    def _():
        body = farmer.refused("POST", "/parcels", 422, {
            "areaHectares": "1.0", "latitude": "10.0050", "longitude": "105.6830",
            "gpsAccuracyMeters": "5", "mockedLocation": True})
        expect(error_code(body) == "GPS_MOCKED", body)

    @steps("farmer parcel waits for approval and cannot get a season yet")
    def _():
        parcel = farmer.ok("POST", "/parcels", {
            "areaHectares": "1.2", "location": "E2E: Phong Điền, Cần Thơ", "latitude": "10.0050",
            "longitude": "105.6830", "gpsAccuracyMeters": "8", "mockedLocation": False},
            headers={"Idempotency-Key": str(uuid.uuid4())})
        expect(parcel["approvalStatus"] == "PENDING", parcel["approvalStatus"])
        state["parcel"] = parcel
        variety = admin.ok("GET", "/rice-varieties")[0]
        state["variety"] = variety
        body = farmer.refused("POST", f"/parcels/{parcel['code']}/crop-seasons", 422, {
            "riceVarietyCode": variety["code"], "startDate": today.isoformat(), "seasonType": "THU_DONG"})
        expect(error_code(body) == "PARCEL_NOT_APPROVED", body)

    @steps("only an administrator approves the parcel")
    def _():
        case = dx.case_for(admin, "PARCEL_APPROVAL", state["parcel"]["id"])
        engineer.refused("PATCH", f"/workflow-cases/{case['id']}/approve", 403)
        admin.ok("PATCH", f"/workflow-cases/{case['id']}/approve")

    @steps("season request is approved and a harvest before the expected date is refused")
    def _():
        season = farmer.ok("POST", f"/parcels/{state['parcel']['code']}/crop-seasons", {
            "riceVarietyCode": state["variety"]["code"], "startDate": today.isoformat(),
            "seasonType": "THU_DONG"})
        expect(season["approvalStatus"] == "PENDING", season["approvalStatus"])
        case = dx.case_for(admin, "CROP_SEASON_APPROVAL", season["id"])
        admin.ok("PATCH", f"/workflow-cases/{case['id']}/approve")
        state["season"] = season
        body = farmer.refused("POST", f"/crop-seasons/{season['id']}/harvest", 422, {"yieldKg": "100"})
        expect(error_code(body) == "HARVEST_TOO_EARLY", body)

    @steps("upload is idempotent for the same key")
    def _():
        key = str(uuid.uuid4())
        first = seed_farm.upload_report(farmer, state["season"]["id"], seed_farm.leaf_photo(1), key)
        second = seed_farm.upload_report(farmer, state["season"]["id"], seed_farm.leaf_photo(1), key)
        expect(first["id"] == second["id"], (first["id"], second["id"]))
        state["report"] = first

    @steps("a blank photo fails the quality check")
    def _():
        from PIL import Image

        buffer = io.BytesIO()
        Image.new("RGB", (640, 480), "green").save(buffer, format="PNG")
        content, content_type = dx.multipart("image", "blank.png", buffer.getvalue(), "image/png")
        farmer.refused("POST", f"/crop-seasons/{state['season']['id']}/disease-reports", 422,
                       headers={"Content-Type": content_type}, raw=content)

    @steps("diagnosis waits for the engineer, then is approved")
    def _():
        report_id = state["report"]["id"]
        diagnosis = engineer.ok("POST", f"/disease-reports/{report_id}/diagnosis", {
            "diseaseCode": DISEASE, "confidence": 0.85, "modelVersion": "manual-input-v1"})
        expect(diagnosis["approvalStatus"] == "PENDING", diagnosis["approvalStatus"])
        visible = farmer.ok("GET", f"/disease-reports/{report_id}")["diagnosis"]
        expect(visible.get("diseaseCode") is None, "farmer sees the diagnosis before approval")
        state["diagnosis"] = engineer.ok("POST", f"/disease-reports/{report_id}/diagnosis/approve")

    rule = state.get("diagnosis", {}).get("treatmentRule") if state.get("diagnosis") else None
    if require_rule and not rule:
        steps.fail("approved treatment rule exists", AssertionError("no rule for " + DISEASE))
    elif rule:
        @steps("spraying report blocks the harvest until the engineer decides, then isolation applies")
        def _():
            report_id = state["report"]["id"]
            farmer.ok("POST", f"/disease-reports/{report_id}/treatment-application/self-report",
                      {"doseAmount": rule["doseMin"], "appliedAt": today.isoformat()})
            body = farmer.refused("POST", f"/crop-seasons/{state['season']['id']}/harvest", 422,
                                  {"yieldKg": "100", "confirmEarly": True, "earlyReason": "E2E"})
            expect(error_code(body) == "TREATMENT_PENDING_CONFIRMATION", body)
            application = engineer.ok("POST", f"/disease-reports/{report_id}/treatment-application/confirm")
            body = farmer.refused("POST", f"/crop-seasons/{state['season']['id']}/harvest", 422,
                                  {"yieldKg": "100", "confirmEarly": True, "earlyReason": "E2E"})
            expect(error_code(body) == "POKA_YOKE_VIETGAP_VIOLATION", body)
            expect(body["data"]["harvestLockedUntil"] == application["harvestLockedUntil"], body)
    else:
        @steps("without an approved treatment rule the diagnosis stays review-only")
        def _():
            expect(state["diagnosis"]["treatmentRule"] is None, "unexpected treatment rule")
