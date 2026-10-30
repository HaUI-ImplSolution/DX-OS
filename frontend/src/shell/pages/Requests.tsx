// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { listApprovalRequests } from "../api";
import type { CaseUi } from "../appModule";
import Badge from "../components/Badge";
import Pagination from "../components/Pagination";
import Section from "../components/Section";
import { formatDateTime, statusLabel, statusTone } from "../labels";
import type { ApprovalRequest, Page } from "../types";

interface RequestsProps {
  role: string;
  onError: (message: string) => void;
  ui: CaseUi;
}

const PAGE_SIZE = 10;

// Filters live in the URL so a filtered list can be bookmarked and survives a reload.
export default function Requests({ role, onError, ui }: RequestsProps) {
  const [params, setParams] = useSearchParams();
  const [result, setResult] = useState<Page<ApprovalRequest> | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  const caseType = params.get("type") ?? "";
  // No status in the URL means the default view: only what still waits. "ALL" lifts the filter.
  const rawStatus = params.get("status");
  const status = rawStatus === null ? "PENDING" : rawStatus === "ALL" ? "" : rawStatus;
  const unit = params.get(ui.unit.urlKey) ?? "";
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
      [ui.unit.filterParam]: unit || undefined,
      from: from || undefined,
      to: to || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [caseType, status, unit, from, to, page, onError, ui.unit.filterParam]);

  useEffect(load, [load]);

  return (
    <Section step="📋" title={ui.title} subtitle={ui.subtitle}>
      <div className="filter-bar">
        <select value={caseType} onChange={(e) => setFilter("type", e.target.value)}>
          <option value="">Mọi loại</option>
          {Object.keys(ui.caseTypeLabels).map((type) => (
            <option key={type} value={type}>
              {ui.caseTypeLabels[type]}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => setFilter("status", e.target.value || "ALL")}>
          <option value="">Mọi trạng thái</option>
          {ui.statusOptions.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
        <input
          placeholder={ui.unit.filterPlaceholder}
          value={unit}
          onChange={(e) => setFilter(ui.unit.urlKey, e.target.value.trim().toUpperCase())}
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
              <th>{ui.unit.columnLabel}</th>
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
                ui={ui}
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
  ui,
  role,
  open,
  onToggle,
  onDone,
  onError,
}: {
  request: ApprovalRequest;
  ui: CaseUi;
  role: string;
  open: boolean;
  onToggle: () => void;
  onDone: () => void;
  onError: (message: string) => void;
}) {
  const location = useLocation();
  const unit = ui.unit.display(request);
  return (
    <>
      <tr>
        <td>{request.title}</td>
        <td>{ui.caseTypeLabels[request.caseType] ?? request.caseType}</td>
        <td>
          {unit.code ?? "—"}
          {unit.person && <div className="cell-sub">{unit.person}</div>}
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
            {ui.renderDetails(request)}
            {ui.renderActions(request, { role, onDone, onError })}
            <Link
              className="btn btn-link"
              to={`/requests/${request.caseId}`}
              state={{ from: `${location.pathname}${location.search}` }}
            >
              Mở trang chi tiết và lịch sử →
            </Link>
          </td>
        </tr>
      )}
    </>
  );
}
