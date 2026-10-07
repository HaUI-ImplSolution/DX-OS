#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Validate rice canonical JSON against the local collection workbook."""

from __future__ import annotations

import argparse
import json
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

REPO_ROOT = Path(__file__).resolve().parents[2]
DEFAULT_WORKBOOK = (
    REPO_ROOT / "development" / ".local" / "RICE_DATA_COLLECTION_TEMPLATE.xlsx"
)
NS = {"x": "http://schemas.openxmlformats.org/spreadsheetml/2006/main"}
REL_NS = {
    "r": "http://schemas.openxmlformats.org/package/2006/relationships"
}
OFFICE_REL = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"


def workbook_rows(path: Path) -> dict[str, list[dict[str, str | None]]]:
    with zipfile.ZipFile(path) as archive:
        shared: list[str] = []
        if "xl/sharedStrings.xml" in archive.namelist():
            root = ET.fromstring(archive.read("xl/sharedStrings.xml"))
            for item in root.findall("x:si", NS):
                shared.append("".join(node.text or "" for node in item.findall(".//x:t", NS)))

        workbook = ET.fromstring(archive.read("xl/workbook.xml"))
        relationships = ET.fromstring(archive.read("xl/_rels/workbook.xml.rels"))
        targets = {
            item.attrib["Id"]: item.attrib["Target"]
            for item in relationships.findall("r:Relationship", REL_NS)
        }
        result: dict[str, list[dict[str, str | None]]] = {}
        for sheet in workbook.findall("x:sheets/x:sheet", NS):
            relationship_id = sheet.attrib[f"{{{OFFICE_REL}}}id"]
            target = targets[relationship_id].lstrip("./")
            root = ET.fromstring(archive.read(f"xl/{target}"))
            raw_rows: list[dict[str, str | None]] = []
            for row in root.findall(".//x:sheetData/x:row", NS):
                values: dict[str, str | None] = {}
                for cell in row.findall("x:c", NS):
                    column = "".join(char for char in cell.attrib["r"] if char.isalpha())
                    value_node = cell.find("x:v", NS)
                    inline_nodes = cell.findall("x:is//x:t", NS)
                    if inline_nodes:
                        value = "".join(node.text or "" for node in inline_nodes)
                    elif value_node is None:
                        value = None
                    elif cell.attrib.get("t") == "s":
                        value = shared[int(value_node.text or "0")]
                    else:
                        value = value_node.text
                    values[column] = value
                raw_rows.append(values)
            headers = raw_rows[0]
            result[sheet.attrib["name"]] = [
                {header: row.get(column) for column, header in headers.items()}
                for row in raw_rows[1:]
            ]
        return result


def load_json(relative_path: str):
    return json.loads((REPO_ROOT / relative_path).read_text(encoding="utf-8"))


def boolean(value: str | None) -> bool:
    return value == "1"


def number(value: str | None):
    if value in (None, ""):
        return None
    numeric = float(value)
    return int(numeric) if numeric.is_integer() else numeric


def array(value: str | None) -> list[str]:
    return json.loads(value or "[]")


def normalize(value):
    return None if value == "" else value


