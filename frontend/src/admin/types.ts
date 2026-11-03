// SPDX-License-Identifier: Apache-2.0
export interface FieldMeta {
  name: string;
  label: string;
  type: "text" | "number" | "datetime" | "choice" | "reference" | "user" | "unit" | "file";
  required: boolean;
  unique: boolean;
  options: string[];
  reference: string | null;
}

export interface ProcessState {
  name: string;
  label: string;
  terminal: boolean;
}

export interface ProcessAction {
  name: string;
  label: string;
  from: string[];
  to: string;
  by: "HANDLER" | "OWNER";
  needsNote: boolean;
}

export interface ProcessInfo {
  family: string;
  handler: string;
  allowSelfDecision: boolean;
  deadlineSeconds: number | null;
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
}

export interface UnitMeta {
  name: string;
  label: string;
}

export interface ModelMeta {
  name: string;
  units: UnitMeta[];
  entities: EntityMeta[];
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
