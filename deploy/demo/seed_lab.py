# SPDX-License-Identifier: Apache-2.0
"""Lab demo data: sample accounts, class and department, rooms, devices, support tickets in every
state, device loans and room bookings.

Called by seed_demo.py when the running app is Lab. Everything goes through the Lab endpoints, so
the workflow, SLA deadlines and booking rules are exercised for real; only the due date of the
overdue loan is set in the database. Requests, loans and bookings
carry fixed Idempotency-Keys and each item is driven only up to its target state, so a second run
creates nothing twice. Booking times are relative to the day of the run.
"""
import datetime
import json
import uuid

import dxos_client as dx

NAMESPACE = uuid.UUID("5f0b0a8e-6d54-4a52-9a43-3f1b7a3b6f10")
LOCAL_TZ = datetime.timezone(datetime.timedelta(hours=7))

ACCOUNTS = {
    "admin": dx.Account("admin1", "admin123", dx.WEB_CLIENT, "ADMIN", "Quản Trị", "Viên"),
    "tech1": dx.Account("tech1", "tech1234", dx.WEB_CLIENT, "TECHNICIAN", "Phạm Quốc", "Dũng"),
    "tech2": dx.Account("tech2", "tech1234", dx.WEB_CLIENT, "TECHNICIAN", "Hoàng Thị", "Em"),
    "lecturer": dx.Account("lecturer1", "lecturer123", dx.WEB_CLIENT, "LECTURER", "Lê Minh", "Cường"),
    "student1": dx.Account("student1", "student123", dx.WEB_CLIENT, "STUDENT", "Nguyễn Văn", "An"),
    "student2": dx.Account("student2", "student123", dx.WEB_CLIENT, "STUDENT", "Trần Thị", "Bình"),
}

WORKSPACES = [
    {"type": "CLASS", "name": "Lớp CNTT K15A", "members": ["student1", "student2"]},
    {"type": "DEPARTMENT", "name": "Bộ môn Công nghệ phần mềm", "members": ["lecturer1"]},
]

ROOMS = [
    {"code": "A101", "building": "Nhà A", "name": "Phòng máy A101", "roomType": "COMPUTER_LAB", "capacity": 40},
    {"code": "A102", "building": "Nhà A", "name": "Phòng máy A102", "roomType": "COMPUTER_LAB", "capacity": 40},
    {"code": "A103", "building": "Nhà A", "name": "Phòng máy A103", "roomType": "COMPUTER_LAB", "capacity": 30},
    {"code": "A201", "building": "Nhà A", "name": "Phòng học A201", "roomType": "CLASSROOM", "capacity": 60},
    {"code": "B301", "building": "Nhà B", "name": "Phòng họp B301", "roomType": "MEETING_ROOM", "capacity": 20},
]
ROOMS_UNDER_MAINTENANCE = ["A103"]

DEVICES = [
    {"assetCode": "LAP-001", "deviceType": "Laptop", "model": "Dell Latitude 3540"},
    {"assetCode": "LAP-002", "deviceType": "Laptop", "model": "Dell Latitude 3540"},
    {"assetCode": "LAP-003", "deviceType": "Laptop", "model": "Lenovo ThinkPad E14"},
    {"assetCode": "PRJ-001", "deviceType": "Máy chiếu", "model": "Epson EB-X49"},
    {"assetCode": "PRJ-002", "deviceType": "Máy chiếu", "model": "Epson EB-X49"},
    {"assetCode": "SWT-A101", "deviceType": "Thiết bị mạng", "model": "Cisco SG350-28", "room": "A101"},
    {"assetCode": "PRN-A201", "deviceType": "Máy in", "model": "HP LaserJet Pro M404", "room": "A201"},
]

