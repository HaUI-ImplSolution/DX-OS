import { useRef, useState } from "react";
import { createDiseaseReport, staticUrl } from "../api";
import type { DiseaseReport } from "../types";
import Badge from "./Badge";
import Section from "./Section";

const MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024;

function validateImage(file: File | undefined): string | null {
  if (!file) return "Vui lòng chọn ảnh sâu bệnh";
  if (file.size === 0) return "Ảnh đã chọn không có dữ liệu";
  if (!file.type.startsWith("image/")) return "Chỉ chấp nhận tệp hình ảnh";
  if (file.size > MAX_IMAGE_SIZE_BYTES) return "Ảnh không được vượt quá 10 MB";
  return null;
}

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
  const [fileError, setFileError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const file = fileInput.current?.files?.[0];
    const validationError = validateImage(file);
    if (validationError || !file) {
      setFileError(validationError);
      return;
    }
    setFileError(null);
    setSubmitting(true);
    try {
      const report = await createDiseaseReport(cropSeasonId, file);
      onCreated(report);
      if (fileInput.current) fileInput.current.value = "";
      setFileError(null);
      setShowForm(false);
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không gửi được báo cáo");
    } finally {
      setSubmitting(false);
    }
  };

  const cancel = () => {
    if (fileInput.current) fileInput.current.value = "";
    setFileError(null);
    setShowForm(false);
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
          <input
            type="file"
            accept="image/*"
            ref={fileInput}
            required
            aria-invalid={fileError ? "true" : "false"}
            aria-describedby={fileError ? "disease-report-file-error" : undefined}
            onChange={(e) => setFileError(validateImage(e.currentTarget.files?.[0]))}
          />
          {fileError && (
            <p id="disease-report-file-error" className="form-error" role="alert">
              {fileError}
            </p>
          )}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Đang gửi…" : "Gửi báo cáo"}
            </button>
            <button type="button" className="btn" onClick={cancel}>
              Huỷ
            </button>
          </div>
        </form>
      )}
    </Section>
  );
}
