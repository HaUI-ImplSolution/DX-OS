import { useState } from "react";
import { createParcel } from "../api";
import type { Parcel } from "../types";
import Section from "./Section";

interface ParcelPanelProps {
  parcels: Parcel[];
  selectedCode: string | null;
  // Set for FARMER: new parcels are always registered under this name.
  ownerName?: string;
  onSelect: (code: string) => void;
  onCreated: (parcel: Parcel) => void;
  onError: (message: string) => void;
}

export default function ParcelPanel({
  parcels,
  selectedCode,
  ownerName,
  onSelect,
  onCreated,
  onError,
}: ParcelPanelProps) {
  const [showForm, setShowForm] = useState(false);
  const [code, setCode] = useState("");
  const [farmerName, setFarmerName] = useState("");
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
        code,
        farmerName: ownerName ?? farmerName,
        areaHectares: Number(areaHectares),
        location: location || undefined,
        latitude: latitude ? Number(latitude) : undefined,
        longitude: longitude ? Number(longitude) : undefined,
      });
      onCreated(parcel);
      setCode("");
      setFarmerName("");
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
          </button>
        ))}
        {parcels.length === 0 && <p className="empty-hint">Chưa có thửa ruộng nào.</p>}
      </div>

      {!showForm && (
        <button className="btn btn-link" onClick={() => setShowForm(true)}>
          + Đăng ký thửa ruộng mới
        </button>
      )}

      {showForm && (
        <form className="inline-form" onSubmit={submit}>
          <input placeholder="Mã thửa (vd. TD-01)" value={code} onChange={(e) => setCode(e.target.value)} required />
          <input
            placeholder="Tên chủ hộ"
            value={ownerName ?? farmerName}
            onChange={(e) => setFarmerName(e.target.value)}
            disabled={ownerName !== undefined}
            required
          />
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
            placeholder="Vĩ độ (tuỳ chọn, vd. 10.031967)"
            value={latitude}
            onChange={(e) => setLatitude(e.target.value)}
          />
          <input
            type="number"
            step="0.000001"
            placeholder="Kinh độ (tuỳ chọn, vd. 105.783833)"
            value={longitude}
            onChange={(e) => setLongitude(e.target.value)}
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
