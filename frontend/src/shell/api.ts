// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { clearSession, getToken } from "./auth";
import { translateData } from "./backendMessages";
import { errorMessageFor, statusMessageFor } from "./errorMessages";
import type { ApiResponse } from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:9080").replace(/\/$/, "");
export const API_V1 = `${API_BASE}/api/v1`;

export class ApiError extends Error {
  status: number;
  data: unknown;

  constructor(message: string, status: number, data: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_V1}${path}`, { ...init, headers });
  const contentType = response.headers.get("content-type") ?? "";
  const body: ApiResponse<T> | null = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (response.status === 401) {
    clearSession();
  }
  if (!response.ok || !body?.success) {
    const reason = (body?.data as { reason?: unknown } | null | undefined)?.reason;
    const message = errorMessageFor(
      body?.errorCode,
      body?.message ?? statusMessageFor(response.status),
      typeof reason === "string" ? reason : undefined,
    );
    throw new ApiError(message, response.status, translateData(body?.data));
  }
  return body.data;
}

// A file the API answers with, read with the same token; its name is the one the server gave.
export async function requestFile(path: string): Promise<{ blob: Blob; name: string }> {
  const token = await getToken();
  const headers = new Headers();
  if (token) headers.set("Authorization", `Bearer ${token}`);
  const response = await fetch(`${API_V1}${path}`, { headers });
  if (response.status === 401) clearSession();
  if (!response.ok) {
    const contentType = response.headers.get("content-type") ?? "";
    const body: ApiResponse<unknown> | null = contentType.includes("application/json") ? await response.json() : null;
    throw new ApiError(errorMessageFor(body?.errorCode, body?.message ?? statusMessageFor(response.status)), response.status, null);
  }
  const disposition = response.headers.get("content-disposition") ?? "";
  const name = /filename="?([^";]+)"?/.exec(disposition)?.[1] ?? "document.pdf";
  return { blob: await response.blob(), name };
}

export function json(method: string, payload: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}

export function queryString(params: Record<string, string | number | boolean | undefined | null>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : "";
}
