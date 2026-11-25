// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { conditionMet, conditionSource, conditionText } from "./condition";
import type { ProcessCondition } from "./types";

const amount: ProcessCondition = { field: "amount", op: ">", value: 5000000 };
const kind: ProcessCondition = { field: "kind", op: "==", value: "loan" };
const urgent: ProcessCondition = { field: "note", op: "==", value: "very urgent" };

describe("conditionMet", () => {
  it("meets an empty condition", () => {
    expect(conditionMet(null, {})).toBe(true);
  });

  it("compares a number as a number and a text as a text", () => {
    expect(conditionMet(amount, { amount: "6000000" })).toBe(true);
    expect(conditionMet(amount, { amount: 900 })).toBe(false);
    expect(conditionMet(kind, { kind: "loan" })).toBe(true);
  });

  it("never meets a comparison on a missing or non-numeric value", () => {
    expect(conditionMet(amount, {})).toBe(false);
    expect(conditionMet(amount, { amount: "" })).toBe(false);
    expect(conditionMet(amount, { amount: "abc" })).toBe(false);
  });

  it("joins comparisons with and, or and not", () => {
    const record = { amount: 9000000, kind: "gift" };
    expect(conditionMet({ op: "and", conditions: [amount, kind] }, record)).toBe(false);
    expect(conditionMet({ op: "or", conditions: [amount, kind] }, record)).toBe(true);
    expect(conditionMet({ op: "not", conditions: [kind] }, record)).toBe(true);
    expect(conditionMet({ op: "not", conditions: [amount] }, {})).toBe(true);
  });
});

describe("conditionSource", () => {
  it("writes a comparison the way the model file does", () => {
    expect(conditionSource(amount)).toBe("amount > 5000000");
    expect(conditionSource(null)).toBe("");
  });

  it("quotes a value with spaces or a keyword only inside a joined condition", () => {
    expect(conditionSource(urgent)).toBe("note == very urgent");
    expect(conditionSource({ op: "and", conditions: [kind, urgent] })).toBe('kind == loan and note == "very urgent"');
    expect(conditionSource({ op: "and", conditions: [kind, { field: "k", op: "==", value: "or" }] })).toBe('kind == loan and k == "or"');
  });

  it("brackets an or inside an and, and a group under not", () => {
    expect(conditionSource({ op: "and", conditions: [amount, { op: "or", conditions: [kind, urgent] }] })).toBe('amount > 5000000 and (kind == loan or note == "very urgent")');
    expect(conditionSource({ op: "not", conditions: [{ op: "or", conditions: [kind, amount] }] })).toBe("not (kind == loan or amount > 5000000)");
  });
});

describe("conditionText", () => {
  it("uses the label of the field and groups an or inside an and", () => {
    const fields = [{ name: "kind", label: "Loại" }] as Parameters<typeof conditionText>[1];
    expect(conditionText(kind, fields)).toBe("Loại == loan");
    expect(conditionText({ op: "and", conditions: [kind, { op: "or", conditions: [urgent, kind] }] }, fields)).toContain("(");
  });
});
