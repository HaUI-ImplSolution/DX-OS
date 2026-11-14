// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { json, queryString, request, requestFile } from "../shell/api";
import type { AttachmentItem, EntityMeta, InboxItem, ModelMeta, Page, Person, PrintableDocument, RecordEvent } from "./types";

export const listModels = () => request<ModelMeta[]>("/meta/models");

export const getEntityMeta = (model: string, entity: string) =>
  request<EntityMeta>(`/meta/models/${model}/entities/${entity}`);

export const listInbox = (page: number, size: number) => request<Page<InboxItem>>(`/inbox?page=${page}&size=${size}`);

export interface EntityStats {
  total: number;
  byStatus: { status: string; count: number }[];
  overdue: number;
  // Records that reached a final state without ever going overdue, and those that did; only for an entity with a deadline.
  closedOnTime?: number | null;
  closedLate?: number | null;
  byUnit?: { unit: string | null; count: number }[] | null;
  avgDecisionSeconds?: number | null;
  avgOpenAgeSeconds?: number | null;
  oldestOpenAgeSeconds?: number | null;
  computedAt: string;
}

export interface ReportGroup {
  key: string | null;
  [figure: string]: string | number | boolean | null;
}

export interface EntityReport {
  groupBy: string | null;
  metrics: string[];
  groups: ReportGroup[];
  totals: Record<string, number | null>;
  truncated: boolean;
  computedAt: string;
}

export const getReport = (model: string, entity: string, groupBy: string, metrics: string[]) =>
  request<EntityReport>(`/data/${model}/${entity}/report${queryString({ groupBy, metrics: metrics.join(",") })}`);

export const getStats = (model: string, entity: string) => request<EntityStats>(`/data/${model}/${entity}/stats`);

const recordPath = (model: string, entity: string, id: number | string) => `/data/${model}/${entity}/${id}`;

export const takeAction = (model: string, entity: string, id: number | string, action: string, note?: string) =>
  request<Record<string, unknown>>(`${recordPath(model, entity, id)}/actions/${action}`, json("POST", { note }));

export const assignRecord = (model: string, entity: string, id: number | string, assignee: string | null) =>
  request<Record<string, unknown>>(`${recordPath(model, entity, id)}/assign`, json("POST", { assignee }));

export const listHandlers = (model: string, entity: string, id: number | string) =>
  request<Person[]>(`${recordPath(model, entity, id)}/handlers`);

export const listFiles = (model: string, entity: string, id: number | string) =>
  request<AttachmentItem[]>(`${recordPath(model, entity, id)}/files`);

export const uploadFile = (model: string, entity: string, id: number | string, field: string, file: File) => {
  const form = new FormData();
  form.append("file", file);
  return request<AttachmentItem>(`${recordPath(model, entity, id)}/files?field=${encodeURIComponent(field)}`, {
    method: "POST",
    body: form,
  });
};

export const listDocuments = (model: string, entity: string, id: number | string) =>
  request<PrintableDocument[]>(`${recordPath(model, entity, id)}/documents`);

export const printDocument = (model: string, entity: string, id: number | string, name: string) =>
  requestFile(`${recordPath(model, entity, id)}/documents/${encodeURIComponent(name)}`);

export const fileLink = (model: string, entity: string, id: number | string, fileId: number) =>
  request<{ name: string; url: string }>(`${recordPath(model, entity, id)}/files/${fileId}/link`);

export const removeFile = (model: string, entity: string, id: number | string, fileId: number) =>
  request<null>(`${recordPath(model, entity, id)}/files/${fileId}`, { method: "DELETE" });

export const listEvents = (model: string, entity: string, id: number | string) =>
  request<RecordEvent[]>(`${recordPath(model, entity, id)}/events`);

export const searchPeople = (text: string) => request<Person[]>(`/people?q=${encodeURIComponent(text)}`);

export const peopleByIds = (ids: string[]) => request<Person[]>(`/people?ids=${ids.map(encodeURIComponent).join(",")}`);
