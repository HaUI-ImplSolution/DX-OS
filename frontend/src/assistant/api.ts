// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { API_BASE, ApiError } from "../shell/api";
import { getToken } from "../shell/auth";
import { errorMessageFor, registerErrorMessages, statusMessageFor } from "../shell/errorMessages";

registerErrorMessages({
  ERR_ORIGIN: "Hãy mở trợ lý bằng đúng địa chỉ công khai của DX-OS. Liên hệ quản trị viên nếu đang dùng IP hoặc tên miền khác.",
  ERR_REQUEST_TOO_LARGE: "Nội dung gửi quá lớn. Hãy rút gọn rồi thử lại.",
  ERR_INVALID_REQUEST: "Dữ liệu gửi chưa hợp lệ. Hãy kiểm tra rồi thử lại.",
  ERR_UNAUTHORIZED: "Phiên đăng nhập chưa hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại DX-OS.",
  ERR_CORE_LINK: "Hãy liên kết lại tài khoản DX-OS cho trợ lý.",
  ERR_LINK_IDENTITY: "Hãy liên kết bằng cùng tài khoản đang đăng nhập DX-OS.",
  ERR_LINK_EXPIRED: "Phiên liên kết đã hết hạn hoặc đã dùng. Hãy bắt đầu liên kết lại.",
  ERR_LINK_INVALID: "Hãy hoàn tất liên kết trong cùng trình duyệt đã bắt đầu.",
  ERR_IDENTITY_UNAVAILABLE: "Chưa kết nối được dịch vụ đăng nhập. Hãy thử lại sau.",
  ERR_INFERENCE_REQUIRED: "Bản cài chưa cấu hình model self-host. Liên hệ người vận hành.",
  ERR_INFERENCE_BUSY: "Model đang bận. Hãy thử lại sau.",
  ERR_INFERENCE_CONTEXT: "Hồ sơ và nguồn vượt giới hạn ngữ cảnh của model.",
  ERR_INFERENCE_REJECTED: "Model chưa sẵn sàng hoặc không nhận được yêu cầu.",
  ERR_INFERENCE_OUTPUT: "Model chưa trả được đề xuất hợp lệ. Hồ sơ chưa được thay đổi.",
  ERR_INFERENCE_CANCELLED: "Đã hủy lượt tạo đề xuất.",
  ERR_TASK_UNAVAILABLE: "Nhiệm vụ không còn khả dụng hoặc bạn không có quyền sửa hồ sơ.",
  ERR_SOURCE_UNAVAILABLE: "Nguồn tri thức của nhiệm vụ chưa được cấu hình đầy đủ.",
  ERR_PROPOSAL_NO_CHANGE: "Giá trị đề xuất đã trùng với hồ sơ. Không có thay đổi để lưu.",
  ERR_PROPOSAL_STALE: "Hồ sơ, mô hình hoặc nguồn đã đổi. Hãy tạo đề xuất mới.",
  ERR_PROPOSAL_RETIRED: "Đề xuất của phiên bản cũ chỉ dùng để xem lịch sử.",
  ERR_PROPOSAL_CLOSED: "Đề xuất đã hết hạn hoặc đã được xử lý. Hãy tải lại.",
  ERR_EXECUTION_UNCERTAIN: "Chưa rõ kết quả. Kiểm tra lịch sử hồ sơ trước khi thử lại.",
  ERR_CONDITIONAL_API_REQUIRED: "Backend cần hỗ trợ điều kiện phiên bản trước khi trợ lý được ghi dữ liệu.",
  ERR_PROPOSAL_VERSION_REQUIRED: "Đề xuất cũ thiếu phiên bản. Hãy tạo lại đề xuất trước khi xác nhận.",
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

export async function assistantRequest<T>(path: string, method = "GET", payload?: unknown, signal?: AbortSignal): Promise<T> {
  const token = await getToken();
  const response = await fetch(`${API_BASE}/assistant-api/v1${path}`, {
    method, signal: AbortSignal.any([AbortSignal.timeout(path === "/me/proposals" && method === "POST" ? 270000 : 60000), ...(signal ? [signal] : [])]),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(payload === undefined ? {} : { "Content-Type": "application/json" }) },
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
  });
  const body = response.headers.get("content-type")?.includes("application/json") ? await response.json() : null;
  if (!response.ok || !body?.success) throw new ApiError(errorMessageFor(body?.errorCode,
    body?.message ?? statusMessageFor(response.status)), response.status, body?.data);
  return body.data;
}

export type Proposal = { id: string; state: string; expires: number; receipt?: { version?: number } | null; snapshot: {
  version?: number;
  warnings?: { field: string; expected: string; actual: string | null }[];
  value?: string; previousValue?: unknown; field?: string;
  sources?: { id: string; title: string; text: string; revision: string; license: string }[];
  summary: string; operation: string; note?: string; webOnly: boolean;
  effects?: { operation: string; userSource: string; group: string; asynchronous: boolean }[];
} };
