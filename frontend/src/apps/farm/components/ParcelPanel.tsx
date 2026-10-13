import { useState } from "react";
import { createParcel, deleteParcel } from "../api";
import type { Household, Parcel } from "../types";
import { Badge, DeleteButton, Section } from "../../../shell";

interface ParcelPanelProps {
  parcels: Parcel[];
  selectedCode: string | null;
  // Households an ADMIN can register parcels for; null when the user may not register parcels.
  households: Household[] | null;
  onSelect: (code: string) => void;
  onCreated: (parcel: Parcel) => void;
  onDeleted: (id: number) => void;
  onError: (message: string) => void;
}

export default function ParcelPanel({
  parcels,
  selectedCode,
  households,
  onSelect,
  onCreated,
  onDeleted,
  onError,
}: ParcelPanelProps) {
  const [showForm, setShowForm] = useState(false);
  const [householdCode, setHouseholdCode] = useState("");
  const [areaHectares, setAreaHectares] = useState("1");
  const [location, setLocation] = useState("");
  const [latitude, setLatitude] = useState("");
  const [longitude, setLongitude] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const parcel = await createParcel({
        householdCode,
        areaHectares: Number(areaHectares),
        location: location || undefined,
        latitude: Number(latitude),
        longitude: Number(longitude),
      });
      onCreated(parcel);
      setHouseholdCode("");
      setAreaHectares("1");
      setLocation("");
      setLatitude("");
      setLongitude("");
      setShowForm(false);
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không tạo được thửa ruộng");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <Section step="1" title="Thửa ruộng" subtitle="Chọn thửa ruộng đang canh tác, hoặc đăng ký mới">
      <div className="chip-list">
        {parcels.map((p) => (
          <button
            key={p.code}
            className={`chip${p.code === selectedCode ? " chip-active" : ""}`}
            onClick={() => onSelect(p.code)}
          >
            {p.code} — {p.farmerName}
            {p.approvalStatus !== "APPROVED" && (
              <Badge tone={p.approvalStatus === "PENDING" ? "warning" : "danger"}>
                {" "}
                {p.approvalStatus === "PENDING" ? "Chờ duyệt" : "Bị từ chối"}
              </Badge>
            )}
          </button>
        ))}
        {parcels.length === 0 && <p className="empty-hint">Chưa có thửa ruộng nào.</p>}
      </div>

      {households && selectedCode && (
        <DeleteButton
          label="Xóa thửa đang chọn"
          confirmMessage={`Xóa thửa ${selectedCode}? Thửa còn vụ mùa thì phải xóa các vụ trước.`}
          onDelete={async () => {
            const parcel = parcels.find((p) => p.code === selectedCode);
            if (!parcel) return;
            await deleteParcel(parcel.id);
            onDeleted(parcel.id);
          }}
          onError={onError}
        />
      )}

      {households && !showForm && (
        <button className="btn btn-link" onClick={() => setShowForm(true)}>
          + Đăng ký thửa ruộng mới
        </button>
      )}

      {households && showForm && (
        <form className="inline-form" onSubmit={submit}>
          <select value={householdCode} onChange={(e) => setHouseholdCode(e.target.value)} required>
            <option value="">— Chọn nông hộ —</option>
            {households.map((h) => (
              <option key={h.code} value={h.code}>
                {h.code} — {h.fullName}
              </option>
            ))}
          </select>
          <input
            type="number"
            step="0.01"
            min="0.01"
            placeholder="Diện tích (ha)"
            value={areaHectares}
            onChange={(e) => setAreaHectares(e.target.value)}
            required
          />
          <input placeholder="Vị trí (tuỳ chọn)" value={location} onChange={(e) => setLocation(e.target.value)} />
          <input
            type="number"
            step="0.000001"
            placeholder="Vĩ độ (vd. 10.031967)"
            value={latitude}
            onChange={(e) => setLatitude(e.target.value)}
            required
          />
          <input
            type="number"
            step="0.000001"
            placeholder="Kinh độ (vd. 105.783833)"
            value={longitude}
            onChange={(e) => setLongitude(e.target.value)}
            required
          />
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Đang lưu…" : "Tạo thửa ruộng"}
            </button>
            <button type="button" className="btn" onClick={() => setShowForm(false)}>
              Huỷ
            </button>
          </div>
        </form>
      )}
    </Section>
  );
}
