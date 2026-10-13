#!/usr/bin/env python3
# SPDX-License-Identifier: Apache-2.0
"""Generate backend data.sql from the top-level data/structured/*.json files.

Dev-time only: run by hand when data/ changes, then commit the resulting diff.
    python3 data/scripts/generate_seed_sql.py [--output PATH]
"""
import argparse
import json
import re
import sys
from pathlib import Path

if hasattr(sys.stdout, "reconfigure"):
    sys.stdout.reconfigure(encoding="utf-8")

REPO_ROOT = Path(__file__).resolve().parents[2]
STRUCTURED_DIR = REPO_ROOT / "data" / "structured"
DEFAULT_OUTPUT = (
    REPO_ROOT / "backend" / "app-farm" / "src" / "main" / "resources" / "farm" / "data.sql"
)
SEED_VERSION = "2026.09.30-rice-workbook-v1"
PESTICIDE_CODE_PATTERN = re.compile(r"^[A-Z0-9]+(?:_[A-Z0-9]+)*$")
ALLOWED_SEED_AUTHORIZATION_STATUSES = {"E2E_ASSUMPTION", "EXPERT_APPROVED"}


def load(name):
    with open(STRUCTURED_DIR / name, encoding="utf-8") as f:
        return json.load(f)


def q(value):
    if value is None:
        return "NULL"
    return "'" + str(value).replace("'", "''") + "'"


def num(value):
    return f"{value:.2f}::numeric"


def rows(items):
    return ",\n".join(f"  ({', '.join(item)})" for item in items)


def validated_seedable_pesticides(candidates):
    codes = set()
    seedable = []

    for candidate in candidates:
        candidate_id = candidate.get("candidate_id", "<missing candidate_id>")
        code = candidate.get("code")
        unit = candidate.get("unit")

        if not isinstance(code, str) or not PESTICIDE_CODE_PATTERN.fullmatch(code):
            raise ValueError(f"{candidate_id}: code must be UPPER_SNAKE_CASE")
        if code in codes:
            raise ValueError(f"Duplicate pesticide code: {code}")
        codes.add(code)

        if candidate.get("eligible_for_pesticide_seed"):
            if not isinstance(candidate.get("name"), str) or not candidate["name"].strip():
                raise ValueError(f"{candidate_id}: eligible pesticide requires name")
            if not isinstance(unit, str) or not unit.strip():
                raise ValueError(f"{candidate_id}: eligible pesticide requires unit")
            seedable.append(candidate)
        elif unit is not None and (not isinstance(unit, str) or not unit.strip()):
            raise ValueError(f"{candidate_id}: unit must be a non-empty string or null")

    return sorted(seedable, key=lambda candidate: candidate["code"])


def rule_notes(rule):
    parts = [f"Registered target: {rule['registered_target_text']}."]
    if rule.get("application_method"):
        parts.append(f"Application guidance: {rule['application_method']}")
    if rule.get("water_volume_min") is not None:
        lo, hi = rule["water_volume_min"], rule["water_volume_max"]
        span = f"{lo:g}" if lo == hi else f"{lo:g}-{hi:g}"
        parts.append(f"Water volume: {span} {rule['water_volume_unit']}.")
    authorization = rule["seed_authorization"]
    parts.append(f"Seed authorization: {authorization['status']}.")
    parts.append(authorization["note"])
    return " ".join(parts)


def validated_rules(candidates):
    candidate_ids = set()
    seedable = []

    for candidate in candidates:
        candidate_id = candidate.get("candidate_rule_id", "<missing candidate_rule_id>")
        if candidate_id in candidate_ids:
            raise ValueError(f"Duplicate treatment rule candidate: {candidate_id}")
        candidate_ids.add(candidate_id)

        if candidate.get("eligible_for_seed"):
            authorization = candidate.get("seed_authorization")
            if not isinstance(authorization, dict):
                raise ValueError(f"{candidate_id}: eligible rule requires seed_authorization")
            if authorization.get("status") not in ALLOWED_SEED_AUTHORIZATION_STATUSES:
                raise ValueError(f"{candidate_id}: invalid seed authorization status")
            if not authorization.get("note") or not candidate.get("seed_version"):
                raise ValueError(f"{candidate_id}: authorization note and seed_version required")
            seedable.append(candidate)
        elif not candidate.get("blocked_reason"):
            raise ValueError(f"{candidate_id}: blocked rule requires blocked_reason")

    return seedable


