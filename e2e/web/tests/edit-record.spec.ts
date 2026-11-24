// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { execFileSync } from "node:child_process";
import path from "node:path";
import { expect, test, type Page } from "@playwright/test";

const USER = process.env.E2E_USER ?? "ui_admin";
const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";
const REPO = path.resolve(__dirname, "../../..");

async function signIn(page: Page, address: string) {
  await page.goto(address);
  await page.locator("#username").fill(USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
}

// Runs on an empty stack: it applies the notes template, gives the account the role of that model, then
// creates a record and edits it. The API adds webUrl to every record, and saving the edit form must not
// send it back.
test("an edited record is saved without the keys the API adds to it", async ({ page }) => {
  await signIn(page, "/console");
  await page.getByRole("tab", { name: "Thư viện mẫu" }).click();
  await page.getByRole("heading", { name: "Ghi chú", exact: true }).locator("xpath=ancestor::*[.//button][1]").getByRole("button", { name: "Dùng mẫu này" }).click();
  await page.getByRole("button", { name: "Xem kế hoạch" }).click();
  await page.getByRole("button", { name: "Áp dụng", exact: true }).click();
  await expect(page.getByRole("tab", { name: "Mô hình đang chạy", selected: true })).toBeVisible();

  // The role of a model exists once the model is applied; the token carries it after the next sign-in.
  execFileSync("python3", ["-c", `import sys; sys.path.insert(0, "e2e/api"); import kc; kc.ensure_user("${USER}", ["ADMIN", "notes/EDITOR"])`], { cwd: REPO });
  await page.context().clearCookies();
  await page.evaluate(() => sessionStorage.clear());
  await signIn(page, "/notes-note/create");

  await page.getByLabel("Tiêu đề").fill("First title");
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  await expect(page).toHaveURL(/\/notes-note\/[^/]+\/show$/);

  await page.goto(page.url().replace(/\/show$/, ""));
  await page.getByLabel("Tiêu đề").fill("Second title");
  const saved = page.waitForResponse((response) => response.request().method() === "PATCH" && response.url().includes("/data/notes/note/"));
  await page.getByRole("button", { name: "Lưu", exact: true }).click();
  const response = await saved;
  expect(Object.keys(response.request().postDataJSON())).not.toContain("webUrl");
  expect(response.status()).toBe(200);
  await expect(page.getByRole("heading", { name: "Second title" })).toBeVisible();
});
