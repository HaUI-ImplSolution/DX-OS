// SPDX-License-Identifier: Apache-2.0
import { json, request } from "../shell/api";
import type { EntityMeta, InboxItem, ModelMeta, Person, RecordEvent } from "./types";

export const listModels = () => request<ModelMeta[]>("/meta/models");

export const getEntityMeta = (model: string, entity: string) =>
  request<EntityMeta>(`/meta/models/${model}/entities/${entity}`);

export const listInbox = () => request<InboxItem[]>("/inbox");

const recordPath = (model: string, entity: string, id: number | string) => `/data/${model}/${entity}/${id}`;

export const takeAction = (model: string, entity: string, id: number | string, action: string, note?: string) =>
  request<Record<string, unknown>>(`${recordPath(model, entity, id)}/actions/${action}`, json("POST", { note }));

export const assignRecord = (model: string, entity: string, id: number | string, assignee: string | null) =>
  request<Record<string, unknown>>(`${recordPath(model, entity, id)}/assign`, json("POST", { assignee }));

export const listHandlers = (model: string, entity: string, id: number | string) =>
  request<Person[]>(`${recordPath(model, entity, id)}/handlers`);

export const listEvents = (model: string, entity: string, id: number | string) =>
  request<RecordEvent[]>(`${recordPath(model, entity, id)}/events`);
