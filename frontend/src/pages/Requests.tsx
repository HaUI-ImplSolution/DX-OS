import { useCallback, useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { listApprovalRequests } from "../api";
import Badge from "../components/Badge";
import DecisionActions from "../components/DecisionActions";
import Pagination from "../components/Pagination";
import RequestDetails from "../components/RequestDetails";
import Section from "../components/Section";
import { CASE_TYPE_LABEL, STATUS_LABEL, formatDateTime, statusLabel, statusTone } from "../labels";
import type { ApprovalRequest, CaseStatus, CaseType, Page, Role } from "../types";

interface RequestsProps {
  role: Role;
  onError: (message: string) => void;
}

const PAGE_SIZE = 10;

// Filters live in the URL so a filtered list can be bookmarked and survives a reload.
export default function Requests({ role, onError }: RequestsProps) {
  const [params, setParams] = useSearchParams();
  const [result, setResult] = useState<Page<ApprovalRequest> | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  const caseType = (params.get("type") ?? "") as CaseType | "";
  // No status in the URL means the default view: only what still waits. "ALL" lifts the filter.
  const rawStatus = params.get("status");
  const status = (rawStatus === null ? "PENDING" : rawStatus === "ALL" ? "" : rawStatus) as
    | CaseStatus
    | "";
  const household = params.get("household") ?? "";
  const from = params.get("from") ?? "";
  const to = params.get("to") ?? "";
  const page = Number(params.get("page") ?? 0);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next, { replace: true });
  };

  const load = useCallback(() => {
    listApprovalRequests({
      caseType: caseType || undefined,
      status: status || undefined,
      householdCode: household || undefined,
      from: from || undefined,
      to: to || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [caseType, status, household, from, to, page, onError]);

  useEffect(load, [load]);

  return (
    <Section
      step="📋"
      title="Yêu cầu"
      subtitle="Thửa, vụ mùa và chẩn đoán đang chờ xử lý, cũ nhất ở trên"
    >
      <div className="filter-bar">
        <select value={caseType} onChange={(e) => setFilter("type", e.target.value)}>
          <option value="">Mọi loại</option>
          {(Object.keys(CASE_TYPE_LABEL) as CaseType[]).map((type) => (
            <option key={type} value={type}>
              {CASE_TYPE_LABEL[type]}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => setFilter("status", e.target.value || "ALL")}>
          <option value="">Mọi trạng thái</option>
          {(Object.keys(STATUS_LABEL) as CaseStatus[]).map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
        <input
          placeholder="Mã hộ (vd. H0042)"
          value={household}
          onChange={(e) => setFilter("household", e.target.value.trim().toUpperCase())}
        />
        <label className="field-label">
          Từ ngày
          <input type="date" value={from} onChange={(e) => setFilter("from", e.target.value)} />
        </label>
        <label className="field-label">
          Đến ngày
          <input type="date" value={to} onChange={(e) => setFilter("to", e.target.value)} />
        </label>
      </div>

      {result && result.items.length === 0 && <p className="empty-hint">Không có yêu cầu nào khớp bộ lọc.</p>}

      {result && result.items.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Yêu cầu</th>
              <th>Loại</th>
              <th>Hộ</th>
              <th>Gửi lúc</th>
              <th>Trạng thái</th>
              <th>Người xử lý</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {result.items.map((request) => (
              <RequestRow
                key={request.caseId}
                request={request}
                role={role}
                open={expanded === request.caseId}
                onToggle={() => setExpanded(expanded === request.caseId ? null : request.caseId)}
                onDone={load}
                onError={onError}
              />
            ))}
          </tbody>
        </table>
      )}

      {result && <Pagination page={result} onChange={(p) => setFilter("page", String(p))} />}
    </Section>
  );
}

function RequestRow({
  request,
  role,
  open,
  onToggle,
  onDone,
  onError,
}: {
  request: ApprovalRequest;
  role: Role;
  open: boolean;
  onToggle: () => void;
  onDone: () => void;
  onError: (message: string) => void;
}) {
  return (
    <>
      <tr>
        <td>{request.title}</td>
        <td>{CASE_TYPE_LABEL[request.caseType] ?? request.caseType}</td>
        <td>
          {request.householdCode ?? "—"}
          {request.farmerName && <div className="cell-sub">{request.farmerName}</div>}
        </td>
        <td>{formatDateTime(request.createdAt)}</td>
        <td>
          <Badge tone={statusTone(request.status)}>{statusLabel(request.status)}</Badge>
        </td>
        <td>{request.assignedToName ?? "—"}</td>
        <td>
          <button className="btn btn-link" onClick={onToggle}>
            {open ? "Thu gọn" : "Xem & xử lý"}
          </button>
        </td>
      </tr>
      {open && (
        <tr className="expanded-row">
          <td colSpan={7}>
            <RequestDetails request={request} />
            <DecisionActions request={request} role={role} onDone={onDone} onError={onError} />
            <Link className="btn btn-link" to={`/requests/${request.caseId}`}>
              Mở trang chi tiết và lịch sử →
            </Link>
          </td>
        </tr>
      )}
    </>
  );
}
