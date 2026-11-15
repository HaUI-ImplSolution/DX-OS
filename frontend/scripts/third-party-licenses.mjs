// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// Writes the license notices of the production dependencies of an npm project, so that a build
// ships them with the code it bundles.
//
//   node third-party-licenses.mjs <project dir> <output file>
//
// The packages come from package-lock.json (everything not marked dev-only) and the texts from the
// license files inside each installed package.
import { existsSync, mkdirSync, readFileSync, readdirSync, statSync, writeFileSync } from "node:fs";
import { dirname, join, resolve } from "node:path";

const [projectArg, outputArg] = process.argv.slice(2);
if (!projectArg || !outputArg) {
  console.error("usage: node third-party-licenses.mjs <project dir> <output file>");
  process.exit(2);
}

const project = resolve(projectArg);
const output = resolve(outputArg);
const lock = JSON.parse(readFileSync(join(project, "package-lock.json"), "utf8"));
const projectName = JSON.parse(readFileSync(join(project, "package.json"), "utf8")).name;
const LICENSE_FILE = /^(licen[cs]e|copying|notice)(\.|-|_|$)/i;

function licenseOf(pkg) {
  if (typeof pkg.license === "string") return pkg.license;
  if (pkg.license?.type) return pkg.license.type;
  if (Array.isArray(pkg.licenses)) return pkg.licenses.map((l) => l.type ?? l).join(" OR ");
  return "not declared";
}

function repositoryOf(pkg) {
  const repository = typeof pkg.repository === "string" ? pkg.repository : pkg.repository?.url;
  return repository ?? pkg.homepage ?? "";
}

const entries = [];
for (const [path, meta] of Object.entries(lock.packages ?? {})) {
  if (!path.includes("node_modules/") || meta.dev) continue;
  const dir = join(project, path);
  if (!existsSync(join(dir, "package.json"))) continue;
  const pkg = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"));
  const texts = readdirSync(dir)
    .filter((file) => LICENSE_FILE.test(file) && statSync(join(dir, file)).isFile())
    .sort()
    .map((file) => readFileSync(join(dir, file), "utf8").trim());
  entries.push({
    name: pkg.name ?? path.split("node_modules/").pop(),
    version: pkg.version ?? meta.version ?? "",
    license: licenseOf(pkg),
    repository: repositoryOf(pkg),
    texts,
  });
}
entries.sort((a, b) => a.name.localeCompare(b.name) || a.version.localeCompare(b.version));

const rule = "=".repeat(80);
const parts = [
  `Third-party notices of ${projectName}`,
  "",
  "This build bundles or depends on the packages below, each under the license shown.",
  "The text of each license is copied from the package.",
  "",
];
for (const entry of entries) {
  parts.push(rule, `${entry.name}@${entry.version}`, `License: ${entry.license}`);
  if (entry.repository) parts.push(`Source: ${entry.repository}`);
  parts.push("", entry.texts.length ? entry.texts.join("\n\n") : "(the package has no license file; its license field is shown above)", "");
}

mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, parts.join("\n"));
console.log(`${entries.length} packages written to ${output}`);