def generate():
    diseases = load("diseases.json")
    all_pesticides = load("pesticides_candidates_master.json")
    all_rules = load("treatment_rules_candidates_master.json")
    all_policies = load("disease_management_policies_master.json")

    pesticides = validated_seedable_pesticides(all_pesticides)
    by_candidate = {p["candidate_id"]: p for p in pesticides}
    rules = validated_rules(all_rules)
    policies = [p for p in all_policies if p["eligible_for_deterministic_guardrail"]]
    disease_codes = {d["code"] for d in diseases}
    if len(disease_codes) != len(diseases):
        raise ValueError("Duplicate disease code")
    if not disease_codes:
        raise ValueError("At least one disease is required")

    out = [
        "-- GENERATED by data/scripts/generate_seed_sql.py from data/structured/*.json.",
        "-- Do not edit by hand; change data/ and regenerate.",
        "",
        "-- Existing installations need an explicit lifecycle flag for legacy taxonomy.",
        "ALTER TABLE diseases",
        "ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;",
        "ALTER TABLE pesticides",
        "ADD COLUMN IF NOT EXISTS active BOOLEAN NOT NULL DEFAULT TRUE;",
        "",
        "INSERT INTO diseases (code, name, description, active)",
        "VALUES",
        rows(
            [q(d["code"]), q(d["name"]), q(d.get("description")), "TRUE"]
            for d in diseases
        ),
        "ON CONFLICT (code) DO UPDATE",
        "SET name = EXCLUDED.name,",
        "    description = EXCLUDED.description,",
        "    active = TRUE;",
        "",
        "-- Legacy disease rows remain available to history but disappear from new lookups.",
        "UPDATE diseases",
        "SET active = FALSE",
        "WHERE code NOT IN (",
        rows([q(code)] for code in sorted(disease_codes)),
        ");",
        "",
    ]

    if pesticides:
        out += [
            "INSERT INTO pesticides (code, name, unit, active)",
            "VALUES",
            rows(
                [q(p["code"]), q(p["name"]), q(p["unit"]), "TRUE"]
                for p in pesticides
            ),
            "ON CONFLICT (code) DO UPDATE",
            "SET name = EXCLUDED.name,",
            "    unit = EXCLUDED.unit,",
            "    active = TRUE;",
            "",
        ]
    else:
        out += ["-- No pesticide has eligible_for_pesticide_seed=true; none are seeded.", ""]

    if rules:
        rule_rows = []
        for r in rules:
            pesticide = by_candidate.get(r["pesticide_candidate_id"])
            if pesticide is None:
                raise ValueError(f"{r['candidate_rule_id']}: pesticide not seeded")
            if r["pesticide_name"] != pesticide["name"]:
                raise ValueError(
                    f"{r['candidate_rule_id']}: pesticide_name "
                    f"{r['pesticide_name']!r} does not match {pesticide['name']!r}"
                )
            if r["disease_code"] not in disease_codes:
                raise ValueError(f"{r['candidate_rule_id']}: unknown disease_code")
            if r.get("phi_days") is None:
                raise ValueError(f"{r['candidate_rule_id']}: phi_days missing")
            if r["dose_unit"] != pesticide["unit"]:
                raise ValueError(f"{r['candidate_rule_id']}: dose_unit does not match pesticide unit")
            rule_rows.append(
                [
                    q(r["disease_code"]),
                    q(pesticide["code"]),
                    num(r["dose_min"]),
                    num(r["dose_max"]),
                    q(r["dose_unit"]),
                    str(r["phi_days"]),
                    q(rule_notes(r)),
                    q(",".join(r["source_ids"])),
                    q(r["seed_version"]),
                    "TRUE",
                ]
            )
        out += [
            "INSERT INTO treatment_rules (",
            "  disease_id, pesticide_id, dose_min, dose_max, dose_unit,",
            "  phi_days, notes, source, version, active",
            ")",
            "SELECT d.id, p.id, seed.dose_min, seed.dose_max, seed.dose_unit,",
            "       seed.phi_days, seed.notes, seed.source, seed.version, seed.active",
            "FROM (VALUES",
            rows(rule_rows),
            ") AS seed(",
            "  disease_code, pesticide_code, dose_min, dose_max, dose_unit,",
            "  phi_days, notes, source, version, active",
            ")",
            "JOIN diseases d ON d.code = seed.disease_code",
            "JOIN pesticides p ON p.code = seed.pesticide_code",
            "ON CONFLICT (disease_id, pesticide_id) DO UPDATE",
            "SET dose_min = EXCLUDED.dose_min,",
            "    dose_max = EXCLUDED.dose_max,",
            "    dose_unit = EXCLUDED.dose_unit,",
            "    phi_days = EXCLUDED.phi_days,",
            "    notes = EXCLUDED.notes,",
            "    source = EXCLUDED.source,",
            "    version = EXCLUDED.version,",
            "    active = TRUE;",
            "",
        ]
    else:
        out += ["-- No treatment rule has eligible_for_seed=true; none are seeded.", ""]

    if rules:
        desired_pairs = []
        for rule in rules:
            pesticide = by_candidate[rule["pesticide_candidate_id"]]
            desired_pairs.append([q(rule["disease_code"]), q(pesticide["code"])])
        out += [
            "-- Inactive rules are excluded from new lookups; referenced rows remain for audit.",
            "UPDATE treatment_rules tr",
            "SET active = FALSE",
            "FROM diseases d, pesticides p",
            "WHERE tr.disease_id = d.id",
            "  AND tr.pesticide_id = p.id",
            "  AND NOT EXISTS (",
            "    SELECT 1",
            "    FROM (VALUES",
            rows(desired_pairs),
            "    ) AS desired(disease_code, pesticide_code)",
            "    WHERE desired.disease_code = d.code",
            "      AND desired.pesticide_code = p.code",
            "  );",
            "",
        ]
    else:
        out += [
            "-- No rule is currently eligible; retain referenced history as inactive.",
            "UPDATE treatment_rules SET active = FALSE WHERE active = TRUE;",
            "",
        ]

    out += [
        "-- Remove inactive rules only when no diagnosis history references them.",
        "DELETE FROM treatment_rules tr",
        "WHERE tr.active = FALSE",
        "  AND NOT EXISTS (",
        "    SELECT 1 FROM diagnoses dg WHERE dg.treatment_rule_id = tr.id",
        "  );",
        "",
    ]

    policy_rows = []
    for p in policies:
        if p["disease_code"] not in disease_codes:
            raise ValueError(f"{p['policy_id']}: unknown disease_code")
        policy_rows.append(
            [
                q(p["disease_code"]),
                q(p["management_status"]),
                "TRUE" if p["direct_treatment_rule_allowed"] else "FALSE",
                q(p["chemical_rule_scope"]),
                q("\n".join(p["recommended_actions"])),
                q(",".join(p["source_ids"])),
                q(SEED_VERSION),
            ]
        )
    if policy_rows:
        out += [
            "INSERT INTO disease_management_policies (",
            "  disease_id, management_status, direct_treatment_rule_allowed,",
            "  chemical_rule_scope, recommended_actions, source, version",
            ")",
            "SELECT d.id, seed.management_status, seed.direct_treatment_rule_allowed,",
            "       seed.chemical_rule_scope, seed.recommended_actions, seed.source, seed.version",
            "FROM (VALUES",
            rows(policy_rows),
            ") AS seed(",
            "  disease_code, management_status, direct_treatment_rule_allowed,",
            "  chemical_rule_scope, recommended_actions, source, version",
            ")",
            "JOIN diseases d ON d.code = seed.disease_code",
            "ON CONFLICT (disease_id) DO UPDATE",
            "SET management_status = EXCLUDED.management_status,",
            "    direct_treatment_rule_allowed = EXCLUDED.direct_treatment_rule_allowed,",
            "    chemical_rule_scope = EXCLUDED.chemical_rule_scope,",
            "    recommended_actions = EXCLUDED.recommended_actions,",
            "    source = EXCLUDED.source,",
            "    version = EXCLUDED.version;",
            "",
            "DELETE FROM disease_management_policies p",
            "USING diseases d",
            "WHERE p.disease_id = d.id",
            "  AND d.code NOT IN (",
            rows([q(p["disease_code"])] for p in policies),
            "  );",
            "",
        ]
    else:
        out += [
            "-- No policy is approved for deterministic lookup.",
            "DELETE FROM disease_management_policies;",
            "",
        ]

    eligible_pesticide_codes = sorted(p["code"] for p in pesticides)
    out += ["-- Legacy pesticides stay inactive while history references them."]
    if eligible_pesticide_codes:
        out += [
            "UPDATE pesticides",
            "SET active = FALSE",
            "WHERE code NOT IN (",
            rows([q(code)] for code in eligible_pesticide_codes),
            ");",
            "",
            "-- Remove inactive pesticides only when nothing references them.",
            "DELETE FROM pesticides p",
            "WHERE p.active = FALSE",
        ]
    else:
        out += [
            "UPDATE pesticides SET active = FALSE WHERE active = TRUE;",
            "",
            "-- Remove inactive pesticides only when nothing references them.",
            "DELETE FROM pesticides p",
            "WHERE p.active = FALSE",
        ]
    out += [
        "  AND NOT EXISTS (",
        "    SELECT 1 FROM treatment_rules tr WHERE tr.pesticide_id = p.id",
        "  )",
        "  AND NOT EXISTS (",
        "    SELECT 1 FROM treatment_applications ta WHERE ta.pesticide_id = p.id",
        "  );",
        "",
        "-- Remove legacy diseases only when no history or retained rule references them.",
        "DELETE FROM diseases d",
        "WHERE d.active = FALSE",
        "  AND NOT EXISTS (SELECT 1 FROM diagnoses dg WHERE dg.disease_id = d.id)",
        "  AND NOT EXISTS (SELECT 1 FROM treatment_rules tr WHERE tr.disease_id = d.id)",
        "  AND NOT EXISTS (",
        "    SELECT 1 FROM disease_management_policies p WHERE p.disease_id = d.id",
        "  );",
        "",
    ]

    blocked = [r for r in all_rules if not r["eligible_for_seed"]]
    return "\n".join(out), blocked


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", type=Path, default=DEFAULT_OUTPUT)
    parser.add_argument("--check", action="store_true",
                        help="fail unless the output already matches generated SQL")
    args = parser.parse_args()
    sql, blocked = generate()
    if args.check:
        if not args.output.exists() or args.output.read_text(encoding="utf-8") != sql:
            print(f"{args.output} is not generated from data/structured", file=sys.stderr)
            return 1
        return 0
    args.output.write_text(sql, encoding="utf-8")
    print(f"Wrote {args.output}")
    print(f"Treatment rules blocked from seed: {len(blocked)}")
    for r in blocked:
        reasons = "; ".join(r.get("blocked_reason") or [])
        print(f"  {r['candidate_rule_id']} {r['disease_code']}: {reasons}")


if __name__ == "__main__":
    sys.exit(main())