# Each ticket is driven from NEW to `target`: ASSIGNED, IN_PROGRESS, WAITING_USER, RESOLVED, CLOSED,
# REJECTED or CANCELLED. `technician` takes the ticket; `rating` closes it.
TICKETS = [
    {"key": "t1", "by": "student1", "category": "PROJECTOR", "title": "Máy chiếu phòng A201 không lên hình",
     "description": "Máy chiếu bật đèn nhưng không có tín hiệu từ laptop, đã thử đổi cáp HDMI.",
     "room": "A201", "target": "CLOSED", "technician": "tech1", "rating": 5, "attach": True},
    {"key": "t2", "by": "student2", "category": "NETWORK", "title": "Không vào được Wi-Fi sinh viên",
     "description": "Điện thoại và laptop đều báo sai mật khẩu dù đã nhập đúng.",
     "target": "CLOSED", "technician": "tech2", "rating": 4},
    {"key": "t3", "by": "student1", "category": "ACCOUNT", "title": "Quên mật khẩu tài khoản học liệu",
     "description": "Cần đặt lại mật khẩu tài khoản học liệu của lớp.",
     "target": "RESOLVED", "technician": "tech1"},
    {"key": "t4", "by": "student2", "category": "COMPUTER", "title": "Máy số 12 phòng A101 khởi động chậm",
     "description": "Máy mất hơn năm phút mới vào được Windows.",
     "room": "A101", "target": "IN_PROGRESS", "technician": "tech1"},
    {"key": "t5", "by": "student1", "category": "SOFTWARE", "title": "Cần cài MATLAB cho phòng A102",
     "description": "Học phần Xử lý tín hiệu cần MATLAB trên các máy phòng A102.",
     "room": "A102", "target": "WAITING_USER", "technician": "tech2"},
    {"key": "t6", "by": "lecturer", "category": "EXAM_SUPPORT", "title": "Cả phòng A101 mất mạng giữa giờ thi",
     "description": "Cả phòng máy mất mạng giữa giờ thi thực hành, cần xử lý gấp.",
     "room": "A101", "priority": "URGENT", "target": "ASSIGNED", "technician": "tech2"},
    {"key": "t7", "by": "student2", "category": "PRINTER", "title": "Máy in tầng 2 nhà A kẹt giấy",
     "description": "Máy in báo kẹt giấy nhưng mở khay không thấy giấy.",
     "device": "PRN-A201", "target": "NEW"},
    {"key": "t8", "by": "student1", "category": "OTHER", "title": "Loa phòng họp B301 bị rè",
     "description": "Loa phòng họp phát ra tiếng rè khi tăng âm lượng.",
     "room": "B301", "target": "NEW"},
    {"key": "t9", "by": "lecturer", "category": "ACCOUNT", "title": "Xin cấp tài khoản cho lớp thực hành",
     "description": "Cần tạo 40 tài khoản tạm cho buổi thực hành tuần sau.",
     "target": "NEW"},
    {"key": "t10", "by": "student2", "category": "OTHER", "title": "Đề nghị đổi chỗ ngồi trong phòng máy",
     "description": "Tôi muốn đổi sang máy gần cửa sổ.",
     "target": "REJECTED"},
    {"key": "t11", "by": "student1", "category": "NETWORK", "title": "Gửi trùng yêu cầu về Wi-Fi",
     "description": "Yêu cầu này gửi nhầm, xin hủy.",
     "target": "CANCELLED"},
    {"key": "t12", "by": "student2", "category": "COMPUTER", "title": "Chuột phòng A102 bị hỏng",
     "description": "Hai chuột ở dãy cuối phòng A102 không di chuyển được con trỏ.",
     "room": "A102", "target": "CLOSED", "technician": "tech1", "rating": 3, "reopen": True},
    {"key": "t13", "by": "student2", "category": "ACCOUNT", "title": "Không đăng nhập được email trường",
     "description": "Đăng nhập mail.haui.edu.vn báo sai mật khẩu, cần đổi mật khẩu email.",
     "target": "RESOLVED", "technician": "tech2"},
    {"key": "t14", "by": "lecturer", "category": "SOFTWARE", "title": "Máy phòng A102 nhiễm virus, tường lửa bị tắt",
     "description": "Cần bật lại tường lửa Windows và quét virus cho các máy dãy đầu.",
     "room": "A102", "target": "IN_PROGRESS", "technician": "tech1"},
    {"key": "t15", "by": "lecturer", "category": "SOFTWARE", "title": "Không khóa được màn hình sinh viên bằng Faronics",
     "description": "Faronics Insight trên máy giảng viên phòng A101 không hiện máy sinh viên nên không dùng được Blank Screen.",
     "room": "A101", "target": "NEW"},
]

