// Vietnamese text per backend errorCode. Codes without an entry show the backend message,
// which is already Vietnamese for them.
const ERROR_MESSAGES: Record<string, string> = {
  POKA_YOKE_VIETGAP_VIOLATION: "Vụ mùa đang trong thời gian cách ly thuốc, chưa được thu hoạch.",
  HARVEST_TOO_EARLY:
    "Chưa đến ngày thu hoạch dự kiến. Cần xác nhận thu hoạch sớm và nêu lý do.",
  TREATMENT_PENDING_CONFIRMATION:
    "Có báo cáo phun thuốc đang chờ kỹ sư xác nhận, chưa thu hoạch được.",
  INVALID_APPLIED_AT: "Ngày phun thuốc không hợp lệ (không được ở tương lai hoặc quá xa trong quá khứ).",
  CROP_SEASON_NOT_APPROVED: "Vụ mùa chưa được duyệt.",
  CROP_SEASON_HARVESTED: "Vụ mùa đã thu hoạch, không nhận thêm báo cáo.",
  PARCEL_LOCATION_MISSING: "Thửa chưa có vị trí GPS nên chưa xem được thời tiết.",
  ERR_AI_UNAVAILABLE: "Dịch vụ chẩn đoán AI tạm thời không hoạt động, hãy thử lại sau.",
  ERR_CONCURRENT_MODIFICATION: "Bản ghi vừa được người khác thay đổi, hãy tải lại rồi thử lại.",
  ERR_ACCOUNT_NOT_LINKED: "Tài khoản chưa được liên kết với nông hộ.",
  ERR_ACCOUNT_SERVICE_UNAVAILABLE: "Dịch vụ tài khoản không hoạt động, nông hộ chưa được tạo.",
  ERR_BAD_CREDENTIALS: "Sai tên đăng nhập hoặc mật khẩu.",
  ERR_UNAUTHENTICATED: "Phiên đăng nhập đã hết hạn, hãy đăng nhập lại.",
  ERR_FORBIDDEN: "Bạn không đủ quyền thực hiện thao tác này.",
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

export function errorMessageFor(errorCode: string | undefined, fallback: string): string {
  if (!errorCode) return fallback;
  if (ERROR_MESSAGES[errorCode]) return ERROR_MESSAGES[errorCode];
  const summary = DETAILED_ERROR_MESSAGES[errorCode];
  return summary ? `${summary} (${fallback})` : fallback;
}
