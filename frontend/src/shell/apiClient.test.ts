// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { beforeEach, describe, expect, it, vi } from "vitest";

const { clearSession } = vi.hoisted(() => ({ clearSession: vi.fn() }));
vi.mock("./auth", () => ({ getToken: async () => "token-1", clearSession }));

import * as admin from "../admin/api";
import * as console_ from "../console/api";
import { API_V1, ApiError, queryString, request, requestFile } from "./api";

const calls: { url: string; init: RequestInit }[] = [];

function answer(body: unknown, status = 200, headers: Record<string, string> = { "content-type": "application/json" }) {
  calls.length = 0;
  vi.stubGlobal("fetch", async (url: string, init: RequestInit) => {
    calls.push({ url: String(url), init });
    return new Response(typeof body === "string" ? body : JSON.stringify(body), { status, headers });
  });
}

const sent = () => ({ path: calls[0].url.replace(API_V1, ""), method: calls[0].init?.method ?? "GET", body: calls[0].init?.body });

beforeEach(() => {
  clearSession.mockClear();
  answer({ success: true, data: { page: { items: [] }, rejections: [], questions: [], changes: [] } });
});

describe("a call of the API", () => {
  it("carries the token and returns the data", async () => {
    answer({ success: true, data: 7 });
    expect(await request<number>("/models")).toBe(7);
    expect(new Headers(calls[0].init.headers).get("Authorization")).toBe("Bearer token-1");
  });

  it("ends the session on 401", async () => {
    answer({ success: false, message: "no" }, 401);
    await expect(request("/models")).rejects.toBeInstanceOf(ApiError);
    expect(clearSession).toHaveBeenCalled();
  });

  it("keeps the status and translates the problems of a refusal", async () => {
    answer({ success: false, message: "refused", data: ["x"] }, 409);
    await expect(request("/models")).rejects.toMatchObject({ status: 409, data: ["x"] });
  });

  it("explains an answer that is not JSON", async () => {
    answer("slow down", 429, { "content-type": "text/plain" });
    await expect(request("/models")).rejects.toThrow("quá nhanh");
  });

  it("reads a file with the name the server gave", async () => {
    answer("pdf", 200, { "content-disposition": 'attachment; filename="phieu.pdf"' });
    expect((await requestFile("/x")).name).toBe("phieu.pdf");
    answer("pdf", 200, {});
    expect((await requestFile("/x")).name).toBe("document.pdf");
    answer({ success: false, message: "gone" }, 404);
    await expect(requestFile("/x")).rejects.toMatchObject({ status: 404 });
  });

  it("leaves empty values out of a query", () => {
    expect(queryString({ a: 1, b: "", c: undefined, d: null, e: false })).toBe("?a=1&e=false");
    expect(queryString({})).toBe("");
  });
});

