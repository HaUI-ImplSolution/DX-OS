// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from "react";
import { API_BASE, ApiError } from "../shell/api";
import type { EntityMeta, ModelMeta } from "../admin/types";
import { getToken } from "../shell/auth";
import { errorMessageFor, registerErrorMessages, statusMessageFor } from "../shell/errorMessages";
import { t } from "../shell/t";

registerErrorMessages({
  ERR_ORIGIN: t("Hãy mở trợ lý bằng đúng địa chỉ công khai của DX-OS. Liên hệ quản trị viên nếu đang dùng IP hoặc tên miền khác."),
  ERR_REQUEST_TOO_LARGE: t("Nội dung gửi quá lớn. Hãy rút gọn rồi thử lại."),
  ERR_INVALID_REQUEST: t("Dữ liệu gửi chưa hợp lệ. Hãy kiểm tra rồi thử lại."),
  ERR_UNAUTHORIZED: t("Phiên đăng nhập chưa hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại DX-OS."),
  ERR_CORE_LINK: t("Hãy liên kết lại tài khoản DX-OS cho trợ lý."),
  ERR_LINK_IDENTITY: t("Hãy liên kết bằng cùng tài khoản đang đăng nhập DX-OS."),
  ERR_LINK_EXPIRED: t("Phiên liên kết đã hết hạn hoặc đã dùng. Hãy bắt đầu liên kết lại."),
  ERR_LINK_INVALID: t("Hãy hoàn tất liên kết trong cùng trình duyệt đã bắt đầu."),
  ERR_IDENTITY_UNAVAILABLE: t("Chưa kết nối được dịch vụ đăng nhập. Hãy thử lại sau."),
  ERR_CLASSIFIER_UNAVAILABLE: t("Bộ phân loại của nhiệm vụ chưa sẵn sàng."),
  ERR_CLASSIFIER_LABELS: t("Các lựa chọn của trường đề xuất phải khớp đúng với nhãn của bộ phân loại."),
  ERR_CLASSIFIER_OUTPUT: t("Bộ phân loại chưa trả được đề xuất hợp lệ. Hồ sơ chưa được thay đổi."),
  ERR_PROPOSAL_VALUE: t("Giá trị đề xuất không nằm trong các lựa chọn của trường."),
  ERR_TASK_UNAVAILABLE: t("Nhiệm vụ không còn khả dụng hoặc bạn không có quyền sửa hồ sơ."),
  ERR_PROPOSAL_NO_CHANGE: t("Giá trị đề xuất đã trùng với hồ sơ. Không có thay đổi để lưu."),
  ERR_PROPOSAL_STALE: t("Hồ sơ, mô hình hoặc trọng số đã đổi. Hãy tạo đề xuất mới."),
  ERR_PROPOSAL_RETIRED: t("Đề xuất của phiên bản cũ chỉ dùng để xem lịch sử."),
  ERR_PROPOSAL_CLOSED: t("Đề xuất đã hết hạn hoặc đã được xử lý. Hãy tải lại."),
  ERR_EXECUTION_UNCERTAIN: t("Chưa rõ kết quả. Kiểm tra lịch sử hồ sơ trước khi thử lại."),
  ERR_CONDITIONAL_API_REQUIRED: t("Backend cần hỗ trợ điều kiện phiên bản trước khi trợ lý được ghi dữ liệu."),
  ERR_PROPOSAL_VERSION_REQUIRED: t("Đề xuất cũ thiếu phiên bản. Hãy tạo lại đề xuất trước khi xác nhận."),
});

let availability: Promise<boolean> | undefined;

export function assistantAvailable(): Promise<boolean> {
  availability ??= loadAvailability();
  return availability;
}

async function loadAvailability(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE}/assistant-api/v1/features`, { signal: AbortSignal.timeout(3000) });
    return response.ok && response.headers.get("content-type")?.includes("application/json") === true && (await response.json()).enabled === true;
  } catch { return false; }
}

// The assistant is asked about only when ai tasks are declared, and what it says is remembered for the page.
function useAvailability(declared: boolean): boolean {
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    if (!declared) return;
    let active = true;
    void assistantAvailable().then(value => { if (active) setEnabled(value); });
    return () => { active = false; };
  }, [declared]);
  return enabled;
}

const declares = (entity: EntityMeta) => Object.keys(entity.aiTasks ?? {}).length > 0;

export function useAssistantEnabled(models: ModelMeta[] | null): boolean {
  const declared = useMemo(() => !!models?.some(model => model.entities.some(declares)), [models]);
  return useAvailability(declared);
}

export type AssistantAccount = {
  linked: boolean;
  model: { configured: boolean; classifiers?: string[] };
};

export function useEntityAssistant(entity: EntityMeta) {
  return useAssistantStatus(declares(entity));
}

export function useAssistantModel(declared: boolean): AssistantAccount["model"] | undefined {
  return useAssistantStatus(declared)?.model;
}

function useAssistantStatus(declared: boolean) {
  const enabled = useAvailability(declared);
  const [status, setStatus] = useState<{ model?: AssistantAccount["model"]; failed?: true }>();
  useEffect(() => {
    if (!declared || !enabled) return;
    const controller = new AbortController();
    void assistantRequest<AssistantAccount>("/me", "GET", undefined, controller.signal)
      .then(account => { if (!controller.signal.aborted) setStatus({ model: account.model }); })
      .catch(() => { if (!controller.signal.aborted) setStatus({ failed: true }); });
    return () => controller.abort();
  }, [declared, enabled]);
  return declared && enabled ? status : undefined;
}

export async function assistantRequest<T>(path: string, method = "GET", payload?: unknown, signal?: AbortSignal): Promise<T> {
  const token = await getToken();
  const response = await fetch(`${API_BASE}/assistant-api/v1${path}`, {
    method, signal: AbortSignal.any([AbortSignal.timeout(60000), ...(signal ? [signal] : [])]),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(payload === undefined ? {} : { "Content-Type": "application/json" }) },
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
  });
  const body = response.headers.get("content-type")?.includes("application/json") ? await response.json() : null;
  if (!response.ok || !body?.success) throw new ApiError(errorMessageFor(body?.errorCode,
    body?.message ?? statusMessageFor(response.status)), response.status, body?.data);
  return body.data;
}

export type RecordTarget = { model: string; entity: string; id: number };
export type ClassifierReview = RecordTarget & {
  task: string;
  field: string;
  current: string | null;
  suggested: string | null;
  confidence?: number;
  evidence: string[];
  mismatch: boolean;
};

export const reviewRecords = (items: RecordTarget[], signal: AbortSignal) =>
  assistantRequest<{ items: ClassifierReview[]; skipped: number }>("/me/reviews", "POST", { items }, signal);

// expires and now are both server clock readings, so a browser clock that is off does not matter.
export type Proposal = { id: string; state: string; expires: number; now: number; receipt?: { version?: number } | null; snapshot: {
  version?: number;
  value?: string; previousValue?: unknown; field?: string;
  evidence?: string[];
  confidence?: number;
  summary: string; operation: string; note?: string; webOnly: boolean;
  effects?: { operation: string; userSource: string; group: string; asynchronous: boolean }[];
} };
