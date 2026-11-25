// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { entityUses, fieldUses, renameFieldIn, renameInCondition, renameRoleIn, roleUses } from "./modelRefs";

describe("renameInCondition", () => {
  it("renames the field before an operator", () => {
    expect(renameInCondition("kind == a and level >= 3", "kind", "type")).toBe("type == a and level >= 3");
  });

  it("keeps a value or a quoted text that equals the name", () => {
    expect(renameInCondition('note == "kind" and other == kind', "kind", "type")).toBe('note == "kind" and other == kind');
  });

  it("leaves a name that only contains the field", () => {
    expect(renameInCondition("kinds == 1", "kind", "type")).toBe("kinds == 1");
  });

  it("returns a value that is not text as it is", () => {
    expect(renameInCondition(undefined, "kind", "type")).toBeUndefined();
  });
});

describe("fieldUses and renameFieldIn", () => {
  const entity = {
    owner_via: "parent",
    fields: { parent: { type: "reference" }, reason: { type: "text", required_when: "parent == x" } },
    process: { after: { DONE: [{ user: "parent" }] } },
    ui: { list: { columns: ["parent", "reason"], sort: "-parent" }, form: { groups: [{ fields: ["parent"] }] }, hints: { parent: "text" } },
    documents: { slip: { file: "slip-{{parent}}", body: [{ fields: ["parent"], text: "for {{parent}}" }] } },
    ai: { tasks: { fill: { input_fields: ["parent"], output_field: "reason" } } },
  };

  it("lists every place that uses a field", () => {
    expect(fieldUses(entity, "parent", undefined)).toHaveLength(8);
  });

  it("lists nothing for a field no place uses", () => {
    expect(fieldUses(entity, "unused", undefined)).toEqual([]);
  });

  it("follows the new name in every place it lists", () => {
    const next = renameFieldIn(entity, "parent", "case_ref", undefined);
    expect(Object.keys(next.fields as object)).toEqual(["case_ref", "reason"]);
    expect(next.owner_via).toBe("case_ref");
    expect(JSON.stringify(next)).not.toContain('"parent"');
    expect(JSON.stringify(next)).not.toContain("{{parent}}");
    expect(fieldUses({ ...entity, ...next }, "parent", undefined)).toEqual([]);
  });
});

describe("entityUses", () => {
  it("lists the fields of other entities that point at one entity", () => {
    const entities = { a: { label: "A" }, b: { label: "B", fields: { link: { to: "a" } } }, c: { fields: { x: { to: "b" } } } };
    expect(entityUses(entities, "a")).toHaveLength(1);
    expect(entityUses(entities, "a")[0]).toContain("B");
    expect(entityUses(entities, "c")).toEqual([]);
  });
});

describe("roleUses and renameRoleIn", () => {
  const entities = {
    case: {
      access: { OFFICER: "all", STAFF: "own" },
      process: { handler: ["OFFICER", "BOSS"], actions: { approve: { role: "OFFICER" }, close: { role: ["OFFICER", "STAFF"] } }, escalate: [{ to: "OFFICER" }, { to: ["BOSS"] }] },
    },
  };

  it("lists the access, the handler, the actions and the escalation that name a role", () => {
    expect(roleUses(entities, "OFFICER")).toHaveLength(5);
    expect(roleUses(entities, "NOBODY")).toEqual([]);
  });

  it("renames a role everywhere and keeps a single name single and a list a list", () => {
    const next = renameRoleIn(entities, "OFFICER", "CLERK").case as typeof entities.case;
    expect(Object.keys(next.access)).toEqual(["CLERK", "STAFF"]);
    expect(next.process.handler).toEqual(["CLERK", "BOSS"]);
    expect(next.process.actions.approve.role).toBe("CLERK");
    expect(next.process.actions.close.role).toEqual(["CLERK", "STAFF"]);
    expect(next.process.escalate[0].to).toBe("CLERK");
    expect(roleUses({ case: next }, "OFFICER")).toEqual([]);
  });
});
