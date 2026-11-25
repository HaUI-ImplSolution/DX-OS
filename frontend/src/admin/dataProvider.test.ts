// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it, vi } from "vitest";
import { body } from "./dataProvider";
import type { EntityMeta, FieldMeta } from "./types";

// The request layer reads the browser address when it loads; body() does not use it.
vi.mock("../shell/api", () => ({ ApiError: class extends Error {}, json: () => ({}), queryString: () => "", request: () => undefined }));

const field = (name: string, type: FieldMeta["type"]): FieldMeta => ({ name, label: name, type, required: false, unique: false, options: [], reference: null });
const entity = { fields: [field("title", "text"), field("due", "datetime"), field("attachment", "file"), field("number", "code")] } as EntityMeta;

describe("body", () => {
  it("sends only the fields the model declares", () => {
    expect(body(entity, { title: "A", webUrl: "https://x", id: 7, version: 3 })).toEqual({ title: "A" });
  });

  it("leaves out file and code fields", () => {
    expect(body(entity, { title: "A", attachment: "x.pdf", number: "HS-1" })).toEqual({ title: "A" });
  });

  it("sends an empty value as null and a date as an instant", () => {
    expect(body(entity, { title: "", due: "2026-11-25T08:00:00Z" })).toEqual({ title: null, due: "2026-11-25T08:00:00.000Z" });
  });

  it("drops the system columns and webUrl while the entity is not loaded", () => {
    expect(body(undefined, { title: "A", webUrl: "u", id: 1, status: "NEW", created_by: "x" })).toEqual({ title: "A" });
  });
});
