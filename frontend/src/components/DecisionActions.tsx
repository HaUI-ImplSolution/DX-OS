import { useState } from "react";
import { approveCase, approveDiagnosis, assignCase, rejectCase, rejectDiagnosis } from "../api";
import type { ApprovalRequest, Role } from "../types";

interface DecisionActionsProps {
  request: ApprovalRequest;
  role: Role;
  onDone: () => void;
  onError: (message: string) => void;
}

// Parcel and season requests are decided by an ADMIN; diagnoses by an ENGINEER or ADMIN through
// their own endpoints.
export default function DecisionActions({ request, role, onDone, onError }: DecisionActionsProps) {
  const [rejecting, setRejecting] = useState(false);
  const [reason, setReason] = useState("");
  const [busy, setBusy] = useState(false);

  if (request.status !== "PENDING") return null;

  const isDiagnosis = request.caseType === "DIAGNOSIS_APPROVAL";
  const canDecide = role === "ADMIN" || (role === "ENGINEER" && isDiagnosis);
  const reportId = Number(request.details.reportId);

  const run = async (action: () => Promise<unknown>, failure: string) => {
    setBusy(true);
    try {
      await action();
      setRejecting(false);
      setReason("");
      onDone();
    } catch (err) {
      onError(err instanceof Error ? err.message : failure);
    } finally {
      setBusy(false);
    }
  };

  return (
    <div className="decision-actions">
      <div className="form-actions">
        <button
          className="btn"
          disabled={busy}
          onClick={() => run(() => assignCase(request.caseId), "Không nhận được việc")}
        >
          {request.assignedToName ? "Nhận lại việc" : "Nhận việc"}
        </button>
        {canDecide && !rejecting && (
          <>
            <button
              className="btn btn-primary"
              disabled={busy}
              onClick={() =>
                run(
                  () => (isDiagnosis ? approveDiagnosis(reportId) : approveCase(request.caseId)),
                  "Không duyệt được",
                )
              }
            >
              Duyệt
            </button>
            <button className="btn btn-secondary" disabled={busy} onClick={() => setRejecting(true)}>
              Từ chối
            </button>
          </>
        )}
      </div>
      {canDecide && rejecting && (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            void run(
              () =>
                isDiagnosis
                  ? rejectDiagnosis(reportId, { reason })
                  : rejectCase(request.caseId, reason),
              "Không từ chối được",
            );
          }}
        >
          <input
            placeholder="Lý do từ chối (bắt buộc)"
            value={reason}
            maxLength={255}
            onChange={(e) => setReason(e.target.value)}
            required
          />
          <div className="form-actions">
            <button type="submit" className="btn btn-secondary" disabled={busy}>
              Xác nhận từ chối
            </button>
            <button type="button" className="btn" onClick={() => setRejecting(false)}>
              Huỷ
            </button>
          </div>
        </form>
      )}
    </div>
  );
}
