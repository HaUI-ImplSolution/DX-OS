-- CI-only fixture so the E2E run can exercise the pesticide harvest lock.
-- It is not an approved treatment rule and is never part of the demo or seed data.
INSERT INTO pesticides (code, name, unit, active)
VALUES ('CI_FIXTURE', 'CI fixture pesticide', 'ml/ha', TRUE)
ON CONFLICT (code) DO NOTHING;

INSERT INTO treatment_rules (active, dose_min, dose_max, dose_unit, notes, phi_days, source, version,
                             disease_id, pesticide_id)
SELECT TRUE, 10, 20, 'ml/ha', 'CI fixture, not an approved rule', 7, 'ci-fixture', 'ci', d.id, p.id
FROM diseases d, pesticides p
WHERE d.code = 'Rice___Blast' AND p.code = 'CI_FIXTURE'
  AND NOT EXISTS (SELECT 1 FROM treatment_rules WHERE source = 'ci-fixture');
