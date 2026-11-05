// SPDX-License-Identifier: Apache-2.0
import { json, request } from "../shell/api";
import type { AttachmentItem, EntityMeta, InboxItem, ModelMeta, Page, Person, RecordEvent } from "./types";

export const listModels = () => request<ModelMeta[]>("/meta/models");

export const getEntityMeta = (model: string, entity: string) =>
  request<EntityMeta>(`/meta/models/${model}/entities/${entity}`);

export const listInbox = (page: number, size: number) => request<Page<InboxItem>>(`/inbox?page=${page}&size=${size}`);

export interface EntityStats {
  total: number;
  byStatus: { status: string; count: number }[];
  overdue: number;
  byUnit?: { unit: string | null; count: number }[] | null;
  avgDecisionSeconds?: number | null;
  avgOpenAgeSeconds?: number | null;
  oldestOpenAgeSeconds?: number | null;
  computedAt: string;
}

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

export const fileLink = (model: string, entity: string, id: number | string, fileId: number) =>
  request<{ name: string; url: string }>(`${recordPath(model, entity, id)}/files/${fileId}/link`);

export const removeFile = (model: string, entity: string, id: number | string, fileId: number) =>
  request<null>(`${recordPath(model, entity, id)}/files/${fileId}`, { method: "DELETE" });

export const listEvents = (model: string, entity: string, id: number | string) =>
  request<RecordEvent[]>(`${recordPath(model, entity, id)}/events`);
