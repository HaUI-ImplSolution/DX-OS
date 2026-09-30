import { API_BASE_URL } from "@/config";
import { clearSession } from "@/auth/session";
import { getValidAccessToken } from "@/auth/tokens";
import type { ApiResponse } from "@/types";

const API_V1 = `${API_BASE_URL}/api/v1`;
const REQUEST_TIMEOUT_MS = 30_000;
const UPLOAD_TIMEOUT_MS = 60_000;

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
  const token = await getValidAccessToken();
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
  return unwrap(response.status, body);
}

async function unwrap<T>(status: number, body: ApiResponse<T> | null): Promise<T> {
  if (status === 401) {
    await clearSession();
  }
  if (status < 200 || status >= 300 || !body?.success) {
    const message = body?.message ?? `Lỗi HTTP ${status}`;
    throw new ApiError(message, status, body?.data);
  }
  return body.data;
}

export interface UploadFile {
  uri: string;
  name: string;
  type: string;
}

// fetch has no upload progress in React Native, so multipart uploads go through XMLHttpRequest.
export async function upload<T>(
  path: string,
  field: string,
  file: UploadFile,
  onProgress?: (fraction: number) => void,
  headers: Record<string, string> = {},
): Promise<T> {
  const token = await getValidAccessToken();
  const form = new FormData();
  form.append(field, file as unknown as Blob);

  const { status, body } = await new Promise<{ status: number; body: ApiResponse<T> | null }>(
    (resolve, reject) => {
      const xhr = new XMLHttpRequest();
      xhr.open("POST", `${API_V1}${path}`);
      if (token) xhr.setRequestHeader("Authorization", `Bearer ${token}`);
      Object.entries(headers).forEach(([name, value]) => xhr.setRequestHeader(name, value));
      xhr.timeout = UPLOAD_TIMEOUT_MS;
      xhr.upload.onprogress = (event) => {
        if (event.lengthComputable) onProgress?.(event.loaded / event.total);
      };
      xhr.onload = () => {
        let parsed: ApiResponse<T> | null = null;
        try {
          parsed = JSON.parse(xhr.responseText);
        } catch {
          // Non-JSON response: reported through the status code.
        }
        resolve({ status: xhr.status, body: parsed });
      };
      const fail = () => reject(new ApiError("Không kết nối được. Kiểm tra mạng rồi thử lại.", 0, null));
      xhr.onerror = fail;
      xhr.ontimeout = fail;
      xhr.send(form);
    },
  );
  return unwrap(status, body);
}

export function json(method: string, payload: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}
