# SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
# SPDX-License-Identifier: Apache-2.0
import sys
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import check_license_inventory as inventory  # noqa: E402
import check_licenses  # noqa: E402


class LicenseNames(unittest.TestCase):
    def test_pom_names_become_spdx_ids(self):
        self.assertEqual(inventory.spdx_name("The Apache Software License, Version 2.0"), "Apache-2.0")
        self.assertEqual(inventory.spdx_name("Apache License, Version 2.0"), "Apache-2.0")
        self.assertEqual(inventory.spdx_name("Eclipse Public License - v 2.0"), "EPL-2.0")
        self.assertEqual(inventory.spdx_name("GPL2 w/ CPE"), "GPL-2.0-only WITH Classpath-exception-2.0")
        self.assertEqual(inventory.spdx_name("MIT"), "MIT")
        self.assertEqual(inventory.spdx_name("Bouncy Castle Licence"), "MIT")
        self.assertEqual(inventory.spdx_name("Universal Permissive License, Version 1.0"), "UPL-1.0")
        self.assertEqual(inventory.spdx_name("Eclipse Public License 2.0"), "EPL-2.0")

    def test_a_name_nobody_maps_stays_as_written(self):
        self.assertEqual(inventory.spdx_name("Some Custom License"), "Some Custom License")

    def test_an_spdx_expression_loses_its_brackets_only(self):
        self.assertEqual(inventory.spdx_name("(MIT OR CC0-1.0)"), "MIT OR CC0-1.0")


class OsiPolicy(unittest.TestCase):
    def test_or_needs_one_side_and_needs_both(self):
        self.assertTrue(inventory.osi_ok("MIT OR CC0-1.0"))
        self.assertTrue(inventory.osi_ok("Apache-2.0 AND MIT"))
        self.assertFalse(inventory.osi_ok("MIT AND CC0-1.0"))
        self.assertFalse(inventory.osi_ok("CC-BY-4.0"))

    def test_unknown_and_missing_licenses_are_refused(self):
        self.assertFalse(inventory.osi_ok("Some Custom License"))
        self.assertFalse(inventory.osi_ok("(not declared)"))

    def test_the_equivalents_of_the_policy_count(self):
        self.assertTrue(inventory.osi_ok("EDL-1.0"))
        self.assertTrue(inventory.osi_ok("GPL-2.0-only WITH Classpath-exception-2.0"))

    def test_psf_2_0_is_not_an_osi_name_and_python_2_0_is(self):
        self.assertFalse(inventory.osi_ok("PSF-2.0"))
        self.assertTrue(inventory.osi_ok("Python-2.0"))

    def test_every_license_the_sbom_check_allows_is_approved_by_the_osi(self):
        for allowed in check_licenses.ALLOWED:
            self.assertTrue(inventory.osi_ok(allowed), allowed)


class MavenOutput(unittest.TestCase):
    def test_dependencies_and_plugins_are_read_with_their_scopes(self):
        listing = "\nThe following files have been resolved:\n   org.slf4j:slf4j-api:jar:2.0.17:compile -- module org.slf4j\n   org.junit:junit:jar:5.1:test\n   HaUI.ImplSolution:dx-core:jar:2.0.2:compile\n"
        plugins = "[INFO]    org.apache.maven.plugins:maven-deploy-plugin:jar:3.1.4\n[INFO]       org.codehaus.plexus:plexus-utils:jar:4.0.1\n[INFO] Reactor Summary\n"
        found = inventory.parse_maven(listing, plugins)
        self.assertEqual(found[("org.slf4j", "slf4j-api", "2.0.17")], {"compile"})
        self.assertEqual(found[("org.junit", "junit", "5.1")], {"test"})
        self.assertEqual(found[("org.codehaus.plexus", "plexus-utils", "4.0.1")], {"plugin"})
        self.assertNotIn(("HaUI.ImplSolution", "dx-core", "2.0.2"), found)

    def test_a_classifier_does_not_shift_the_version(self):
        found = inventory.parse_maven("   io.netty:netty-x:jar:linux-x86_64:4.2.1:runtime\n", "")
        self.assertEqual(found[("io.netty", "netty-x", "4.2.1")], {"runtime"})


class Section(unittest.TestCase):
    ROWS = {"npm: demo": [("left-pad", "1.0.0", "MIT", "prod"), ("data", "2.0.0", "CC0-1.0", "dev")], "Image": []}

    def test_what_is_written_is_read_back(self):
        text = "head\n" + inventory.render(self.ROWS) + "\ntail"
        self.assertEqual(inventory.parse(text), self.ROWS)

    def test_a_license_outside_the_osi_needs_an_exception(self):
        problems = inventory.policy_problems(self.ROWS)
        self.assertEqual(len(problems), 1)
        self.assertIn("data 2.0.0", problems[0])

    def test_an_exception_by_name_or_by_name_and_version_passes(self):
        inventory.EXCEPTIONS["data@2.0.0"] = "a reason"
        try:
            self.assertEqual(inventory.policy_problems(self.ROWS), [])
        finally:
            del inventory.EXCEPTIONS["data@2.0.0"]


if __name__ == "__main__":
    unittest.main()
