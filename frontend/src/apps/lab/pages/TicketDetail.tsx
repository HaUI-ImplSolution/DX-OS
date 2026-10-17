import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useLocation, useParams } from "react-router-dom";
import { Badge, Section, Timeline, formatDateTime, statusLabel, statusTone } from "../../../shell";
import {
  addTicketAttachment,
  addTicketComment,
  assignTicket,
  cancelTicket,
  confirmTicket,
  getTicket,
  listRooms,
  listTechnicians,
  rejectTicket,
  removeTicketAttachment,
  reopenTicket,
  resolveTicket,
  startTicket,
  updateTicket,
  waitTicket,
} from "../api";
import { PriorityBadge, SlaClock, Stars, runAction } from "../components/common";
import { formatBytes } from "../labels";
import type { Room, TechnicianLoad, Ticket, TicketDetail as TicketDetailData } from "../types";

interface TicketDetailProps {
  userId: string;
  role: string;
  onError: (message: string) => void;
}

const MAX_REOPENS = 3;
const MAX_FILES = 5;
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const IMAGE_TYPES = ["image/jpeg", "image/png"];
const OTHER_ROOM = "other";
const OPEN_STATUSES = ["NEW", "ASSIGNED", "IN_PROGRESS", "WAITING_USER"];
const FINISHED_STATUSES = ["CLOSED", "REJECTED", "CANCELLED"];

type Run = (action: () => Promise<unknown>) => Promise<void>;

// Where the page returns to: the list it was opened from, with its filter or tab.
function backTarget(from: unknown, isStaff: boolean, isAdmin: boolean) {
  const path = typeof from === "string" && from.startsWith("/") ? from : null;
  const target = path ?? (isStaff && !isAdmin ? "/queue" : "/tickets");
  const base = target.split("?")[0];
  const label =
    base === "/queue"
      ? "hàng đợi xử lý"
      : base === "/"
        ? "tổng quan"
        : base === "/notifications"
          ? "thông báo"
          : "danh sách yêu cầu";
  return { target, label };
}

export default function TicketDetail({ userId, role, onError }: TicketDetailProps) {
  const { id } = useParams();
  const location = useLocation();
  const ticketId = Number(id);
  const [detail, setDetail] = useState<TicketDetailData | null>(null);
  const [busy, setBusy] = useState(false);
  const [unavailable, setUnavailable] = useState(false);
  const [editing, setEditing] = useState(false);

  const isStaff = role === "TECHNICIAN" || role === "ADMIN";
  const isAdmin = role === "ADMIN";

  const back = backTarget((location.state as { from?: string } | null)?.from, isStaff, isAdmin);

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
        <Link className="btn btn-link" to={back.target}>
          ← Về {back.label}
        </Link>
      </>
    );
  }
  if (!detail) return <Section step="…" title="Đang tải yêu cầu" children={null} />;
  const { ticket, comments, attachments, timeline } = detail;
  const isRequester = ticket.requesterId === userId;
  const canEdit = isRequester && OPEN_STATUSES.includes(ticket.status);
  const run: Run = (action) => runAction(action, setBusy, onError, load);
  return (
    <div className="ticket-layout">
      <div className="ticket-main">
        <Section
          step="🎫"
          title={`${ticket.code} · ${ticket.title}`}
          subtitle={`${ticket.categoryName} · gửi bởi ${ticket.requesterName} lúc ${formatDateTime(ticket.createdAt)}`}
        >
          {isRequester && ticket.status === "WAITING_USER" && (
            <div className="banner banner-warning">
              <p>
                Kỹ thuật viên đang chờ bạn bổ sung. Trả lời ở khung Trao đổi, hoặc bấm Chỉnh sửa để
                thêm ảnh. Khi bạn gửi, yêu cầu được chuyển lại cho kỹ thuật viên.
              </p>
            </div>
          )}
          {editing && canEdit ? (
            <EditForm
              ticket={ticket}
              busy={busy}
              run={run}
              onError={onError}
              onDone={() => setEditing(false)}
            />
          ) : (
            <>
              <TicketSummary ticket={ticket} />
              {canEdit && (
                <div className="form-actions">
                  <button className="btn" onClick={() => setEditing(true)}>
                    Chỉnh sửa
                  </button>
                </div>
              )}
            </>
          )}
        </Section>

        <Actions
          ticket={ticket}
          isStaff={isStaff}
          isAdmin={isAdmin}
          isRequester={isRequester}
          busy={busy}
          run={run}
          onError={onError}
        />

        <Attachments
          ticketId={ticketId}
          attachments={attachments}
          editable={editing && canEdit}
          busy={busy}
          run={run}
          onError={onError}
        />

        <Section step="🕓" title="Lịch sử xử lý" subtitle="Mỗi bước được ghi lại, không sửa hay xoá">
          <Timeline events={timeline} />
        </Section>

        {isRequester && ticket.status === "NEW" && (
          <div className="danger-zone">
            <button
              className="btn btn-danger"
              disabled={busy}
              onClick={() => {
                if (window.confirm("Huỷ yêu cầu này? Thao tác không thể hoàn tác.")) {
                  void run(() => cancelTicket(ticketId));
                }
              }}
            >
              Huỷ yêu cầu
            </button>
          </div>
        )}

        <Link className="btn btn-link" to={back.target}>
          ← Về {back.label}
        </Link>
      </div>

      <aside className="ticket-side">
        <ChatPanel
          comments={comments}
          isStaff={isStaff}
          finished={FINISHED_STATUSES.includes(ticket.status)}
          busy={busy}
          onSubmit={(body, internal) => run(() => addTicketComment(ticketId, body, internal))}
        />
      </aside>
    </div>
  );
}

