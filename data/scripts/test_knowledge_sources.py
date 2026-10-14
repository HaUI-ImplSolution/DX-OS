# SPDX-License-Identifier: Apache-2.0
"""The source lists the backend serves match the registries the corpora are built from."""

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
    "license": "license",
    "reviewStatus": "review_status",
}


def load(path: Path):
    return json.loads(path.read_text(encoding="utf-8"))


class KnowledgeSourcesTest(unittest.TestCase):
    def test_every_cited_source_is_served_with_the_registry_values(self):
        for name, (registry_path, corpus_path, served_path) in NAMESPACES.items():
            with self.subTest(namespace=name):
                registry = {item["source_id"]: item for item in load(registry_path)}
                served = {item["sourceId"]: item for item in load(served_path)}
                cited = {
                    source_id
                    for document in load(corpus_path)
                    for source_id in document["provenance"]["source_ids"]
                }
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
