// SPDX-License-Identifier: Apache-2.0
import type { Diagnosis, DiseaseReport } from "@/types";
import type { chipTones } from "@/theme";

export interface ReportStatus {
  label: string;
  tone: keyof typeof chipTones;
}

export function reportStatus(diagnosis: Diagnosis | null, status?: DiseaseReport["status"]): ReportStatus {
  if (status === "NEEDS_RETAKE") return { label: "Cần chụp lại", tone: "danger" };
  switch (diagnosis?.approvalStatus) {
    case "PENDING":
      return { label: "Chờ kỹ sư duyệt", tone: "warning" };
    case "APPROVED":
      return { label: "Đã duyệt", tone: "success" };
    case "REJECTED":
      return { label: "Khuyến nghị bị từ chối", tone: "danger" };
    default:
      return { label: "Chờ chẩn đoán", tone: "neutral" };
  }
}
