import { useCallback, useEffect, useMemo, useState, type FormEvent } from "react";
import { Badge, Pagination, Section, formatDateTime } from "../../../shell";
import type { Page } from "../../../shell";
import {
  approveBooking,
  cancelBooking,
  createBooking,
  getRoomSchedule,
  listBookings,
  listRooms,
  rejectBooking,
} from "../api";
import { runAction, toIsoDate, toLocalInput } from "../components/common";
import { BOOKING_STATUS_LABEL, ROOM_STATUS_LABEL, ROOM_TYPE_LABEL } from "../labels";
import type { Booking, Room, Schedule, ScheduleEntry } from "../types";

interface BookingsProps {
  userId: string;
  role: string;
  onError: (message: string) => void;
}

const FIRST_HOUR = 7;
const LAST_HOUR = 21;
const HOURS = Array.from({ length: LAST_HOUR - FIRST_HOUR + 1 }, (_, i) => FIRST_HOUR + i);
const DAY_LABEL = ["Thứ hai", "Thứ ba", "Thứ tư", "Thứ năm", "Thứ sáu", "Thứ bảy", "Chủ nhật"];
const PAGE_SIZE = 10;
const STEP_SECONDS = 30 * 60;

function mondayOf(date: Date): Date {
  const monday = new Date(date.getFullYear(), date.getMonth(), date.getDate());
  const offset = (monday.getDay() + 6) % 7;
  monday.setDate(monday.getDate() - offset);
  return monday;
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

export default function Bookings({ userId, role, onError }: BookingsProps) {
  const canBook = role !== "STUDENT";
  const isStaff = role === "TECHNICIAN" || role === "ADMIN";

  const [rooms, setRooms] = useState<Room[]>([]);
  const [roomId, setRoomId] = useState<number | null>(null);
  const [weekStart, setWeekStart] = useState(() => mondayOf(new Date()));
  const [schedule, setSchedule] = useState<Schedule | null>(null);
  const [startsAt, setStartsAt] = useState("");
  const [endsAt, setEndsAt] = useState("");
  const [listVersion, setListVersion] = useState(0);

  useEffect(() => {
    listRooms()
      .then((list) => {
        setRooms(list);
        setRoomId((current) => current ?? list[0]?.id ?? null);
      })
      .catch((err) => onError(err.message));
  }, [onError]);

  const loadSchedule = useCallback(() => {
    if (roomId === null) return;
    getRoomSchedule(roomId, toIsoDate(weekStart), toIsoDate(addDays(weekStart, 6)))
      .then(setSchedule)
      .catch((err) => onError(err.message));
  }, [roomId, weekStart, onError]);

  useEffect(loadSchedule, [loadSchedule]);

  const room = rooms.find((r) => r.id === roomId) ?? null;

  const refresh = () => {
    loadSchedule();
    setListVersion((n) => n + 1);
  };

  const pickSlot = (day: Date, hour: number) => {
    if (!canBook) return;
    const start = new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour);
    setStartsAt(toLocalInput(start));
    setEndsAt(toLocalInput(new Date(start.getTime() + 60 * 60 * 1000)));
  };

  return (
    <>
      <Section
        step="🗓️"
        title="Lịch phòng"
        subtitle={canBook ? "Chọn một ô trống trên lịch để điền sẵn giờ đặt phòng" : "Xem lịch đặt của phòng máy và phòng thực hành"}
      >
        <div className="filter-bar">
          <select value={roomId ?? ""} onChange={(e) => setRoomId(Number(e.target.value))}>
            {rooms.map((r) => (
              <option key={r.id} value={r.id}>
                {r.code} · {r.name} ({ROOM_TYPE_LABEL[r.roomType]}, {r.capacity} chỗ)
              </option>
            ))}
          </select>
          <button className="btn" onClick={() => setWeekStart(addDays(weekStart, -7))}>
            ‹ Tuần trước
          </button>
          <button className="btn" onClick={() => setWeekStart(mondayOf(new Date()))}>
            Tuần này
          </button>
          <button className="btn" onClick={() => setWeekStart(addDays(weekStart, 7))}>
            Tuần sau ›
          </button>
          <span>
            {weekStart.toLocaleDateString("vi-VN")} – {addDays(weekStart, 6).toLocaleDateString("vi-VN")}
          </span>
          {room && room.status === "MAINTENANCE" && (
            <Badge tone={ROOM_STATUS_LABEL[room.status].tone}>{ROOM_STATUS_LABEL[room.status].label}</Badge>
          )}
        </div>
        <WeekGrid
          weekStart={weekStart}
          entries={schedule?.entries ?? []}
          selectable={canBook && room?.status !== "MAINTENANCE"}
          selectedStart={startsAt}
          onPick={pickSlot}
        />
      </Section>

      {canBook && (
        <BookingForm
          rooms={rooms}
          roomId={roomId}
          onRoomChange={setRoomId}
          startsAt={startsAt}
          endsAt={endsAt}
          onStartsAt={setStartsAt}
          onEndsAt={setEndsAt}
          onError={onError}
          onCreated={refresh}
        />
      )}

      {canBook && (
        <BookingList
          userId={userId}
          isStaff={isStaff}
          version={listVersion}
          onError={onError}
          onChanged={refresh}
        />
      )}
    </>
  );
}

