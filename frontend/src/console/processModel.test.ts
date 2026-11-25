// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import type { Family } from "./api";
import { processRows, writeSpec } from "./processModel";

const family = {
  name: "approval",
  initial: "PENDING",
  params: [],
  states: [
    { name: "PENDING", label: "Pending", terminal: false },
    { name: "APPROVED", label: "Approved", terminal: true },
    { name: "withdrawn", label: "Withdrawn", terminal: true },
  ],
  actions: [
    { name: "approve", label: "Approve", from: ["PENDING"], to: "APPROVED", by: "HANDLER", needsNote: false, roles: [], require: [] },
    { name: "withdraw", label: "Withdraw", from: ["PENDING"], to: "withdrawn", by: "OWNER", needsNote: false, roles: [], require: [] },
  ],
} as Family;

describe("processRows", () => {
  it("lists the template rows without withdraw and withdrawn", () => {
    const rows = processRows({}, family);
    expect(rows.states.map((state) => state.name)).toEqual(["PENDING", "APPROVED"]);
    expect(rows.actions.map((action) => action.name)).toEqual(["approve"]);
    expect(rows.actions[0].origin).toBe("base");
  });

  it("marks a changed, a removed and an added row", () => {
    const rows = processRows({ states: { PENDING: { label: "Waiting" }, APPROVED: null, DONE: { label: "Done" } } }, family);
    const origin = Object.fromEntries(rows.states.map((state) => [state.name, state.origin]));
    expect(origin).toEqual({ PENDING: "changed", APPROVED: "removed", DONE: "added" });
    expect(rows.states[0].label).toBe("Waiting");
    expect(rows.live.map((state) => state.name)).toEqual(["PENDING", "DONE"]);
  });

  it("takes the fields an action changes and keeps the others from the template", () => {
    const [approve] = processRows({ actions: { approve: { role: "OFFICER", needs_note: true } } }, family).actions;
    expect(approve.role).toEqual(["OFFICER"]);
    expect(approve.needsNote).toBe(true);
    expect(approve.to).toBe("APPROVED");
  });
});

describe("writeSpec", () => {
  const base = { label: "Approve", to: "APPROVED", role: [] };

  it("writes only what differs from the template", () => {
    expect(writeSpec({}, "approve", { label: "Approve", to: "APPROVED", role: ["OFFICER"] }, base)).toEqual({ approve: { role: ["OFFICER"] } });
  });

  it("drops the row when nothing differs from the template", () => {
    expect(writeSpec({ approve: { role: ["OFFICER"] } }, "approve", { ...base }, base)).toBeUndefined();
  });

  it("removes a template row with null and a row of the file with undefined", () => {
    expect(writeSpec({}, "approve", null, base)).toEqual({ approve: null });
    expect(writeSpec({ extra: { label: "X" } }, "extra", undefined)).toBeUndefined();
  });

  it("writes a row the file adds whole, without empty values", () => {
    expect(writeSpec({}, "extra", { label: "X", to: "DONE", role: [], needs_note: false })).toEqual({ extra: { label: "X", to: "DONE" } });
  });
});
