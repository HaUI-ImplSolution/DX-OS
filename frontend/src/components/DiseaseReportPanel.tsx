import type { DiseaseReport } from "../types";
import Badge from "./Badge";
import Section from "./Section";

interface DiseaseReportPanelProps {
  reports: DiseaseReport[];
  selectedId: number | null;
  onSelect: (id: number) => void;
}

function diagnosisBadge(report: DiseaseReport) {
  if (report.status === "NEEDS_RETAKE") return <Badge tone="danger">Cần chụp lại</Badge>;
  if (!report.diagnosis) return <Badge tone="neutral">Chưa chẩn đoán</Badge>;
  const status = report.diagnosis.approvalStatus;
  const tone = status === "APPROVED" ? "success" : status === "REJECTED" ? "danger" : "warning";
  return <Badge tone={tone}>{status}</Badge>;
}

export default function DiseaseReportPanel({
  reports,
  selectedId,
  onSelect,
}: DiseaseReportPanelProps) {
  return (
    <Section step="3" title="Báo cáo sâu bệnh" subtitle="Nông dân gửi báo cáo bằng ảnh từ ứng dụng di động">
      <div className="report-grid">
        {reports.map((r) => (
          <button
            key={r.id}
            className={`report-card${r.id === selectedId ? " report-card-active" : ""}`}
            onClick={() => onSelect(r.id)}
          >
            <img src={r.imageUrl} alt="Ảnh báo cáo sâu bệnh" />
            <div className="report-card-meta">
              <span>{r.reportedBy}</span>
              {diagnosisBadge(r)}
            </div>
          </button>
        ))}
        {reports.length === 0 && <p className="empty-hint">Vụ mùa này chưa có báo cáo nào.</p>}
      </div>
    </Section>
  );
}
