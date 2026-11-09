// SPDX-License-Identifier: Apache-2.0
import { translateBackend } from "./backendMessages";

// Vietnamese text per backend errorCode. Codes without an entry show the backend message.
const ERROR_MESSAGES: Record<string, string> = {
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
  ERR_BAD_REQUEST: "Yêu cầu không hợp lệ.",
};

// Vietnamese text per data.reason, the stable code of a refused action (403 and 409), so the text does
// not depend on how the backend words its message.
const REASON_MESSAGES: Record<string, string> = {
  KEPT_BY_PROCESS: "Hồ sơ có quy trình được giữ lại; người nộp rút lại thay vì xóa",
  HANDLER_ONLY: "Chỉ vai xử lý của quy trình mới được thực hiện thao tác này",
  OWNER_ONLY: "Chỉ người nộp hồ sơ mới được thực hiện thao tác này",
  FILE_ADDER_ONLY: "Chỉ người đã thêm tệp mới gỡ được tệp",
  STILL_REFERENCED: "Vẫn còn hồ sơ khác tham chiếu tới hồ sơ này",
  PARENT_OUT_OF_REACH: "Hồ sơ cha nằm ngoài phạm vi của bạn",
  SELF_DECISION: "Người tạo hồ sơ không được tự xử lý hồ sơ của mình",
  WRONG_STATE: "Hồ sơ không thực hiện được thao tác này ở trạng thái hiện tại",
  CONDITION_NOT_MET: "Hồ sơ chưa đạt điều kiện của hành động này",
  ASSIGNED_TO_OTHER: "Hồ sơ đang được giao cho người khác",
  RECORD_CLOSED: "Hồ sơ đã đóng, không thay đổi được",
  SLOT_TAKEN: "Tài nguyên đã có người đặt trong khung giờ này",
  STATUS_BY_PROCESS_ONLY: "Trạng thái chỉ đổi qua quy trình",
  UNIT_OUT_OF_REACH: "Đơn vị nằm ngoài phạm vi của bạn",
  NOT_ASSIGNER: "Vai của bạn không được giao hồ sơ này",
  CANNOT_CREATE: "Vai của bạn không được tạo hồ sơ này",
  NO_ROLE_FOR_ENTITY: "Vai của bạn không truy cập được loại hồ sơ này",
  READ_ONLY: "Vai của bạn chỉ được xem loại hồ sơ này",
};

// A reason whose message names a role, an action, a state or a condition; the translated message says
// more than the reason, so it is used when the translator knows it.
const REASONS_WITH_DETAIL = new Set(["HANDLER_ONLY", "OWNER_ONLY", "PARENT_OUT_OF_REACH", "WRONG_STATE", "CONDITION_NOT_MET"]);

export function registerErrorMessages(messages: Record<string, string>) {
  Object.assign(ERROR_MESSAGES, messages);
}

// Replies that carry no backend message (the gateway answers 429 in plain text) get a Vietnamese text per status.
export function statusMessageFor(status: number): string {
  return status === 429 ? "Bạn thao tác quá nhanh, vui lòng thử lại sau ít phút." : `Lỗi HTTP ${status}`;
}

export function errorMessageFor(errorCode: string | undefined, backendMessage: string, reason?: string): string {
  const translated = translateBackend(backendMessage);
  const byReason = reason ? REASON_MESSAGES[reason] : undefined;
  if (byReason && !(REASONS_WITH_DETAIL.has(reason ?? "") && translated !== backendMessage)) return byReason;
  if (!errorCode) return translated;
  if (ERROR_MESSAGES[errorCode]) return ERROR_MESSAGES[errorCode];
  // A message the translator knows already says what went wrong; any other keeps the summary of its kind.
  if (translated !== backendMessage) return translated;
  const summary = DETAILED_ERROR_MESSAGES[errorCode];
  return summary ? `${summary} (${backendMessage})` : backendMessage;
}
