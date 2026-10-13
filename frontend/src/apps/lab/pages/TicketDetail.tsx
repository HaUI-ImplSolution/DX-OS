import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Link, useParams } from "react-router-dom";
import { Badge, Section, Timeline, formatDateTime, statusLabel, statusTone } from "../../../shell";
import {
  addTicketAttachment,
  addTicketComment,
  assignTicket,
  cancelTicket,
  confirmTicket,
  getTicket,
  listTechnicians,
  rejectTicket,
  reopenTicket,
  resolveTicket,
  startTicket,
  waitTicket,
} from "../api";
import { PriorityBadge, SlaClock, Stars, runAction } from "../components/common";
import { formatBytes } from "../labels";
import type { TechnicianLoad, TicketDetail as TicketDetailData } from "../types";

interface TicketDetailProps {
  userId: string;
  role: string;
  onError: (message: string) => void;
}

const MAX_REOPENS = 3;

export default function TicketDetail({ userId, role, onError }: TicketDetailProps) {
  const { id } = useParams();
  const ticketId = Number(id);
  const [detail, setDetail] = useState<TicketDetailData | null>(null);
  const [busy, setBusy] = useState(false);
  const [unavailable, setUnavailable] = useState(false);

  const isStaff = role === "TECHNICIAN" || role === "ADMIN";
  const isAdmin = role === "ADMIN";

  const load = useCallback(() => {
    getTicket(ticketId)
      .then(setDetail)
      .catch((err) => {
        setUnavailable(true);
        onError(err.message);
      });
  }, [ticketId, onError]);

  useEffect(load, [load]);

  if (unavailable) {
    return (
      <>
        <Section
          step="🚫"
          title="Không xem được yêu cầu"
          subtitle="Yêu cầu không tồn tại hoặc không thuộc về bạn."
        >
          {null}
        </Section>
        <Link className="btn btn-link" to="/tickets">
          ← Về danh sách yêu cầu
        </Link>
      </>
    );
  }
  if (!detail) return <Section step="…" title="Đang tải yêu cầu" children={null} />;
  const { ticket, comments, attachments, timeline } = detail;
  const isRequester = ticket.requesterId === userId;
  const run = (action: () => Promise<unknown>) => runAction(action, setBusy, onError, load);

  return (
    <>
      <Section
        step="🎫"
        title={`${ticket.code} · ${ticket.title}`}
        subtitle={`${ticket.categoryName} · gửi bởi ${ticket.requesterName} lúc ${formatDateTime(ticket.createdAt)}`}
      >
        <p>
          <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>{" "}
          <PriorityBadge priority={ticket.priority} />
          {ticket.assigneeName && <span> · Người xử lý: {ticket.assigneeName}</span>}
          {ticket.reopenCount > 0 && <span> · Đã mở lại {ticket.reopenCount} lần</span>}
        </p>
        <p style={{ whiteSpace: "pre-line" }}>{ticket.description}</p>
        <table className="kv-table">
          <tbody>
            {ticket.roomCode && (
              <tr>
                <th>Phòng</th>
                <td>{ticket.roomCode}</td>
              </tr>
            )}
            {ticket.deviceAssetCode && (
              <tr>
                <th>Thiết bị</th>
                <td>{ticket.deviceAssetCode}</td>
              </tr>
            )}
            {ticket.resolutionNote && (
              <tr>
                <th>Cách đã xử lý</th>
                <td>{ticket.resolutionNote}</td>
              </tr>
            )}
            {ticket.rootCause && (
              <tr>
                <th>Nguyên nhân gốc</th>
                <td>{ticket.rootCause}</td>
              </tr>
            )}
            {ticket.rating !== null && (
              <tr>
                <th>Đánh giá</th>
                <td>
                  <Stars value={ticket.rating} />
                  {ticket.ratingComment && <div className="cell-sub">{ticket.ratingComment}</div>}
                </td>
              </tr>
            )}
          </tbody>
        </table>
        <SlaClock ticket={ticket} />
      </Section>

      <Actions
        ticket={detail.ticket}
        isStaff={isStaff}
        isAdmin={isAdmin}
        isRequester={isRequester}
        busy={busy}
        run={run}
        onError={onError}
      />

      <Section step="📎" title="Tệp đính kèm">
        {attachments.length === 0 && <p className="empty-hint">Chưa có tệp đính kèm.</p>}
        <ul className="attachment-list">
          {attachments.map((attachment) => (
            <li key={attachment.id}>
              {attachment.contentType.startsWith("image/") && (
                <a href={attachment.url} target="_blank" rel="noreferrer">
                  <img className="attachment-thumb" src={attachment.url} alt={attachment.filename} />
                </a>
              )}
              <a href={attachment.url} target="_blank" rel="noreferrer">
                {attachment.filename}
              </a>{" "}
              <small>({formatBytes(attachment.sizeBytes)})</small>
            </li>
          ))}
        </ul>
        <label className="field-label">
          Thêm tệp (JPEG, PNG, PDF, tối đa 5 MB)
          <input
            type="file"
            accept="image/jpeg,image/png,application/pdf"
            disabled={busy}
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              if (file) void run(() => addTicketAttachment(ticketId, file));
            }}
          />
        </label>
      </Section>

      <CommentsSection
        comments={comments}
        isStaff={isStaff}
        busy={busy}
        onSubmit={(body, internal) => run(() => addTicketComment(ticketId, body, internal))}
      />

      <Section step="🕓" title="Lịch sử xử lý" subtitle="Mỗi bước được ghi lại, không sửa hay xoá">
        <Timeline events={timeline} />
      </Section>

      <Link className="btn btn-link" to="/tickets">
        ← Về danh sách yêu cầu
      </Link>
    </>
  );
}

