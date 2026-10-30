// SPDX-License-Identifier: Apache-2.0
import type { CaseStatus, Tone } from "./types";

interface StatusDefinition {
  label: string;
  tone: Tone;
}

// Statuses of the standard approval flow; an app adds the ones its case types declare.
const statuses: Record<string, StatusDefinition> = {
  PENDING: { label: "Chờ duyệt", tone: "warning" },
  APPROVED: { label: "Đã duyệt", tone: "success" },
  REJECTED: { label: "Bị từ chối", tone: "danger" },
  CANCELLED: { label: "Đã huỷ", tone: "neutral" },
};

const actions: Record<string, string> = {
  SUBMITTED: "Gửi yêu cầu",
  APPROVED: "Duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Huỷ",
  RESUBMITTED: "Gửi lại",
  ASSIGNED: "Nhận việc",
};

export interface LabelRegistration {
  statuses?: Record<string, StatusDefinition>;
  actions?: Record<string, string>;
}

export function registerLabels(registration: LabelRegistration) {
  Object.assign(statuses, registration.statuses);
  Object.assign(actions, registration.actions);
}

// A status the app does not know is shown as it is, in a neutral tone.
export function statusLabel(status: CaseStatus): string {
  return statuses[status]?.label ?? status;
}

export function statusTone(status: CaseStatus): Tone {
  return statuses[status]?.tone ?? "neutral";
}

export function actionLabel(action: string): string {
  return actions[action] ?? action;
}

export function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString("vi-VN");
}

export function formatDate(iso: string): string {
  return new Date(iso).toLocaleDateString("vi-VN");
}

// A disease passage drops the code and name lines that repeat the title, and the backend-facing
// sentence of its legal note.
export function passageText(content: string): string {
  return content
    .replace(/^(Mã bệnh|Tên tiếng Việt):.*\n?/gm, "")
    .replace(/ Tên thuốc, liều và PHI phải do Backend.*$/gm, "");
}
