// SPDX-License-Identifier: Apache-2.0
import { useCallback, useEffect, useState, type FormEvent } from "react";
import { Badge, Section, formatDate, formatDateTime } from "../../../shell";
import {
  changeRoomStatus,
  completeMaintenance,
  createDevice,
  createRoom,
  listDevices,
  listLoans,
  listMaintenance,
  listRooms,
  retireDevice,
  startMaintenance,
  updateDevice,
  updateRoom,
} from "../api";
import { runAction } from "../components/common";
import {
  DEVICE_STATUS_LABEL,
  LOAN_STATUS_LABEL,
  MAINTENANCE_KIND_LABEL,
  ROOM_STATUS_LABEL,
  ROOM_TYPE_LABEL,
} from "../labels";
import type {
  Booking,
  Device,
  DeviceRequest,
  DeviceStatus,
  Loan,
  Maintenance,
  MaintenanceKind,
  Room,
  RoomRequest,
  RoomType,
} from "../types";

interface AssetsProps {
  onError: (message: string) => void;
}

export default function Assets({ onError }: AssetsProps) {
  const [rooms, setRooms] = useState<Room[]>([]);

  const loadRooms = useCallback(() => {
    listRooms()
      .then(setRooms)
      .catch((err) => onError(err.message));
  }, [onError]);

  useEffect(loadRooms, [loadRooms]);

  return (
    <>
      <Rooms rooms={rooms} onChanged={loadRooms} onError={onError} />
      <Devices rooms={rooms} onError={onError} />
    </>
  );
}

