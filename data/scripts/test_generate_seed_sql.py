import importlib.util
import unittest
from pathlib import Path


SCRIPT = Path(__file__).with_name("generate_seed_sql.py")
SPEC = importlib.util.spec_from_file_location("generate_seed_sql", SCRIPT)
GENERATOR = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(GENERATOR)


class GenerateSeedSqlTest(unittest.TestCase):

    def test_output_is_deterministic_and_tracks_blocked_rules(self):
        first_sql, first_blocked = GENERATOR.generate()
        second_sql, second_blocked = GENERATOR.generate()

        self.assertEqual(first_sql, second_sql)
        self.assertEqual(15, len(first_blocked))
        self.assertEqual(first_blocked, second_blocked)
        self.assertTrue(all(rule["blocked_reason"] for rule in first_blocked))

    def test_e2e_assumed_rule_is_seeded_with_explicit_scope(self):
        sql, _ = GENERATOR.generate()

        self.assertIn("'Tomato___Early_blight', 'ORTIVA_600SC'", sql)
        self.assertIn("'2026.09.26-e2e-assumed-v1'", sql)
        self.assertIn("Seed authorization: E2E_ASSUMPTION", sql)
        self.assertIn("chưa phải phê duyệt của chuyên gia nông nghiệp", sql)

    def test_ineligible_candidates_are_deactivated_and_cleaned_safely(self):
        sql, _ = GENERATOR.generate()

        self.assertIn("SET active = FALSE", sql)
        self.assertIn("'Squash___Powdery_mildew', 'THUMB_0_5SL'", sql)
        self.assertIn("SELECT 1 FROM diagnoses dg WHERE dg.treatment_rule_id = tr.id", sql)
        self.assertIn("SELECT 1 FROM treatment_applications ta WHERE ta.pesticide_id = p.id", sql)


if __name__ == "__main__":
    unittest.main()
