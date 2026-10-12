import type { CaseStatus, CaseType, KnownCaseStatus } from "./types";

export const CASE_TYPE_LABEL: Record<CaseType, string> = {
  PARCEL_APPROVAL: "Thửa ruộng mới",
  PARCEL_EDIT_REQUEST: "Chỉnh sửa thửa",
  CROP_SEASON_APPROVAL: "Vụ mùa mới",
  DIAGNOSIS_APPROVAL: "Chẩn đoán",
  TREATMENT_CONFIRMATION: "Khai phun thuốc",
};

export const STATUS_LABEL: Record<KnownCaseStatus, string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Bị từ chối",
  CANCELLED: "Đã huỷ",
};

export const STATUS_TONE: Record<KnownCaseStatus, "neutral" | "success" | "warning" | "danger"> = {
  PENDING: "warning",
  APPROVED: "success",
  REJECTED: "danger",
  CANCELLED: "neutral",
};

// A status the app does not know is shown as it is, in a neutral tone.
export function statusLabel(status: CaseStatus): string {
  return (STATUS_LABEL as Record<string, string>)[status] ?? status;
}

export function statusTone(status: CaseStatus): "neutral" | "success" | "warning" | "danger" {
  return (STATUS_TONE as Record<string, "neutral" | "success" | "warning" | "danger">)[status] ?? "neutral";
}

export const ACTION_LABEL: Record<string, string> = {
  SUBMITTED: "Gửi yêu cầu",
  APPROVED: "Duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Huỷ",
  RESUBMITTED: "Gửi lại",
  ASSIGNED: "Nhận việc",
};

export const DETAIL_LABEL: Record<string, string> = {
  parcelCode: "Mã thửa",
  areaHectares: "Diện tích (ha)",
  location: "Vị trí",
  latitude: "Vĩ độ",
  longitude: "Kinh độ",
  gpsAccuracyMeters: "Sai số GPS (m)",
  riceVariety: "Giống lúa",
  seasonType: "Loại vụ",
  startDate: "Ngày bắt đầu",
  expectedHarvestDate: "Dự kiến thu hoạch",
  reasonForOverride: "Lý do chọn ngày",
  reason: "Lý do",
  rejectionReason: "Lý do từ chối",
  reportId: "Mã báo cáo",
  diseaseCode: "Mã bệnh",
  diseaseName: "Bệnh",
  confidence: "Độ tin cậy",
  hasTreatmentRule: "Có phác đồ",
  recommendation: "Khuyến nghị",
  seasonId: "Mã vụ (nội bộ)",
  pesticide: "Thuốc",
  doseAmount: "Liều đã phun",
  doseUnit: "Đơn vị liều",
  appliedAt: "Ngày phun",
  harvestLockedUntil: "Hết cách ly",
};

export const SEASON_TYPE_LABEL: Record<string, string> = {
  DONG_XUAN: "Đông Xuân",
  HE_THU: "Hè Thu",
  THU_DONG: "Thu Đông",
};

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN");
}

export function formatValue(key: string, value: string | number | boolean | null): string {
  if (value === null || value === "") return "—";
  if (typeof value === "boolean") return value ? "Có" : "Không";
  if (key === "seasonType") return SEASON_TYPE_LABEL[String(value)] ?? String(value);
  if (key === "confidence") return `${Math.round(Number(value) * 100)}%`;
  return String(value);
}