# Loans: device, borrower, approver and the state to reach: PENDING, APPROVED, RETURNED,
# RETURNED_DAMAGED or REJECTED. `overdue_days` moves the due date of an approved loan into the past.
LOANS = [
    {"key": "l1", "by": "student2", "device": "LAP-001", "purpose": "Làm bài tập lớn môn Lập trình web",
     "target": "REJECTED", "approver": "lecturer"},
    {"key": "l2", "by": "student1", "device": "LAP-002", "purpose": "Thuyết trình đồ án nhóm",
     "target": "APPROVED", "approver": "lecturer", "overdue_days": 2},
    {"key": "l3", "by": "student1", "device": "LAP-003", "purpose": "Thực hành cài đặt hệ điều hành",
     "target": "RETURNED", "approver": "lecturer"},
    {"key": "l4", "by": "lecturer", "device": "PRJ-002", "purpose": "Dạy bù tại phòng không có máy chiếu",
     "target": "RETURNED_DAMAGED", "approver": "tech1"},
    {"key": "l5", "by": "student2", "device": "PRJ-001", "purpose": "Báo cáo chuyên đề nhóm",
     "target": "PENDING"},
]

# Bookings: (day offset, start hour, hours, room, state) with PENDING, APPROVED, REJECTED, CANCELLED.
BOOKINGS = [
    {"key": "b1", "day": 1, "start": 7.5, "hours": 2, "room": "A101", "attendees": 35,
     "title": "Thực hành Lập trình Java, lớp K15A", "target": "APPROVED"},
    {"key": "b2", "day": 1, "start": 13, "hours": 2, "room": "A102", "attendees": 30,
     "title": "Thực hành Cơ sở dữ liệu, lớp K15B", "target": "PENDING"},
    {"key": "b3", "day": 2, "start": 9, "hours": 2, "room": "A101", "attendees": 38,
     "title": "Kiểm tra thực hành Mạng máy tính", "target": "APPROVED"},
    {"key": "b4", "day": 3, "start": 14, "hours": 1.5, "room": "B301", "attendees": 18,
     "title": "Họp bộ môn", "target": "REJECTED"},
    {"key": "b5", "day": 1, "start": 9, "hours": 1, "room": "A201", "attendees": 40,
     "title": "Ôn tập cuối kỳ", "target": "CANCELLED"},
]


def request_key(name):
    return str(uuid.uuid5(NAMESPACE, name))


def ensure_workspaces(admin):
    for spec in WORKSPACES:
        found = [w for w in admin.ok("GET", f"/workspaces?type={spec['type']}") if w["name"] == spec["name"]]
        workspace = found[0] if found else admin.ok(
            "POST", "/workspaces", {"type": spec["type"], "name": spec["name"]})["workspace"]
        for username in spec["members"]:
            # 409 when the account already belongs to it
            status, _ = admin.call("POST", f"/workspaces/{workspace['code']}/members", {"username": username})
            if status not in (201, 409):
                raise dx.ApiError("POST", f"/workspaces/{workspace['code']}/members", status, None)


def ensure_rooms(tech):
    existing = {r["code"]: r for r in tech.ok("GET", "/rooms")}
    for spec in ROOMS:
        if spec["code"] not in existing:
            existing[spec["code"]] = tech.ok("POST", "/rooms", spec)
    for code in ROOMS_UNDER_MAINTENANCE:
        if existing[code]["status"] != "MAINTENANCE":
            tech.ok("PATCH", f"/rooms/{existing[code]['id']}/status", {"status": "MAINTENANCE"})
    return existing


