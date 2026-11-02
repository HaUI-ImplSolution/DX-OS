// SPDX-License-Identifier: Apache-2.0
export interface FieldMeta {
  name: string;
  label: string;
  type: "text" | "number" | "datetime" | "choice" | "reference" | "user";
  required: boolean;
  unique: boolean;
  options: string[];
  reference: string | null;
}

export interface ProcessInfo {
  template: string;
  approver: string;
  allowSelfDecision: boolean;
}

export interface EntityMeta {
  name: string;
  label: string;
  fields: FieldMeta[];
  hasProcess: boolean;
  process: ProcessInfo | null;
  canRead: boolean;
  canWrite: boolean;
  canDecide: boolean;
}

export interface ModelMeta {
  name: string;
  entities: EntityMeta[];
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
