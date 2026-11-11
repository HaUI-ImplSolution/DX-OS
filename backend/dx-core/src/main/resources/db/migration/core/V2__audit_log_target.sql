CREATE INDEX idx_audit_log_target ON audit_log (target_type, target_id, occurred_at DESC, id DESC);
