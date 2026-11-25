# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_third_party_notices as notices  # noqa: E402


def jar(folder: Path, files: dict[str, str]) -> Path:
    path = folder / "lib.jar"
    with zipfile.ZipFile(path, "w") as archive:
        for name, text in files.items():
            archive.writestr(name, text)
    return path


class LegalFiles(unittest.TestCase):
    def test_license_and_notice_files_are_taken_and_classes_are_not(self):
        with tempfile.TemporaryDirectory() as folder:
            path = jar(Path(folder), {"META-INF/LICENSE.txt": "MIT text\r\n", "META-INF/NOTICE": "Some notice", "META-INF/licenses/dep/LICENSE": "Dep text", "a/B.class": "x", "META-INF/MANIFEST.MF": "m"})
            names = [name for name, _text in notices.legal_files(path)]
        self.assertEqual(sorted(names), ["META-INF/LICENSE.txt", "META-INF/NOTICE", "META-INF/licenses/dep/LICENSE"])

    def test_line_endings_are_normalised_and_an_empty_file_is_dropped(self):
        with tempfile.TemporaryDirectory() as folder:
            path = jar(Path(folder), {"LICENSE": "a\r\nb\r\n", "NOTICE": "  \n"})
            self.assertEqual(notices.legal_files(path), [("LICENSE", "a\nb")])


class Grouping(unittest.TestCase):
    def test_a_text_that_packages_share_is_given_once(self):
        entries = [("a 1", [("LICENSE", "same")]), ("b 2", [("LICENSE", "same")]), ("c 3", [("NOTICE", "other")]), ("d 4", [])]
        lines, without = notices.grouped(entries)
        text = "\n".join(lines)
        self.assertEqual(text.count("same"), 1)
        self.assertIn("LICENSE text of 2 packages", text)
        self.assertIn("NOTICE text of 1 packages", text)
        self.assertEqual(without, ["d 4"])


if __name__ == "__main__":
    unittest.main()