function CommentsSection({
  comments,
  isStaff,
  busy,
  onSubmit,
}: {
  comments: TicketDetailData["comments"];
  isStaff: boolean;
  busy: boolean;
  onSubmit: (body: string, internal: boolean) => Promise<void>;
}) {
  const [body, setBody] = useState("");
  const [internal, setInternal] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void onSubmit(body.trim(), internal).then(() => setBody(""));
  };

  return (
    <Section step="💬" title="Trao đổi" subtitle="Người gửi và kỹ thuật viên trao đổi tại đây">
      {comments.length === 0 && <p className="empty-hint">Chưa có trao đổi nào.</p>}
      <ul className="comment-list">
        {comments.map((comment) => (
          <li
            key={comment.id}
            className={comment.internal ? "comment comment-internal" : "comment"}
          >
            <div>
              <strong>{comment.authorName}</strong>{" "}
              {comment.staff && <Badge tone="neutral">Kỹ thuật viên</Badge>}{" "}
              {comment.internal && <Badge tone="warning">Nội bộ</Badge>}
              <small className="timeline-time"> {formatDateTime(comment.createdAt)}</small>
            </div>
            <p style={{ whiteSpace: "pre-line" }}>{comment.body}</p>
          </li>
        ))}
      </ul>
      <form className="harvest-box" onSubmit={submit}>
        <label className="field-label">
          Nội dung
          <textarea
            value={body}
            maxLength={4000}
            onChange={(e) => setBody(e.target.value)}
            required
          />
        </label>
        {isStaff && (
          <label className="field-label check-label">
            <input
              type="checkbox"
              checked={internal}
              onChange={(e) => setInternal(e.target.checked)}
            />{" "}
            Ghi chú nội bộ (người gửi không thấy)
          </label>
        )}
        <div className="form-actions">
          <button className="btn btn-primary" type="submit" disabled={busy || !body.trim()}>
            Gửi
          </button>
        </div>
      </form>
    </Section>
  );
}

type Dialog = null | "wait" | "resolve" | "reject" | "confirm" | "reopen";

