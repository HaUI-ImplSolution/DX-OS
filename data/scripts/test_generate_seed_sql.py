# SPDX-License-Identifier: Apache-2.0
import importlib.util
import json
from copy import deepcopy
from unittest.mock import patch
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
        self.assertEqual([], first_blocked)
        self.assertEqual(first_blocked, second_blocked)

    def test_only_expert_approved_rice_candidates_are_seeded(self):
        sql, _ = GENERATOR.generate()

        self.assertIn("'Rice___Blast'", sql)
        self.assertIn("'Rice___healthy'", sql)
        self.assertIn("'NEWTEC_300SC'", sql)
        self.assertIn("'2026.10.05-expert-approved-v1'", sql)
        self.assertIn("'HELP_400SC'", sql)
        self.assertIn("'2026.10.20-help-400sc-v1'", sql)
        self.assertNotIn("No pesticide has eligible_for_pesticide_seed=true", sql)
        self.assertNotIn("No treatment rule has eligible_for_seed=true", sql)
        self.assertIn("No policy is approved for deterministic lookup", sql)

    def test_brown_spot_rule_retains_the_verified_guidance(self):
        rules = GENERATOR.load("treatment_rules_candidates_master.json")
        rule = next(item for item in rules if item["candidate_rule_id"] == "TR-RICE-003")

        self.assertEqual("Rice___Brown_spot", rule["disease_code"])
        self.assertEqual("P-RICE-002", rule["pesticide_candidate_id"])
        self.assertEqual((0.6, 0.6, "L/ha"),
                         (rule["dose_min"], rule["dose_max"], rule["dose_unit"]))
        self.assertEqual((400, 400, "L/ha"),
                         (rule["water_volume_min"], rule["water_volume_max"],
                          rule["water_volume_unit"]))
        self.assertEqual(14, rule["phi_days"])
        self.assertIn("khi bệnh chớm xuất hiện", rule["application_method"])
        self.assertIn("SRC_ADC_HELP_400SC", rule["source_ids"])
        self.assertEqual("project_owner", rule["seed_authorization"]["authorized_by"])

    def test_revoking_brown_spot_excludes_it_without_changing_newtec(self):
        original_load = GENERATOR.load
        rules = deepcopy(original_load("treatment_rules_candidates_master.json"))
        rule = next(item for item in rules if item["candidate_rule_id"] == "TR-RICE-003")
        rule.update(eligible_for_seed=False, seed_authorization=None, seed_version=None,
                    review_status="IN_REVIEW", blocked_reason=["Approval withdrawn"])

        def revoked_load(name):
            if name == "treatment_rules_candidates_master.json":
                return rules
            return original_load(name)

        with patch.object(GENERATOR, "load", side_effect=revoked_load):
            sql, blocked = GENERATOR.generate()

        self.assertEqual(["TR-RICE-003"], [item["candidate_rule_id"] for item in blocked])
        self.assertNotIn("2026.10.20-help-400sc-v1", sql)
        self.assertIn("2026.10.05-expert-approved-v1", sql)
        self.assertIn("WHERE tr.active = FALSE", sql)

    def test_legacy_rows_are_deactivated_and_cleaned_safely(self):
        sql, _ = GENERATOR.generate()

        self.assertIn("UPDATE diseases\nSET active = FALSE", sql)
        self.assertIn("UPDATE pesticides\nSET active = FALSE", sql)
        self.assertIn("UPDATE treatment_rules tr", sql)
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
            0: ("Healthy", "Rice___healthy"),
            1: ("Blast", "Rice___Blast"),
            2: ("Brown Spot", "Rice___Brown_spot"),
            3: ("Leaf Smut", "Rice___Leaf_smut"),
            4: ("Rice Tungro", "Rice___Tungro"),
            5: ("Sheath Blight", "Rice___Sheath_blight"),
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
        self.assertEqual(
            {"P-RICE-001", "P-RICE-002"},
            {item["candidate_id"] for item in pesticides if item["eligible_for_pesticide_seed"]},
        )
        seeded_rules = [item for item in rules if item["eligible_for_seed"]]
        self.assertEqual({"TR-RICE-001", "TR-RICE-002", "TR-RICE-003"}, {
            item["candidate_rule_id"] for item in seeded_rules
        })
        self.assertTrue(
            all(item["seed_authorization"]["status"] == "EXPERT_APPROVED"
                for item in seeded_rules)
        )
        self.assertTrue(
            all(set(item["source_ids"]) <= source_ids for item in rules)
        )
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
