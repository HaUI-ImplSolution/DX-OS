// SPDX-License-Identifier: Apache-2.0
import type { EscalationLevel } from "../console/api";

export interface FieldMeta {
  name: string;
  label: string;
  type: "text" | "number" | "boolean" | "date" | "datetime" | "choice" | "reference" | "user" | "unit" | "file" | "code";
  required: boolean;
  unique: boolean;
  options: string[];
  reference: string | null;
  // Several of the options at once (a choice or a unit).
  multiple?: boolean;
  // Bounds of a number or a date, as text; a pattern the whole text must match.
  min?: string | null;
  max?: string | null;
  pattern?: string | null;
  // The field is required while the comparison holds.
  requiredWhen?: ProcessCondition | null;
  // The pattern a code field is made from, such as HS-{year}-{seq:5}.
  format?: string | null;
}

// How an entity looks on screen, as the model file declares it; every part is optional.
export interface EntityUi {
  icon?: string | null;
  menu?: { group: string | null; order: number } | null;
  list?: { columns: string[] | null; sort: string | null } | null;
  form?: { groups: { label: string; fields: string[] }[] } | null;
  hints?: Record<string, string> | null;
}

export interface ProcessState {
  name: string;
  label: string;
  terminal: boolean;
}

// The comparison an action waits for: the record field, the operator and the value to compare with.
export interface ProcessCondition {
  field: string;
  op: "==" | "!=" | "<" | "<=" | ">" | ">=";
  value: number | string;
}

export interface ProcessAction {
  name: string;
  label: string;
  from: string[];
  to: string;
  by: "HANDLER" | "OWNER";
  needsNote: boolean;
  when?: ProcessCondition | null;
}

export interface ProcessInfo {
  family: string;
  handlers: string[];
  allowSelfDecision: boolean;
  deadlineSeconds: number | null;
  escalations: EscalationLevel[];
  booking: { resource: string; starts: string; ends: string; capacity: number } | null;
  states: ProcessState[];
  actions: ProcessAction[];
}

export interface EntityMeta {
  name: string;
  label: string;
  fields: FieldMeta[];
  hasProcess: boolean;
  process: ProcessInfo | null;
  unitField: string | null;
  canRead: boolean;
  canWrite: boolean;
  canHandle: boolean;
  canAssign: boolean;
  // Names of the process actions the roles of the user may take.
  handlerActions?: string[];
  ui?: EntityUi | null;
}

export interface UnitMeta {
  name: string;
  label: string;
}

export interface ModelMeta {
  name: string;
  units: UnitMeta[];
  entities: EntityMeta[];
  // True while the model is being applied or needs reconciliation; it has no entities then.
  held?: boolean;
}

export interface Person {
  id: string;
  name: string;
}

export interface RecordEvent {
  id: number;
  action: string;
  actor_name: string | null;
  at: string;
  from_status: string | null;
  to_status: string | null;
  note: string | null;
  detail: string | null;
}

export interface AttachmentItem {
  id: number;
  field: string;
  name: string;
  size: number;
  content_type: string;
  uploaded_by: string;
  uploaded_at: string;
}

export interface InboxItem {
  model: string;
  entity: string;
  entityLabel: string;
  id: number;
  title: string | null;
  status: string;
  assignee: string | null;
  overdue: boolean;
  createdAt: string;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

// react-admin resource names join the model and the entity with a hyphen, which names never contain.
export const resourceName = (model: string, entity: string) => `${model}-${entity}`;

export function splitResource(resource: string): { model: string; entity: string } {
  const at = resource.indexOf("-");
  return { model: resource.slice(0, at), entity: resource.slice(at + 1) };
}
