-- Treatment-rule notes combine application guidance, provenance and authorization details.
-- Keep room for future reviewed rules without truncating their audit trail.
ALTER TABLE treatment_rules
ALTER COLUMN notes TYPE text;
