// SPDX-License-Identifier: Apache-2.0
import { fileURLToPath } from "node:url";
import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

// monaco-yaml imports Monaco files by their path inside the package; Monaco now exposes them
// without the esm/vs prefix, so the old paths are pointed at the files.
const monaco = fileURLToPath(new URL("./node_modules/monaco-editor/esm/vs/", import.meta.url));

export default defineConfig({
  plugins: [react()],
  resolve: {
    alias: [{ find: /^monaco-editor\/esm\/vs\//, replacement: monaco }],
  },
  server: {
    port: 5173,
  },
});
