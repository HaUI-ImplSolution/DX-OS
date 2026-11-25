// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { expect, test } from "@playwright/test";

// A test account made by e2e/api/kc.py (kc.ensure_user): ADMIN, no second factor, the project's test password.
const USER = process.env.E2E_USER ?? "ui_admin";
const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";

test("opening a template asks first when the editor holds changes that are not applied", async ({ page }) => {
  await page.goto("/console");
  await page.locator("#username").fill(USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();

  const dialog = page.getByRole("dialog", { name: "Thay bản đang soạn?" });
  const unapplied = page.getByText("Có thay đổi chưa áp dụng");
  const useTemplate = async () => {
    await page.getByRole("tab", { name: "Thư viện mẫu" }).click();
    await page.getByRole("button", { name: "Dùng mẫu này" }).first().click();
  };

  // Nothing is written yet, so a template opens at once.
  await useTemplate();
  await expect(page.getByRole("tab", { name: "Soạn và áp dụng", selected: true })).toBeVisible();
  await expect(dialog).toHaveCount(0);

  await page.locator(".monaco-editor").first().click();
  await page.keyboard.press("Control+Home");
  await page.keyboard.type("# my draft\n");
  await expect(unapplied).toBeVisible();

  // Cancel keeps the draft and the page.
  await useTemplate();
  await expect(dialog).toBeVisible();
  await dialog.getByRole("button", { name: "Hủy" }).click();
  await expect(dialog).toHaveCount(0);
  await expect(page.getByRole("tab", { name: "Thư viện mẫu", selected: true })).toBeVisible();
  await page.getByRole("tab", { name: "Soạn và áp dụng" }).click();
  await expect(unapplied).toBeVisible();

  // Replace opens the template and drops the draft.
  await useTemplate();
  await dialog.getByRole("button", { name: "Thay thế" }).click();
  await expect(page.getByRole("tab", { name: "Soạn và áp dụng", selected: true })).toBeVisible();
  await expect(unapplied).toHaveCount(0);
});
