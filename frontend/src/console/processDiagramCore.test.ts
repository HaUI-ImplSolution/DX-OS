// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { describe, expect, it } from "vitest";
import type { ProcessView } from "./api";
import { buildProcessDiagram, PROCESS_ANCHORS, PROCESS_NODE_HEIGHT, PROCESS_NODE_WIDTH } from "./processDiagramCore";

const state = (name: string, terminal = false, completions: unknown[] = []) => ({ name, label: name, terminal, completions });
const action = (name: string, from: string[], to: string, by = "HANDLER", roles: string[] = []) => ({ name, label: name, from, to, by, roles, when: null });

const family = {
  initial: "submitted",
  handlers: ["CLERK"],
  deadlineSeconds: 3600,
  awaiting: ["submitted"],
  autoCloseSeconds: 86400,
  autoCloseAction: null,
  states: [
    state("submitted"),
    state("resolved"),
    state("closed", true, [{ operation: "add_to_group", group: "STAFF" }, { operation: "remove_from_group", groupField: "team" }]),
    state("withdrawn", true),
    state("unused"),
  ],
  actions: [
    action("resolve", ["submitted"], "resolved", "HANDLER", ["LEAD"]),
    action("reopen", ["resolved"], "submitted"),
    action("confirm", ["resolved"], "closed", "OWNER"),
    action("withdraw", ["submitted"], "withdrawn", "OWNER"),
  ],
} as unknown as ProcessView;

describe("the process diagram", () => {
  const diagram = buildProcessDiagram(family, 260);

  it("draws only the states reached without withdrawing", () => {
    expect(diagram.nodes.map((node) => node.id)).toEqual(["submitted", "resolved", "closed"]);
    expect(diagram.nodes.find((node) => node.initial)?.id).toBe("submitted");
  });

  it("captions a waiting state with its deadline and a final one with what follows", () => {
    const caption = (id: string) => diagram.nodes.find((node) => node.id === id)?.caption ?? "";
    expect(caption("submitted")).toContain("hạn");
    expect(caption("closed")).toContain("STAFF");
    expect(caption("closed")).toContain("{team}");
    expect(caption("resolved")).toBe("");
  });

  it("names who takes each action and adds the automatic close", () => {
    const label = (id: string) => diagram.edges.find((edge) => edge.id === id)?.label ?? "";
    expect(label("resolve-submitted")).toContain("LEAD");
    expect(label("reopen-resolved")).toContain("CLERK");
    expect(label("confirm-resolved")).toContain("người nộp");
    expect(diagram.edges.find((edge) => edge.id === "auto_close")?.dashed).toBe(true);
  });

  it("routes every arrow between known anchors without putting boxes on top of each other", () => {
    const anchors = new Set(PROCESS_ANCHORS.map((anchor) => anchor.id));
    for (const edge of diagram.edges) {
      expect(anchors.has(edge.sourceAnchorId)).toBe(true);
      expect(anchors.has(edge.targetAnchorId)).toBe(true);
      expect(edge.labelOffset).toBeGreaterThan(0);
    }
    for (const a of diagram.nodes) {
      for (const b of diagram.nodes) {
        if (a.id >= b.id) continue;
        const apart = Math.abs(a.position.x - b.position.x) >= PROCESS_NODE_WIDTH || Math.abs(a.position.y - b.position.y) >= PROCESS_NODE_HEIGHT;
        expect(apart).toBe(true);
      }
    }
  });
});
