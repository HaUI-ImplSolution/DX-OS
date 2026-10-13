-- Reference data every Lab installation needs: the incident categories the classifier rules refer
-- to and the default SLA of each priority (minutes, calendar time).

INSERT INTO sla_policies (priority, response_minutes, resolution_minutes, updated_at) VALUES
    ('URGENT', 60, 240, now()),
    ('HIGH', 240, 1440, now()),
    ('NORMAL', 1440, 4320, now()),
    ('LOW', 2880, 10080, now())
ON CONFLICT (priority) DO NOTHING;

INSERT INTO ticket_categories (code, name, default_priority, active, created_at, updated_at) VALUES
    ('NETWORK', 'Mạng và Wi-Fi', 'NORMAL', true, now(), now()),
    ('ACCOUNT', 'Tài khoản và mật khẩu', 'NORMAL', true, now(), now()),
    ('PROJECTOR', 'Máy chiếu và màn hình', 'NORMAL', true, now(), now()),
    ('PRINTER', 'Máy in và máy quét', 'LOW', true, now(), now()),
    ('COMPUTER', 'Máy tính và phần cứng', 'NORMAL', true, now(), now()),
    ('SOFTWARE', 'Phần mềm và cài đặt', 'NORMAL', true, now(), now()),
    ('EXAM_SUPPORT', 'Hỗ trợ thi và thực hành', 'HIGH', true, now(), now()),
    ('OTHER', 'Khác', 'LOW', true, now(), now())
ON CONFLICT (code) DO NOTHING;
