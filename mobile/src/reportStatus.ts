import type { Diagnosis } from "@/types";
import type { chipTones } from "@/theme";

export interface ReportStatus {
  label: string;
  tone: keyof typeof chipTones;
}

export function reportStatus(diagnosis: Diagnosis | null): ReportStatus {
  switch (diagnosis?.approvalStatus) {
    case "PENDING":
      return { label: "Chờ kỹ sư duyệt", tone: "warning" };
    case "APPROVED":
      return { label: "Đã duyệt", tone: "success" };
    case "REJECTED":
      return { label: "Cần chụp lại", tone: "danger" };
    default:
      return { label: "Chờ chẩn đoán", tone: "neutral" };
  }
}
