// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { parseDocument } from "yaml";
import { describe, expect, it, vi } from "vitest";

// The screens read the browser address through the sign-in module when they load.
vi.mock("../shell/auth", () => ({ getToken: async () => null, clearSession: () => undefined }));
// The text editor needs a browser; only the functions around it are tested here.
vi.mock("./YamlEditor", () => ({ default: () => null }));

import { patch, prune } from "./EditorTab";
import { tidyUi } from "./UiEditor";

function edit(source: string, change: (value: Record<string, unknown>) => Record<string, unknown>) {
  const doc = parseDocument(source);
  const before = doc.toJS() as Record<string, unknown>;
  const after = change(structuredClone(before));
  patch(doc, [], before, after);
  return doc.toString();
}

describe("prune", () => {
  it("drops what the form left empty, at any depth", () => {
    expect(prune({ a: "", b: [], c: {}, d: { e: "", f: [{ g: "" }] }, h: "keep", n: 0, off: false })).toEqual({
      d: { f: [{}] },
      h: "keep",
      n: 0,
      off: false,
    });
  });

  it("keeps scalars and lists as they are", () => {
    expect(prune("x")).toBe("x");
    expect(prune(null)).toBeNull();
    expect(prune([1, "a"])).toEqual([1, "a"]);
  });
});

describe("patch", () => {
  const source = [
    "# the model",
    "name: support",
    "# who works here",
    "roles:",
    "  - ADMIN # the owner",
    "  - STAFF",
    "entities:",
    "  ticket:",
    "    label: Ticket # shown on screen",
    "",
  ].join("\n");

  it("writes nothing when nothing differs", () => {
    expect(edit(source, (value) => value)).toBe(source);
  });

  it("changes one value and keeps every comment", () => {
    const result = edit(source, (value) => ({ ...value, name: "help" }));
    expect(result).toContain("name: help");
    expect(result).toContain("# the model");
    expect(result).toContain("# who works here");
    expect(result).toContain("# the owner");
    expect(result).toContain("# shown on screen");
  });

  it("adds a key to a mapping and deletes one", () => {
    const result = edit(source, (value) => {
      const { name: _name, ...rest } = value;
      return { ...rest, title: "Support" };
    });
    expect(result).toContain("title: Support");
    expect(result).not.toContain("name: support");
  });

  it("appends to a list without touching the items before it", () => {
    const result = edit(source, (value) => ({ ...value, roles: [...(value.roles as string[]), "GUEST"] }));
    expect(result).toContain("- ADMIN # the owner");
    expect(result).toMatch(/- GUEST/);
  });

  it("removes an item from the middle of a list and keeps the others", () => {
    const result = edit(source, (value) => ({ ...value, roles: ["ADMIN"] }));
    expect(result).toContain("- ADMIN # the owner");
    expect(result).not.toContain("STAFF");
  });

  it("keeps the comment above the list on the first item when the first item changes", () => {
    const result = edit(source, (value) => ({ ...value, roles: ["BOSS", "STAFF"] }));
    expect(result).toContain("# who works here");
    expect(result).toContain("BOSS");
  });

  it("replaces a value that changes type", () => {
    const result = edit(source, (value) => ({ ...value, name: ["a", "b"] }));
    expect(parseDocument(result).toJS().name).toEqual(["a", "b"]);
  });

  it("puts a renamed key back in its place", () => {
    const result = edit("a: 1\nb: 2\nc: 3\n", () => ({ a: 1, x: 2, c: 3 }));
    expect(Object.keys(parseDocument(result).toJS())).toEqual(["a", "x", "c"]);
  });
});

describe("tidyUi", () => {
  it("leaves out the parts with nothing in them and the block itself", () => {
    expect(tidyUi({ icon: "", menu: { group: "", order: undefined }, list: { columns: [], sort: "" }, hints: {} })).toBeUndefined();
  });

  it("keeps what was filled in", () => {
    expect(
      tidyUi({
        icon: "Inbox",
        menu: { group: "Work", order: 2 },
        list: { columns: ["title"], sort: "" },
        form: { groups: [{ label: "Main", fields: ["title"] }] },
        hints: { title: "Short" },
      }),
    ).toEqual({
      icon: "Inbox",
      menu: { group: "Work", order: 2 },
      list: { columns: ["title"] },
      form: { groups: [{ label: "Main", fields: ["title"] }] },
      hints: { title: "Short" },
    });
  });

  it("drops a form without groups", () => {
    expect(tidyUi({ icon: "Inbox", form: { groups: [] } })).toEqual({ icon: "Inbox" });
  });
});
