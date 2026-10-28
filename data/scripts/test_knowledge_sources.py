# SPDX-License-Identifier: Apache-2.0
"""The source lists served by the backend cover the corpora and Farm rules."""

import json
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]

# (registry, corpus, backend source list)
NAMESPACES = {
    "farm": (
        ROOT / "data/rag/source_registry.json",
        ROOT / "data/rag/processed/disease_knowledge.json",
        ROOT / "backend/app-farm/src/main/resources/knowledge-sources.json",
    ),
    "lab": (
        ROOT / "data/rag/lab/source_registry.json",
        ROOT / "data/rag/lab/processed/lab_knowledge.json",
        ROOT / "backend/app-lab/src/main/resources/lab/knowledge-sources.json",
    ),
}
FIELDS = {
    "title": "title",
    "organization": "organization",
    "url": "url",
    "documentUrl": "document_url",
    "license": "license",
    "reviewStatus": "review_status",
}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


class KnowledgeSourcesTest(unittest.TestCase):
    def test_recorded_owner_approval_allows_flagged_rag_documents(self):
        from data.scripts.promote_farm_corpus import promotable_documents

        pending = {"review_status": "IN_REVIEW", "review_flags": ["CONTAINS_TREATMENT_REFERENCES"]}
        approved = {**pending, "review_status": "APPROVED", "approved_by": "project_owner"}
        self.assertEqual(promotable_documents([pending, approved]), [approved])

    def test_every_cited_source_is_served_with_the_registry_values(self):
        for name, (registry_path, corpus_path, served_path) in NAMESPACES.items():
            with self.subTest(namespace=name):
                registry_items = load(registry_path)
                if name == "farm":
                    registry_items += load(ROOT / "data/rag/farm_knowledge_source_registry.json")
                served_items = load(served_path)
                registry = {item["source_id"]: item for item in registry_items}
                served = {item["sourceId"]: item for item in served_items}
                self.assertEqual(len(registry_items), len(registry), "duplicate registry source")
                self.assertEqual(len(served_items), len(served), "duplicate served source")
                cited = {
                    source_id
                    for document in load(corpus_path)
                    for source_id in document["provenance"]["source_ids"]
                }
                if name == "farm":
                    generic = load(ROOT / "data/rag/processed/farm_knowledge.json")
                    for document in generic:
                        self.assertNotIn("sha256", document["provenance"])
                    cited.update(sid for doc in generic for sid in doc["provenance"]["source_ids"])
                    rules = load(
                        ROOT / "data/structured/treatment_rules_candidates_master.json"
                    )
                    cited.update(
                        source_id
                        for rule in rules
                        for source_id in rule["source_ids"]
                    )
                self.assertEqual(set(), cited - set(served), "cited but not served")
                self.assertEqual(set(), set(served) - set(registry), "served but not in registry")
                for source_id, item in served.items():
                    for served_field, registry_field in FIELDS.items():
                        self.assertEqual(
                            registry[source_id].get(registry_field),
                            item.get(served_field),
                            f"{source_id}.{served_field}",
                        )


if __name__ == "__main__":
    unittest.main()
