// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { Badge, Pagination, Section, formatDateTime, statusLabel, statusTone } from "../../../shell";
import type { Page } from "../../../shell";
import { assignTicket, listTechnicians, listTickets, startTicket } from "../api";
import { PriorityBadge, runAction } from "../components/common";
import type { TechnicianLoad, Ticket } from "../types";

interface QueueProps {
  userId: string;
  role: string;
  onError: (message: string) => void;
}

type Tab = "free" | "mine" | "overdue";

const PAGE_SIZE = 10;

const TAB_LABEL: Record<Tab, string> = {
  free: "Chưa có người nhận",
  mine: "Của tôi",
  overdue: "Quá hạn",
};

// Work queue of the technicians: tickets nobody has taken, the ones taken by me, and the overdue ones.
// The tab is kept in the address (?tab=free|mine|overdue) so links from the overview open it.
export default function Queue({ userId, role, onError }: QueueProps) {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  // The detail page returns to this tab through the address it is opened from.
  const from = { from: `${location.pathname}${location.search}` };
  const requested = params.get("tab");
  const tab: Tab = requested === "mine" || requested === "overdue" ? requested : "free";
  const [page, setPage] = useState(0);
  const [result, setResult] = useState<Page<Ticket> | null>(null);
  const [technicians, setTechnicians] = useState<TechnicianLoad[]>([]);
  const [busy, setBusy] = useState(false);

  const loadTickets = useCallback(() => {
    const filter =
      tab === "free"
        ? { unassigned: true, status: "NEW" }
        : tab === "mine"
          ? { assignedTo: userId }
          : { overdue: true };
    listTickets({ ...filter, page, size: PAGE_SIZE })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [tab, page, userId, onError]);

  const loadTechnicians = useCallback(() => {
    listTechnicians()
      .then(setTechnicians)
      .catch((err) => onError(err.message));
  }, [onError]);

  useEffect(loadTickets, [loadTickets]);
  useEffect(loadTechnicians, [loadTechnicians]);

  const refresh = () => {
    loadTickets();
    loadTechnicians();
  };

  const act = (action: () => Promise<unknown>) => runAction(action, setBusy, onError, refresh);

  return (
    <>
      <Section
        step="👷"
        title="Hàng đợi xử lý"
        subtitle="Nhận việc, bắt đầu xử lý và theo dõi các yêu cầu sắp quá hạn"
      >
        <div className="chip-list">
          {(Object.keys(TAB_LABEL) as Tab[]).map((key) => (
            <button
              key={key}
              className={key === tab ? "chip chip-active" : "chip"}
              onClick={() => {
                setParams({ tab: key }, { replace: true });
                setPage(0);
              }}
            >
              {TAB_LABEL[key]}
            </button>
          ))}
        </div>

        {result && result.items.length === 0 && <p className="empty-hint">Không có yêu cầu nào.</p>}

        {result?.items.map((ticket) => (
          <article key={ticket.id} className="queue-card">
            <div>
              <Link to={`/tickets/${ticket.id}`} state={from}>
                <strong>{ticket.code}</strong> · {ticket.title}
              </Link>{" "}
              <PriorityBadge priority={ticket.priority} />{" "}
              <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>{" "}
              {ticket.overdue && <Badge tone="danger">Quá hạn</Badge>}
            </div>
            <div className="cell-sub">
              {ticket.categoryName} · {ticket.requesterName}
              {ticket.roomCode && ` · phòng ${ticket.roomCode}`} · hạn{" "}
              {ticket.resolutionDueAt ? formatDateTime(ticket.resolutionDueAt) : "—"}
              {ticket.assigneeName && ` · ${ticket.assigneeName}`}
            </div>
            <div className="form-actions">
              {ticket.status === "NEW" && (
                <button className="btn btn-primary" disabled={busy} onClick={() => act(() => assignTicket(ticket.id))}>
                  Nhận việc
                </button>
              )}
              {ticket.status === "ASSIGNED" && (ticket.assigneeId === userId || role === "ADMIN") && (
                <button className="btn btn-primary" disabled={busy} onClick={() => act(() => startTicket(ticket.id))}>
                  Bắt đầu xử lý
                </button>
              )}
              <Link className="btn btn-link" to={`/tickets/${ticket.id}`} state={from}>
                Mở chi tiết →
              </Link>
            </div>
          </article>
        ))}

        {result && <Pagination page={result} onChange={setPage} noun="yêu cầu" />}
      </Section>

      <Section step="📊" title="Tải của kỹ thuật viên" subtitle="Số yêu cầu đang xử lý của từng người">
        {technicians.length === 0 ? (
          <p className="empty-hint">Chưa có kỹ thuật viên nào nhận việc.</p>
        ) : (
          <table className="data-table">
            <thead>
              <tr>
                <th>Kỹ thuật viên</th>
                <th>Đang xử lý</th>
              </tr>
            </thead>
            <tbody>
              {technicians.map((technician) => (
                <tr key={technician.userId}>
                  <td>{technician.name}</td>
                  <td>{technician.openTickets}</td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </Section>
    </>
  );
}
