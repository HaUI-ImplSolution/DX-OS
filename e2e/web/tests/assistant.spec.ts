// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { expect, test, type Page } from "@playwright/test";

// Runs only against a full-mode stack, where the assistant runs (see assistant/README.md).
// E2E_ASSISTANT_RECORD_PATH is a record whose suggestion differs from its value, E2E_ASSISTANT_UNCHANGED_RECORD_PATH one whose suggestion equals it.
const PASSWORD = process.env.E2E_PASSWORD ?? "Test1234!";
const USER = process.env.E2E_ASSISTANT_USER ?? "business-demo";

async function linkAccount(page: Page) {
  await page.goto("/assistant");
  await page.locator("#username").fill(USER);
  await page.locator("#password").fill(PASSWORD);
  await page.locator("#kc-login").click();
  const linked = page.waitForResponse(response => new URL(response.url()).pathname === "/assistant-api/v1/oauth/callback");
  await page.getByRole("button", { name: /^Liên kết (lại )?DX-OS$/ }).click();
  expect((await linked).status()).toBe(302);
}

async function propose(page: Page, path: string) {
  await page.goto(path);
  const generated = page.waitForResponse(response => response.url().endsWith("/assistant-api/v1/me/proposals") && response.request().method() === "POST", { timeout: 60000 });
  await page.getByRole("button", { name: "Tạo đề xuất", exact: true }).click();
  const response = await generated;
  expect(response.status()).toBe(200);
  const proposal = (await response.json()).data.proposal;
  expect(proposal.state).toBe("pending");
  return proposal;
}

test("a suggestion is written to Core only after its owner confirms", async ({ page }) => {
  const path = process.env.E2E_ASSISTANT_RECORD_PATH;
  test.skip(!path, "Requires the assistant and a record chosen by the operator");
  test.setTimeout(120000);
  await linkAccount(page);
  const writes: string[] = [];
  page.on("request", request => {
    if (request.method() === "PATCH" && request.url().includes("/api/v1/data/")) writes.push(request.url());
  });
  const proposal = await propose(page, path!);
  expect(proposal.snapshot.evidence.length).toBeGreaterThan(0);
  expect(writes).toEqual([]);
  const confirmed = page.waitForResponse(response => response.url().endsWith(`/proposals/${proposal.id}/confirm`) && response.request().method() === "POST");
  await page.getByRole("button", { name: "Xác nhận lưu", exact: true }).click();
  const receipt = await confirmed;
  expect(receipt.status()).toBe(200);
  const saved = (await receipt.json()).data;
  expect(saved.coreCommitted).toBe(true);
  expect(saved.record.version).toBeGreaterThan(proposal.snapshot.version);
});

test("confirming a value the record already has changes nothing in Core", async ({ page }) => {
  const path = process.env.E2E_ASSISTANT_UNCHANGED_RECORD_PATH;
  test.skip(!path, "Requires the assistant and a record chosen by the operator");
  test.setTimeout(120000);
  await linkAccount(page);
  const proposal = await propose(page, path!);
  expect(proposal.snapshot.value).toEqual(proposal.snapshot.previousValue);
  const result = await page.evaluate(async ({ proposal }) => {
    const key = Object.keys(sessionStorage).find(name => name.startsWith("oidc.user:"))!;
    const headers = { Authorization: `Bearer ${JSON.parse(sessionStorage.getItem(key)!).access_token}` };
    const record = `/api/v1/data/${proposal.snapshot.model}/${proposal.snapshot.entity}/${proposal.snapshot.recordId}`;
    const version = async () => (await (await fetch(record, { headers })).json()).data.version;
    const before = await version();
    const confirm = await fetch(`/assistant-api/v1/me/proposals/${proposal.id}/confirm`, {
      method: "POST", headers: { ...headers, "Content-Type": "application/json" }, body: "{}",
    });
    return { status: confirm.status, errorCode: (await confirm.json()).errorCode, before, after: await version() };
  }, { proposal });
  expect(result.status).toBe(409);
  expect(result.errorCode).toBe("ERR_PROPOSAL_NO_CHANGE");
  expect(result.after).toEqual(result.before);
});
