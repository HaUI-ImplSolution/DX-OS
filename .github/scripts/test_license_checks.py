# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
"""Tests of the license checks that need no network: python3 -m unittest discover -s .github/scripts"""
import json
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import check_licenses  # noqa: E402
import check_upstream_licenses as upstream  # noqa: E402

ENTRY = {"license": "Apache-2.0", "source": "https://github.com/org/project"}


def answers(spdx_by_ref: dict, files: dict | None = None):
    """A stand-in for the GitHub API: the license of each ref, and the text of the files."""
    def fetch(path: str, raw: bool = False):
        if raw:
            return (files or {}).get(path.split("?ref=")[0].rsplit("/contents/", 1)[1])
        ref = path.split("?ref=")[1].replace("%2F", "/")
        if ref not in spdx_by_ref:
            return None
        return {"license": {"spdx_id": spdx_by_ref[ref]}}
    return fetch


class UpstreamLicenseTest(unittest.TestCase):
    def test_the_declared_open_license_at_the_version_passes(self):
        self.assertIsNone(upstream.verify("p", ENTRY, "1.2.3", answers({"v1.2.3": "Apache-2.0"})))

    def test_a_license_that_changed_fails(self):
        problem = upstream.verify("redis", {**ENTRY, "license": "BSD-3-Clause"}, "7.4", answers({"v7.4": "SSPL-1.0"}))
        self.assertIn("the catalog says BSD-3-Clause", problem)
        self.assertIn("SSPL-1.0", problem)

    def test_a_declared_license_that_is_not_open_fails(self):
        problem = upstream.verify("p", {**ENTRY, "license": "SSPL-1.0"}, "1.0", answers({"v1.0": "SSPL-1.0"}))
        self.assertIn("not an allowed", problem)

    def test_a_license_github_cannot_identify_fails(self):
        problem = upstream.verify("p", ENTRY, "1.0", answers({"v1.0": "NOASSERTION"}))
        self.assertIn("not an open one", problem)

    def test_an_unidentified_license_passes_only_with_its_text(self):
        entry = {**ENTRY, "text": {"path": "LICENSE", "contains": ["Apache License"]}}
        self.assertIsNone(upstream.verify("p", entry, "1.0", answers({"v1.0": "NOASSERTION"}, {"LICENSE": "Apache License, Version 2.0"})))
        problem = upstream.verify("p", entry, "1.0", answers({"v1.0": "NOASSERTION"}, {"LICENSE": "Source-available terms"}))
        self.assertIn("no longer holds", problem)

    def test_a_version_without_a_ref_fails(self):
        self.assertIn("none of the refs", upstream.verify("p", ENTRY, "9.9", answers({})))

    def test_the_refs_of_an_entry_replace_the_default(self):
        entry = {**ENTRY, "refs": ["release-{version}.0", "REL_{major}_STABLE"]}
        self.assertEqual(upstream.refs_for(entry, "1.30"), ["release-1.30.0", "REL_1_STABLE"])
        self.assertEqual(upstream.refs_for(ENTRY, "3.11.0"), ["v3.11.0", "3.11.0"])

    def test_a_network_error_is_not_swallowed(self):
        def broken(path, raw=False):
            raise upstream.Unreadable("offline")
        with self.assertRaises(upstream.Unreadable):
            upstream.verify("p", ENTRY, "1.0", broken)

    def test_the_version_of_a_tag(self):
        self.assertEqual(upstream.version_of("3.11.0-debian"), "3.11.0")
        self.assertEqual(upstream.version_of("v8.30.1"), "8.30.1")
        self.assertEqual(upstream.version_of("22-alpine"), "22")
        self.assertIsNone(upstream.version_of("latest"))


class GithubAccessTest(unittest.TestCase):
    """The data is public: an organization that refuses the token is asked again without it."""

    def setUp(self):
        self.calls = []
        self.original = upstream.urllib.request.urlopen
        self.original_token = upstream.os.environ.get("GITHUB_TOKEN")
        upstream.os.environ["GITHUB_TOKEN"] = "secret"
        self.original_sleep = upstream.time.sleep
        upstream.time.sleep = lambda seconds: None

    def tearDown(self):
        upstream.urllib.request.urlopen = self.original
        upstream.time.sleep = self.original_sleep
        if self.original_token is None:
            upstream.os.environ.pop("GITHUB_TOKEN", None)
        else:
            upstream.os.environ["GITHUB_TOKEN"] = self.original_token

    def respond(self, handler):
        def urlopen(request, timeout=0):
            self.calls.append(request.get_header("Authorization"))
            return handler(request)
        upstream.urllib.request.urlopen = urlopen

    @staticmethod
    def forbidden(url):
        import io
        import urllib.error
        return urllib.error.HTTPError(url, 403, "Forbidden", {}, io.BytesIO(b'{"message": "IP allow list"}'))

    def test_a_token_that_is_refused_is_dropped(self):
        import io
        def handler(request):
            if request.get_header("Authorization"):
                raise self.forbidden(request.full_url)
            return io.BytesIO(b'{"license": {"spdx_id": "Apache-2.0"}}')
        self.respond(handler)
        self.assertEqual(upstream.github("repos/o/p/license"), {"license": {"spdx_id": "Apache-2.0"}})
        self.assertEqual(self.calls, ["Bearer secret", None])

    def test_a_refusal_without_a_token_is_reported_with_its_reason(self):
        def handler(request):
            raise self.forbidden(request.full_url)
        self.respond(handler)
        with self.assertRaises(upstream.Unreadable) as raised:
            upstream.github("repos/o/p/license")
        self.assertIn("HTTP 403", str(raised.exception))
        self.assertIn("IP allow list", str(raised.exception))


class SbomLicenseTest(unittest.TestCase):
    def check(self, licenses):
        component = {"group": "org.example", "name": "lib", "version": "1.0", "licenses": licenses}
        with tempfile.TemporaryDirectory() as folder:
            path = Path(folder) / "sbom.cdx.json"
            path.write_text(json.dumps({"components": [component]}), encoding="utf-8")
            return check_licenses.check_sboms([path])

    def test_an_open_license_passes(self):
        self.assertEqual(self.check([{"license": {"id": "Apache-2.0"}}]), [])

    def test_a_license_that_is_not_open_fails(self):
        self.assertIn("not in the allowed list", self.check([{"license": {"id": "SSPL-1.0"}}])[0])

    def test_a_package_without_a_license_fails(self):
        self.assertIn("has no license", self.check([])[0])


if __name__ == "__main__":
    unittest.main()