def validate(path: Path) -> None:
    sheets = workbook_rows(path)
    diseases = load_json("data/structured/diseases.json")
    contract = load_json("data/structured/class_contract.json")
    pesticides = load_json("data/structured/pesticides_candidates_master.json")
    rules = load_json("data/structured/treatment_rules_candidates_master.json")
    policies = load_json("data/structured/disease_management_policies_master.json")
    knowledge = load_json("data/rag/processed/disease_knowledge.json")
    sources = load_json("data/rag/source_registry.json")

    disease_rows = sheets["03_BENH"]
    assert len(disease_rows) == len(diseases) == 6
    by_code = {item["code"]: item for item in diseases}
    for row in disease_rows:
        actual = by_code[row["disease_code"]]
        assert actual["name"] == row["name_vi"]
        assert actual["description"] == row["description"]
        assert actual["review_status"] == row["review_status"]

    contract_by_code = {item["disease_code"]: item for item in contract["classes"]}
    cv_rows = sheets["02_CV_CLASSES"]
    assert len(contract_by_code) == 5
    for row in cv_rows:
        if row["observed_yolo_id"] in (None, "NONE"):
            continue
        actual = contract_by_code[row["disease_code"]]
        assert actual["class_id"] == int(row["observed_yolo_id"])
        assert actual["model_label"].casefold() == row["model_label"].casefold()
    assert contract["fallback"]["disease_code"] == "Rice___healthy"

    source_rows = sheets["05_NGUON"]
    assert len(source_rows) == len(sources) == 9
    sources_by_id = {item["source_id"]: item for item in sources}
    source_fields = (
        "title", "organization", "source_type", "recommended_use", "url",
        "authority_level", "accessed_at", "version", "license",
        "crop_target_locale", "review_status", "notes",
    )
    for row in source_rows:
        actual = sources_by_id[row["source_id"]]
        for field in source_fields:
            assert normalize(actual.get(field)) == normalize(row[field])

    knowledge_rows = sheets["04_RAG_DOCUMENTS"]
    assert len(knowledge_rows) == len(knowledge) == 6
    knowledge_by_code = {item["disease_code"]: item for item in knowledge}
    knowledge_fields = (
        "symptoms", "disease_development", "favorable_conditions", "transmission",
        "prevention", "cultural_control", "biological_control",
        "chemical_control_non_quantitative", "treatment_timing",
        "resistance_management", "safety_notes",
    )
    for row in knowledge_rows:
        actual = knowledge_by_code[row["disease_code"]]
        for field in ("class_name_vi", "crop_name", "disease_name", "disease_type"):
            assert actual[field] == row[field]
        assert normalize(actual["pathogen"].get("name")) == normalize(row["pathogen_name"])
        assert normalize(actual["pathogen"].get("scientific_name")) == normalize(
            row["pathogen_scientific_name"]
        )
        for field in knowledge_fields:
            assert normalize(actual["knowledge"].get(field)) == normalize(row[field])
        assert actual["provenance"]["source_ids"] == array(row["source_ids"])
        assert actual["provenance"]["evidence_scope"] == row["evidence_scope"]
        assert actual["provenance"]["reviewed_at"] == row["reviewed_at"]
        assert actual["governance"]["rag_priority"] == row["rag_priority"]
        assert actual["governance"]["structured_rule_status"] == row["structured_rule_status"]
        assert actual["governance"]["regulatory_note"] == row["regulatory_note"]
        assert actual["content"] == row["content"]
        assert actual["review_status"] == row["review_status"]
        assert actual["notes"] == row["notes"]

    pesticide_rows = sheets["07_THUOC"]
    assert len(pesticide_rows) == len(pesticides) == 1
    row = pesticide_rows[0]
    actual = pesticides[0]
    for field in (
        "candidate_id", "code", "name", "active_ingredient", "concentration",
        "formulation", "unit", "registered_company", "registered_crop",
        "registered_target", "review_status", "blocked_reason", "notes",
    ):
        assert actual[field] == row[field]
    assert actual["eligible_for_pesticide_seed"] == boolean(
        row["eligible_for_pesticide_seed"]
    )
    assert set(actual["registration_verification"]["source_ids"] + actual["label_verification"]["source_ids"]) == set(array(row["source_ids"]))

    rule_rows = sheets["08_TREATMENT_RULES"]
    assert len(rule_rows) == len(rules) == 2
    rules_by_id = {item["candidate_rule_id"]: item for item in rules}
    for row in rule_rows:
        actual = rules_by_id[row["candidate_rule_id"]]
        for field in (
            "disease_code", "crop_name", "registered_target_text",
            "pesticide_candidate_id", "dose_unit", "water_volume_unit",
            "application_method", "review_status", "notes",
        ):
            assert actual[field] == row[field]
        for field in ("dose_min", "dose_max", "water_volume_min", "water_volume_max", "phi_days"):
            assert actual[field] == number(row[field])
        assert actual["source_ids"] == array(row["source_ids"])
        assert actual["eligible_for_seed"] == boolean(row["eligible_for_seed"])
        assert actual["blocked_reason"] == [row["blocked_reason"]]

    policy_rows = sheets["09_MGMT_POLICIES"]
    assert len(policy_rows) == len(policies) == 6
    policies_by_id = {item["policy_id"]: item for item in policies}
    for row in policy_rows:
        actual = policies_by_id[row["policy_id"]]
        assert actual["disease_code"] == row["disease_code"]
        assert actual["management_status"] == row["management_status"]
        assert actual["direct_treatment_rule_allowed"] == boolean(
            row["direct_treatment_rule_allowed"]
        )
        assert actual["chemical_rule_scope"] == row["chemical_rule_scope"]
        assert actual["recommended_actions"] == row["recommended_actions"].splitlines()
        assert actual["source_ids"] == array(row["source_ids"])
        assert actual["eligible_for_deterministic_guardrail"] == boolean(
            row["eligible_for_deterministic_guardrail"]
        )
        assert actual["review"] == {
            "reviewed_at": row["reviewed_at"],
            "review_status": row["review_status"],
        }


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("workbook", nargs="?", type=Path, default=DEFAULT_WORKBOOK)
    args = parser.parse_args()
    validate(args.workbook.resolve())
    print("Rice workbook export validation: PASS")
    return 0


if __name__ == "__main__":
    sys.exit(main())
