import json
from pathlib import Path

AI_ROOT = Path(__file__).resolve().parents[2]
STRUCTURED = AI_ROOT / "data" / "structured"
STAGE3 = AI_ROOT / "data" / "stage3"


def load_json(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


import pytest


def test_stage3_final_data_matches_review_decisions():
    decisions_path = STAGE3 / "review" / "agronomy_review_decisions.json"
    if not decisions_path.exists():
        pytest.skip("Stage3 review decision artifacts are not included in distribution.")
    diseases = load_json(STRUCTURED / "diseases.json")
    pesticides = load_json(STRUCTURED / "pesticides.json")
    rules = load_json(STRUCTURED / "treatment_rules.json")
    policies = load_json(STRUCTURED / "disease_management_policies.json")
    candidates = load_json(STRUCTURED / "treatment_rules_candidates_master.json")
    decisions = load_json(decisions_path)
    manifest = load_json(STAGE3 / "seed_manifest.json")

    assert manifest["counts"] == {
        "pesticides": 4,
        "treatment_rules": 8,
        "disease_management_policies": 21,
    }
    assert (len(pesticides), len(rules), len(policies)) == (4, 8, 21)

    candidates_by_id = {item["candidate_rule_id"]: item for item in candidates}
    approved_identities = {
        (
            candidates_by_id[item_id]["disease_code"],
            candidates_by_id[item_id]["pesticide_name"],
        )
        for item_id in decisions["approved_rule_ids"]
    }
    final_identities = {
        (item["disease_code"], item["pesticide_name"]) for item in rules
    }
    assert final_identities == approved_identities

    hold = load_json(STAGE3 / "hold" / "treatment_rules_hold.json")
    assert {item["candidate_rule_id"] for item in hold} == set(
        decisions["held_rule_ids"]
    )
    assert not set(decisions["approved_rule_ids"]) & set(decisions["held_rule_ids"])

    nonhealthy_codes = {
        item["code"] for item in diseases if not item["code"].endswith("___healthy")
    }
    rule_codes = {item["disease_code"] for item in rules}
    policy_codes = {item["disease_code"] for item in policies}
    assert not rule_codes & policy_codes
    assert rule_codes | policy_codes == nonhealthy_codes


def test_stage3_final_data_keeps_quantitative_guardrails():
    pesticides = load_json(STRUCTURED / "pesticides.json")
    rules = load_json(STRUCTURED / "treatment_rules.json")
    policies = load_json(STRUCTURED / "disease_management_policies.json")
    registry = load_json(AI_ROOT / "data" / "rag" / "source_registry.json")

    product_names = {item["name"] for item in pesticides}
    source_ids = {item["source_id"] for item in registry}

    assert len(product_names) == len(pesticides) == 4
    known_product = False
    for item in rules:
        assert item["pesticide_name"] in product_names
        assert item["dose_min"] <= item["dose_max"]
        assert item["phi_days"] > 0
        assert set(item["source"].split(",")) <= source_ids
        if item["disease_code"] == "Corn_(maize)___Northern_Leaf_Blight":
            assert item["pesticide_name"] == "Amistar Top 325SC"
            assert (item["dose_min"], item["dose_max"]) == (0.25, 0.5)
            assert item["dose_unit"] == "L/ha"
            assert item["phi_days"] == 10
            known_product = True
    assert known_product

    assert len({item["disease_code"] for item in policies}) == 21
    assert all(item["direct_treatment_rule_allowed"] is False for item in policies)
    assert all(set(item["source"].split(",")) <= source_ids for item in policies)
