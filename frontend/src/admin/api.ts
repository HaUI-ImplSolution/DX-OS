// SPDX-License-Identifier: Apache-2.0
import { json, request } from "../shell/api";
import type { EntityMeta, ModelMeta } from "./types";

export const listModels = () => request<ModelMeta[]>("/meta/models");

export const getEntityMeta = (model: string, entity: string) =>
  request<EntityMeta>(`/meta/models/${model}/entities/${entity}`);

export const decide = (model: string, entity: string, id: number | string, outcome: "approve" | "reject") =>
  request<Record<string, unknown>>(`/data/${model}/${entity}/${id}/decision`, json("POST", { outcome }));
