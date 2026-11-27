// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { HttpError } from "react-admin";
import { beforeEach, describe, expect, it, vi } from "vitest";

const { request } = vi.hoisted(() => ({ request: vi.fn() }));
// The real request layer reads the browser address through the sign-in module; only the call itself is replaced.
vi.mock("../shell/auth", () => ({ getToken: async () => null, clearSession: () => undefined }));
vi.mock("../shell/api", async () => ({ ...(await vi.importActual<object>("../shell/api")), request }));

import { ApiError } from "../shell/api";
import { createDataProvider } from "./dataProvider";
import type { EntityMeta, FieldMeta } from "./types";

const field = (name: string, type: FieldMeta["type"]): FieldMeta => ({ name, label: name, type, required: false, unique: false, options: [], reference: null });
const meta = { fields: [field("title", "text"), field("note", "text")] } as EntityMeta;
const provider = createDataProvider(() => meta);
const page = (items: unknown[]) => ({ items, page: 0, size: 25, totalElements: items.length, totalPages: 1 });
const sentPath = () => String(request.mock.calls[0][0]);
const sentInit = () => request.mock.calls[0][1] as RequestInit;

beforeEach(() => {
  request.mockReset();
});

// The error a call ends with.
async function refused(call: Promise<unknown>): Promise<unknown> {
  try {
    await call;
  } catch (error) {
    return error;
  }
  throw new Error("the call was not refused");
}

describe("reading", () => {
  it("lists a page with its sort and range, counting from zero", async () => {
    request.mockResolvedValue(page([{ id: 1 }, { id: 2 }]));
    const result = await provider.getList("support-ticket", {
      pagination: { page: 2, perPage: 10 },
      sort: { field: "created_at", order: "DESC" },
      filter: {},
    });
    expect(sentPath()).toBe("/data/support/ticket?page=1&size=10&sort=created_at&order=desc");
    expect(result).toEqual({ data: [{ id: 1 }, { id: 2 }], total: 2 });
  });

  it("turns a filter into filter.* parameters, a list into .in and a range end into .op", async () => {
    request.mockResolvedValue(page([]));
    await provider.getList("support-ticket", {
      pagination: { page: 1, perPage: 25 },
      sort: { field: "id", order: "ASC" },
      filter: { status: "NEW", unit: ["A", "B"], due: { gte: "2026-01-01" }, empty: "", none: null, q: "  hello " },
    });
    const query = new URLSearchParams(sentPath().split("?")[1]);
    expect(query.get("filter.status")).toBe("NEW");
    expect(query.get("filter.unit.in")).toBe("A,B");
    expect(query.get("filter.due.gte")).toBe("2026-01-01");
    expect(query.get("q")).toBe("hello");
    expect(query.has("filter.empty")).toBe(false);
    expect(query.has("filter.none")).toBe(false);
  });

  it("drops a blank search text", async () => {
    request.mockResolvedValue(page([]));
    await provider.getList("support-ticket", { pagination: { page: 1, perPage: 25 }, sort: { field: "id", order: "ASC" }, filter: { q: "   " } });
    expect(sentPath()).not.toContain("q=");
  });

  it("reads one record and several by id", async () => {
    request.mockResolvedValueOnce({ id: 7 });
    expect((await provider.getOne("support-ticket", { id: 7 })).data).toEqual({ id: 7 });
    expect(sentPath()).toBe("/data/support/ticket/7");

    request.mockReset();
    request.mockResolvedValue(page([{ id: 1 }, { id: 2 }]));
    await provider.getMany("support-ticket", { ids: [1, 2] });
    expect(sentPath()).toContain("filter.id.in=1%2C2");
    expect(sentPath()).toContain("size=200");
  });

  it("lists the records that point at another one", async () => {
    request.mockResolvedValue(page([]));
    await provider.getManyReference("support-comment", {
      target: "ticket",
      id: 5,
      pagination: { page: 1, perPage: 25 },
      sort: { field: "id", order: "ASC" },
      filter: {},
    });
    expect(sentPath()).toContain("filter.ticket=5");
  });
});

describe("writing", () => {
  it("creates with only the declared fields", async () => {
    request.mockResolvedValue({ id: 9 });
    await provider.create("support-ticket", { data: { title: "A", id: 1, webUrl: "u" } });
    expect(sentPath()).toBe("/data/support/ticket");
    expect(sentInit().method).toBe("POST");
    expect(JSON.parse(String(sentInit().body))).toEqual({ title: "A" });
  });

  it("sends the version the form was opened on, so a stale save is refused", async () => {
    request.mockResolvedValue({ id: 9 });
    await provider.update("support-ticket", { id: 9, data: { title: "B" }, previousData: { id: 9, version: 4 } });
    expect(sentInit().method).toBe("PATCH");
    expect(JSON.parse(String(sentInit().body))).toEqual({ title: "B", version: 4 });
  });

  it("sends no version for a record without one", async () => {
    request.mockResolvedValue({ id: 9 });
    await provider.update("support-ticket", { id: 9, data: { title: "B" }, previousData: { id: 9 } as never });
    expect(JSON.parse(String(sentInit().body))).toEqual({ title: "B" });
  });

  it("changes and deletes many records one call each", async () => {
    request.mockResolvedValue(null);
    expect((await provider.updateMany("support-ticket", { ids: [1, 2], data: { title: "X" } })).data).toEqual([1, 2]);
    expect(request).toHaveBeenCalledTimes(2);
    request.mockClear();
    expect((await provider.deleteMany("support-ticket", { ids: [1, 2, 3] })).data).toEqual([1, 2, 3]);
    expect(request).toHaveBeenCalledTimes(3);
    expect((request.mock.calls[0][1] as RequestInit).method).toBe("DELETE");
  });

  it("returns the previous record of a delete", async () => {
    request.mockResolvedValue(null);
    const result = await provider.delete("support-ticket", { id: 3, previousData: { id: 3, title: "A" } });
    expect(result.data).toEqual({ id: 3, title: "A" });
  });
});

describe("a refused call", () => {
  it("hands the problem of each field to the form", async () => {
    request.mockImplementation(async () => {
      throw new ApiError("Invalid", 422, { title: "required" });
    });
    const error = await refused(provider.create("support-ticket", { data: { title: "" } }));
    expect(error).toBeInstanceOf(HttpError);
    expect((error as HttpError).status).toBe(422);
    expect((error as HttpError).body).toEqual({ errors: { title: "required" } });
  });

  it("raises the message of the server when no problem belongs to a field of the form", async () => {
    request.mockImplementation(async () => {
      throw new ApiError("Rule failed", 422, { elsewhere: "x" });
    });
    const error = (await refused(provider.create("support-ticket", { data: { title: "A" } }))) as HttpError;
    expect(error.message).toBe("Rule failed");
    expect(error.body).toBeNull();
  });

  it("lets any other error through", async () => {
    request.mockImplementation(async () => {
      throw new TypeError("network");
    });
    expect(await refused(provider.getOne("support-ticket", { id: 1 }))).toBeInstanceOf(TypeError);
  });
});