def ensure_devices(tech, rooms):
    existing = {d["assetCode"]: d for d in tech.ok("GET", "/devices")}
    for spec in DEVICES:
        if spec["assetCode"] not in existing:
            body = {k: v for k, v in spec.items() if k != "room"}
            if "room" in spec:
                body["roomId"] = rooms[spec["room"]]["id"]
            existing[spec["assetCode"]] = tech.ok("POST", "/devices", body)
    return existing


def create_ticket(api, spec, rooms, devices):
    body = {"title": spec["title"], "description": spec["description"], "categoryCode": spec["category"]}
    if "priority" in spec:
        body["priority"] = spec["priority"]
    if "room" in spec:
        body["roomId"] = rooms[spec["room"]]["id"]
    if "device" in spec:
        body["deviceId"] = devices[spec["device"]]["id"]
    parts = [("ticket", None, json.dumps(body).encode(), "application/json")]
    if spec.get("attach"):
        def paint(draw):
            draw.rectangle((40, 40, 600, 440), outline=(30, 30, 30), width=6)
            draw.text((60, 60), "No signal", fill=(30, 30, 30))
        parts.append(("files", "man-hinh.png", dx.png_image(paint), "image/png"))
    content, content_type = dx.multipart_parts(parts)
    headers = {"Content-Type": content_type, "Idempotency-Key": request_key(spec["key"])}
    return api.ok("POST", "/tickets", headers=headers, raw=content)["ticket"]


def drive_ticket(users, ticket, spec):
    """Moves a ticket to its target state, doing only the steps that are still missing."""
    target = spec["target"]
    status = ticket["status"]
    if status == target or status in ("CLOSED", "REJECTED", "CANCELLED"):
        return
    ticket_id = ticket["id"]
    requester = users[spec["by"]]

    if target == "REJECTED":
        users["admin"].ok("POST", f"/tickets/{ticket_id}/reject",
                          {"reason": "Yêu cầu không thuộc phạm vi hỗ trợ CNTT."})
        return
    if target == "CANCELLED":
        requester.ok("POST", f"/tickets/{ticket_id}/cancel")
        return
    if target == "NEW":
        return

    technician = users[spec["technician"]]
    if status == "NEW":
        technician.ok("POST", f"/tickets/{ticket_id}/assign")
        status = "ASSIGNED"
    if target == "ASSIGNED":
        return
    if status == "ASSIGNED":
        technician.ok("POST", f"/tickets/{ticket_id}/start")
        technician.ok("POST", f"/tickets/{ticket_id}/comments",
                      {"body": "Đã tiếp nhận, kỹ thuật viên đang kiểm tra."})
        status = "IN_PROGRESS"
    if target == "IN_PROGRESS":
        return
    if target == "WAITING_USER":
        technician.ok("POST", f"/tickets/{ticket_id}/wait",
                      {"message": "Bạn cho biết tên học phần và phiên bản phần mềm cần cài?"})
        return
    if status == "IN_PROGRESS":
        technician.ok("POST", f"/tickets/{ticket_id}/resolve",
                      {"resolutionNote": "Đã xử lý và kiểm tra lại hoạt động bình thường.",
                       "rootCause": "Cấu hình thiết bị bị thay đổi ngoài ý muốn."})
        status = "RESOLVED"
    if status == "RESOLVED" and spec.get("reopen") and ticket["reopenCount"] == 0:
        requester.ok("POST", f"/tickets/{ticket_id}/reopen", {"reason": "Còn một chuột chưa dùng được."})
        technician.ok("POST", f"/tickets/{ticket_id}/resolve",
                      {"resolutionNote": "Đã thay thêm một chuột mới.",
                       "rootCause": "Chuột hết tuổi thọ sử dụng."})
    if target == "CLOSED":
        requester.ok("POST", f"/tickets/{ticket_id}/confirm",
                     {"rating": spec["rating"], "comment": "Xử lý nhanh, cảm ơn kỹ thuật viên."})


def seed_tickets(users, rooms, devices):
    for spec in TICKETS:
        ticket = create_ticket(users[spec["by"]], spec, rooms, devices)
        drive_ticket(users, ticket, spec)


