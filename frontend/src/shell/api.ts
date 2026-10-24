import { clearSession, getToken } from "./auth";
import { errorMessageFor, statusMessageFor } from "./errorMessages";
import type {
  ApiResponse,
  AppManifest,
  ApprovalRequest,
  ApprovalRequestDetail,
  ApprovalRequestFilter,
  KnowledgeAnswer,
  Notification,
  Page,
  Workspace,
  WorkspaceCreateRequest,
  WorkspaceCreateResult,
  WorkspaceMember,
} from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:9080").replace(/\/$/, "");
const API_V1 = `${API_BASE}/api/v1`;

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
    const message = errorMessageFor(body?.errorCode, body?.message ?? statusMessageFor(response.status));
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

export function queryString(params: Record<string, string | number | boolean | undefined | null>) {
  const query = new URLSearchParams();
  Object.entries(params).forEach(([key, value]) => {
    if (value !== undefined && value !== null && value !== "") query.set(key, String(value));
  });
  const text = query.toString();
  return text ? `?${text}` : "";
}

export const getAppManifest = () => request<AppManifest>("/app");

// Requests waiting for a decision
export const listApprovalRequests = <T extends ApprovalRequest = ApprovalRequest>(
  filter: ApprovalRequestFilter,
) => request<Page<T>>(`/approval-requests${queryString(filter)}`);
export const getApprovalRequest = <T extends ApprovalRequest = ApprovalRequest>(caseId: number) =>
  request<ApprovalRequestDetail<T>>(`/approval-requests/${caseId}`);
export const assignCase = (caseId: number) =>
  request<unknown>(`/workflow-cases/${caseId}/assign`, { method: "PATCH" });
export const approveCase = (caseId: number) =>
  request<unknown>(`/workflow-cases/${caseId}/approve`, { method: "PATCH" });
export const rejectCase = (caseId: number, reason: string) =>
  request<unknown>(`/workflow-cases/${caseId}/reject`, json("PATCH", { reason }));

// Knowledge: the namespace is the app that is running; extra fields narrow the question.
export const askKnowledge = (question: string, extra: Record<string, unknown> = {}) =>
  request<KnowledgeAnswer>("/knowledge/ask?includeAnswer=true", json("POST", { question, ...extra }));

// Notifications of the signed-in account
export const listNotifications = (unreadOnly: boolean, page: number, size: number) =>
  request<Page<Notification>>(`/notifications${queryString({ unreadOnly, page, size })}`);
export const getUnreadCount = () =>
  request<{ unread: number }>("/notifications/unread-count").then((r) => r.unread);
export const markNotificationRead = (id: number) =>
  request<Notification>(`/notifications/${id}/read`, { method: "PATCH" });
export const markAllNotificationsRead = () =>
  request<{ updated: number }>("/notifications/read-all", { method: "PATCH" });

// Workspaces (households, classes, departments)
export const listWorkspaces = (type?: string) =>
  request<Workspace[]>(`/workspaces${queryString({ type })}`);
export const createWorkspace = (payload: WorkspaceCreateRequest) =>
  request<WorkspaceCreateResult>("/workspaces", json("POST", payload));
export const listWorkspaceMembers = (code: string) =>
  request<WorkspaceMember[]>(`/workspaces/${encodeURIComponent(code)}/members`);
export const addWorkspaceMember = (code: string, username: string) =>
  request<WorkspaceMember>(
    `/workspaces/${encodeURIComponent(code)}/members`,
    json("POST", { username }),
  );
