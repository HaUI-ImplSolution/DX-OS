// SPDX-License-Identifier: Apache-2.0
import { API_BASE, ApiError } from "../shell/api";
import { getToken } from "../shell/auth";
import { errorMessageFor, registerErrorMessages, statusMessageFor } from "../shell/errorMessages";

registerErrorMessages({
  ERR_UNAUTHORIZED: "Phiên đăng nhập chưa hợp lệ hoặc đã hết hạn. Hãy đăng nhập lại DX-OS.",
  ERR_CORE_LINK: "Hãy liên kết lại tài khoản DX-OS cho trợ lý.",
  ERR_LINK_IDENTITY: "Hãy liên kết bằng cùng tài khoản đang đăng nhập DX-OS.",
  ERR_LINK_EXPIRED: "Phiên liên kết đã hết hạn hoặc đã dùng. Hãy bắt đầu liên kết lại.",
  ERR_LINK_INVALID: "Hãy hoàn tất liên kết trong cùng trình duyệt đã bắt đầu.",
  ERR_IDENTITY_UNAVAILABLE: "Chưa kết nối được dịch vụ đăng nhập. Hãy thử lại sau.",
  ERR_PROVIDER_REQUIRED: "Hãy chọn một profile API trước khi bật trợ lý.",
  ERR_PROVIDER_ORIGIN: "Endpoint phải là HTTPS và được quản trị viên cho phép.",
  ERR_PROVIDER_LIMIT: "Mỗi tài khoản lưu tối đa 5 profile API.",
  ERR_PROVIDER_PROBE: "Kiểm kết nối chưa thành công. Hãy kiểm tra key, model, quota và kết nối.",
  ERR_TOOL_CAPABILITY: "Model có phản hồi nhưng chưa thực hiện được công cụ kiểm tra.",
  ERR_RUNTIME_REQUIRED: "Quản trị viên cần chuẩn bị runtime riêng trước khi bật trợ lý.",
  ERR_RUNTIME_CAPACITY: "Các runtime riêng đã được cấp hết. Hãy liên hệ quản trị viên.",
  ERR_RUNTIME_UNAVAILABLE: "Chưa kết nối được runtime riêng. Kiểm tra trạng thái trợ lý.",
  ERR_RUNTIME_BUSY: "Runtime đang xử lý một thao tác khác. Hãy chờ rồi thử lại.",
  ERR_TELEGRAM_REQUIRED: "Hãy cấu hình bot Telegram trước.",
  ERR_TELEGRAM_TOKEN: "Hãy nhập token bot do @BotFather cấp.",
  ERR_TELEGRAM_WEBHOOK: "Bot đang dùng webhook ở nơi khác. Hãy dùng bot riêng.",
  ERR_TELEGRAM_UNAVAILABLE: "Chưa xác minh được bot. Kiểm tra token và kết nối Telegram.",
  ERR_PAIRING_CODE: "Mã liên kết Telegram chưa hợp lệ hoặc đã hết hạn.",
  ERR_ALREADY_PAIRED: "Bot đã được liên kết. Cấu hình lại bot nếu muốn đổi người dùng.",
  ERR_BOT_IN_USE: "Bot này đã được tài khoản khác liên kết.",
  ERR_PROPOSAL_CLOSED: "Đề xuất đã hết hạn hoặc đã được xử lý. Hãy tải lại.",
  ERR_EXECUTION_UNCERTAIN: "Chưa rõ kết quả. Kiểm tra lịch sử hồ sơ trước khi thử lại.",
  ERR_CONDITIONAL_API_REQUIRED: "Backend cần hỗ trợ điều kiện phiên bản trước khi trợ lý được ghi dữ liệu.",
});

export async function assistantAvailable(): Promise<boolean> {
  try {
    const response = await fetch(`${API_BASE}/assistant-api/v1/features`, { signal: AbortSignal.timeout(3000) });
    return response.ok && response.headers.get("content-type")?.includes("application/json") === true && (await response.json()).enabled === true;
  } catch { return false; }
}

export async function assistantRequest<T>(path: string, method = "GET", payload?: unknown): Promise<T> {
  const token = await getToken();
  const response = await fetch(`${API_BASE}/assistant-api/v1${path}`, {
    method, signal: AbortSignal.timeout(path.endsWith("/probe") ? 110000 : 60000),
    headers: { ...(token ? { Authorization: `Bearer ${token}` } : {}), ...(payload === undefined ? {} : { "Content-Type": "application/json" }) },
    ...(payload === undefined ? {} : { body: JSON.stringify(payload) }),
  });
  const body = response.headers.get("content-type")?.includes("application/json") ? await response.json() : null;
  if (!response.ok || !body?.success) throw new ApiError(errorMessageFor(body?.errorCode,
    body?.message ?? statusMessageFor(response.status)), response.status, body?.data);
  return body.data;
}

export type ProviderProfile = { id: string; provider: "gemini" | "openai" | "anthropic" | "compatible";
  model: string; baseUrl?: string; configured: boolean };
export type Profiles = { profiles: ProviderProfile[]; selected: string | null };
export type Notifications = { enabled: boolean; timeZone: string; digestHour: number;
  lastScan: string | null; error: string | null; pending: number; uncertain: number };
export type Proposal = { id: string; state: string; expires: number; snapshot: {
  summary: string; operation: string; note?: string; webOnly: boolean;
  effects?: { operation: string; userSource: string; group: string; asynchronous: boolean }[];
} };
