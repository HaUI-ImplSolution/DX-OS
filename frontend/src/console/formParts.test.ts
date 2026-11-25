// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { asList, compact, mentions, NAME, rec, renameKey, ROLE, text, uniqueName } from "./formParts";

describe("readers of the parsed file", () => {
  it("reads anything that is not a mapping as an empty one", () => {
    expect(rec(null)).toEqual({});
    expect(rec([1])).toEqual({});
    expect(rec({ a: 1 })).toEqual({ a: 1 });
  });

  it("reads one name or a list of names as a list", () => {
    expect(asList("A")).toEqual(["A"]);
    expect(asList(["A", 2])).toEqual(["A", "2"]);
    expect(asList(undefined)).toEqual([]);
    expect(text(null)).toBe("");
  });
});

describe("compact, renameKey and uniqueName", () => {
  it("drops empty values but keeps zero", () => {
    expect(compact({ a: "", b: false, c: undefined, d: 0, e: "x" })).toEqual({ d: 0, e: "x" });
  });

  it("renames a key in the same place", () => {
    expect(Object.keys(renameKey({ a: 1, b: 2, c: 3 }, "b", "z"))).toEqual(["a", "z", "c"]);
  });

  it("numbers a name that is taken", () => {
    expect(uniqueName("field", ["other"])).toBe("field");
    expect(uniqueName("field", ["field", "field_2"])).toBe("field_3");
  });
});

describe("mentions and the name patterns", () => {
  it("finds a field as a whole word in a condition", () => {
    expect(mentions("kind == a", "kind")).toBe(true);
    expect(mentions("kinds == a", "kind")).toBe(false);
    expect(mentions("sub_kind == a", "kind")).toBe(false);
    expect(mentions(undefined, "kind")).toBe(false);
  });

  it("accepts a name in lower case and a role in upper case", () => {
    expect(NAME.test("case_2")).toBe(true);
    expect(NAME.test("Case")).toBe(false);
    expect(ROLE.test("OFFICER_2")).toBe(true);
    expect(ROLE.test("officer")).toBe(false);
  });
});