def seed_loans(users, devices):
    today = datetime.date.today()
    overdue = []
    for spec in LOANS:
        borrower = users[spec["by"]]
        device = devices[spec["device"]]
        loan = borrower.ok("POST", "/device-loans",
                           {"deviceId": device["id"], "purpose": spec["purpose"],
                            "dueDate": (today + datetime.timedelta(days=5)).isoformat()},
                           headers={"Idempotency-Key": request_key(spec["key"])})
        if spec.get("overdue_days"):
            overdue.append((loan["id"], spec["overdue_days"]))
        target = spec["target"]
        if loan["status"] != "PENDING" or target == "PENDING":
            continue
        approver = users[spec["approver"]]
        if target == "REJECTED":
            approver.ok("POST", f"/device-loans/{loan['id']}/reject",
                        {"reason": "Thiết bị dành cho giờ học đã đặt lịch."})
            continue
        approver.ok("POST", f"/device-loans/{loan['id']}/approve")
        if target == "RETURNED":
            users["tech1"].ok("POST", f"/device-loans/{loan['id']}/return", {"condition": "GOOD"})
        elif target == "RETURNED_DAMAGED":
            users["tech1"].ok("POST", f"/device-loans/{loan['id']}/return",
                              {"condition": "DAMAGED", "note": "Đèn chiếu mờ, cần kiểm tra bóng đèn."})
    # The API refuses a due date in the past, so the overdue loan is moved back in the database.
    for loan_id, days in overdue:
        dx.psql(f"UPDATE device_loans SET due_date = CURRENT_DATE - {int(days)} "
                f"WHERE id = {int(loan_id)} AND status = 'APPROVED'")


def seed_maintenance(tech, devices):
    switch = devices["SWT-A101"]
    if not tech.ok("GET", f"/devices/{switch['id']}/maintenance"):
        entry = tech.ok("POST", f"/devices/{switch['id']}/maintenance",
                        {"kind": "INSPECTION", "note": "Kiểm tra định kỳ cổng mạng và nhiệt độ thiết bị."})
        tech.ok("POST", f"/devices/{switch['id']}/maintenance/{entry['id']}/complete",
                {"note": "Hoạt động bình thường."})


def seed_bookings(users, rooms):
    base = datetime.datetime.now(LOCAL_TZ).replace(hour=0, minute=0, second=0, microsecond=0)
    for spec in BOOKINGS:
        starts = base + datetime.timedelta(days=spec["day"], hours=spec["start"])
        ends = starts + datetime.timedelta(hours=spec["hours"])

        def instant(moment):
            return moment.astimezone(datetime.timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ")

        booking = users["lecturer"].ok(
            "POST", "/room-bookings",
            {"roomId": rooms[spec["room"]]["id"], "title": spec["title"], "attendees": spec["attendees"],
             "startsAt": instant(starts), "endsAt": instant(ends)},
            headers={"Idempotency-Key": request_key(spec["key"])})
        if booking["status"] != "PENDING" or spec["target"] == "PENDING":
            continue
        if spec["target"] == "APPROVED":
            users["tech1"].ok("POST", f"/room-bookings/{booking['id']}/approve")
        elif spec["target"] == "REJECTED":
            users["tech1"].ok("POST", f"/room-bookings/{booking['id']}/reject",
                              {"reason": "Phòng họp đã được giữ cho buổi làm việc khác."})
        elif spec["target"] == "CANCELLED":
            users["lecturer"].ok("POST", f"/room-bookings/{booking['id']}/cancel")


def seed(base_url, images=None):
    dx.ensure_accounts(base_url, ACCOUNTS.values())
    users = {key: dx.sign_in(base_url, account) for key, account in ACCOUNTS.items()}

    ensure_workspaces(users["admin"])
    rooms = ensure_rooms(users["tech1"])
    devices = ensure_devices(users["tech1"], rooms)
    seed_tickets(users, rooms, devices)
    seed_loans(users, devices)
    seed_maintenance(users["tech1"], devices)
    seed_bookings(users, rooms)
    print("Demo data created: workspaces, rooms, devices, tickets, loans and bookings.")
