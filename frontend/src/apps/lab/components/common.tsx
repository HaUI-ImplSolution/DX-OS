import { Badge, formatDateTime } from "../../../shell";
import { PRIORITY_LABEL, PRIORITY_TONE } from "../labels";
import type { Ticket, TicketPriority } from "../types";

export function PriorityBadge({ priority }: { priority: TicketPriority }) {
  return <Badge tone={PRIORITY_TONE[priority]}>{PRIORITY_LABEL[priority]}</Badge>;
}

// Response and resolution deadlines of a ticket, marked once the ticket is overdue.
export function SlaClock({ ticket }: { ticket: Ticket }) {
  const closed = ["CLOSED", "REJECTED", "CANCELLED", "RESOLVED"].includes(ticket.status);
  return (
    <div className="sla-clock">
      <div>
        <small>Hạn phản hồi</small>
        <div>
          {ticket.firstRespondedAt
            ? `Đã phản hồi ${formatDateTime(ticket.firstRespondedAt)}`
            : ticket.responseDueAt
              ? formatDateTime(ticket.responseDueAt)
              : "—"}
        </div>
      </div>
      <div>
        <small>Hạn xử lý</small>
        <div>
          {ticket.resolutionDueAt ? formatDateTime(ticket.resolutionDueAt) : "—"}{" "}
          {ticket.overdue && !closed && <Badge tone="danger">Quá hạn</Badge>}
        </div>
      </div>
    </div>
  );
}

export function Stars({ value }: { value: number | null }) {
  if (value === null) return <span>—</span>;
  return (
    <span className="stars" aria-label={`${value} trên 5 sao`}>
      {"★".repeat(value)}
      {"☆".repeat(Math.max(0, 5 - value))}
    </span>
  );
}

// Runs one async action with a busy flag; errors go to the page's error banner and the page
// reloads so it shows the state the action ran against.
export async function runAction(
  action: () => Promise<unknown>,
  setBusy: (busy: boolean) => void,
  onError: (message: string) => void,
  onDone: () => void,
) {
  setBusy(true);
  try {
    await action();
    onDone();
  } catch (err) {
    onError(err instanceof Error ? err.message : "Thao tác không thành công");
    onDone();
  } finally {
    setBusy(false);
  }
}

export function toLocalInput(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(
    date.getHours(),
  )}:${pad(date.getMinutes())}`;
}

export function toIsoDate(date: Date): string {
  const pad = (n: number) => String(n).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`;
}
