// SPDX-License-Identifier: Apache-2.0
// Vietnamese text per backend errorCode. Codes without an entry show the backend message.
const ERROR_MESSAGES: Record<string, string> = {
  ERR_AI_UNAVAILABLE: "Dịch vụ AI tạm thời không hoạt động, hãy thử lại sau.",
  ERR_CONCURRENT_MODIFICATION: "Bản ghi vừa được người khác thay đổi, hãy tải lại rồi thử lại.",
  ERR_BAD_CREDENTIALS: "Sai tên đăng nhập hoặc mật khẩu.",
  ERR_UNAUTHENTICATED: "Phiên đăng nhập đã hết hạn, hãy đăng nhập lại.",
  ERR_FORBIDDEN: "Bạn không đủ quyền thực hiện thao tác này.",
  ERR_TOO_MANY_REQUESTS: "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút.",
  ERR_INTERNAL: "Hệ thống gặp lỗi, vui lòng thử lại sau.",
};

// Generic codes keep the backend detail after the Vietnamese summary.
const DETAILED_ERROR_MESSAGES: Record<string, string> = {
  ERR_VALIDATION: "Dữ liệu gửi lên chưa hợp lệ.",
  ERR_NOT_FOUND: "Không tìm thấy dữ liệu yêu cầu.",
  ERR_INVALID_STATE: "Thao tác không phù hợp với trạng thái hiện tại của bản ghi.",
  ERR_AI_TRIAGE: "Hệ thống AI chưa đưa ra được kết luận cho ảnh này.",
  ERR_BAD_REQUEST: "Yêu cầu không hợp lệ.",
};

export function registerErrorMessages(messages: Record<string, string>) {
  Object.assign(ERROR_MESSAGES, messages);
}

// Replies that carry no backend message (the gateway answers 429 in plain text) get a Vietnamese text per status.
export function statusMessageFor(status: number): string {
  return status === 429 ? "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút." : `Lỗi HTTP ${status}`;
}

export function errorMessageFor(errorCode: string | undefined, fallback: string): string {
  if (!errorCode) return fallback;
  if (ERROR_MESSAGES[errorCode]) return ERROR_MESSAGES[errorCode];
  const summary = DETAILED_ERROR_MESSAGES[errorCode];
  return summary ? `${summary} (${fallback})` : fallback;
}
