// SPDX-License-Identifier: Apache-2.0
import { expect, test, type Page } from "@playwright/test";

// A test account made by deploy/dev/kc.py (kc.ensure_user): ADMIN, no second factor, the project's test password.
const USER = process.env.E2E_USER ?? "ui_admin";
const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";

const TABS = ["Mô hình đang chạy", "Thư viện mẫu", "Soạn và áp dụng", "Hàng đợi sự kiện", "Người dùng"];

async function shot(page: Page, name: string) {
  await page.screenshot({ path: `screenshots/${name}.png`, fullPage: true });
}

test("an admin signs in, applies a template and sees the model running", async ({ page }) => {
  // The web app sends a visitor without a session straight to the sign-in page of Keycloak.
  await page.goto("/console");
  await page.locator("#username").fill(USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();

  for (const label of TABS) {
    await expect(page.getByRole("tab", { name: label })).toBeVisible();
  }
  await shot(page, "01-console");

  // Library, then the editor with the template, then the plan, then the applied model.
  await page.getByRole("tab", { name: "Thư viện mẫu" }).click();
  await shot(page, "02-templates");
  await page.getByRole("button", { name: "Dùng mẫu này" }).first().click();

  await expect(page.getByRole("tab", { name: "Soạn và áp dụng", selected: true })).toBeVisible();
  await page.getByRole("button", { name: "Xem kế hoạch" }).click();
  await expect(page.getByText("Áp dụng được ngay, không cần chuyển dữ liệu.")).toBeVisible();
  await shot(page, "03-plan");

  await page.getByRole("button", { name: "Áp dụng", exact: true }).click();
  await expect(page.getByRole("tab", { name: "Mô hình đang chạy", selected: true })).toBeVisible();
  await expect(page.getByText("Phiên bản 1", { exact: true })).toBeVisible();
  await shot(page, "04-model-running");

  for (const label of TABS.slice(3)) {
    await page.getByRole("tab", { name: label }).click();
    await expect(page.getByRole("tab", { name: label, selected: true })).toBeVisible();
  }
  await shot(page, "05-users");
});
