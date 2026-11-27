// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import { levelUnder, localIssues, navKey, placeProblems, targetOf, targetOfKey } from "./navModel";

describe("targetOf and navKey", () => {
  it("places a key path on the part of the builder that edits it", () => {
    expect(targetOf(["roles", "0"])).toEqual({ kind: "roles" });
    expect(targetOf(["entities", "case", "fields", "title"])).toEqual({ kind: "entity", entity: "case", part: "fields" });
    expect(targetOf(["entities", "case", "title"])).toEqual({ kind: "entity", entity: "case", part: "general" });
    expect(targetOf(["version"])).toEqual({ kind: "general" });
  });

  it("sends each process key to its step, the rest to the overview", () => {
    expect(targetOf(["entities", "case", "process", "calendar"])).toMatchObject({ part: "process", step: "deadline" });
    expect(targetOf(["entities", "case", "process", "after"])).toMatchObject({ step: "automation" });
    expect(targetOf(["entities", "case", "process", "handler"])).toMatchObject({ step: "overview" });
  });

  it("turns a target into a key and back", () => {
    for (const path of [["roles"], ["entities", "case", "ui"], ["entities", "case", "process", "states"]]) {
      const target = targetOf(path);
      expect(targetOfKey(navKey(target))).toEqual(target);
    }
  });
});

describe("localIssues", () => {
  const model = {
    roles: [{ name: "OFFICER" }],
    entities: {
      case: {
        access: { OFFICER: "assigned", STAFF: "all" },
        fields: { kind: { type: "choice" }, owner: { type: "reference" } },
        process: { handler: ["OFFICER", "BOSS"] },
      },
    },
  };

  it("reports a role, a choice and a reference the file leaves undefined", () => {
    const keys = localIssues(model).map((issue) => `${issue.key}|${issue.field ?? ""}`);
    expect(keys).toContain("entity:case:access|");
    expect(keys).toContain("entity:case:fields|kind");
    expect(keys).toContain("entity:case:fields|owner");
    expect(keys).toContain("entity:case:process:overview|");
  });

  it("asks for a role when there is none", () => {
    expect(localIssues({ roles: [], entities: {} }).map((issue) => issue.key)).toEqual(["roles"]);
  });

  it("reports a handler that cannot see the records", () => {
    const blind = { roles: [{ name: "OFFICER" }], entities: { case: { access: { OFFICER: "own" }, process: { handler: "OFFICER" } } } };
    expect(localIssues(blind).map((issue) => issue.key)).toEqual(["entity:case:process:overview", "entity:case:access"]);
  });
});

describe("levelUnder", () => {
  const issues = [
    { key: "entity:case:fields", message: "a", level: "warning" as const },
    { key: "entity:case:process:states", message: "b", level: "error" as const },
  ];

  it("takes the worst issue at the place or below it", () => {
    expect(levelUnder(issues, "entity:case")).toBe("error");
    expect(levelUnder(issues, "entity:case:fields")).toBe("warning");
    expect(levelUnder(issues, "entity:other")).toBeNull();
  });

  it("does not match a longer name that only starts the same", () => {
    expect(levelUnder(issues, "entity:cas")).toBeNull();
  });
});

describe("placeProblems", () => {
  const source = "roles:\n  - name: A\nentities:\n  case:\n    fields:\n      title:\n        type: wrong\n";

  it("places a problem on the part that holds its line", () => {
    const [issue] = placeProblems(source, [{ line: 7, column: 15, message: "bad" }]);
    expect(issue.key).toBe("entity:case:fields");
    expect(issue.level).toBe("error");
  });

  it("gives a problem without a line to the model as a whole", () => {
    expect(placeProblems(source, [{ line: 0, column: 0, message: "bad" }])[0].key).toBe("general");
  });
});