function roomOf(ticket: Ticket): string | null {
  return ticket.roomCode ?? ticket.roomNote;
}

function TicketSummary({ ticket }: { ticket: Ticket }) {
  const room = roomOf(ticket);
  return (
    <>
      <p>
        <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>{" "}
        <PriorityBadge priority={ticket.priority} />
        {ticket.assigneeName && <span> · Người xử lý: {ticket.assigneeName}</span>}
        {ticket.reopenCount > 0 && <span> · Đã mở lại {ticket.reopenCount} lần</span>}
      </p>
      <p style={{ whiteSpace: "pre-line" }}>{ticket.description}</p>
      <table className="kv-table">
        <tbody>
          {room && (
            <tr>
              <th>Phòng</th>
              <td>{room}</td>
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
    </>
  );
}

// The requester changes the content of an open report here; attachments change in the section below.
function EditForm({
  ticket,
  busy,
  run,
  onError,
  onDone,
}: {
  ticket: Ticket;
  busy: boolean;
  run: Run;
  onError: (message: string) => void;
  onDone: () => void;
}) {
  const [title, setTitle] = useState(ticket.title);
  const [description, setDescription] = useState(ticket.description);
  const [roomId, setRoomId] = useState(
    ticket.roomId ? String(ticket.roomId) : ticket.roomNote ? OTHER_ROOM : "",
  );
  const [roomNote, setRoomNote] = useState(ticket.roomNote ?? "");
  const [rooms, setRooms] = useState<Room[]>([]);

  useEffect(() => {
    listRooms()
      .then(setRooms)
      .catch((err) => onError(err.message));
  }, [onError]);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    void run(() =>
      updateTicket(ticket.id, {
        title: title.trim(),
        description: description.trim(),
        roomId: roomId && roomId !== OTHER_ROOM ? Number(roomId) : undefined,
        roomNote: roomId === OTHER_ROOM && roomNote.trim() ? roomNote.trim() : undefined,
        deviceId: ticket.deviceId ?? undefined,
      }),
    ).then(onDone);
  };

  return (
    <form className="harvest-box" onSubmit={submit}>
      <label className="field-label">
        Tiêu đề
        <input value={title} maxLength={255} onChange={(e) => setTitle(e.target.value)} required />
      </label>
      <label className="field-label">
        Mô tả sự cố
        <textarea
          value={description}
          maxLength={4000}
          onChange={(e) => setDescription(e.target.value)}
          required
        />
      </label>
      <label className="field-label">
        Phòng
        <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
          <option value="">Không chọn</option>
          {rooms.map((room) => (
            <option key={room.id} value={room.id}>
              {room.code} · {room.name}
            </option>
          ))}
          <option value={OTHER_ROOM}>Phòng khác…</option>
        </select>
      </label>
      {roomId === OTHER_ROOM && (
        <label className="field-label">
          Tên hoặc vị trí phòng
          <input value={roomNote} maxLength={255} onChange={(e) => setRoomNote(e.target.value)} required />
        </label>
      )}
      <div className="form-actions">
        <button
          className="btn btn-primary"
          type="submit"
          disabled={busy || !title.trim() || !description.trim()}
        >
          Lưu thay đổi
        </button>
        <button className="btn" type="button" onClick={onDone}>
          Xong
        </button>
      </div>
    </form>
  );
}

function Attachments({
  ticketId,
  attachments,
  editable,
  busy,
  run,
  onError,
}: {
  ticketId: number;
  attachments: TicketDetailData["attachments"];
  editable: boolean;
  busy: boolean;
  run: Run;
  onError: (message: string) => void;
}) {
  if (attachments.length === 0 && !editable) return null;

  const pick = (file: File | undefined) => {
    if (!file) return;
    if (!IMAGE_TYPES.includes(file.type) || file.size > MAX_FILE_BYTES) {
      onError("Chỉ nhận ảnh JPEG hoặc PNG, tối đa 5 MB.");
      return;
    }
    if (attachments.length >= MAX_FILES) {
      onError(`Chỉ đính kèm tối đa ${MAX_FILES} ảnh.`);
      return;
    }
    void run(() => addTicketAttachment(ticketId, file));
  };

  return (
    <Section step="🖼️" title="Ảnh đính kèm">
      {attachments.length === 0 && <p className="empty-hint">Chưa có ảnh nào.</p>}
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
            {editable && (
              <button
                className="btn btn-danger-outline"
                disabled={busy}
                onClick={() => run(() => removeTicketAttachment(ticketId, attachment.id))}
              >
                Xoá
              </button>
            )}
          </li>
        ))}
      </ul>
      {editable && (
        <label className="field-label">
          Thêm ảnh (JPEG hoặc PNG, tối đa 5 MB)
          <input
            type="file"
            accept="image/jpeg,image/png"
            disabled={busy}
            onChange={(e) => {
              const file = e.target.files?.[0];
              e.target.value = "";
              pick(file);
            }}
          />
        </label>
      )}
    </Section>
  );
}

