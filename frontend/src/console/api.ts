// SPDX-License-Identifier: Apache-2.0
import { translateBackend } from "../shell/backendMessages";
import { json, queryString, request } from "../shell/api";

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
  applied_by_name: string | null;
  applied_at: string;
  // An apply of the model runs or stopped before it finished, so its users cannot use it.
  held: boolean;
}

export interface AppliedVersion {
  version: number;
  hash: string;
  applied_by: string;
  applied_by_name: string | null;
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
  // The roles this action is limited to (empty: the handlers of the process) and the fields it requires.
  roles: string[];
  require: string[];
  when?: { field: string; op: string; value: number | string } | null;
}

export interface FamilyParam {
  name: string;
  required: boolean;
  description: string;
}

export interface Family {
  name: string;
  initial: string;
  states: FamilyState[];
  actions: FamilyAction[];
  params: FamilyParam[];
}

export interface Completion {
  operation: string;
  user: string | null;
  group: string | null;
  groupField: string | null;
}

export interface DiagramState extends FamilyState {
  completions: Completion[];
}

// The process of one entity as its model file declares it: the states and actions of its family and
// what the file adds to them.
export interface ProcessView {
  entity: string;
  label: string;
  family: string;
  initial: string;
  handlers: string[];
  allowSelfDecision: boolean;
  deadlineSeconds: number | null;
  autoCloseSeconds: number | null;
  autoCloseAction: string | null;
  booking: { resource: string; starts: string; ends: string; capacity: number } | null;
  states: DiagramState[];
  actions: FamilyAction[];
  // The states where the deadline runs.
  awaiting: string[];
}

export interface DiagramProblem {
  line: number;
  column: number;
  message: string;
}

export interface DiagramResult {
  valid: boolean;
  errors: DiagramProblem[];
  processes: ProcessView[];
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

export interface OutboxMessage {
  id: number;
  topic: string;
  model: string;
  entity: string;
  record_id: number | null;
  attempts: number;
  last_error: string | null;
  created_at: string;
  stopped: boolean;
  next_attempt_at: string | null;
  delivered_at: string | null;
  payload: string;
}

export type OutboxStatus = "failed" | "stopped" | "pending" | "delivered";

export interface OutboxPage {
  counts: Record<OutboxStatus, number>;
  page: { items: OutboxMessage[]; page: number; size: number; totalElements: number; totalPages: number };
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
  request<AppliedVersion[]>(`/models/${model}/versions`);
export const getVersion = (model: string, version: number) =>
  request<{ version: number; content: string }>(`/models/${model}/versions/${version}`);
export const listRuns = (model: string) => request<Run[]>(`/models/${model}/runs`);

// The texts of a plan are the backend's own; they are read in the language of the interface.
const readable = (plan: Plan): Plan => ({
  ...plan,
  rejections: plan.rejections.map((text) => translateBackend(text)),
  questions: plan.questions.map((text) => translateBackend(text)),
  changes: plan.changes.map((change) => ({ ...change, detail: change.detail ? translateBackend(change.detail) : change.detail })),
});

// What the process diagrams of a model file show; a file with problems answers valid false.
export const previewDiagram = (text: string) => request<DiagramResult>("/models/diagram", yaml("POST", text));

export const planModel = async (text: string) => readable(await request<Plan>("/models/plan", yaml("POST", text)));
export const applyModel = (text: string, base?: number, accept?: string) =>
  request<Applied>(`/models/apply${query({ base, accept })}`, yaml("POST", text));

export const resumeModel = (model: string) => request<Applied>(`/models/${model}/resume`, { method: "POST" });
export const abandonModel = (model: string) => request<Applied>(`/models/${model}/abandon`, { method: "POST" });

export const planRollback = async (model: string) =>
  readable(await request<Plan>(`/models/${model}/rollback/plan`, { method: "POST" }));
export const rollbackModel = (model: string, base: number, accept?: string) =>
  request<Applied>(`/models/${model}/rollback${query({ base, accept })}`, { method: "POST" });

export const listOutbox = async (status: OutboxStatus, page: number, size: number) => {
  const loaded = await request<OutboxPage>(`/outbox${query({ status, page, size })}`);
  loaded.page.items = loaded.page.items.map((item) => ({
    ...item,
    last_error: item.last_error ? translateBackend(item.last_error) : item.last_error,
  }));
  return loaded;
};
export const retryOutbox = (id: number) => request<null>(`/outbox/${id}/retry`, { method: "POST" });

export interface UserView {
  id: string;
  username: string;
  name: string;
  email: string;
  enabled: boolean;
}

export interface UserPage {
  items: UserView[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface UserDetail {
  user: UserView;
  // The roles the user holds, by model.
  roles: Record<string, string[]>;
  units: string[];
}

export interface UserOptions {
  models: { model: string; roles: { name: string; description: string }[] }[];
  units: { name: string; label: string }[];
}

export const listUsers = (search: string, page: number, size: number) =>
  request<UserPage>(`/users${queryString({ search, page, size })}`);
export const getUser = (id: string) => request<UserDetail>(`/users/${id}`);
export const getUserOptions = () => request<UserOptions>("/users/options");
export const grantRole = (id: string, model: string, role: string) =>
  request<null>(`/users/${id}/roles/${model}/${role}`, { method: "PUT" });
export const revokeRole = (id: string, model: string, role: string) =>
  request<null>(`/users/${id}/roles/${model}/${role}`, { method: "DELETE" });
export const addUnit = (id: string, unit: string) => request<null>(`/users/${id}/units/${unit}`, { method: "PUT" });
export const removeUnit = (id: string, unit: string) =>
  request<null>(`/users/${id}/units/${unit}`, { method: "DELETE" });
export const createUser = (username: string, fullName: string, email: string) =>
  request<{ id: string; username: string; temporaryPassword: string }>("/users", json("POST", { username, fullName, email }));
export const setUserEnabled = (id: string, enabled: boolean) =>
  request<null>(`/users/${id}/enabled`, json("PUT", { enabled }));
export const resetUserPassword = (id: string) =>
  request<{ temporaryPassword: string }>(`/users/${id}/password`, { method: "POST" });

// One audit record about an account: who changed what, and when.
export interface UserHistoryItem {
  id: number;
  occurredAt: string;
  actorName: string | null;
  action: string;
  metadata: Record<string, unknown> | null;
}

export interface UserHistoryPage {
  items: UserHistoryItem[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export const listUserHistory = (id: string, page: number, size: number) =>
  request<UserHistoryPage>(`/audit-logs${queryString({ targetType: "USER", targetId: id, page, size })}`);

export interface AppClient {
  name: string;
  clientId: string;
  model: string;
  role: string;
  enabled: boolean;
}

// The answer to registering an application or giving it a new secret; the secret is shown only now.
export interface AppSecret {
  name: string;
  clientId: string;
  model?: string;
  role?: string;
  secret: string;
}

export const listApps = () => request<AppClient[]>("/clients");
export const registerApp = (name: string, model: string, role: string) => request<AppSecret>("/clients", json("POST", { name, model, role }));
export const renewAppSecret = (name: string) => request<AppSecret>(`/clients/${name}/secret`, { method: "POST" });
export const removeApp = (name: string) => request<null>(`/clients/${name}`, { method: "DELETE" });