function Rooms({
  rooms,
  onChanged,
  onError,
}: {
  rooms: Room[];
  onChanged: () => void;
  onError: (message: string) => void;
}) {
  const [editing, setEditing] = useState<Room | "new" | null>(null);
  const [busy, setBusy] = useState(false);
  const [affected, setAffected] = useState<{ room: Room; bookings: Booking[] } | null>(null);

  const toggle = (room: Room) => {
    const next = room.status === "AVAILABLE" ? "MAINTENANCE" : "AVAILABLE";
    setBusy(true);
    changeRoomStatus(room.id, next)
      .then((change) => {
        setAffected(change.affectedBookings.length > 0 ? { room: change.room, bookings: change.affectedBookings } : null);
        onChanged();
      })
      .catch((err) => onError(err.message))
      .finally(() => setBusy(false));
  };

  return (
    <Section
      step="🏫"
      title="Phòng máy và phòng thực hành"
      subtitle="Chuyển phòng sang bảo trì thì phòng không nhận thêm lịch đặt"
    >
      <div className="form-actions">
        <button className="btn btn-primary" onClick={() => setEditing("new")}>
          Thêm phòng
        </button>
      </div>

      {editing && (
        <RoomForm
          room={editing === "new" ? null : editing}
          onError={onError}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            onChanged();
          }}
        />
      )}

      {affected && (
        <div className="banner banner-warning banner-dismissible">
          <span>
            Phòng {affected.room.code} đang bảo trì nhưng còn {affected.bookings.length} lịch đặt cần xử lý:{" "}
            {affected.bookings.map((b) => `${b.title} (${formatDateTime(b.startsAt)})`).join("; ")}
          </span>
          <button onClick={() => setAffected(null)}>✕</button>
        </div>
      )}

      <table className="data-table">
        <thead>
          <tr>
            <th>Mã</th>
            <th>Tên</th>
            <th>Loại</th>
            <th>Sức chứa</th>
            <th>Trạng thái</th>
            <th />
          </tr>
        </thead>
        <tbody>
          {rooms.map((room) => (
            <tr key={room.id}>
              <td>
                {room.code}
                <div className="cell-sub">{room.building}</div>
              </td>
              <td>{room.name}</td>
              <td>{ROOM_TYPE_LABEL[room.roomType]}</td>
              <td>{room.capacity}</td>
              <td>
                <Badge tone={ROOM_STATUS_LABEL[room.status].tone}>{ROOM_STATUS_LABEL[room.status].label}</Badge>
              </td>
              <td>
                <div className="form-actions">
                  <button className="btn" disabled={busy} onClick={() => setEditing(room)}>
                    Sửa
                  </button>
                  <button className="btn" disabled={busy} onClick={() => toggle(room)}>
                    {room.status === "AVAILABLE" ? "Chuyển sang bảo trì" : "Mở lại phòng"}
                  </button>
                </div>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </Section>
  );
}

function RoomForm({
  room,
  onError,
  onCancel,
  onSaved,
}: {
  room: Room | null;
  onError: (message: string) => void;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<RoomRequest>({
    code: room?.code ?? "",
    building: room?.building ?? "",
    name: room?.name ?? "",
    roomType: room?.roomType ?? "COMPUTER_LAB",
    capacity: room?.capacity ?? 30,
  });
  const [saving, setSaving] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    (room ? updateRoom(room.id, form) : createRoom(form))
      .then(onSaved)
      .catch((err) => onError(err.message))
      .finally(() => setSaving(false));
  };

  return (
    <form className="harvest-box" onSubmit={submit}>
      <div className="filter-bar">
        <input
          placeholder="Mã phòng"
          value={form.code}
          maxLength={32}
          onChange={(e) => setForm({ ...form, code: e.target.value.trim() })}
          required
        />
        <input
          placeholder="Tòa nhà"
          value={form.building}
          maxLength={64}
          onChange={(e) => setForm({ ...form, building: e.target.value })}
          required
        />
        <input
          placeholder="Tên phòng"
          value={form.name}
          maxLength={255}
          onChange={(e) => setForm({ ...form, name: e.target.value })}
          required
        />
        <select value={form.roomType} onChange={(e) => setForm({ ...form, roomType: e.target.value as RoomType })}>
          {Object.entries(ROOM_TYPE_LABEL).map(([key, label]) => (
            <option key={key} value={key}>
              {label}
            </option>
          ))}
        </select>
        <input
          type="number"
          min={1}
          max={1000}
          value={form.capacity}
          onChange={(e) => setForm({ ...form, capacity: Number(e.target.value) })}
          required
        />
      </div>
      <div className="form-actions">
        <button className="btn btn-primary" type="submit" disabled={saving}>
          {room ? "Lưu thay đổi" : "Thêm phòng"}
        </button>
        <button className="btn" type="button" onClick={onCancel}>
          Huỷ
        </button>
      </div>
    </form>
  );
}

function Devices({ rooms, onError }: { rooms: Room[]; onError: (message: string) => void }) {
  const [devices, setDevices] = useState<Device[]>([]);
  const [roomId, setRoomId] = useState("");
  const [status, setStatus] = useState("");
  const [deviceType, setDeviceType] = useState("");
  const [editing, setEditing] = useState<Device | "new" | null>(null);
  const [expanded, setExpanded] = useState<number | null>(null);

  const load = useCallback(() => {
    listDevices({
      roomId: roomId ? Number(roomId) : undefined,
      status: status || undefined,
      deviceType: deviceType.trim() || undefined,
    })
      .then(setDevices)
      .catch((err) => onError(err.message));
  }, [roomId, status, deviceType, onError]);

  useEffect(load, [load]);

  return (
    <Section step="🖥️" title="Thiết bị" subtitle="Danh mục thiết bị, nhật ký bảo trì và lịch sử mượn">
      <div className="filter-bar">
        <select value={roomId} onChange={(e) => setRoomId(e.target.value)}>
          <option value="">Mọi phòng</option>
          {rooms.map((room) => (
            <option key={room.id} value={room.id}>
              {room.code}
            </option>
          ))}
        </select>
        <select value={status} onChange={(e) => setStatus(e.target.value)}>
          <option value="">Mọi trạng thái</option>
          {(Object.keys(DEVICE_STATUS_LABEL) as DeviceStatus[]).map((key) => (
            <option key={key} value={key}>
              {DEVICE_STATUS_LABEL[key].label}
            </option>
          ))}
        </select>
        <input placeholder="Loại thiết bị" value={deviceType} onChange={(e) => setDeviceType(e.target.value)} />
        <button className="btn btn-primary" onClick={() => setEditing("new")}>
          Thêm thiết bị
        </button>
      </div>

      {editing && (
        <DeviceForm
          device={editing === "new" ? null : editing}
          rooms={rooms}
          onError={onError}
          onCancel={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            load();
          }}
        />
      )}

      {devices.length === 0 ? (
        <p className="empty-hint">Không có thiết bị nào khớp bộ lọc.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Mã tài sản</th>
              <th>Loại</th>
              <th>Phòng</th>
              <th>Bảo hành đến</th>
              <th>Trạng thái</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {devices.map((device) => (
              <DeviceRow
                key={device.id}
                device={device}
                open={expanded === device.id}
                onToggle={() => setExpanded(expanded === device.id ? null : device.id)}
                onEdit={() => setEditing(device)}
                onChanged={load}
                onError={onError}
              />
            ))}
          </tbody>
        </table>
      )}
    </Section>
  );
}

