// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { isMap, isScalar, isSeq, LineCounter, parseDocument, type Node } from "yaml";
import type { DiagramProblem } from "./api";
import { asList, rec, text, type Rec } from "./formParts";
import { translateBackend } from "../shell/backendMessages";
import { t } from "../shell/t";

export type EntityPart = "general" | "fields" | "access" | "process" | "ui" | "documents" | "ai";
export type ProcessPart = "overview" | "states" | "actions" | "deadline" | "automation";

// A place of the model builder: a part of the model, or a part of one entity.
export type NavTarget =
  | { kind: "general" }
  | { kind: "roles" }
  | { kind: "units" }
  | { kind: "entities" }
  | { kind: "entity"; entity: string; part: EntityPart; step?: ProcessPart };

export const navKey = (target: NavTarget): string =>
  target.kind !== "entity" ? target.kind : target.part === "process" ? `entity:${target.entity}:process:${target.step ?? "overview"}` : `entity:${target.entity}:${target.part}`;

// The place of the builder that edits a key path of the file.
export function targetOf(path: string[]): NavTarget {
  const [top, entity, part, key] = path;
  if (top === "roles" || top === "units") return { kind: top };
  if (top !== "entities" || !entity) return { kind: "general" };
  if (part === "fields") return { kind: "entity", entity, part: "fields" };
  if (part === "access") return { kind: "entity", entity, part: "access" };
  if (part === "ai") return { kind: "entity", entity, part: "ai" };
  if (part === "ui") return { kind: "entity", entity, part: "ui" };
  if (part === "documents") return { kind: "entity", entity, part: "documents" };
  if (part === "process") return { kind: "entity", entity, part: "process", step: STEP_OF[key ?? ""] ?? "overview" };
  return { kind: "entity", entity, part: "general" };
}

// The step of the process page that edits each key of `process`; every other key is on the overview.
const STEP_OF: Record<string, ProcessPart> = {
  states: "states",
  actions: "actions",
  deadline: "deadline",
  calendar: "deadline",
  auto_close: "deadline",
  escalate: "deadline",
  after: "automation",
};

// The key path of the deepest node of the file that holds a position.
function pathAt(node: Node | null | undefined, offset: number, path: string[]): string[] {
  if (isMap(node)) {
    for (const pair of node.items) {
      const key = pair.key as Node | null;
      const value = pair.value as Node | null;
      const start = key?.range?.[0] ?? value?.range?.[0];
      const end = value?.range?.[2] ?? key?.range?.[2];
      if (start === undefined || end === undefined || offset < start || offset > end) continue;
      const name = isScalar(key) ? String(key.value) : "";
      return pathAt(value, offset, [...path, name]);
    }
  }
  if (isSeq(node)) {
    for (const item of node.items as Node[]) {
      const range = item?.range;
      if (range && offset >= range[0] && offset <= range[2]) return pathAt(item, offset, path);
    }
  }
  return path;
}

// error: Core refused the file, so it cannot be applied. warning: the builder expects Core to refuse it;
// Core still decides, when the plan is asked for.
export type Level = "error" | "warning";

export interface Issue {
  key: string;
  message: string;
  level: Level;
  // The field the issue is about, marked on its row.
  field?: string;
}

// The place of the builder a key of navKey stands for.
export function targetOfKey(key: string): NavTarget {
  if (key === "roles" || key === "units" || key === "entities") return { kind: key };
  const [kind, entity, part, step] = key.split(":");
  if (kind !== "entity" || !entity) return { kind: "general" };
  if (part === "process") return { kind: "entity", entity, part: "process", step: (step as ProcessPart) ?? "overview" };
  return { kind: "entity", entity, part: (part as EntityPart) ?? "general" };
}

// The problems the backend found in the file, each placed on the part of the builder that edits it.
// A problem without a line belongs to the model as a whole.
export function placeProblems(source: string, problems: DiagramProblem[]): Issue[] {
  if (problems.length === 0) return [];
  const lines = new LineCounter();
  const doc = parseDocument(source, { lineCounter: lines });
  return problems.map((problem) => {
    const message = translateBackend(problem.message);
    if (problem.line <= 0) return { key: "general", message, level: "error" as Level };
    const offset = (lines.lineStarts[problem.line - 1] ?? 0) + Math.max(0, problem.column - 1);
    return { key: navKey(targetOf(pathAt(doc.contents as Node, offset, []))), message, level: "error" as Level };
  });
}

const VIEWING = ["all", "read", "unit", "assigned"];

// Problems the builder can see by itself while the file is written, before the backend checks it.
export function localIssues(value: Rec): Issue[] {
  const issues: Omit<Issue, "level">[] = [];
  const roleNames = (Array.isArray(value.roles) ? value.roles : []).map((role) => text(rec(role).name));
  if (roleNames.length === 0) issues.push({ key: "roles", message: t("Mô hình cần ít nhất một vai trò.") });
  Object.entries(rec(value.entities)).forEach(([entity, raw]) => {
    const item = rec(raw);
    const access = rec(item.access);
    const unknown = Object.keys(access).filter((name) => !roleNames.includes(name));
    if (unknown.length > 0) {
      issues.push({ key: `entity:${entity}:access`, message: t("Vai trò chưa khai báo: {roles}", { roles: unknown.join(", ") }) });
    }
    Object.entries(rec(item.fields)).forEach(([field, spec]) => {
      const type = text(rec(spec).type);
      if (type === "choice" && asList(rec(spec).options).length === 0) {
        issues.push({ key: `entity:${entity}:fields`, field, message: t("Trường {field} chưa có lựa chọn nào.", { field }) });
      }
      if (type === "reference" && !rec(spec).to) {
        issues.push({ key: `entity:${entity}:fields`, field, message: t("Trường {field} chưa chọn thực thể tham chiếu tới.", { field }) });
      }
    });
    if (item.process === undefined) return;
    const handlers = asList(rec(item.process).handler);
    const missing = handlers.filter((name) => !roleNames.includes(name));
    if (missing.length > 0) {
      issues.push({ key: `entity:${entity}:process:overview`, message: t("Vai trò xử lý chưa khai báo: {roles}", { roles: missing.join(", ") }) });
    }
    const blind = handlers.filter((name) => roleNames.includes(name) && !VIEWING.includes(text(access[name])));
    if (blind.length > 0) {
      const message = t("Vai trò xử lý không xem được hồ sơ: {roles}", { roles: blind.join(", ") });
      issues.push({ key: `entity:${entity}:process:overview`, message });
      issues.push({ key: `entity:${entity}:access`, message });
    }
  });
  return issues.map((issue) => ({ ...issue, level: "warning" }));
}

// The worst issue at this place or below it, so a closed branch of the tree still shows it.
export function levelUnder(issues: Issue[], key: string): Level | null {
  const under = issues.filter((issue) => issue.key === key || issue.key.startsWith(`${key}:`));
  return under.some((issue) => issue.level === "error") ? "error" : under.length > 0 ? "warning" : null;
}
