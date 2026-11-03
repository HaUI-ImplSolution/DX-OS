// SPDX-License-Identifier: Apache-2.0
import { request } from "../shell/api";

export interface Change {
  level: string;
  kind: string;
  entity: string;
  field: string | null;
  detail: string | null;
}

export interface Violation {
  entity: string;
  field: string | null;
  rule: string;
  rows: number;
  sample: number[];
}

export interface Plan {
  model: string;
  fromVersion: number;
  version: number;
  action: string;
  level: string;
  changes: Change[];
  rejections: string[];
  questions: string[];
  violations: Violation[];
  openRecords: Record<string, number>;
  statements: string[];
}

export interface Applied {
  model: string;
  version: number;
  status: string;
  runId: number;
}

export interface AppliedModel {
  model: string;
  version: number;
  hash: string;
  applied_by: string;
  applied_at: string;
}

export interface Template {
  id: string;
  name: string;
  family: string;
  description: string;
  content: string;
}

export interface FamilyState {
  name: string;
  label: string;
  terminal: boolean;
}

export interface FamilyAction {
  name: string;
  label: string;
  from: string[];
  to: string;
  by: "HANDLER" | "OWNER";
  needsNote: boolean;
}

export interface Family {
  name: string;
  initial: string;
  states: FamilyState[];
  actions: FamilyAction[];
  params: { name: string; required: boolean; description: string }[];
}

export interface Run {
  id: number;
  status: string;
  actor: string;
  started_at: string;
  finished_at: string | null;
  // A JSON list of the steps: seq, kind, target, status, error.
  steps: string;
}

const yaml = (method: string, text: string): RequestInit => ({
  method,
  headers: { "Content-Type": "application/yaml" },
  body: text,
});

const query = (params: Record<string, string | number | undefined>) => {
  const entries = Object.entries(params).filter(([, value]) => value !== undefined);
  return entries.length ? `?${new URLSearchParams(entries.map(([k, v]) => [k, String(v)])).toString()}` : "";
};

export const listApplied = () => request<AppliedModel[]>("/models");
export const getSchema = () => request<Record<string, unknown>>("/models/schema");
export const listFamilies = () => request<Family[]>("/models/families");
export const listTemplates = () => request<Template[]>("/models/templates");
export const listVersions = (model: string) =>
  request<{ version: number; hash: string; applied_by: string; applied_at: string }[]>(`/models/${model}/versions`);
export const getVersion = (model: string, version: number) =>
  request<{ version: number; content: string }>(`/models/${model}/versions/${version}`);
export const listRuns = (model: string) => request<Run[]>(`/models/${model}/runs`);

export const planModel = (text: string) => request<Plan>("/models/plan", yaml("POST", text));
export const applyModel = (text: string, base?: number, accept?: string) =>
  request<Applied>(`/models/apply${query({ base, accept })}`, yaml("POST", text));

export const planRollback = (model: string) => request<Plan>(`/models/${model}/rollback/plan`, { method: "POST" });
export const rollbackModel = (model: string, base: number, accept?: string) =>
  request<Applied>(`/models/${model}/rollback${query({ base, accept })}`, { method: "POST" });
