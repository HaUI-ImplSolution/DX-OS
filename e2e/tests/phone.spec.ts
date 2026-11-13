// SPDX-License-Identifier: Apache-2.0
import { expect, test, type Page } from "@playwright/test";

const USER = process.env.E2E_USER ?? "ui_admin";
const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";

// A phone: the layout viewport must stay as wide as the screen, or the browser zooms the page out.
test.use({ viewport: { width: 390, height: 844 }, isMobile: true, hasTouch: true });

async function fitsScreen(page: Page, where: string) {
  const { scrollWidth, innerWidth } = await page.evaluate(() => ({
    scrollWidth: document.documentElement.scrollWidth,
    innerWidth: window.innerWidth,
  }));
  expect(scrollWidth, `${where}: the page is wider than the screen`).toBeLessThanOrEqual(innerWidth);
}

test("the console and the inbox fit a phone screen", async ({ page }) => {
  await page.goto("/console");
  await page.locator("#username").fill(USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  await expect(page.getByRole("tab").first()).toBeVisible();
  await fitsScreen(page, "console");

  for (const label of ["Thư viện mẫu", "Soạn và áp dụng", "Hàng đợi sự kiện", "Người dùng"]) {
    await page.getByRole("tab", { name: label }).click();
    await expect(page.getByRole("tab", { name: label, selected: true })).toBeVisible();
    await fitsScreen(page, label);
  }

  // The language switch and the account menu stay on the screen next to the page title.
  const switchBox = await page.getByRole("button", { name: "English" }).boundingBox();
  expect(switchBox && switchBox.x + switchBox.width).toBeLessThanOrEqual(390);

  await page.goto("/");
  await expect(page.locator("#main-content").getByRole("heading", { name: "Hộp việc" })).toBeVisible();
  await fitsScreen(page, "inbox");
});