// The conversation is a panel of its own that scrolls, so a long exchange never pushes the report
// out of view.
function ChatPanel({
  comments,
  isStaff,
  finished,
  busy,
  onSubmit,
}: {
  comments: TicketDetailData["comments"];
  isStaff: boolean;
  finished: boolean;
  busy: boolean;
  onSubmit: (body: string, internal: boolean) => Promise<void>;
}) {
  const [body, setBody] = useState("");
  const [internal, setInternal] = useState(false);
  const messages = useRef<HTMLUListElement>(null);

  useEffect(() => {
    const list = messages.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [comments.length]);

  const send = () => {
    if (!body.trim()) return;
    void onSubmit(body.trim(), internal).then(() => setBody(""));
  };
  const submit = (event: FormEvent) => {
    event.preventDefault();
    send();
  };

  return (
    <section className="section chat-panel">
      <header className="section-header">
        <span className="section-step">💬</span>
        <div>
          <h2>Trao đổi</h2>
        </div>
      </header>
      <ul className="chat-messages comment-list" ref={messages}>
        {comments.length === 0 && <li className="empty-hint">Chưa có trao đổi nào.</li>}
        {comments.map((comment) => (
          <li key={comment.id} className={comment.internal ? "comment comment-internal" : "comment"}>
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
      {finished ? (
        <p className="empty-hint chat-closed">Yêu cầu đã kết thúc, không trao đổi thêm.</p>
      ) : (
        <form className="chat-composer" onSubmit={submit}>
          <textarea
            value={body}
            maxLength={4000}
            placeholder="Nhập tin nhắn (Ctrl + Enter để gửi)"
            onChange={(e) => setBody(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === "Enter" && (e.ctrlKey || e.metaKey)) {
                e.preventDefault();
                send();
              }
            }}
            required
          />
          <div className="chat-composer-bar">
            {isStaff ? (
              <label className="check-label">
                <input
                  type="checkbox"
                  checked={internal}
                  onChange={(e) => setInternal(e.target.checked)}
                />{" "}
                Ghi chú nội bộ
              </label>
            ) : (
              <span />
            )}
            <button className="btn btn-primary" type="submit" disabled={busy || !body.trim()}>
              Gửi
            </button>
          </div>
        </form>
      )}
    </section>
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
  ticket: Ticket;
  isStaff: boolean;
  isAdmin: boolean;
  isRequester: boolean;
  busy: boolean;
  run: Run;
  onError: (message: string) => void;
}) {
  const [dialog, setDialog] = useState<Dialog>(null);
  const [text, setText] = useState("");
  const [rootCause, setRootCause] = useState("");
  const [rating, setRating] = useState(5);
  const [technicians, setTechnicians] = useState<TechnicianLoad[]>([]);
  const [assignee, setAssignee] = useState("");

  const canAssign = isAdmin && ["NEW", "ASSIGNED", "IN_PROGRESS"].includes(ticket.status);
  useEffect(() => {
    if (!canAssign) return;
    listTechnicians()
      .then(setTechnicians)
      .catch((err) => onError(err.message));
  }, [canAssign, onError]);

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
    (isRequester && ticket.status === "RESOLVED");
  if (!canAct) return null;

  const canReopen = ticket.reopenCount < MAX_REOPENS;
  const candidates = technicians.filter((technician) => technician.userId !== ticket.assigneeId);

  return (
    <Section step="⚙️" title="Thao tác">
      <div className="form-actions">
        {isStaff && ticket.status === "NEW" && (
          <button className="btn btn-primary" disabled={busy} onClick={() => run(() => assignTicket(id))}>
            Nhận việc
          </button>
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

      {canAssign && (
        <div className="inline-form">
          <select value={assignee} onChange={(e) => setAssignee(e.target.value)}>
            <option value="">{ticket.assigneeName ? "Giao lại cho…" : "Giao cho kỹ thuật viên…"}</option>
            {candidates.map((technician) => (
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
                ).then(() => setAssignee(""));
            }}
          >
            {ticket.assigneeName ? "Giao lại" : "Phân công"}
          </button>
        </div>
      )}

      {dialog === "wait" && (
        <form className="inline-form inline-form-wide" onSubmit={submit(() => waitTicket(id, text.trim()))}>
          <input
            placeholder="Cần bổ sung gì?"
            value={text}
            maxLength={1000}
            onChange={(e) => setText(e.target.value)}
            required
          />
          <FormButtons busy={busy} onCancel={close} label="Gửi" />
        </form>
      )}
      {dialog === "resolve" && (
        <form
          className="inline-form inline-form-wide"
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
        <form className="inline-form inline-form-wide" onSubmit={submit(() => rejectTicket(id, text.trim()))}>
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
        <form
          className="inline-form inline-form-wide"
          onSubmit={submit(() => confirmTicket(id, rating, text.trim()))}
        >
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
        <form className="inline-form inline-form-wide" onSubmit={submit(() => reopenTicket(id, text.trim()))}>
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
