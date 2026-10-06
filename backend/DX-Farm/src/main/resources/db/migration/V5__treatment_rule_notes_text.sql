-- Treatment-rule notes combine application guidance, provenance and authorization details.
-- Some reviewed seed records exceed PostgreSQL's default varchar(255) mapping.
ALTER TABLE treatment_rules
ALTER COLUMN notes TYPE text;
