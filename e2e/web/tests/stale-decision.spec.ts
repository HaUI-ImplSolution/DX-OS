// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { execFileSync } from "node:child_process";
import path from "node:path";
import { expect, test, type Page } from "@playwright/test";

const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";
const HELPER = path.resolve(__dirname, "../helpers/case.py");
const CHANGED = "Hồ sơ vừa được thay đổi, hãy xem lại.";
const APPROVE = { name: "Duyệt", exact: true } as const;

function helper(...args: string[]) {
  return execFileSync("python3", [HELPER, ...args], { encoding: "utf-8" });
}

// The student files a request; the officer opens `address` with the event stream cut, so the page cannot
// read the record again by itself and the officer has not seen what changes next.
async function officerOpens(page: Page, reason: string, address: (record: number) => string) {
  const record: number = JSON.parse(helper("seed", reason).trim().split("\n").pop()!).id;
  await page.route("**/inbox/stream", (route) => route.abort());
  await page.goto(address(record));
  await page.locator("#username").fill("off1");
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  return record;
}

test("a decision on a record that changed is refused and the record is shown again", async ({ page }) => {
  const record = await officerOpens(page, "Decide first", (id) => `/onestop-request/${id}/show`);
  await expect(page.getByRole("heading", { name: "Decide first" })).toBeVisible();

  helper("edit", String(record), "Decide second");
  await page.getByRole("button", APPROVE).click();

  await expect(page.getByText(CHANGED)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Decide second" })).toBeVisible();
  await expect(page.getByRole("button", APPROVE)).toBeVisible();
});

test("taking a record that changed is refused and the record is shown again", async ({ page }) => {
  const record = await officerOpens(page, "Take first", (id) => `/onestop-request/${id}/show`);
  await expect(page.getByText("Chưa giao ai")).toBeVisible();

  helper("edit", String(record), "Take second");
  await page.getByRole("button", { name: "Nhận việc", exact: true }).click();

  await expect(page.getByText(CHANGED)).toBeVisible();
  await expect(page.getByRole("heading", { name: "Take second" })).toBeVisible();
  await expect(page.getByText("Chưa giao ai")).toBeVisible();
});

test("a batch decision on a record that changed is refused for that record", async ({ page }) => {
  const record = await officerOpens(page, "Batch first", () => "/onestop-request");
  const row = page.getByRole("row").filter({ hasText: "Batch first" });
  await row.getByRole("checkbox").check();

  helper("edit", String(record), "Batch second");
  await page.getByRole("button", APPROVE).click();
  await page.getByRole("button", { name: "Thực hiện", exact: true }).click();

  await expect(page.getByText("0 hồ sơ xong, 1 hồ sơ không thực hiện được")).toBeVisible();
  await expect(page.getByText(CHANGED)).toBeVisible();
});

test("a decision after the model changed is refused and the page asks for a reload", async ({ page }) => {
  await officerOpens(page, "Model first", (id) => `/onestop-request/${id}/show`);
  await expect(page.getByRole("heading", { name: "Model first" })).toBeVisible();

  helper("model");
  await page.getByRole("button", APPROVE).click();

  await expect(page.getByText("Mô hình vừa được đổi, hãy tải lại trang rồi thử lại")).toBeVisible();
  await expect(page.getByRole("button", APPROVE)).toBeVisible();
});
