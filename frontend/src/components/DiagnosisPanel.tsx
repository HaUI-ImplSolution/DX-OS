import { useState } from "react";
import {
  applyDiagnosis,
  approveDiagnosis,
  createAiDiagnosis,
  createDiagnosis,
  rejectDiagnosis,
} from "../api";
import type { Diagnosis, Disease, DiseaseReport, Role, TreatmentApplication } from "../types";
import Badge from "./Badge";
import Section from "./Section";

interface DiagnosisPanelProps {
  report: DiseaseReport;
  diseases: Disease[];
  currentRole: Role;
  onUpdated: (diagnosis: Diagnosis) => void;
  onError: (message: string) => void;
}

function CreateDiagnosisForm({
  reportId,
  diseases,
  onUpdated,
  onError,
}: {
  reportId: number;
  diseases: Disease[];
  onUpdated: (diagnosis: Diagnosis) => void;
  onError: (message: string) => void;
}) {
  const [diseaseCode, setDiseaseCode] = useState(diseases[0]?.code ?? "");
  const [confidence, setConfidence] = useState("0.92");
  const [modelVersion, setModelVersion] = useState("manual-input-v1");
  const [submitting, setSubmitting] = useState(false);
  const [aiSubmitting, setAiSubmitting] = useState(false);

  const runAi = async () => {
    setAiSubmitting(true);
    try {
      onUpdated(await createAiDiagnosis(reportId));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không chạy được chẩn đoán AI");
    } finally {
      setAiSubmitting(false);
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const diagnosis = await createDiagnosis(reportId, {
        diseaseCode,
        confidence: Number(confidence),
        modelVersion,
      });
      onUpdated(diagnosis);
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không ghi nhận được chẩn đoán");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <form className="inline-form" onSubmit={submit}>
      <p className="empty-hint">
        Chạy luồng thật CV → Backend facts → RAG → khuyến nghị. Lần đầu có thể chậm do tải model.
      </p>
      <div className="form-actions">
        <button
          type="button"
          className="btn btn-primary"
          onClick={runAi}
          disabled={aiSubmitting || submitting}
        >
          {aiSubmitting ? "AI đang chẩn đoán…" : "Chạy chẩn đoán AI"}
        </button>
      </div>
      <p className="empty-hint">Hoặc nhập tay kết quả CV để kiểm thử nghiệp vụ:
      </p>
      <label className="field-label">
        Bệnh chẩn đoán
        <select value={diseaseCode} onChange={(e) => setDiseaseCode(e.target.value)} required>
          {diseases.map((d) => (
            <option key={d.code} value={d.code}>
              {d.name} ({d.code})
            </option>
          ))}
        </select>
      </label>
      <label className="field-label">
        Độ tin cậy (0–1)
        <input
          type="number"
          min="0"
          max="1"
          step="0.01"
          value={confidence}
          onChange={(e) => setConfidence(e.target.value)}
          required
        />
      </label>
      <input
        placeholder="Phiên bản model"
        value={modelVersion}
        onChange={(e) => setModelVersion(e.target.value)}
        required
      />
      <div className="form-actions">
        <button
          type="submit"
          className="btn btn-secondary"
          disabled={submitting || aiSubmitting || diseases.length === 0}
        >
          {submitting ? "Đang lưu…" : "Ghi nhận chẩn đoán"}
        </button>
      </div>
    </form>
  );
}

function ApprovalActions({
  reportId,
  onUpdated,
  onError,
}: {
  reportId: number;
  onUpdated: (diagnosis: Diagnosis) => void;
  onError: (message: string) => void;
}) {
  const [reason, setReason] = useState("");
  const [showReject, setShowReject] = useState(false);
  const [busy, setBusy] = useState(false);

  const approve = async () => {
    setBusy(true);
    try {
      onUpdated(await approveDiagnosis(reportId));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không duyệt được");
    } finally {
      setBusy(false);
    }
  };

  const reject = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      onUpdated(await rejectDiagnosis(reportId, { reason }));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không từ chối được");
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="approval-box">
      {!showReject && (
        <div className="form-actions">
          <button className="btn btn-primary" onClick={approve} disabled={busy}>
            ✓ Duyệt khuyến nghị
          </button>
          <button className="btn btn-danger-outline" onClick={() => setShowReject(true)} disabled={busy}>
            ✗ Từ chối
          </button>
        </div>
      )}
      {showReject && (
        <form className="inline-form" onSubmit={reject}>
          <input
            placeholder="Lý do từ chối"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
          />
          <div className="form-actions">
            <button type="submit" className="btn btn-danger-outline" disabled={busy}>
              Xác nhận từ chối
            </button>
            <button type="button" className="btn" onClick={() => setShowReject(false)}>
              Huỷ
            </button>
          </div>
        </form>
      )}
    </div>
  );
}

