// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
// Writes the license notices of the production dependencies of an npm project, so that a build
// ships them with the code it bundles.
//
//   node third-party-licenses.mjs <project dir> <output file>
//
// The packages come from package-lock.json (everything not marked dev-only) and the texts from the
// license and third-party notice files inside each installed package. A package without a license
// file gets the standard text of its declared license.
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
const NOTICE_FILE = /^(licen[cs]e|copying|unlicen[cs]e|notice|copyrightnotice|third[-_]?party[-_]?notices?)(\.|-|_|$)/i;
const LICENSE_FILE = /^(licen[cs]e|copying|unlicen[cs]e)(\.|-|_|$)/i;

// The project's own copy of the Apache License 2.0, when it sits next to this script.
const apache = new URL("../licenses/LICENSE", import.meta.url);

const STANDARD_TEXTS = {
  MIT: `Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated documentation files (the "Software"), to deal in the Software without restriction, including without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:

The above copyright notice and this permission notice shall be included in all copies or substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.`,
  ISC: `Permission to use, copy, modify, and/or distribute this software for any purpose with or without fee is hereby granted, provided that the above copyright notice and this permission notice appear in all copies.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.`,
  ...(existsSync(apache) ? { "Apache-2.0": readFileSync(apache, "utf8").trim() } : {}),
};

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

// The standard text of the first declared license that has one, for a package without a license file.
function standardTextOf(pkg) {
  const id = licenseOf(pkg).replace(/[()]/g, "").split(/\s+OR\s+/).find((name) => name in STANDARD_TEXTS);
  if (!id) return "(the package has no license file; its license field is shown above)";
  const author = typeof pkg.author === "string" ? pkg.author : pkg.author?.name;
  const holder = author ? `The author it declares is ${author}.` : "It declares no author.";
  return `The package ships no license file. ${holder} The standard text of the ${id} license follows.\n\n${STANDARD_TEXTS[id]}`;
}

const entries = [];
for (const [path, meta] of Object.entries(lock.packages ?? {})) {
  if (!path.includes("node_modules/") || meta.dev) continue;
  const dir = join(project, path);
  if (!existsSync(join(dir, "package.json"))) continue;
  const pkg = JSON.parse(readFileSync(join(dir, "package.json"), "utf8"));
  const files = readdirSync(dir)
    .filter((file) => NOTICE_FILE.test(file) && statSync(join(dir, file)).isFile())
    .sort();
  const texts = files.map((file) => readFileSync(join(dir, file), "utf8").trim());
  if (!files.some((file) => LICENSE_FILE.test(file))) texts.unshift(standardTextOf(pkg));
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
  "The texts are copied from the package; a package without a license file says so and gets the standard text.",
  "",
];
for (const entry of entries) {
  parts.push(rule, `${entry.name}@${entry.version}`, `License: ${entry.license}`);
  if (entry.repository) parts.push(`Source: ${entry.repository}`);
  parts.push("", entry.texts.join("\n\n"), "");
}

// A project may keep a licenses/THIRD-PARTY-ASSETS.txt for the files that a package ships under another license than its own.
const assets = join(project, "licenses", "THIRD-PARTY-ASSETS.txt");
if (existsSync(assets)) parts.push(rule, "Assets that the packages above ship under their own licenses", "", readFileSync(assets, "utf8").trim(), "");

mkdirSync(dirname(output), { recursive: true });
writeFileSync(output, parts.join("\n"));
console.log(`${entries.length} packages written to ${output}`);
