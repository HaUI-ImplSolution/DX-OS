-- Read-only views for BI (Superset). They carry ids, codes and groups only: no ticket text,
-- comments, names or booking titles. The BI role reads these views and nothing else, see
-- SupersetReadOnlyRoleSeeder.

CREATE VIEW vw_lab_rooms AS
SELECT r.id, r.code, r.building, r.room_type, r.capacity, r.status
FROM rooms r;

CREATE VIEW vw_lab_devices AS
SELECT d.id, d.asset_code, d.device_type, d.model, d.room_id, d.status, d.purchased_on,
       d.warranty_until
FROM devices d;

CREATE VIEW vw_lab_tickets AS
SELECT t.id, t.code, c.code AS category_code, c.name AS category_name, t.priority, t.status,
       t.room_id, t.device_id, t.assignee_id, t.created_at, t.first_responded_at, t.resolved_at,
       t.closed_at, t.response_due_at, t.resolution_due_at, t.overdue, t.reopen_count, t.rating,
       (t.resolved_at IS NOT NULL AND t.resolved_at <= t.resolution_due_at) AS resolved_in_sla
FROM tickets t
JOIN ticket_categories c ON c.id = t.category_id;

CREATE VIEW vw_lab_device_loans AS
SELECT l.id, l.device_id, l.borrower_role, l.status, l.created_at, l.approved_at, l.due_date,
       l.returned_at, l.return_condition
FROM device_loans l;

CREATE VIEW vw_lab_room_bookings AS
SELECT b.id, b.room_id, b.requester_role, b.status, b.attendees, b.starts_at, b.ends_at,
       b.created_at
FROM room_bookings b;
