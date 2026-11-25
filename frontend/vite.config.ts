// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { fileURLToPath } from "node:url";
import { defineConfig } from "vitest/config";
import react from "@vitejs/plugin-react";

// monaco-yaml imports Monaco files by their path inside the package; Monaco now exposes them
// without the esm/vs prefix, so the old paths are pointed at the files.
const monaco = fileURLToPath(new URL("./node_modules/monaco-editor/esm/vs/", import.meta.url));

// Monaco registers language services for CSS, HTML, JSON and TypeScript, with their workers and the reference data they carry;
// the model editor edits YAML only, so those four registrations are left out of the bundle.
const skippedLanguageServices = "\0monaco-language-service-skipped";
const withoutLanguageServices = {
  name: "monaco-without-language-services",
  enforce: "pre" as const,
  resolveId(source: string, importer?: string) {
    if (importer && /monaco-editor\/esm\/vs\/(index|editor\/editor\.main)\.js$/.test(importer) && /languages\/features\/(css|html|json|typescript)\/register\.js$/.test(source)) {
      return skippedLanguageServices;
    }
    return null;
  },
  load(id: string) {
    return id === skippedLanguageServices ? "export {};" : null;
  },
};

export default defineConfig({
  plugins: [withoutLanguageServices, react()],
  resolve: {
    alias: [{ find: /^monaco-editor\/esm\/vs\//, replacement: monaco }],
  },
  server: {
    port: 5173,
  },
  test: {
    include: ["src/**/*.test.ts"],
    setupFiles: ["./src/test/setup.ts"],
    server: { deps: { inline: [/@mui/, /react-admin/, /ra-/] } },
  },
});
