import { ACTION_LABEL, formatDateTime } from "../labels";
import type { WorkflowEvent } from "../types";

export default function Timeline({ events }: { events: WorkflowEvent[] }) {
  return (
    <ol className="timeline">
      {events.map((event) => (
        <li key={event.id}>
          <div>
            <strong>{ACTION_LABEL[event.action] ?? event.action}</strong>
            {event.actorDisplayName && <span> — {event.actorDisplayName}</span>}
          </div>
          <div className="timeline-time">{formatDateTime(event.occurredAt)}</div>
          {event.reason && <div className="timeline-reason">Lý do: {event.reason}</div>}
        </li>
      ))}
    </ol>
  );
}
