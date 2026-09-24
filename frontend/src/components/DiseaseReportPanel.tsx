import { useEffect, useRef, useState } from "react";
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

const MAX_IMAGE_BYTES = 10 * 1024 * 1024;
const ALLOWED_IMAGE_TYPES = new Set(["image/jpeg", "image/png"]);
const ALLOWED_IMAGE_EXTENSIONS = new Set(["jpg", "jpeg", "png"]);

function validateImage(file: File): string | null {
  const extension = file.name.split(".").pop()?.toLowerCase() ?? "";
  if (file.size === 0) return "Ảnh không được để trống.";
  if (file.size > MAX_IMAGE_BYTES) return "Ảnh không được vượt quá 10 MiB.";
  if (!ALLOWED_IMAGE_TYPES.has(file.type) || !ALLOWED_IMAGE_EXTENSIONS.has(extension)) {
    return "Chỉ chấp nhận ảnh JPEG hoặc PNG có phần mở rộng phù hợp.";
  }
  return null;
}

function formatFileSize(bytes: number) {
  return `${(bytes / (1024 * 1024)).toFixed(2)} MiB`;
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
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [fileError, setFileError] = useState<string | null>(null);
  const fileInput = useRef<HTMLInputElement>(null);

  useEffect(() => {
    return () => {
      if (previewUrl) URL.revokeObjectURL(previewUrl);
    };
  }, [previewUrl]);

  const clearFile = () => {
    setSelectedFile(null);
    setPreviewUrl(null);
    setFileError(null);
    if (fileInput.current) fileInput.current.value = "";
  };

  const closeForm = () => {
    clearFile();
    setShowForm(false);
  };

  const selectFile = (file: File | null) => {
    setFileError(null);
    setSelectedFile(null);
    setPreviewUrl(null);
    if (!file) return;
    const error = validateImage(file);
    if (error) {
      setFileError(error);
      if (fileInput.current) fileInput.current.value = "";
      fileInput.current?.focus();
      return;
    }
    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    const file = selectedFile;
    if (!file) {
      setFileError("Vui lòng chọn ảnh sâu bệnh.");
      fileInput.current?.focus();
      return;
    }
    const validationError = validateImage(file);
    if (validationError) {
      setFileError(validationError);
      fileInput.current?.focus();
      return;
    }
    setSubmitting(true);
    try {
      const report = await createDiseaseReport(cropSeasonId, file);
      onCreated(report);
      closeForm();
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
          <label className="field-label">
            Ảnh lá cây (JPEG/PNG, tối đa 10 MiB)
            <input
              type="file"
              accept="image/jpeg,image/png,.jpg,.jpeg,.png"
              ref={fileInput}
              onChange={(event) => selectFile(event.target.files?.[0] ?? null)}
              aria-describedby={fileError ? "report-image-error" : undefined}
              aria-invalid={Boolean(fileError)}
              required
            />
          </label>
          {fileError && (
            <p id="report-image-error" className="field-error" role="alert" aria-live="assertive">
              {fileError}
            </p>
          )}
          {selectedFile && previewUrl && (
            <div className="image-preview">
              <img src={previewUrl} alt="Xem trước ảnh sâu bệnh đã chọn" />
              <div>
                <strong>{selectedFile.name}</strong>
                <span>{formatFileSize(selectedFile.size)}</span>
                <button type="button" className="btn btn-link" onClick={clearFile} disabled={submitting}>
                  Xóa ảnh
                </button>
              </div>
            </div>
          )}
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting || !selectedFile}>
              {submitting ? "Đang gửi…" : "Gửi báo cáo"}
            </button>
            <button type="button" className="btn" onClick={closeForm} disabled={submitting}>
              Huỷ
            </button>
          </div>
        </form>
      )}
    </Section>
  );
}
