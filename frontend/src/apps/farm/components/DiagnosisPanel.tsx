import { useEffect, useState } from "react";
import {
  applyDiagnosis,
  suggestDiagnosis,
  requestReportRetake,
  diagnosisHistory,
  approveDiagnosis,
  createAiDiagnosis,
  createDiagnosis,
  rejectDiagnosis,
} from "../api";
import type {
  Diagnosis,
  Disease,
  DiseaseReport,
  Role,
  TreatmentApplication,
  TreatmentRule,
  SymptomSuggestion,
} from "../types";
import { Badge, GeneratedAnswerCard, PassageText, Section, SourceList } from "../../../shell";

interface DiagnosisPanelProps {
  onReportUpdated: (report: DiseaseReport) => void;
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
  const [symptomText, setSymptomText] = useState("");
  const [suggestions, setSuggestions] = useState<SymptomSuggestion[]>([]);
  const [suggesting, setSuggesting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [aiSubmitting, setAiSubmitting] = useState(false);

  const suggest = async () => {
    setSuggesting(true);
    setSuggestions([]);
    try {
      setSuggestions(await suggestDiagnosis(reportId, symptomText));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không tìm được gợi ý");
    } finally {
      setSuggesting(false);
    }
  };

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
        symptomText: symptomText.trim() || undefined,
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
      <label className="field-label">
        Triệu chứng quan sát được
        <textarea
          value={symptomText}
          maxLength={4000}
          onChange={(e) => {
            setSymptomText(e.target.value);
            setSuggestions([]);
          }}
          placeholder="Mô tả màu, hình dạng vết bệnh, vị trí và tình trạng cây…"
        />
      </label>
      <button
        type="button"
        className="btn"
        disabled={suggesting || !symptomText.trim()}
        onClick={() => void suggest()}
      >
        {suggesting ? "Đang tra cứu…" : "Gợi ý bệnh từ tài liệu"}
      </button>
      <p className="empty-hint">
        Điểm tương đồng tài liệu chỉ hỗ trợ tra cứu. Kỹ sư chốt bệnh và độ tin cậy.
      </p>
      {suggestions.map((item) => (
        <div key={item.disease_code} className="banner">
          <button type="button" className="btn" onClick={() => setDiseaseCode(item.disease_code)}>
            {item.disease_name} · tương đồng {item.score.toFixed(3)}
          </button>
          <p>{item.evidence}</p>
          <small>Nguồn: {item.source_ids.join(", ")}</small>
        </div>
      ))}
      <p className="empty-hint">Hoặc chọn bệnh trực tiếp:</p>
      <label className="field-label">
        Bệnh chẩn đoán
        <select value={diseaseCode} onChange={(e) => setDiseaseCode(e.target.value)} required>
          {diseases.map((d) => (
            <option key={d.code} value={d.code}>
              {d.name}
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

function ApplyTreatmentBox({
  reportId,
  rule,
  onError,
}: {
  reportId: number;
  rule: TreatmentRule;
  onError: (message: string) => void;
}) {
  const [doseAmount, setDoseAmount] = useState("");
  const [appliedAt, setAppliedAt] = useState(() => new Date().toISOString().slice(0, 10));
  const [busy, setBusy] = useState(false);
  const [result, setResult] = useState<TreatmentApplication | null>(null);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      setResult(await applyDiagnosis(reportId, { doseAmount: Number(doseAmount), appliedAt }));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không ghi nhận được đã phun thuốc");
    } finally {
      setBusy(false);
    }
  };

  if (result) {
    return (
      <div className="banner banner-success">
        <strong>Đã ghi nhận phun thuốc</strong>
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
        Liều thực tế ({rule.doseUnit}, cho phép {rule.doseMin}–{rule.doseMax})
        <input
          type="number"
          min={rule.doseMin}
          max={rule.doseMax}
          step="0.01"
          value={doseAmount}
          onChange={(e) => setDoseAmount(e.target.value)}
          required
        />
      </label>
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
  onReportUpdated,
  report,
  diseases,
  currentRole,
  onUpdated,
  onError,
}: DiagnosisPanelProps) {
  const diagnosis = report.diagnosis;
  const needsRetake = report.status === "NEEDS_RETAKE";
  const [retakeReason, setRetakeReason] = useState("");
  const [retakeBusy, setRetakeBusy] = useState(false);
  const [history, setHistory] = useState<Diagnosis[]>([]);
  useEffect(() => {
    setRetakeReason("");
    setHistory([]);
    if (currentRole !== "ENGINEER" && currentRole !== "ADMIN") {
      return;
    }
    let cancelled = false;
    diagnosisHistory(report.id)
      .then((items) => {
        if (!cancelled) setHistory(items);
      })
      .catch(() => {});
    return () => {
      cancelled = true;
    };
  }, [report.id, diagnosis?.id, diagnosis?.approvalStatus, currentRole]);

  const requestRetake = async (e: React.FormEvent) => {
    e.preventDefault();
    setRetakeBusy(true);
    try {
      onReportUpdated(await requestReportRetake(report.id, retakeReason));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không yêu cầu chụp lại được");
    } finally {
      setRetakeBusy(false);
    }
  };

  // Backend đã chặn cứng bằng @PreAuthorize - ẩn nút ở đây chỉ là defense-in-depth cho UX,
  // không phải điểm chặn thật (giống nguyên tắc poka-yoke 2 tầng đã áp dụng cho PHI).
  const canDecide = currentRole === "ENGINEER" || currentRole === "ADMIN";

  return (
    <Section
      step="4"
      title="Chẩn đoán, duyệt & xử lý"
      subtitle="Tra cứu phác đồ → khuyến nghị → kỹ sư duyệt → ghi nhận đã phun"
    >
      {needsRetake && (
        <div className="banner banner-warning">Cần chụp lại: {report.retakeReason}</div>
      )}
      {canDecide && !needsRetake && diagnosis?.approvalStatus !== "APPROVED" && (
        <form className="inline-form" onSubmit={requestRetake}>
          <label className="field-label">
            Ảnh không đủ để đánh giá — lý do cần chụp lại
            <input
              required
              maxLength={1000}
              value={retakeReason}
              onChange={(e) => setRetakeReason(e.target.value)}
            />
          </label>
          <button className="btn" disabled={retakeBusy}>
            Yêu cầu ảnh mới
          </button>
        </form>
      )}
      {canDecide && !needsRetake && (
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
            <h3>{diagnosis.diseaseName ?? "Chẩn đoán"}</h3>
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
          {diagnosis.confidence != null && (
            <div className="diagnosis-confidence">
              <div>
                <span>Độ tin cậy của mô hình</span>
                <strong>{Math.round(diagnosis.confidence * 100)}%</strong>
              </div>
              <progress
                aria-label="Độ tin cậy của mô hình"
                max="100"
                value={Math.round(diagnosis.confidence * 100)}
              />
            </div>
          )}

          {diagnosis.treatmentRule && (
            <section className="treatment-card" aria-labelledby={`treatment-title-${diagnosis.id}`}>
              <h4 id={`treatment-title-${diagnosis.id}`}>Phác đồ điều trị</h4>
              <dl className="treatment-facts">
                <div>
                  <dt>Thuốc khuyến cáo</dt>
                  <dd>{diagnosis.treatmentRule.pesticideCode}</dd>
                </div>
                <div>
                  <dt>Liều lượng</dt>
                  <dd>
                    {diagnosis.treatmentRule.doseMin === diagnosis.treatmentRule.doseMax
                      ? diagnosis.treatmentRule.doseMin
                      : `${diagnosis.treatmentRule.doseMin}–${diagnosis.treatmentRule.doseMax}`}{" "}
                    {diagnosis.treatmentRule.doseUnit}
                  </dd>
                </div>
                <div>
                  <dt>Thời gian cách ly</dt>
                  <dd>{diagnosis.treatmentRule.phiDays} ngày</dd>
                </div>
              </dl>
              {(diagnosis.treatmentRule.source || diagnosis.treatmentRule.version) && (
                <div className="treatment-source">
                  <span>Nguồn phác đồ</span>
                  {diagnosis.treatmentRule.source && (
                    <strong>{diagnosis.treatmentRule.source.split(",").join(" · ")}</strong>
                  )}
                  {diagnosis.treatmentRule.version && (
                    <small>Phiên bản {diagnosis.treatmentRule.version}</small>
                  )}
                </div>
              )}
            </section>
          )}

          {diagnosis.generatedExplanation && (
            <GeneratedAnswerCard answer={diagnosis.generatedExplanation} diagnosis />
          )}

          {diagnosis.knowledgeEvidence && diagnosis.knowledgeEvidence.length > 0 && (
            <section
              className="knowledge-card"
              aria-labelledby={`knowledge-title-${diagnosis.id}`}
            >
              <h4 id={`knowledge-title-${diagnosis.id}`}>Thông tin tham khảo từ tài liệu</h4>
              <p className="knowledge-note">
                Chỉ để tham khảo, không thay thế phác đồ đã xác thực.
              </p>
              {diagnosis.knowledgeEvidence.map((passage, index) => (
                <article key={index} className="knowledge-passage">
                  {passage.documentTitle && <strong>{passage.documentTitle}</strong>}
                  <PassageText content={passage.content} />
                  <small>Độ liên quan của tài liệu: {Math.round(passage.score * 100)}%</small>
                  {passage.sources.length > 0 && <SourceList sources={passage.sources} />}
                </article>
              ))}
            </section>
          )}

          {diagnosis.approvalStatus === "PENDING" && canDecide && !needsRetake && (
            <ApprovalActions reportId={report.id} onUpdated={onUpdated} onError={onError} />
          )}
          {diagnosis.approvalStatus === "PENDING" && !canDecide && (
            <p className="empty-hint">Chỉ kỹ sư nông nghiệp mới được duyệt/từ chối khuyến nghị.</p>
          )}

          {diagnosis.approvalStatus === "APPROVED" &&
            canDecide &&
            !needsRetake &&
            diagnosis.treatmentRule && (
            <ApplyTreatmentBox reportId={report.id} rule={diagnosis.treatmentRule} onError={onError} />
          )}
          {diagnosis.approvalStatus === "APPROVED" && !diagnosis.treatmentRule && (
            <div className="banner banner-warning">
              Chưa có quy tắc điều trị được duyệt nên không thể ghi nhận phun thuốc.
            </div>
          )}

          {diagnosis.approvalStatus === "REJECTED" && (
            <div className="banner banner-danger">
              <strong>Đã từ chối{diagnosis.decidedBy ? ` bởi ${diagnosis.decidedBy}` : ""}</strong>
              <p>{diagnosis.rejectionReason}</p>
            </div>
          )}
        </div>
      )}
      {history.length > 1 && (
        <details>
          <summary>Lịch sử chẩn đoán ({history.length})</summary>
          {history.map((item) => (
            <p key={item.id}>
              #{item.id} · {item.diseaseName} · {item.approvalStatus} ·{" "}
              {item.diagnosedAt && new Date(item.diagnosedAt).toLocaleString("vi-VN")}
            </p>
          ))}
        </details>
      )}
    </Section>
  );
}