function DeviceForm({
  device,
  rooms,
  onError,
  onCancel,
  onSaved,
}: {
  device: Device | null;
  rooms: Room[];
  onError: (message: string) => void;
  onCancel: () => void;
  onSaved: () => void;
}) {
  const [form, setForm] = useState<DeviceRequest>({
    assetCode: device?.assetCode ?? "",
    deviceType: device?.deviceType ?? "",
    model: device?.model ?? "",
    serialNumber: device?.serialNumber ?? "",
    roomId: device?.roomId ?? null,
    purchasedOn: device?.purchasedOn ?? "",
    warrantyUntil: device?.warrantyUntil ?? "",
  });
  const [saving, setSaving] = useState(false);

  const submit = (event: FormEvent) => {
    event.preventDefault();
    setSaving(true);
    const payload: DeviceRequest = {
      ...form,
      model: form.model || undefined,
      serialNumber: form.serialNumber || undefined,
      purchasedOn: form.purchasedOn || undefined,
      warrantyUntil: form.warrantyUntil || undefined,
    };
    (device ? updateDevice(device.id, payload) : createDevice(payload))
      .then(onSaved)
      .catch((err) => onError(err.message))
      .finally(() => setSaving(false));
  };

  return (
    <form className="harvest-box" onSubmit={submit}>
      <div className="filter-bar">
        <input
          placeholder="Mã tài sản"
          value={form.assetCode}
          maxLength={64}
          onChange={(e) => setForm({ ...form, assetCode: e.target.value.trim() })}
          required
        />
        <input
          placeholder="Loại (máy tính, máy chiếu...)"
          value={form.deviceType}
          maxLength={64}
          onChange={(e) => setForm({ ...form, deviceType: e.target.value })}
          required
        />
        <input
          placeholder="Model"
          value={form.model ?? ""}
          maxLength={255}
          onChange={(e) => setForm({ ...form, model: e.target.value })}
        />
        <input
          placeholder="Số serial"
          value={form.serialNumber ?? ""}
          maxLength={128}
          onChange={(e) => setForm({ ...form, serialNumber: e.target.value })}
        />
        <select
          value={form.roomId ?? ""}
          onChange={(e) => setForm({ ...form, roomId: e.target.value ? Number(e.target.value) : null })}
        >
          <option value="">Chưa đặt ở phòng nào</option>
          {rooms.map((room) => (
            <option key={room.id} value={room.id}>
              {room.code}
            </option>
          ))}
        </select>
        <label className="field-label">
          Ngày mua
          <input
            type="date"
            value={form.purchasedOn ?? ""}
            onChange={(e) => setForm({ ...form, purchasedOn: e.target.value })}
          />
        </label>
        <label className="field-label">
          Bảo hành đến
          <input
            type="date"
            value={form.warrantyUntil ?? ""}
            onChange={(e) => setForm({ ...form, warrantyUntil: e.target.value })}
          />
        </label>
      </div>
      <div className="form-actions">
        <button className="btn btn-primary" type="submit" disabled={saving}>
          {device ? "Lưu thay đổi" : "Thêm thiết bị"}
        </button>
        <button className="btn" type="button" onClick={onCancel}>
          Huỷ
        </button>
      </div>
    </form>
  );
}

function DeviceRow({
  device,
  open,
  onToggle,
  onEdit,
  onChanged,
  onError,
}: {
  device: Device;
  open: boolean;
  onToggle: () => void;
  onEdit: () => void;
  onChanged: () => void;
  onError: (message: string) => void;
}) {
  const definition = DEVICE_STATUS_LABEL[device.status];
  return (
    <>
      <tr>
        <td>
          {device.assetCode}
          {device.model && <div className="cell-sub">{device.model}</div>}
        </td>
        <td>{device.deviceType}</td>
        <td>{device.roomCode ?? "—"}</td>
        <td>{device.warrantyUntil ? formatDate(device.warrantyUntil) : "—"}</td>
        <td>
          <Badge tone={definition.tone}>{definition.label}</Badge>
        </td>
        <td>
          <button className="btn btn-link" onClick={onToggle}>
            {open ? "Thu gọn" : "Bảo trì & lịch sử"}
          </button>
        </td>
      </tr>
      {open && (
        <tr className="expanded-row">
          <td colSpan={6}>
            <DevicePanel device={device} onEdit={onEdit} onChanged={onChanged} onError={onError} />
          </td>
        </tr>
      )}
    </>
  );
}

