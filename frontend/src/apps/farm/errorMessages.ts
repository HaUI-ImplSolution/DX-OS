// Vietnamese text for the error codes only Farm raises, registered with the shell at load.
export const FARM_ERROR_MESSAGES: Record<string, string> = {
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
  ERR_ACCOUNT_NOT_LINKED: "Tài khoản chưa được liên kết với nông hộ.",
  ERR_ACCOUNT_SERVICE_UNAVAILABLE: "Dịch vụ tài khoản không hoạt động, nông hộ chưa được tạo.",
};
