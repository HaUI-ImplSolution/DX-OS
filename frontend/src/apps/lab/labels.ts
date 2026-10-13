import type { LabelRegistration, Tone } from "../../shell";
import type {
  DeviceStatus,
  MaintenanceKind,
  RoomStatus,
  RoomType,
  TicketPriority,
} from "./types";

export const ROLE_LABEL: Record<string, string> = {
  STUDENT: "Sinh viên",
  LECTURER: "Giảng viên",
  TECHNICIAN: "Kỹ thuật viên",
  ADMIN: "Quản trị viên",
};

// Statuses of the ticket lifecycle and the actions the workflow records, registered with the shell.
export const LAB_LABELS: LabelRegistration = {
  statuses: {
    NEW: { label: "Mới", tone: "warning" },
    ASSIGNED: { label: "Đã phân công", tone: "neutral" },
    IN_PROGRESS: { label: "Đang xử lý", tone: "neutral" },
    WAITING_USER: { label: "Chờ người gửi", tone: "warning" },
    RESOLVED: { label: "Đã xử lý, chờ xác nhận", tone: "success" },
    CLOSED: { label: "Đã đóng", tone: "neutral" },
    RETURNED: { label: "Đã trả", tone: "neutral" },
  },
  actions: {
    ACCEPTED: "Nhận việc",
    STARTED: "Bắt đầu xử lý",
    WAITING_FOR_USER: "Chờ người gửi bổ sung",
    RESUMED: "Tiếp tục xử lý",
    RESOLVED: "Đã xử lý xong",
    CONFIRMED: "Người gửi xác nhận",
    REOPENED: "Mở lại",
    RETURNED: "Nhận lại thiết bị",
  },
};

export const PRIORITY_LABEL: Record<TicketPriority, string> = {
  URGENT: "Khẩn",
  HIGH: "Cao",
  NORMAL: "Thường",
  LOW: "Thấp",
};

export const PRIORITY_TONE: Record<TicketPriority, Tone> = {
  URGENT: "danger",
  HIGH: "warning",
  NORMAL: "neutral",
  LOW: "neutral",
};

export const PRIORITIES: TicketPriority[] = ["URGENT", "HIGH", "NORMAL", "LOW"];

export const TICKET_STATUSES = [
  "NEW",
  "ASSIGNED",
  "IN_PROGRESS",
  "WAITING_USER",
  "RESOLVED",
  "CLOSED",
  "REJECTED",
  "CANCELLED",
] as const;

// Loan and booking statuses share names with the approval flow, so they carry their own labels.
export const LOAN_STATUS_LABEL: Record<string, { label: string; tone: Tone }> = {
  PENDING: { label: "Chờ duyệt", tone: "warning" },
  APPROVED: { label: "Đang mượn", tone: "success" },
  RETURNED: { label: "Đã trả", tone: "neutral" },
  REJECTED: { label: "Bị từ chối", tone: "danger" },
  CANCELLED: { label: "Đã huỷ", tone: "neutral" },
};

export const BOOKING_STATUS_LABEL: Record<string, { label: string; tone: Tone }> = {
  PENDING: { label: "Chờ duyệt", tone: "warning" },
  APPROVED: { label: "Đã duyệt", tone: "success" },
  REJECTED: { label: "Bị từ chối", tone: "danger" },
  CANCELLED: { label: "Đã huỷ", tone: "neutral" },
};

export const DEVICE_STATUS_LABEL: Record<DeviceStatus, { label: string; tone: Tone }> = {
  AVAILABLE: { label: "Sẵn sàng", tone: "success" },
  ON_LOAN: { label: "Đang mượn", tone: "warning" },
  UNDER_REPAIR: { label: "Đang sửa", tone: "danger" },
  RETIRED: { label: "Đã thanh lý", tone: "neutral" },
};

export const ROOM_STATUS_LABEL: Record<RoomStatus, { label: string; tone: Tone }> = {
  AVAILABLE: { label: "Hoạt động", tone: "success" },
  MAINTENANCE: { label: "Đang bảo trì", tone: "danger" },
};

export const ROOM_TYPE_LABEL: Record<RoomType, string> = {
  COMPUTER_LAB: "Phòng máy",
  CLASSROOM: "Phòng học",
  MEETING_ROOM: "Phòng họp",
};

export const MAINTENANCE_KIND_LABEL: Record<MaintenanceKind, string> = {
  REPAIR: "Sửa chữa",
  INSPECTION: "Kiểm tra",
  REPLACEMENT: "Thay thế",
};

export function formatMinutes(minutes: number | null): string {
  if (minutes === null) return "—";
  if (minutes < 60) return `${Math.round(minutes)} phút`;
  const hours = minutes / 60;
  if (hours < 48) return `${hours.toFixed(1).replace(/\.0$/, "")} giờ`;
  return `${(hours / 24).toFixed(1).replace(/\.0$/, "")} ngày`;
}

export function formatPercent(ratio: number | null): string {
  return ratio === null ? "—" : `${Math.round(ratio * 100)}%`;
}

export function formatBytes(bytes: number): string {
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(0)} KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
}
