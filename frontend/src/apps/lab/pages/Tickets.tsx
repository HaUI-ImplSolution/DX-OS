// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, useSearchParams } from "react-router-dom";
import { Badge, Pagination, Section, formatDateTime, statusLabel, statusTone } from "../../../shell";
import type { Page } from "../../../shell";
import { createTicket, listCategories, listDevices, listRooms, listTickets, suggestTicket } from "../api";
import { PriorityBadge } from "../components/common";
import RelatedArticles from "../components/RelatedArticles";
import { PRIORITIES, PRIORITY_LABEL, TICKET_STATUSES } from "../labels";
import type {
  Device,
  Room,
  Ticket,
  TicketCategory,
  TicketPriority,
  TicketSuggestion,
} from "../types";

interface TicketsProps {
  isStaff: boolean;
  onError: (message: string) => void;
}

const PAGE_SIZE = 10;
const MAX_FILES = 5;
const MAX_FILE_BYTES = 5 * 1024 * 1024;
const ALLOWED_TYPES = ["image/jpeg", "image/png"];
const OTHER_ROOM = "other";
const SUGGEST_DELAY_MS = 700;
const MIN_SUGGEST_LENGTH = 10;

export default function Tickets({ isStaff, onError }: TicketsProps) {
  const [reporting, setReporting] = useState(false);
  const [reloadKey, setReloadKey] = useState(0);

  return (
    <>
      <Section
        step="🎫"
        title="Yêu cầu hỗ trợ"
        subtitle="Báo sự cố về máy tính, mạng, thiết bị và phòng học; theo dõi tiến độ xử lý"
      >
        {reporting ? (
          <ReportForm
            onError={onError}
            onCancel={() => setReporting(false)}
            onCreated={() => {
              setReporting(false);
              setReloadKey((n) => n + 1);
            }}
          />
        ) : (
          <div className="form-actions">
            <button className="btn btn-primary" onClick={() => setReporting(true)}>
              Báo sự cố mới
            </button>
          </div>
        )}
      </Section>
      <TicketList isStaff={isStaff} onError={onError} reloadKey={reloadKey} />
    </>
  );
}

