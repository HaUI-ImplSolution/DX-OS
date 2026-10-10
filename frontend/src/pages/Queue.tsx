import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listApprovalRequests } from "../api";
import Badge from "../components/Badge";
import DecisionActions from "../components/DecisionActions";
import Pagination from "../components/Pagination";
import Section from "../components/Section";
import { formatDateTime, statusLabel, statusTone } from "../labels";
import type { ApprovalRequest, Page, Role } from "../types";

interface QueueProps {
  role: Role;
  onError: (message: string) => void;
}

type Tab = "mine" | "free";
type Kind = "DIAGNOSIS_APPROVAL" | "TREATMENT_CONFIRMATION";

const PAGE_SIZE = 10;

// Diagnoses and spraying reports waiting for an engineer: the ones taken by me, and the ones
// nobody has taken yet.
export default function Queue({ role, onError }: QueueProps) {
  const [tab, setTab] = useState<Tab>("mine");
  const [kind, setKind] = useState<Kind>("DIAGNOSIS_APPROVAL");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<ApprovalRequest> | null>(null);

  const load = useCallback(() => {
    listApprovalRequests({
      caseType: kind,
      status: "PENDING",
      assignedTo: tab === "mine" ? "me" : undefined,
      unassigned: tab === "free" ? true : undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [tab, kind, page, onError]);

  useEffect(load, [load]);

  const switchTab = (next: Tab) => {
    setTab(next);
    setPage(0);
  };

  const switchKind = (next: Kind) => {
    setKind(next);
    setPage(0);
  };

  return (
    <Section step="🧑‍🔧" title="Hàng đợi kỹ sư" subtitle="Nhận việc rồi duyệt chẩn đoán hoặc xác nhận việc phun thuốc">
      <nav className="tabs">
        <button
          className={kind === "DIAGNOSIS_APPROVAL" ? "tab tab-active" : "tab"}
          onClick={() => switchKind("DIAGNOSIS_APPROVAL")}
        >
          Chẩn đoán
        </button>
        <button
          className={kind === "TREATMENT_CONFIRMATION" ? "tab tab-active" : "tab"}
          onClick={() => switchKind("TREATMENT_CONFIRMATION")}
        >
          Khai phun thuốc
        </button>
      </nav>
      <nav className="tabs">
        <button className={tab === "mine" ? "tab tab-active" : "tab"} onClick={() => switchTab("mine")}>
          Việc của tôi
        </button>
        <button className={tab === "free" ? "tab tab-active" : "tab"} onClick={() => switchTab("free")}>
          Chưa có người nhận
        </button>
      </nav>

      {result && result.items.length === 0 && (
        <p className="empty-hint">
          {tab === "mine" ? "Bạn chưa nhận việc nào." : "Không còn việc nào chờ người nhận."}
        </p>
      )}

      {result?.items.map((request) => (
        <article key={request.caseId} className="queue-card">
          <div>
            <strong>{request.title}</strong>
            <Badge tone={statusTone(request.status)}>{statusLabel(request.status)}</Badge>
          </div>
          <div className="cell-sub">
            {request.details.diseaseName ?? request.details.pesticide ?? "—"} · {request.farmerName ?? "—"} (hộ {request.householdCode ?? "—"}) ·{" "}
            {formatDateTime(request.createdAt)}
          </div>
          <div className="form-actions">
            <Link className="btn" to={`/requests/${request.caseId}`}>
              Xem chi tiết
            </Link>
          </div>
          <DecisionActions request={request} role={role} onDone={load} onError={onError} />
        </article>
      ))}

      {result && <Pagination page={result} onChange={setPage} />}
    </Section>
  );
}
