# SPDX-License-Identifier: Apache-2.0
import importlib.util
import json
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
        self.assertEqual(2, len(first_blocked))
        self.assertEqual(first_blocked, second_blocked)
        self.assertTrue(all(rule["blocked_reason"] for rule in first_blocked))

    def test_unapproved_rice_candidates_are_not_seeded(self):
        sql, _ = GENERATOR.generate()

        self.assertIn("'Rice___Blast'", sql)
        self.assertIn("'Rice___healthy'", sql)
        self.assertNotIn("NEWTEC_300SC", sql)
        self.assertIn("No pesticide has eligible_for_pesticide_seed=true", sql)
        self.assertIn("No treatment rule has eligible_for_seed=true", sql)
        self.assertIn("No policy is approved for deterministic lookup", sql)

    def test_legacy_rows_are_deactivated_and_cleaned_safely(self):
        sql, _ = GENERATOR.generate()

        self.assertIn("UPDATE diseases\nSET active = FALSE", sql)
        self.assertIn("UPDATE pesticides SET active = FALSE", sql)
        self.assertIn("UPDATE treatment_rules SET active = FALSE", sql)
        self.assertIn("SELECT 1 FROM diagnoses dg WHERE dg.treatment_rule_id = tr.id", sql)
        self.assertIn("SELECT 1 FROM treatment_applications ta WHERE ta.pesticide_id = p.id", sql)
        self.assertIn("SELECT 1 FROM diagnoses dg WHERE dg.disease_id = d.id", sql)

    def test_workbook_exports_have_consistent_codes_and_provenance(self):
        structured = SCRIPT.parents[1] / "structured"
        rag = SCRIPT.parents[1] / "rag"
        diseases = json.loads((structured / "diseases.json").read_text(encoding="utf-8"))
        contract = json.loads(
            (structured / "class_contract.json").read_text(encoding="utf-8")
        )
        knowledge = json.loads(
            (rag / "processed" / "disease_knowledge.json").read_text(encoding="utf-8")
        )
        sources = json.loads((rag / "source_registry.json").read_text(encoding="utf-8"))
        pesticides = json.loads(
            (structured / "pesticides_candidates_master.json").read_text(encoding="utf-8")
        )
        rules = json.loads(
            (structured / "treatment_rules_candidates_master.json").read_text(encoding="utf-8")
        )
        policies = json.loads(
            (structured / "disease_management_policies_master.json").read_text(encoding="utf-8")
        )

        expected_classes = {
            0: ("Blast", "Rice___Blast"),
            1: ("Brown Spot", "Rice___Brown_spot"),
            2: ("Leaf Smut", "Rice___Leaf_smut"),
            3: ("Rice Tungro", "Rice___Tungro"),
            4: ("Sheath Blight", "Rice___Sheath_blight"),
        }
        actual_classes = {
            item["class_id"]: (item["model_label"], item["disease_code"])
            for item in contract["classes"]
        }
        disease_codes = {item["code"] for item in diseases}
        source_ids = {item["source_id"] for item in sources}

        self.assertEqual(expected_classes, actual_classes)
        self.assertEqual("Rice___healthy", contract["fallback"]["disease_code"])
        self.assertEqual(disease_codes, {item["disease_code"] for item in knowledge})
        self.assertEqual(6, len(disease_codes))
        self.assertTrue(all(item["review_status"] == "IN_REVIEW" for item in diseases))
        for item in knowledge:
            self.assertTrue(set(item["provenance"]["source_ids"]) <= source_ids)
        pesticide_ids = {item["candidate_id"] for item in pesticides}
        self.assertTrue(all(not item["eligible_for_pesticide_seed"] for item in pesticides))
        self.assertTrue(all(not item["eligible_for_seed"] for item in rules))
        self.assertTrue(all(item["disease_code"] in disease_codes for item in rules))
        self.assertTrue(
            all(item["pesticide_candidate_id"] in pesticide_ids for item in rules)
        )
        self.assertTrue(
            all(not item["eligible_for_deterministic_guardrail"] for item in policies)
        )
        self.assertTrue(all(item["disease_code"] in disease_codes for item in policies))


if __name__ == "__main__":
    unittest.main()