// Filters live in the URL so a filtered list can be bookmarked and survives a reload.
function TicketList({
  isStaff,
  onError,
  reloadKey,
}: {
  isStaff: boolean;
  onError: (message: string) => void;
  reloadKey: number;
}) {
  const [params, setParams] = useSearchParams();
  const location = useLocation();
  const [result, setResult] = useState<Page<Ticket> | null>(null);
  const [categories, setCategories] = useState<TicketCategory[]>([]);

  const status = params.get("status") ?? "";
  const categoryCode = params.get("category") ?? "";
  const priority = params.get("priority") ?? "";
  const overdue = params.get("overdue") === "true";
  const unassigned = params.get("unassigned") === "true";
  const page = Number(params.get("page") ?? 0);

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== "page") next.delete("page");
    setParams(next, { replace: true });
  };

  useEffect(() => {
    listCategories()
      .then(setCategories)
      .catch((err) => onError(err.message));
  }, [onError]);

  const load = useCallback(() => {
    listTickets({
      status: status || undefined,
      categoryCode: categoryCode || undefined,
      priority: priority || undefined,
      overdue: overdue || undefined,
      unassigned: unassigned || undefined,
      page,
      size: PAGE_SIZE,
    })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [status, categoryCode, priority, overdue, unassigned, page, onError]);

  useEffect(load, [load, reloadKey]);

  return (
    <Section step="📋" title={isStaff ? "Tất cả yêu cầu" : "Yêu cầu của tôi"}>
      <div className="filter-bar">
        <select value={status} onChange={(e) => setFilter("status", e.target.value)}>
          <option value="">Mọi trạng thái</option>
          {TICKET_STATUSES.map((s) => (
            <option key={s} value={s}>
              {statusLabel(s)}
            </option>
          ))}
        </select>
        <select value={categoryCode} onChange={(e) => setFilter("category", e.target.value)}>
          <option value="">Mọi loại sự cố</option>
          {categories.map((category) => (
            <option key={category.code} value={category.code}>
              {category.name}
            </option>
          ))}
        </select>
        <select value={priority} onChange={(e) => setFilter("priority", e.target.value)}>
          <option value="">Mọi mức ưu tiên</option>
          {PRIORITIES.map((p) => (
            <option key={p} value={p}>
              {PRIORITY_LABEL[p]}
            </option>
          ))}
        </select>
        <label className="field-label check-label">
          <input
            type="checkbox"
            checked={overdue}
            onChange={(e) => setFilter("overdue", e.target.checked ? "true" : "")}
          />{" "}
          Quá hạn
        </label>
        {isStaff && (
          <label className="field-label check-label">
            <input
              type="checkbox"
              checked={unassigned}
              onChange={(e) => setFilter("unassigned", e.target.checked ? "true" : "")}
            />{" "}
            Chưa có người nhận
          </label>
        )}
      </div>

      {result && result.items.length === 0 && <p className="empty-hint">Không có yêu cầu nào khớp bộ lọc.</p>}

      {result && result.items.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Mã</th>
              <th>Tiêu đề</th>
              <th>Loại</th>
              <th>Ưu tiên</th>
              <th>Trạng thái</th>
              <th>Người xử lý</th>
              <th>Hạn xử lý</th>
            </tr>
          </thead>
          <tbody>
            {result.items.map((ticket) => (
              <tr key={ticket.id}>
                <td>
                  <Link
                    to={`/tickets/${ticket.id}`}
                    state={{ from: `${location.pathname}${location.search}` }}
                  >
                    {ticket.code}
                  </Link>
                </td>
                <td>
                  {ticket.title}
                  {isStaff && <div className="cell-sub">{ticket.requesterName}</div>}
                </td>
                <td>{ticket.categoryName}</td>
                <td>
                  <PriorityBadge priority={ticket.priority} />
                </td>
                <td>
                  <Badge tone={statusTone(ticket.status)}>{statusLabel(ticket.status)}</Badge>
                </td>
                <td>{ticket.assigneeName ?? "—"}</td>
                <td>
                  {ticket.resolutionDueAt ? formatDateTime(ticket.resolutionDueAt) : "—"}
                  {ticket.overdue && <Badge tone="danger">Quá hạn</Badge>}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {result && <Pagination page={result} onChange={(p) => setFilter("page", String(p))} noun="yêu cầu" />}
    </Section>
  );
}

function ReportForm({
  onError,
  onCancel,
  onCreated,
}: {
  onError: (message: string) => void;
  onCancel: () => void;
  onCreated: () => void;
}) {
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [description, setDescription] = useState("");
  const [categoryCode, setCategoryCode] = useState("");
  const [priority, setPriority] = useState<TicketPriority | "">("");
  const [roomId, setRoomId] = useState("");
  const [roomNote, setRoomNote] = useState("");
  const [deviceId, setDeviceId] = useState("");
  const [files, setFiles] = useState<File[]>([]);
  const [categories, setCategories] = useState<TicketCategory[]>([]);
  const [rooms, setRooms] = useState<Room[]>([]);
  const [devices, setDevices] = useState<Device[]>([]);
  const [suggestion, setSuggestion] = useState<TicketSuggestion | null>(null);
  const [suggesting, setSuggesting] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const lastSuggested = useRef("");

  useEffect(() => {
    Promise.all([listCategories(), listRooms(), listDevices()])
      .then(([c, r, d]) => {
        setCategories(c);
        setRooms(r);
        setDevices(d.filter((device) => device.status !== "RETIRED"));
      })
      .catch((err) => onError(err.message));
  }, [onError]);

  // Suggest a category, priority and related articles while the user types the description.
  useEffect(() => {
    const text = `${title}\n${description}`.trim();
    if (description.trim().length < MIN_SUGGEST_LENGTH || text === lastSuggested.current) return;
    const timer = window.setTimeout(() => {
      lastSuggested.current = text;
      setSuggesting(true);
      suggestTicket(title.trim(), description.trim())
        .then(setSuggestion)
        .catch(() => setSuggestion(null))
        .finally(() => setSuggesting(false));
    }, SUGGEST_DELAY_MS);
    return () => window.clearTimeout(timer);
  }, [title, description]);

  const pickFiles = (selected: FileList | null) => {
    const list = Array.from(selected ?? []);
    if (list.length > MAX_FILES) {
      onError(`Chỉ đính kèm tối đa ${MAX_FILES} tệp.`);
      return;
    }
    const invalid = list.find((f) => !ALLOWED_TYPES.includes(f.type) || f.size > MAX_FILE_BYTES);
    if (invalid) {
      onError(`Ảnh "${invalid.name}" không hợp lệ: chỉ nhận JPEG hoặc PNG, tối đa 5 MB.`);
      return;
    }
    setFiles(list);
  };

  const applySuggestion = () => {
    if (!suggestion) return;
    setCategoryCode(suggestion.categoryCode);
    setPriority(suggestion.priority);
  };

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSubmitting(true);
    createTicket(
      {
        title: title.trim(),
        description: description.trim(),
        categoryCode,
        priority: priority || undefined,
        roomId: roomId && roomId !== OTHER_ROOM ? Number(roomId) : undefined,
        roomNote: roomId === OTHER_ROOM && roomNote.trim() ? roomNote.trim() : undefined,
        deviceId: deviceId ? Number(deviceId) : undefined,
      },
      files,
    )
      .then((detail) => {
        onCreated();
        navigate(`/tickets/${detail.ticket.id}`);
      })
      .catch((err) => onError(err.message))
      .finally(() => setSubmitting(false));
  };

  const roomDevices =
    roomId && roomId !== OTHER_ROOM ? devices.filter((d) => d.roomId === Number(roomId)) : devices;

  return (
    <form className="harvest-box" onSubmit={submit}>
      <label className="field-label">
        Tiêu đề
        <input
          value={title}
          maxLength={255}
          onChange={(e) => setTitle(e.target.value)}
          placeholder="Ví dụ: Máy chiếu phòng A2 không lên hình"
          required
        />
      </label>
      <label className="field-label">
        Mô tả sự cố
        <textarea
          value={description}
          maxLength={4000}
          onChange={(e) => setDescription(e.target.value)}
          placeholder="Mô tả hiện tượng, thời điểm xảy ra và những gì bạn đã thử"
          required
        />
      </label>

      {suggesting && <p className="empty-hint">Đang gợi ý loại sự cố…</p>}
      {suggestion && !suggesting && (
        <div className="suggestion-box">
          <div>
            <strong>Gợi ý:</strong> {suggestion.categoryName}
            <button type="button" className="btn btn-link" onClick={applySuggestion}>
              Dùng gợi ý này
            </button>
          </div>
          {suggestion.relatedKnowledge.length > 0 && (
            <RelatedArticles passages={suggestion.relatedKnowledge} />
          )}
        </div>
      )}

      <div className="filter-bar">
        <label className="field-label">
          Loại sự cố
          <select value={categoryCode} onChange={(e) => setCategoryCode(e.target.value)} required>
            <option value="">Chọn loại</option>
            {categories.map((category) => (
              <option key={category.code} value={category.code}>
                {category.name}
              </option>
            ))}
          </select>
        </label>
        <label className="field-label">
          Mức ưu tiên
          <select value={priority} onChange={(e) => setPriority(e.target.value as TicketPriority | "")}>
            <option value="">Theo loại sự cố</option>
            {PRIORITIES.map((p) => (
              <option key={p} value={p}>
                {PRIORITY_LABEL[p]}
              </option>
            ))}
          </select>
        </label>
        <label className="field-label">
          Phòng (nếu có)
          <select
            value={roomId}
            onChange={(e) => {
              setRoomId(e.target.value);
              setDeviceId("");
            }}
          >
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
            <input
              value={roomNote}
              maxLength={255}
              onChange={(e) => setRoomNote(e.target.value)}
              placeholder="Ví dụ: Phòng họp tầng 3 nhà A1"
              required
            />
          </label>
        )}
        <label className="field-label">
          Thiết bị (nếu có)
          <select value={deviceId} onChange={(e) => setDeviceId(e.target.value)}>
            <option value="">Không chọn</option>
            {roomDevices.map((device) => (
              <option key={device.id} value={device.id}>
                {device.assetCode} · {device.deviceType}
              </option>
            ))}
          </select>
        </label>
      </div>

      <label className="field-label">
        Ảnh minh hoạ (tối đa {MAX_FILES} ảnh JPEG hoặc PNG, mỗi ảnh 5 MB)
        <input
          type="file"
          multiple
          accept="image/jpeg,image/png"
          onChange={(e) => pickFiles(e.target.files)}
        />
      </label>

      <div className="form-actions">
        <button
          className="btn btn-primary"
          type="submit"
          disabled={submitting || !title.trim() || !description.trim() || !categoryCode}
        >
          {submitting ? "Đang gửi…" : "Gửi yêu cầu"}
        </button>
        <button className="btn" type="button" onClick={onCancel}>
          Huỷ
        </button>
      </div>
    </form>
  );
}
