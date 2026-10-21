-- When a farmer's spraying report was last submitted; the wait for an engineer is measured from it.
ALTER TABLE treatment_applications ADD COLUMN reported_at timestamp(6) with time zone;
UPDATE treatment_applications SET reported_at = now() WHERE confirmation_status = 'PENDING_CONFIRMATION';