function WeekGrid({
  weekStart,
  entries,
  selectable,
  selectedStart,
  onPick,
}: {
  weekStart: Date;
  entries: ScheduleEntry[];
  selectable: boolean;
  selectedStart: string;
  onPick: (day: Date, hour: number) => void;
}) {
  const days = useMemo(() => Array.from({ length: 7 }, (_, i) => addDays(weekStart, i)), [weekStart]);

  const overlapping = (day: Date, hour: number) => {
    const slotStart = new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour).getTime();
    const slotEnd = slotStart + 60 * 60 * 1000;
    return entries.filter(
      (entry) =>
        new Date(entry.startsAt).getTime() < slotEnd && new Date(entry.endsAt).getTime() > slotStart,
    );
  };

  return (
    <div className="calendar-scroll">
      <table className="calendar">
        <thead>
          <tr>
            <th />
            {days.map((day, index) => (
              <th key={index}>
                {DAY_LABEL[index]}
                <div className="cell-sub">{day.toLocaleDateString("vi-VN", { day: "2-digit", month: "2-digit" })}</div>
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {HOURS.map((hour) => (
            <tr key={hour}>
              <th>{String(hour).padStart(2, "0")}:00</th>
              {days.map((day, index) => {
                const booked = overlapping(day, hour);
                const slot = toLocalInput(new Date(day.getFullYear(), day.getMonth(), day.getDate(), hour));
                const selected = selectedStart === slot;
                const className =
                  booked.length > 0
                    ? "cal-cell cal-booked"
                    : selected
                      ? "cal-cell cal-selected"
                      : selectable
                        ? "cal-cell cal-free"
                        : "cal-cell";
                return (
                  <td
                    key={index}
                    className={className}
                    onClick={() => booked.length === 0 && selectable && onPick(day, hour)}
                  >
                    {booked.map((entry) => (
                      <div key={entry.bookingId} className={`cal-entry cal-${entry.status.toLowerCase()}`}>
                        <strong>{entry.title}</strong>
                        <small>{BOOKING_STATUS_LABEL[entry.status]?.label ?? entry.status}</small>
                      </div>
                    ))}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function BookingForm({
  rooms,
  roomId,
  onRoomChange,
  startsAt,
  endsAt,
  onStartsAt,
  onEndsAt,
  onError,
  onCreated,
}: {
  rooms: Room[];
  roomId: number | null;
  onRoomChange: (id: number) => void;
  startsAt: string;
  endsAt: string;
  onStartsAt: (value: string) => void;
  onEndsAt: (value: string) => void;
  onError: (message: string) => void;
  onCreated: () => void;
}) {
  const [title, setTitle] = useState("");
  const [attendees, setAttendees] = useState(1);
  const [submitting, setSubmitting] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    if (roomId === null) return;
    setSubmitting(true);
    createBooking({
      roomId,
      title: title.trim(),
      attendees,
      startsAt: new Date(startsAt).toISOString(),
      endsAt: new Date(endsAt).toISOString(),
    })
      .then(() => {
        setTitle("");
        onCreated();
      })
      .catch((err) => onError(err.message))
      .finally(() => setSubmitting(false));
  };

  return (
    <Section
      step="📝"
      title="Đặt phòng"
      subtitle="Khung giờ theo bước 30 phút, tối đa 4 giờ mỗi lần và đặt trước tối đa 30 ngày"
    >
      <form className="harvest-box" onSubmit={submit}>
        <div className="filter-bar">
          <label className="field-label">
            Phòng
            <select value={roomId ?? ""} onChange={(e) => onRoomChange(Number(e.target.value))} required>
              {rooms.map((room) => (
                <option key={room.id} value={room.id} disabled={room.status === "MAINTENANCE"}>
                  {room.code} · {room.name}
                  {room.status === "MAINTENANCE" ? " (đang bảo trì)" : ""}
                </option>
              ))}
            </select>
          </label>
          <label className="field-label">
            Mục đích sử dụng
            <input
              value={title}
              maxLength={255}
              onChange={(e) => setTitle(e.target.value)}
              placeholder="Ví dụ: Thực hành Lập trình Web, lớp CNTT1"
              required
            />
          </label>
          <label className="field-label">
            Số người
            <input
              type="number"
              min={1}
              value={attendees}
              onChange={(e) => setAttendees(Number(e.target.value))}
              required
            />
          </label>
        </div>
        <div className="filter-bar">
          <label className="field-label">
            Bắt đầu
            <input
              type="datetime-local"
              step={STEP_SECONDS}
              value={startsAt}
              onChange={(e) => onStartsAt(e.target.value)}
              required
            />
          </label>
          <label className="field-label">
            Kết thúc
            <input
              type="datetime-local"
              step={STEP_SECONDS}
              value={endsAt}
              onChange={(e) => onEndsAt(e.target.value)}
              required
            />
          </label>
        </div>
        <div className="form-actions">
          <button
            className="btn btn-primary"
            type="submit"
            disabled={submitting || roomId === null || !title.trim() || !startsAt || !endsAt}
          >
            Gửi yêu cầu đặt phòng
          </button>
        </div>
      </form>
    </Section>
  );
}

function BookingList({
  userId,
  isStaff,
  version,
  onError,
  onChanged,
}: {
  userId: string;
  isStaff: boolean;
  version: number;
  onError: (message: string) => void;
  onChanged: () => void;
}) {
  const [result, setResult] = useState<Page<Booking> | null>(null);
  const [status, setStatus] = useState("");
  const [page, setPage] = useState(0);
  const [busy, setBusy] = useState(false);
  const [rejecting, setRejecting] = useState<number | null>(null);
  const [reason, setReason] = useState("");

  const load = useCallback(() => {
    listBookings({ status: status || undefined, page, size: PAGE_SIZE })
      .then(setResult)
      .catch((err) => onError(err.message));
  }, [status, page, onError]);

  useEffect(load, [load, version]);

  const act = (action: () => Promise<unknown>) => runAction(action, setBusy, onError, onChanged);

  return (
    <Section
      step="📋"
      title={isStaff ? "Yêu cầu đặt phòng" : "Lịch đặt của tôi"}
      subtitle={isStaff ? "Kỹ thuật viên duyệt hoặc từ chối yêu cầu đặt phòng" : undefined}
    >
      <div className="filter-bar">
        <select
          value={status}
          onChange={(e) => {
            setStatus(e.target.value);
            setPage(0);
          }}
        >
          <option value="">Mọi trạng thái</option>
          {Object.entries(BOOKING_STATUS_LABEL).map(([key, value]) => (
            <option key={key} value={key}>
              {value.label}
            </option>
          ))}
        </select>
      </div>

      {result && result.items.length === 0 && <p className="empty-hint">Chưa có yêu cầu đặt phòng nào.</p>}

      {result && result.items.length > 0 && (
        <table className="data-table">
          <thead>
            <tr>
              <th>Phòng</th>
              <th>Mục đích</th>
              <th>Người đặt</th>
              <th>Thời gian</th>
              <th>Trạng thái</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {result.items.map((booking) => {
              const definition = BOOKING_STATUS_LABEL[booking.status] ?? {
                label: booking.status,
                tone: "neutral" as const,
              };
              const own = booking.requesterId === userId;
              const canDecide = isStaff && booking.status === "PENDING";
              const canCancel =
                ["PENDING", "APPROVED"].includes(booking.status) && (own || isStaff);
              return (
                <tr key={booking.id}>
                  <td>{booking.roomCode}</td>
                  <td>
                    {booking.title}
                    <div className="cell-sub">{booking.attendees} người</div>
                    {booking.rejectionReason && (
                      <div className="cell-sub">Lý do từ chối: {booking.rejectionReason}</div>
                    )}
                  </td>
                  <td>{booking.requesterName}</td>
                  <td>
                    {formatDateTime(booking.startsAt)}
                    <div className="cell-sub">đến {formatDateTime(booking.endsAt)}</div>
                  </td>
                  <td>
                    <Badge tone={definition.tone}>{definition.label}</Badge>
                  </td>
                  <td>
                    {rejecting === booking.id ? (
                      <form
                        className="inline-form"
                        onSubmit={(e) => {
                          e.preventDefault();
                          void act(() => rejectBooking(booking.id, reason.trim())).then(() => {
                            setRejecting(null);
                            setReason("");
                          });
                        }}
                      >
                        <input
                          placeholder="Lý do từ chối"
                          value={reason}
                          maxLength={1000}
                          onChange={(e) => setReason(e.target.value)}
                          required
                        />
                        <button type="submit" className="btn btn-secondary" disabled={busy}>
                          Từ chối
                        </button>
                        <button type="button" className="btn" onClick={() => setRejecting(null)}>
                          Huỷ
                        </button>
                      </form>
                    ) : (
                      <div className="form-actions">
                        {canDecide && (
                          <>
                            <button
                              className="btn btn-primary"
                              disabled={busy}
                              onClick={() => act(() => approveBooking(booking.id))}
                            >
                              Duyệt
                            </button>
                            <button className="btn btn-secondary" disabled={busy} onClick={() => setRejecting(booking.id)}>
                              Từ chối
                            </button>
                          </>
                        )}
                        {canCancel && (
                          <button className="btn" disabled={busy} onClick={() => act(() => cancelBooking(booking.id))}>
                            Huỷ lịch
                          </button>
                        )}
                      </div>
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}

      {result && <Pagination page={result} onChange={setPage} noun="lịch đặt" />}
    </Section>
  );
}