function ApplyTreatmentBox({ reportId, onError }: { reportId: number; onError: (message: string) => void }) {
  const [appliedAt, setAppliedAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<TreatmentApplication | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      setResult(await applyDiagnosis(reportId, { appliedAt }));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không ghi nhận được đã phun thuốc");
    } finally {
      setBusy(false);
    }
  };

  if (result) {
    return (
      <div className="banner banner-success">
        <strong>Đã ghi nhận phun thuốc (M6)</strong>
        <p>
          {result.pesticideCode} — {result.doseAmountSnapshot} {result.doseUnitSnapshot}, cách ly{" "}
          {result.phiDaysSnapshot} ngày. Ruộng bị khoá thu hoạch đến hết ngày{" "}
          <b>{result.harvestLockedUntil}</b>.
        </p>
      </div>
    );
  }

  return (
    <form className="inline-form" onSubmit={submit}>
      <label className="field-label">
        Ngày thực tế đã phun
        <input type="date" value={appliedAt} onChange={(e) => setAppliedAt(e.target.value)} required />
      </label>
      <div className="form-actions">
        <button type="submit" className="btn btn-primary" disabled={busy}>
          {busy ? "Đang lưu…" : "Ghi nhận đã phun thuốc"}
        </button>
      </div>
    </form>
  );
}

export default function DiagnosisPanel({
  report,
  diseases,
  currentRole,
  onUpdated,
  onError,
}: DiagnosisPanelProps) {
  const diagnosis = report.diagnosis;
  // Backend đã chặn cứng bằng @PreAuthorize - ẩn nút ở đây chỉ là defense-in-depth cho UX,
  // không phải điểm chặn thật (giống nguyên tắc poka-yoke 2 tầng đã áp dụng cho PHI).
  const canDecide = currentRole === "ENGINEER" || currentRole === "ADMIN";

  return (
    <Section
      step="4"
      title="Chẩn đoán, duyệt & xử lý"
      subtitle="Tra cứu deterministic (M3) → diễn đạt khuyến nghị (M4) → kỹ sư duyệt (M5) → ghi nhận đã phun (M6)"
    >
      {!diagnosis && canDecide && (
        <CreateDiagnosisForm
          reportId={report.id}
          diseases={diseases}
          onUpdated={onUpdated}
          onError={onError}
        />
      )}
      {!diagnosis && !canDecide && (
        <p className="empty-hint">Chỉ kỹ sư nông nghiệp mới ghi nhận được chẩn đoán.</p>
      )}

      {diagnosis && (
        <div className="diagnosis-card">
          <div className="diagnosis-head">
            <h3>{diagnosis.diseaseName}</h3>
            <Badge
              tone={
                diagnosis.approvalStatus === "APPROVED"
                  ? "success"
                  : diagnosis.approvalStatus === "REJECTED"
                    ? "danger"
                    : "warning"
              }
            >
              {diagnosis.approvalStatus}
            </Badge>
          </div>
          <p className="diagnosis-meta">
            Độ tin cậy {Math.round(diagnosis.confidence * 100)}% · model {diagnosis.modelVersion} ·{" "}
            {new Date(diagnosis.diagnosedAt).toLocaleString("vi-VN")}
          </p>
          <blockquote className="recommendation">{diagnosis.recommendationText}</blockquote>

          {diagnosis.treatmentRule && (
            <table className="rule-table">
              <tbody>
                <tr>
                  <th>Thuốc khuyến cáo</th>
                  <td>{diagnosis.treatmentRule.pesticideCode}</td>
                </tr>
                <tr>
                  <th>Liều lượng</th>
                  <td>
                    {diagnosis.treatmentRule.doseAmount} {diagnosis.treatmentRule.doseUnit}
                  </td>
                </tr>
                <tr>
                  <th>Cách ly (PHI)</th>
                  <td>{diagnosis.treatmentRule.phiDays} ngày</td>
                </tr>
                <tr>
                  <th>Nguồn</th>
                  <td>
                    {diagnosis.treatmentRule.source} {diagnosis.treatmentRule.version}
                  </td>
                </tr>
              </tbody>
            </table>
          )}

          {diagnosis.approvalStatus === "PENDING" && canDecide && (
            <ApprovalActions reportId={report.id} onUpdated={onUpdated} onError={onError} />
          )}
          {diagnosis.approvalStatus === "PENDING" && !canDecide && (
            <p className="empty-hint">Chỉ kỹ sư nông nghiệp mới được duyệt/từ chối khuyến nghị.</p>
          )}

          {diagnosis.approvalStatus === "APPROVED" && canDecide && diagnosis.treatmentRule && (
            <ApplyTreatmentBox reportId={report.id} onError={onError} />
          )}
          {diagnosis.approvalStatus === "APPROVED" && !diagnosis.treatmentRule && (
            <div className="banner banner-warning">
              Chưa có quy tắc điều trị được duyệt nên không thể ghi nhận phun thuốc.
            </div>
          )}

          {diagnosis.approvalStatus === "REJECTED" && (
            <div className="banner banner-danger">
              <strong>Đã từ chối bởi {diagnosis.decidedBy}</strong>
              <p>{diagnosis.rejectionReason}</p>
            </div>
          )}
        </div>
      )}
    </Section>
  );
}
