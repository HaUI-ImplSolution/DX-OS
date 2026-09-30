import { API_BASE_URL } from "@/config";
import { clearSession, getAccessToken } from "@/auth/session";
import type { ApiResponse } from "@/types";

const API_V1 = `${API_BASE_URL}/api/v1`;
const REQUEST_TIMEOUT_MS = 30_000;

export const staticUrl = (path: string) => `${API_BASE_URL}/${path.replace(/^\//, "")}`;

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

// status 0 means the request never reached the backend.
export const isNetworkError = (error: unknown) => error instanceof ApiError && error.status === 0;

export async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const token = await getAccessToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const controller = new AbortController();
  const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
  let response: Response;
  try {
    response = await fetch(`${API_V1}${path}`, { ...init, headers, signal: controller.signal });
  } catch {
    throw new ApiError("Không kết nối được. Kiểm tra mạng rồi thử lại.", 0, null);
  } finally {
    clearTimeout(timer);
  }

  const contentType = response.headers.get("content-type") ?? "";
  const body: ApiResponse<T> | null = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (response.status === 401) {
    await clearSession();
  }
  if (!response.ok || !body?.success) {
    const message = body?.message ?? `Lỗi HTTP ${response.status}`;
    throw new ApiError(message, response.status, body?.data);
  }
  return body.data;
}

export function json(method: string, payload: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}