function Actions({
  ticket,
  isStaff,
  isAdmin,
  isRequester,
  busy,
  run,
  onError,
}: {
  ticket: TicketDetailData["ticket"];
  isStaff: boolean;
  isAdmin: boolean;
  isRequester: boolean;
  busy: boolean;
  run: (action: () => Promise<unknown>) => Promise<void>;
  onError: (message: string) => void;
}) {
  const [dialog, setDialog] = useState<Dialog>(null);
  const [text, setText] = useState("");
  const [rootCause, setRootCause] = useState("");
  const [rating, setRating] = useState(5);
  const [technicians, setTechnicians] = useState<TechnicianLoad[]>([]);
  const [assignee, setAssignee] = useState("");

  const needsAssignment = isAdmin && ticket.status === "NEW";
  useEffect(() => {
    if (!needsAssignment) return;
    listTechnicians()
      .then(setTechnicians)
      .catch((err) => onError(err.message));
  }, [needsAssignment, onError]);

  const id = ticket.id;
  const close = () => {
    setDialog(null);
    setText("");
    setRootCause("");
  };
  const submit = (action: () => Promise<unknown>) => (event: FormEvent) => {
    event.preventDefault();
    void run(action).then(close);
  };

  const canAct =
    (isStaff && ["NEW", "ASSIGNED", "IN_PROGRESS"].includes(ticket.status)) ||
    (isRequester && ["NEW", "RESOLVED"].includes(ticket.status));
  if (!canAct) return null;

  const canReopen = ticket.reopenCount < MAX_REOPENS;

  return (
    <Section step="⚙️" title="Thao tác">
      <div className="form-actions">
        {isStaff && ticket.status === "NEW" && (
          <button className="btn btn-primary" disabled={busy} onClick={() => run(() => assignTicket(id))}>
            Nhận việc
          </button>
        )}
        {needsAssignment && (
          <>
            <select value={assignee} onChange={(e) => setAssignee(e.target.value)}>
              <option value="">Giao cho kỹ thuật viên…</option>
              {technicians.map((technician) => (
                <option key={technician.userId} value={technician.userId}>
                  {technician.name} ({technician.openTickets} đang xử lý)
                </option>
              ))}
            </select>
            <button
              className="btn"
              disabled={busy || !assignee}
              onClick={() => {
                const technician = technicians.find((t) => t.userId === assignee);
                if (technician)
                  void run(() =>
                    assignTicket(id, { assigneeId: technician.userId, assigneeName: technician.name }),
                  );
              }}
            >
              Phân công
            </button>
          </>
        )}
        {isStaff && ticket.status === "ASSIGNED" && (
          <button className="btn btn-primary" disabled={busy} onClick={() => run(() => startTicket(id))}>
            Bắt đầu xử lý
          </button>
        )}
        {isStaff && ticket.status === "IN_PROGRESS" && (
          <>
            <button className="btn" disabled={busy} onClick={() => setDialog("wait")}>
              Chờ người gửi bổ sung
            </button>
            <button className="btn btn-primary" disabled={busy} onClick={() => setDialog("resolve")}>
              Hoàn tất xử lý
            </button>
          </>
        )}
        {isAdmin && ["NEW", "ASSIGNED"].includes(ticket.status) && (
          <button className="btn btn-secondary" disabled={busy} onClick={() => setDialog("reject")}>
            Từ chối yêu cầu
          </button>
        )}
        {isRequester && ticket.status === "NEW" && (
          <button className="btn" disabled={busy} onClick={() => run(() => cancelTicket(id))}>
            Huỷ yêu cầu
          </button>
        )}
        {isRequester && ticket.status === "RESOLVED" && (
          <>
            <button className="btn btn-primary" disabled={busy} onClick={() => setDialog("confirm")}>
              Xác nhận đã xử lý xong
            </button>
            {canReopen ? (
              <button className="btn btn-secondary" disabled={busy} onClick={() => setDialog("reopen")}>
                Chưa xong, mở lại
              </button>
            ) : (
              <small>Đã mở lại tối đa {MAX_REOPENS} lần, hãy tạo yêu cầu mới nếu còn vấn đề.</small>
            )}
          </>
        )}
      </div>

      {dialog === "wait" && (
        <form className="inline-form" onSubmit={submit(() => waitTicket(id, text.trim()))}>
          <input
            placeholder="Cần người gửi bổ sung điều gì? (bắt buộc)"
            value={text}
            onChange={(e) => setText(e.target.value)}
            required
          />
          <FormButtons busy={busy} onCancel={close} label="Gửi yêu cầu bổ sung" />
        </form>
      )}
      {dialog === "resolve" && (
        <form
          className="inline-form"
          onSubmit={submit(() => resolveTicket(id, text.trim(), rootCause.trim()))}
        >
          <input
            placeholder="Cách đã xử lý (bắt buộc)"
            value={text}
            maxLength={2000}
            onChange={(e) => setText(e.target.value)}
            required
          />
          <input
            placeholder="Nguyên nhân gốc (bắt buộc)"
            value={rootCause}
            maxLength={1000}
            onChange={(e) => setRootCause(e.target.value)}
            required
          />
          <FormButtons busy={busy} onCancel={close} label="Hoàn tất" />
        </form>
      )}
      {dialog === "reject" && (
        <form className="inline-form" onSubmit={submit(() => rejectTicket(id, text.trim()))}>
          <input
            placeholder="Lý do từ chối (bắt buộc)"
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
            required
          />
          <FormButtons busy={busy} onCancel={close} label="Xác nhận từ chối" />
        </form>
      )}
      {dialog === "confirm" && (
        <form className="inline-form" onSubmit={submit(() => confirmTicket(id, rating, text.trim()))}>
          <label className="field-label">
            Đánh giá
            <select value={rating} onChange={(e) => setRating(Number(e.target.value))}>
              {[5, 4, 3, 2, 1].map((n) => (
                <option key={n} value={n}>
                  {"★".repeat(n)} ({n} sao)
                </option>
              ))}
            </select>
          </label>
          <input
            placeholder="Nhận xét (không bắt buộc)"
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
          />
          <FormButtons busy={busy} onCancel={close} label="Xác nhận và đóng" />
        </form>
      )}
      {dialog === "reopen" && (
        <form className="inline-form" onSubmit={submit(() => reopenTicket(id, text.trim()))}>
          <input
            placeholder="Vấn đề còn lại là gì?"
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
          />
          <FormButtons busy={busy} onCancel={close} label="Mở lại yêu cầu" />
        </form>
      )}
    </Section>
  );
}

function FormButtons({
  busy,
  onCancel,
  label,
}: {
  busy: boolean;
  onCancel: () => void;
  label: string;
}) {
  return (
    <div className="form-actions">
      <button type="submit" className="btn btn-primary" disabled={busy}>
        {label}
      </button>
      <button type="button" className="btn" onClick={onCancel}>
        Huỷ
      </button>
    </div>
  );
}
