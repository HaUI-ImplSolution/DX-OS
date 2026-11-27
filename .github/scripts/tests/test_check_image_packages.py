# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_image_packages as images  # noqa: E402


class Tables(unittest.TestCase):
    TABLES = {"demo:1": [("bash", "5.2", "GPL-3.0-or-later, Beerware"), ("zlib", "1.3", "Zlib")], "other:2": []}

    def test_what_is_written_is_read_back(self):
        parsed = images.parse(images.render(self.TABLES))
        self.assertEqual(parsed["demo:1"], self.TABLES["demo:1"])
        self.assertEqual(parsed["other:2"], [])

    def test_a_license_outside_the_osi_is_shown_and_an_approved_one_is_not(self):
        self.assertEqual(images.outside_osi("GPL-3.0-or-later, Beerware, public-domain"), "Beerware, public-domain")
        self.assertEqual(images.outside_osi("MIT, Zlib"), "")
        self.assertEqual(images.outside_osi("(not recorded)"), "")

    def test_the_summary_counts_the_packages_with_a_license_outside_the_osi(self):
        text = images.render(self.TABLES)
        self.assertIn("| `demo:1` | 2 | 1 |", text)


class References(unittest.TestCase):
    def test_every_image_the_stack_runs_has_a_tag_and_a_port_is_not_taken_for_one(self):
        refs = images.image_refs()
        self.assertIn("postgres:16-bookworm", refs)
        self.assertNotIn("postgres:5432", refs)
        self.assertTrue(all(":" in key for key in refs))


if __name__ == "__main__":
    unittest.main()