// Each function with the method and path the backend serves it on.
const consoleCalls: [() => Promise<unknown>, string, string][] = [
  [() => console_.listApplied(), "GET", "/models"],
  [() => console_.getSchema(), "GET", "/models/schema"],
  [() => console_.getDataCatalog("m"), "GET", "/models/m/data-catalog"],
  [() => console_.getDataLineage("m"), "GET", "/models/m/data-lineage"],
  [() => console_.getDataQuality("m", "e", 30), "GET", "/data/m/e/quality?staleDays=30"],
  [() => console_.listFamilies(), "GET", "/models/families"],
  [() => console_.listTemplates(), "GET", "/models/templates"],
  [() => console_.listVersions("m"), "GET", "/models/m/versions"],
  [() => console_.getVersion("m", 2), "GET", "/models/m/versions/2"],
  [() => console_.listRuns("m"), "GET", "/models/m/runs"],
  [() => console_.previewDiagram("a: 1"), "POST", "/models/diagram"],
  [() => console_.planModel("a: 1"), "POST", "/models/plan"],
  [() => console_.applyModel("a: 1", 2, "LOSS"), "POST", "/models/apply?base=2&accept=LOSS"],
  [() => console_.applyModel("a: 1"), "POST", "/models/apply"],
  [() => console_.resumeModel("m"), "POST", "/models/m/resume"],
  [() => console_.abandonModel("m"), "POST", "/models/m/abandon"],
  [() => console_.planRollback("m"), "POST", "/models/m/rollback/plan"],
  [() => console_.rollbackModel("m", 3), "POST", "/models/m/rollback?base=3"],
  [() => console_.listOutbox("pending" as never, 0, 20), "GET", "/outbox?status=pending&page=0&size=20"],
  [() => console_.retryOutbox(4), "POST", "/outbox/4/retry"],
  [() => console_.listUsers("", 0, 20), "GET", "/users?page=0&size=20"],
  [() => console_.getUser("u"), "GET", "/users/u"],
  [() => console_.getUserOptions(), "GET", "/users/options"],
  [() => console_.grantRole("u", "m", "R"), "PUT", "/users/u/roles/m/R"],
  [() => console_.revokeRole("u", "m", "R"), "DELETE", "/users/u/roles/m/R"],
  [() => console_.addUnit("u", "N"), "PUT", "/users/u/units/N"],
  [() => console_.removeUnit("u", "N"), "DELETE", "/users/u/units/N"],
  [() => console_.createUser("a", "A B", ""), "POST", "/users"],
  [() => console_.setUserEnabled("u", false), "PUT", "/users/u/enabled"],
  [() => console_.resetUserPassword("u"), "POST", "/users/u/password"],
  [() => console_.listUserHistory("u", 0, 10), "GET", "/audit-logs?targetType=USER&targetId=u&page=0&size=10"],
  [() => console_.listApps(), "GET", "/clients"],
  [() => console_.registerApp("a", "m", "R", null), "POST", "/clients"],
  [() => console_.renewAppSecret("a"), "POST", "/clients/a/secret"],
  [() => console_.changeAppRole("a", "R"), "PUT", "/clients/a"],
  [() => console_.removeApp("a"), "DELETE", "/clients/a"],
  [() => console_.listProjects(), "GET", "/projects"],
  [() => console_.createProject("p", "P", ""), "POST", "/projects"],
  [() => console_.updateProject("p", "P", ""), "PUT", "/projects/p"],
  [() => console_.deleteProject("p"), "DELETE", "/projects/p"],
  [() => console_.setModelProject("m", "p"), "PUT", "/models/m/project"],
  [() => console_.setAppProject("a", null), "PUT", "/clients/a/project"],
  [() => console_.getProjectScope(), "GET", "/projects/mine"],
  [() => console_.listProjectMembers("p"), "GET", "/projects/p/members"],
  [() => console_.setProjectMember("p", "u", "ADMIN"), "PUT", "/projects/p/members/u"],
  [() => console_.removeProjectMember("p", "u"), "DELETE", "/projects/p/members/u"],
];

const adminCalls: [() => Promise<unknown>, string, string][] = [
  [() => admin.listModels(), "GET", "/meta/models"],
  [() => admin.getEntityMeta("m", "e"), "GET", "/meta/models/m/entities/e"],
  [() => admin.listInbox(1, 5), "GET", "/inbox?page=1&size=5"],
  [() => admin.getReport("m", "e", "status", ["count", "sum:a"]), "GET", "/data/m/e/report?groupBy=status&metrics=count%2Csum%3Aa"],
  [() => admin.getStats("m", "e"), "GET", "/data/m/e/stats"],
  [() => admin.takeAction("m", "e", 1, "approve", "ok", { version: 2 }), "POST", "/data/m/e/1/actions/approve"],
  [() => admin.assignRecord("m", "e", 1, null, {}), "POST", "/data/m/e/1/assign"],
  [() => admin.listHandlers("m", "e", 1), "GET", "/data/m/e/1/handlers"],
  [() => admin.listFiles("m", "e", 1), "GET", "/data/m/e/1/files"],
  [() => admin.uploadFile("m", "e", 1, "scan", new File(["x"], "a.pdf")), "POST", "/data/m/e/1/files?field=scan"],
  [() => admin.listDocuments("m", "e", 1), "GET", "/data/m/e/1/documents"],
  [() => admin.printDocument("m", "e", 1, "phieu"), "GET", "/data/m/e/1/documents/phieu"],
  [() => admin.fileLink("m", "e", 1, 9), "GET", "/data/m/e/1/files/9/link"],
  [() => admin.removeFile("m", "e", 1, 9), "DELETE", "/data/m/e/1/files/9"],
  [() => admin.listEvents("m", "e", 1), "GET", "/data/m/e/1/events"],
  [() => admin.searchPeople("an b"), "GET", "/people?q=an%20b"],
  [() => admin.peopleByIds(["a", "b"]), "GET", "/people?ids=a,b"],
];

describe("the calls of the console and of the records", () => {
  it.each([...consoleCalls, ...adminCalls])("%s uses %s %s", async (call, method, path) => {
    await call();
    expect(sent()).toMatchObject({ method, path });
  });

  it("sends the version the caller saw with an action", async () => {
    await admin.takeAction("m", "e", 1, "approve", "ok", { version: 2, modelVersion: 5 });
    expect(JSON.parse(String(sent().body))).toEqual({ note: "ok", version: 2, modelVersion: 5 });
  });
});
