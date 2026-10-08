#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""End-to-end run against a fresh stack, through the gateway.

    python3 deploy/demo/e2e.py [--base-url URL] [--require-rule]

Covers: household scope, poka-yoke on GPS and harvest date, approval workflow and its roles,
idempotent upload, image quality check, diagnosis approval and the pesticide harvest lock.
The lock step needs an approved treatment rule for Rice___Blast; without one the run checks that
the diagnosis stays review-only, unless --require-rule asks for the full chain.
Exit code 0 when every step passes.
"""
import argparse
import datetime
import io
import sys
import uuid

import dxfarm_client as dx
import seed_demo

DISEASE = "Rice___Blast"
RESULTS = []


def step(name):
    def decorate(function):
        try:
            function()
            RESULTS.append((name, None))
            print(f"PASS  {name}")
        except Exception as error:  # noqa: BLE001 - every failure is reported, the run continues
            RESULTS.append((name, error))
            print(f"FAIL  {name}: {error}")
        return function

    return decorate


def expect(condition, message):
    if not condition:
        raise AssertionError(message)


def error_code(body):
    return body.get("errorCode") if body else None


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--base-url", default=dx.DEFAULT_BASE_URL)
    parser.add_argument("--require-rule", action="store_true", help="fail when no treatment rule exists")
    args = parser.parse_args()

    admin = dx.sign_in(args.base_url, "admin")
    engineer = dx.sign_in(args.base_url, "engineer")
    farmer = dx.sign_in(args.base_url, "farmer")
    seed_demo.ensure_farmer_household(admin, farmer)
    state = {}
    today = datetime.date.today()

    @step("system status is public and reports the backend up")
    def _():
        status, body = dx.Api(args.base_url, "").call("GET", "/system/status")
        expect(status == 200, f"status {status}")
        services = {s["name"]: s["status"] for s in body["data"]["services"]}
        expect(services.get("backend") == "UP", services)

    @step("engineer cannot create a parcel (403, ERR_FORBIDDEN)")
    def _():
        body = engineer.refused("POST", "/parcels", 403, {
            "areaHectares": "1.0", "latitude": "10.0", "longitude": "105.7", "householdCode": "H0001"})
        expect(error_code(body) == "ERR_FORBIDDEN", body)

    @step("farmer parcel with a mocked GPS fix is refused (GPS_MOCKED)")
    def _():
        body = farmer.refused("POST", "/parcels", 422, {
            "areaHectares": "1.0", "latitude": "10.0050", "longitude": "105.6830",
            "gpsAccuracyMeters": "5", "mockedLocation": True})
        expect(error_code(body) == "GPS_MOCKED", body)

    @step("farmer parcel waits for approval and cannot get a season yet")
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

    @step("only an administrator approves the parcel")
    def _():
        case = dx.case_for(admin, "PARCEL_APPROVAL", state["parcel"]["id"])
        engineer.refused("PATCH", f"/workflow-cases/{case['id']}/approve", 403)
        admin.ok("PATCH", f"/workflow-cases/{case['id']}/approve")

    @step("season request is approved and a harvest before the expected date is refused")
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

    @step("upload is idempotent for the same key")
    def _():
        key = str(uuid.uuid4())
        first = farmer.upload_report(state["season"]["id"], dx.leaf_photo(1), key)
        second = farmer.upload_report(state["season"]["id"], dx.leaf_photo(1), key)
        expect(first["id"] == second["id"], (first["id"], second["id"]))
        state["report"] = first

    @step("a blank photo fails the quality check")
    def _():
        from PIL import Image

        buffer = io.BytesIO()
        Image.new("RGB", (640, 480), "green").save(buffer, format="PNG")
        content, content_type = dx.multipart("image", "blank.png", buffer.getvalue(), "image/png")
        farmer.refused("POST", f"/crop-seasons/{state['season']['id']}/disease-reports", 422,
                       headers={"Content-Type": content_type}, raw=content)

    @step("diagnosis waits for the engineer, then is approved")
    def _():
        report_id = state["report"]["id"]
        diagnosis = engineer.ok("POST", f"/disease-reports/{report_id}/diagnosis", {
            "diseaseCode": DISEASE, "confidence": 0.85, "modelVersion": "manual-input-v1"})
        expect(diagnosis["approvalStatus"] == "PENDING", diagnosis["approvalStatus"])
        visible = farmer.ok("GET", f"/disease-reports/{report_id}")["diagnosis"]
        expect(visible.get("diseaseCode") is None, "farmer sees the diagnosis before approval")
        state["diagnosis"] = engineer.ok("POST", f"/disease-reports/{report_id}/diagnosis/approve")

    rule = state.get("diagnosis", {}).get("treatmentRule") if state.get("diagnosis") else None
    if args.require_rule and not rule:
        RESULTS.append(("approved treatment rule exists", AssertionError("no rule for " + DISEASE)))
        print("FAIL  approved treatment rule exists")
    elif rule:
        @step("spraying report blocks the harvest until the engineer decides, then isolation applies")
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
        @step("without an approved treatment rule the diagnosis stays review-only")
        def _():
            expect(state["diagnosis"]["treatmentRule"] is None, "unexpected treatment rule")

    failed = [name for name, error in RESULTS if error]
    print(f"\n{len(RESULTS) - len(failed)}/{len(RESULTS)} steps passed")
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
