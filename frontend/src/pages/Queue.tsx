import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { listApprovalRequests } from "../api";
import Badge from "../components/Badge";
import DecisionActions from "../components/DecisionActions";
import Pagination from "../components/Pagination";
import Section from "../components/Section";
import { STATUS_LABEL, STATUS_TONE, formatDateTime } from "../labels";
import type { ApprovalRequest, Page, Role } from "../types";

interface QueueProps {
  role: Role;
  onError: (message: string) => void;
}

type Tab = "mine" | "free";

const PAGE_SIZE = 10;

// Diagnoses waiting for an engineer: the ones taken by me, and the ones nobody has taken yet.
export default function Queue({ role, onError }: QueueProps) {
  const [tab, setTab] = useState<Tab>("mine");
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<ApprovalRequest> | null>(null);

  const load = useCallback(() => {
    listApprovalRequests({
      caseType: "DIAGNOSIS_APPROVAL",
      status: "PENDING",
      assignedTo: tab === "mine" ? "me" : undefined,
      unassigned: tab === "free" ? true : undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [tab, page, onError]);

  useEffect(load, [load]);

  const switchTab = (next: Tab) => {
    setTab(next);
    setPage(0);
  };

  return (
    <Section step="🧑‍🔧" title="Hàng đợi chẩn đoán" subtitle="Nhận việc, xem ảnh và kết quả AI rồi duyệt hoặc từ chối">
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
          {tab === "mine" ? "Bạn chưa nhận việc nào." : "Không còn chẩn đoán nào chờ người nhận."}
        </p>
      )}

      {result?.items.map((request) => (
        <article key={request.caseId} className="queue-card">
          <div>
            <strong>{request.title}</strong>
            <Badge tone={STATUS_TONE[request.status]}>{STATUS_LABEL[request.status]}</Badge>
          </div>
          <div className="cell-sub">
            {request.details.diseaseName ?? "—"} · {request.farmerName ?? "—"} (hộ {request.householdCode ?? "—"}) ·{" "}
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
