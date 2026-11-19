// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { expect, test } from "@playwright/test";

// Runs only against a stack with the optional assistant, its inference and its sources (see assistant/README.md).
const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";

test("business assistant keeps a suggestion pending until its record owner confirms", async ({ page }) => {
  const path = process.env.E2E_ASSISTANT_RECORD_PATH;
  test.skip(!path, "Requires an operator-provisioned AI task, sources and self-host inference");
  test.setTimeout(240000);
  const user = process.env.E2E_ASSISTANT_USER ?? "business-demo";
  await page.goto("/assistant");
  await page.locator("#username").fill(user);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  await expect(page.getByText("Bản cài đã cấu hình model self-host.")).toBeVisible();
  const linked = page.waitForResponse(response => new URL(response.url()).pathname === "/assistant-api/v1/oauth/callback");
  await page.getByRole("button", { name: /^Liên kết (lại )?DX-OS$/ }).click();
  expect((await linked).status()).toBe(302);
  await expect(page.getByText("Đã liên kết tài khoản DX-OS.")).toBeVisible();
  await page.goto(path!);
  await expect(page.getByRole("heading", { name: "Trợ lý nghiệp vụ", exact: true })).toBeVisible();
  const mutations: string[] = [];
  page.on("request", request => {
    if (request.method() === "PATCH" && request.url().includes("/api/v1/data/")) mutations.push(request.url());
  });
  const generated = page.waitForResponse(response => response.url().endsWith("/assistant-api/v1/me/proposals") && response.request().method() === "POST", { timeout: 180000 });
  await page.getByRole("button", { name: "Tạo đề xuất", exact: true }).click();
  const response = await generated;
  expect(response.status()).toBe(200);
  let result = (await response.json()).data;
  expect(result.insufficient).toBe(false);
  expect(result.proposal.state).toBe("pending");
  expect(result.proposal.snapshot.sources.length).toBeGreaterThan(0);
  expect(mutations).toEqual([]);
  await expect(page.getByTestId("assistant-proposed-value")).toContainText(result.proposal.snapshot.value);
  const references = page.locator("details").filter({ has: page.locator("summary", { hasText: "Nguồn tham khảo" }) });
  await expect(references).toBeVisible();
  expect(await references.evaluate(element => (element as HTMLDetailsElement).open)).toBe(false);
  const expectedValue = process.env.E2E_ASSISTANT_EXPECTED_VALUE;
  if (expectedValue) expect(result.proposal.snapshot.value).toBe(expectedValue);
  if (process.env.E2E_ASSISTANT_NO_WARNINGS === "1") expect(result.proposal.snapshot.warnings).toEqual([]);
  const warningField = process.env.E2E_ASSISTANT_WARNING_FIELD;
  if (warningField) {
    expect(result.proposal.snapshot.warnings.some((item: { field: string }) => item.field === warningField)).toBe(true);
    await expect(page.getByRole("alert").filter({ hasText: "Cần kiểm tra" })).toBeVisible();
    await expect(page.getByText("Xác nhận chỉ lưu trường đề xuất; các lưu ý không tự thay đổi hồ sơ.")).toBeVisible();
  }
  if (process.env.E2E_ASSISTANT_NO_CHANGE === "1") {
    expect(result.proposal.snapshot.value).toEqual(result.proposal.snapshot.previousValue);
    await expect(page.getByRole("button", { name: "Không có thay đổi", exact: true })).toBeDisabled();
    await expect(page.getByText("Giá trị đề xuất đã trùng với hồ sơ. Không có thay đổi để lưu.")).toBeVisible();
    const checked = await page.evaluate(async ({ proposal }) => {
      const key = Object.keys(sessionStorage).find(name => name.startsWith("oidc.user:"))!;
      const token = JSON.parse(sessionStorage.getItem(key)!).access_token;
      const headers = { Authorization: `Bearer ${token}` };
      const path = `/api/v1/data/${proposal.snapshot.model}/${proposal.snapshot.entity}/${proposal.snapshot.recordId}`;
      const read = async (suffix = "") => (await (await fetch(path + suffix, { headers })).json()).data;
      const before = await read(), events = await read("/events");
      const confirm = await fetch(`/assistant-api/v1/me/proposals/${proposal.id}/confirm`, {
        method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: "{}",
      });
      const error = await confirm.json(), after = await read(), afterEvents = await read("/events");
      return { status: confirm.status, errorCode: error.errorCode, before: before.version, after: after.version,
        events: events.map((item: { id: number }) => item.id), afterEvents: afterEvents.map((item: { id: number }) => item.id) };
    }, { proposal: result.proposal });
    expect(checked.status).toBe(409); expect(checked.errorCode).toBe("ERR_PROPOSAL_NO_CHANGE");
    expect(checked.after).toEqual(checked.before); expect(checked.afterEvents).toEqual(checked.events);
    await page.screenshot({ path: test.info().outputPath("assistant-no-change.png"), fullPage: true });
    if (warningField) {
      await page.getByText("Sửa hồ sơ theo lưu ý", { exact: true }).click();
      await page.waitForURL(url => url.pathname.endsWith(`/${result.proposal.snapshot.recordId}`));
      await expect(page.getByRole("combobox", { name: /Đơn vị xử lý/ })).toBeVisible();
    }
    return;
  }
  await expect(page.getByRole("button", { name: "Xác nhận lưu", exact: true })).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("assistant-pending.png"), fullPage: true });
  const revisedValue = process.env.E2E_ASSISTANT_REVISED_VALUE;
  if (revisedValue) {
    const originalId = result.proposal.id;
    const input = page.getByRole("combobox").last();
    await input.click();
    await page.getByRole("option", { name: revisedValue, exact: true }).click();
    const revised = page.waitForResponse(response => response.url().endsWith(`/proposals/${originalId}/revise`) && response.request().method() === "POST");
    await page.getByRole("button", { name: "Xem lại nội dung đã sửa", exact: true }).click();
    const revision = await revised;
    expect(revision.status()).toBe(200);
    const proposal = (await revision.json()).data;
    expect(proposal.id).not.toBe(originalId);
    expect(proposal.snapshot.value).toBe(revisedValue);
    expect(proposal.state).toBe("pending");
    result = { ...result, proposal };
    await expect(page.getByText("Đã cập nhật bản đề xuất. Kiểm tra lại rồi xác nhận lưu.")).toBeVisible();
    expect(mutations).toEqual([]);
  }
  expect(result.proposal.snapshot.value).not.toEqual(result.proposal.snapshot.previousValue);
  const refreshedHistory = page.waitForResponse(response => new URL(response.url()).pathname ===
    `/api/v1/data/${result.proposal.snapshot.model}/${result.proposal.snapshot.entity}/${result.proposal.snapshot.recordId}/events`);
  const committed = page.waitForResponse(response => response.url().endsWith(`/proposals/${result.proposal.id}/confirm`) && response.request().method() === "POST");
  await page.getByRole("button", { name: "Xác nhận lưu", exact: true }).click();
  const receipt = await committed;
  expect(receipt.status()).toBe(200);
  const committedResult = (await receipt.json()).data;
  expect(committedResult.coreCommitted).toBe(true);
  expect(committedResult.changed).toBe(true);
  expect(committedResult.record.version).toBeGreaterThan(result.proposal.snapshot.version);
  for (const warning of result.proposal.snapshot.warnings ?? []) {
    expect(committedResult.record[warning.field] ?? null).toEqual(warning.actual);
  }
  expect((await refreshedHistory).status()).toBe(200);
  await expect(page.getByText("Sửa thông tin", { exact: true }).first()).toBeVisible();
  await expect(page.getByText("Đã lưu thay đổi vào hồ sơ.")).toBeVisible();
  await page.screenshot({ path: test.info().outputPath("assistant-saved.png"), fullPage: true });
});
