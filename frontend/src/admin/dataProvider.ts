// SPDX-License-Identifier: Apache-2.0
import { HttpError, type DataProvider } from "react-admin";
import { ApiError, json, queryString, request } from "../shell/api";
import { splitResource, type EntityMeta, type Page } from "./types";

type Row = Record<string, unknown>;

const SYSTEM_FIELDS = [
  "id",
  "created_by",
  "created_at",
  "updated_at",
  "version",
  "status",
  "assignee",
  "decided_by",
  "decided_at",
  "handle_by",
  "overdue",
];

function path(resource: string) {
  const { model, entity } = splitResource(resource);
  return `/data/${model}/${entity}`;
}

// A date typed without an offset becomes an instant in the browser's time zone.
function toApiValue(meta: EntityMeta | undefined, name: string, value: unknown) {
  const type = meta?.fields.find((field) => field.name === name)?.type;
  if (type === "datetime" && typeof value === "string" && value !== "") {
    return new Date(value).toISOString();
  }
  return value === "" ? null : value;
}

function body(meta: EntityMeta | undefined, data: Row) {
  const payload: Row = {};
  Object.entries(data).forEach(([name, value]) => {
    const field = meta?.fields.find((field) => field.name === name);
    if (!SYSTEM_FIELDS.includes(name) && field?.type !== "file" && field?.type !== "code") {
      payload[name] = toApiValue(meta, name, value);
    }
  });
  return payload;
}

// The version of the record the form was opened on; the API refuses the save with 409 when someone else
// changed the record since, so a stale form cannot overwrite it. A record without a version sends none.
function versionOf(previous: Row | undefined) {
  return typeof previous?.version === "number" ? { version: previous.version } : {};
}

// The API answers a rule that failed with the problem of each field; react-admin shows them on the form.
async function call<T>(action: () => Promise<T>): Promise<T> {
  try {
    return await action();
  } catch (error) {
    if (error instanceof ApiError) {
      const errors = error.data && typeof error.data === "object" ? error.data : undefined;
      throw new HttpError(error.message, error.status, errors ? { errors } : undefined);
    }
    throw error;
  }
}

// A filter input named field.op (a range end, a search by part) reaches the provider as {field: {op: value}}.
function addFilter(params: Record<string, string>, name: string, value: unknown) {
  if (value === undefined || value === null || value === "") return;
  if (Array.isArray(value)) params[`filter.${name}.in`] = value.join(",");
  else if (typeof value === "object") Object.entries(value).forEach(([op, inner]) => addFilter(params, `${name}.${op}`, inner));
  else params[`filter.${name}`] = String(value);
}

function filterParams(filter: Row | undefined, extra: Row = {}) {
  const params: Record<string, string> = {};
  Object.entries({ ...filter, ...extra }).forEach(([name, value]) => addFilter(params, name, value));
  return params;
}

export function createDataProvider(metaOf: (resource: string) => EntityMeta | undefined): DataProvider {
  const list = async (resource: string, params: Record<string, string | number | undefined>) => {
    const page = await call(() => request<Page<Row>>(`${path(resource)}${queryString(params)}`));
    return { data: page.items as never[], total: page.totalElements };
  };

  return {
    getList: (resource, { pagination, sort, filter }) =>
      list(resource, {
        page: (pagination?.page ?? 1) - 1,
        size: pagination?.perPage ?? 25,
        sort: sort?.field,
        order: sort?.order.toLowerCase(),
        ...filterParams(filter),
      }),
    getOne: async (resource, { id }) => ({
      data: (await call(() => request<Row>(`${path(resource)}/${id}`))) as never,
    }),
    getMany: (resource, { ids }) => list(resource, { size: 200, ...filterParams({ id: ids }) }),
    getManyReference: (resource, { target, id, pagination, sort, filter }) =>
      list(resource, {
        page: (pagination?.page ?? 1) - 1,
        size: pagination?.perPage ?? 25,
        sort: sort?.field,
        order: sort?.order.toLowerCase(),
        ...filterParams(filter, { [target]: id }),
      }),
    create: async (resource, { data }) => ({
      data: (await call(() =>
        request<Row>(path(resource), json("POST", body(metaOf(resource), data))),
      )) as never,
    }),
    update: async (resource, { id, data, previousData }) => ({
      data: (await call(() =>
        request<Row>(
          `${path(resource)}/${id}`,
          json("PATCH", { ...body(metaOf(resource), data), ...versionOf(previousData) }),
        ),
      )) as never,
    }),
    updateMany: async (resource, { ids, data }) => {
      await Promise.all(
        ids.map((id) =>
          call(() => request<Row>(`${path(resource)}/${id}`, json("PATCH", body(metaOf(resource), data)))),
        ),
      );
      return { data: ids };
    },
    delete: async (resource, { id, previousData }) => {
      await call(() => request<null>(`${path(resource)}/${id}`, { method: "DELETE" }));
      return { data: previousData as never };
    },
    deleteMany: async (resource, { ids }) => {
      await Promise.all(ids.map((id) => call(() => request<null>(`${path(resource)}/${id}`, { method: "DELETE" }))));
      return { data: ids };
    },
  };
}
