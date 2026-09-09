import { useRef, useState } from "react";
import { createDiseaseReport, staticUrl } from "../api";
import type { DiseaseReport } from "../types";
import Badge from "./Badge";
import Section from "./Section";

interface DiseaseReportPanelProps {
  cropSeasonId: number;
  reports: DiseaseReport[];
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreated: (report: DiseaseReport) => void;
  onError: (message: string) => void;
}

function diagnosisBadge(report: DiseaseReport) {
  if (!report.diagnosis) return <Badge tone="neutral">Chưa chẩn đoán</Badge>;
  const status = report.diagnosis.approvalStatus;
  const tone = status === "APPROVED" ? "success" : status === "REJECTED" ? "danger" : "warning";
  return <Badge tone={tone}>{status}</Badge>;
}

export default function DiseaseReportPanel({
  cropSeasonId,
  reports,
  selectedId,
  onSelect,
  onCreated,
  onError,
}: DiseaseReportPanelProps) {
  const [showForm, setShowForm] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const fileInput = useRef<HTMLInputElement>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const file = fileInput.current?.files?.[0];
    if (!file) {
      onError("Vui lòng chọn ảnh sâu bệnh");
      return;
    }
    setSubmitting(true);
    try {
      const report = await createDiseaseReport(cropSeasonId, file);
      onCreated(report);
      if (fileInput.current) fileInput.current.value = "";
      setShowForm(false);
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không gửi được báo cáo");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Section step="3" title="Báo cáo sâu bệnh" subtitle="Nông dân chụp ảnh gửi báo cáo (M1)">
      <div className="report-grid">
        {reports.map((r) => (
          <button
            key={r.id}
            className={`report-card${r.id === selectedId ? " report-card-active" : ""}`}
            onClick={() => onSelect(r.id)}
          >
            <img src={staticUrl(`uploads/${r.imageRef}`)} alt="Ảnh báo cáo sâu bệnh" />
            <div className="report-card-meta">
              <span>{r.reportedBy}</span>
              {diagnosisBadge(r)}
            </div>
          </button>
        ))}
        {reports.length === 0 && <p className="empty-hint">Vụ mùa này chưa có báo cáo nào.</p>}
      </div>

      {!showForm && (
        <button className="btn btn-link" onClick={() => setShowForm(true)}>
          + Gửi báo cáo mới
        </button>
      )}

      {showForm && (
        <form className="inline-form" onSubmit={submit}>
          <input type="file" accept="image/*" ref={fileInput} required />
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Đang gửi…" : "Gửi báo cáo"}
            </button>
            <button type="button" className="btn" onClick={() => setShowForm(false)}>
              Huỷ
            </button>
          </div>
        </form>
      )}
    </Section>
  );
}
