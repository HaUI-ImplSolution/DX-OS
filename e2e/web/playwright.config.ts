// SPDX-License-Identifier: Apache-2.0
import { defineConfig } from "@playwright/test";

// The stack under test answers at DXOS_BASE_URL (default: the gateway of the local stack).
export default defineConfig({
  testDir: "./tests",
  timeout: 120_000,
  expect: { timeout: 20_000 },
  workers: 1,
  retries: 0,
  reporter: [["list"]],
  use: {
    baseURL: process.env.DXOS_BASE_URL ?? "http://localhost:9080",
    locale: "vi-VN",
    viewport: { width: 1366, height: 800 },
    trace: "retain-on-failure",
    screenshot: "only-on-failure",
  },
});