function DevicePanel({
  device,
  onEdit,
  onChanged,
  onError,
}: {
  device: Device;
  onEdit: () => void;
  onChanged: () => void;
  onError: (message: string) => void;
}) {
  const [maintenance, setMaintenance] = useState<Maintenance[]>([]);
  const [loans, setLoans] = useState<Loan[]>([]);
  const [kind, setKind] = useState<MaintenanceKind>("REPAIR");
  const [note, setNote] = useState("");
  const [completing, setCompleting] = useState<number | null>(null);
  const [completionNote, setCompletionNote] = useState("");
  const [busy, setBusy] = useState(false);

  const load = useCallback(() => {
    Promise.all([listMaintenance(device.id), listLoans({ deviceId: device.id, size: 10 })])
      .then(([m, l]) => {
        setMaintenance(m);
        setLoans(l.items);
      })
      .catch((err) => onError(err.message));
  }, [device.id, onError]);

  useEffect(load, [load]);

  const refresh = () => {
    load();
    onChanged();
  };
  const act = (action: () => Promise<unknown>) => runAction(action, setBusy, onError, refresh);

  const underRepair = device.status === "UNDER_REPAIR";
  const retired = device.status === "RETIRED";
  const onLoan = device.status === "ON_LOAN";
  // A device holds one open maintenance entry at a time and none while it is out on loan.
  const canStart = !underRepair && !retired && !onLoan && maintenance.every((entry) => entry.completedAt);

  return (
    <div>
      <div className="form-actions">
        <button className="btn" disabled={busy || retired} onClick={onEdit}>
          Sửa thông tin
        </button>
        <button
          className="btn btn-secondary"
          disabled={busy || retired || onLoan}
          onClick={() => act(() => retireDevice(device.id))}
        >
          Thanh lý
        </button>
      </div>

      <h3>Nhật ký bảo trì</h3>
      {maintenance.length === 0 ? (
        <p className="empty-hint">Chưa có nhật ký bảo trì.</p>
      ) : (
        <table className="data-table">
          <thead>
            <tr>
              <th>Loại</th>
              <th>Nội dung</th>
              <th>Người thực hiện</th>
              <th>Bắt đầu</th>
              <th>Hoàn tất</th>
            </tr>
          </thead>
          <tbody>
            {maintenance.map((entry) => (
              <tr key={entry.id}>
                <td>{MAINTENANCE_KIND_LABEL[entry.kind]}</td>
                <td>
                  {entry.note}
                  {entry.completionNote && <div className="cell-sub">Kết quả: {entry.completionNote}</div>}
                </td>
                <td>{entry.performedByName}</td>
                <td>{formatDateTime(entry.startedAt)}</td>
                <td>
                  {entry.completedAt ? (
                    formatDateTime(entry.completedAt)
                  ) : completing === entry.id ? (
                    <form
                      className="inline-form"
                      onSubmit={(e) => {
                        e.preventDefault();
                        void act(() => completeMaintenance(device.id, entry.id, completionNote.trim())).then(() => {
                          setCompleting(null);
                          setCompletionNote("");
                        });
                      }}
                    >
                      <input
                        placeholder="Kết quả (không bắt buộc)"
                        value={completionNote}
                        maxLength={2000}
                        onChange={(e) => setCompletionNote(e.target.value)}
                      />
                      <button className="btn btn-primary" type="submit" disabled={busy}>
                        Hoàn tất
                      </button>
                    </form>
                  ) : (
                    <button className="btn" disabled={busy} onClick={() => setCompleting(entry.id)}>
                      {entry.kind === "INSPECTION" ? "Hoàn tất kiểm tra" : "Hoàn tất sửa chữa"}
                    </button>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}

      {canStart && (
        <form
          className="inline-form"
          onSubmit={(e) => {
            e.preventDefault();
            void act(() => startMaintenance(device.id, { kind, note: note.trim() })).then(() => setNote(""));
          }}
        >
          <select value={kind} onChange={(e) => setKind(e.target.value as MaintenanceKind)}>
            {Object.entries(MAINTENANCE_KIND_LABEL).map(([key, label]) => (
              <option key={key} value={key}>
                {label}
              </option>
            ))}
          </select>
          <input
            placeholder="Nội dung công việc (bắt buộc)"
            value={note}
            maxLength={2000}
            onChange={(e) => setNote(e.target.value)}
            required
          />
          <button className="btn btn-primary" type="submit" disabled={busy}>
            {kind === "INSPECTION" ? "Ghi nhật ký" : "Ghi nhật ký và đặt đang sửa"}
          </button>
        </form>
      )}

      <h3>Lịch sử mượn</h3>
      {loans.length === 0 ? (
        <p className="empty-hint">Thiết bị chưa được mượn lần nào.</p>
      ) : (
        <ul>
          {loans.map((loan) => (
            <li key={loan.id}>
              {loan.borrowerName} · hạn {formatDate(loan.dueDate)} ·{" "}
              {LOAN_STATUS_LABEL[loan.status]?.label ?? loan.status}
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
