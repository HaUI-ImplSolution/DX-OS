import { useEffect, useState } from "react";
import { ApiError, createCropSeason, getIsolationStatus, harvestCropSeason } from "../api";
import type { CropSeason, IsolationStatus, PokaYokeErrorData } from "../types";
import Badge from "./Badge";
import Section from "./Section";

interface CropSeasonPanelProps {
  parcelCode: string;
  cropSeasons: CropSeason[];
  // Only ADMIN starts seasons and declares harvests on the web (farmers use the mobile app).
  canManage: boolean;
  selectedId: number | null;
  onSelect: (id: number) => void;
  onCreated: (season: CropSeason) => void;
  onHarvested: (season: CropSeason) => void;
  onError: (message: string) => void;
}

export default function CropSeasonPanel({
  parcelCode,
  cropSeasons,
  canManage,
  selectedId,
  onSelect,
  onCreated,
  onHarvested,
  onError,
}: CropSeasonPanelProps) {
  const [showForm, setShowForm] = useState(false);
  const [riceVariety, setRiceVariety] = useState("OM 5451");
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [expectedHarvestDate, setExpectedHarvestDate] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [harvestBusy, setHarvestBusy] = useState(false);
  const [pokaYoke, setPokaYoke] = useState<PokaYokeErrorData | null>(null);
  const [isolationStatus, setIsolationStatus] = useState<IsolationStatus | null>(null);
  const [isolationBusy, setIsolationBusy] = useState(false);

  const selected = cropSeasons.find((s) => s.id === selectedId) ?? null;

  useEffect(() => {
    setIsolationStatus(null);
  }, [selectedId]);

  const askIsolation = async () => {
    if (!selected) return;
    setIsolationBusy(true);
    try {
      setIsolationStatus(await getIsolationStatus(selected.id));
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không hỏi được trạng thái cách ly");
    } finally {
      setIsolationBusy(false);
    }
  };

  const submit = async (e: React.FormEvent) => {
    e.preventDefault();
    setSubmitting(true);
    try {
      const season = await createCropSeason(parcelCode, {
        riceVariety,
        startDate,
        expectedHarvestDate: expectedHarvestDate || undefined,
      });
      onCreated(season);
      setShowForm(false);
      setExpectedHarvestDate("");
    } catch (err) {
      onError(err instanceof Error ? err.message : "Không tạo được vụ mùa");
    } finally {
      setSubmitting(false);
    }
  };

  const harvest = async () => {
    if (!selected) return;
    setHarvestBusy(true);
    setPokaYoke(null);
    try {
      const updated = await harvestCropSeason(selected.id);
      onHarvested(updated);
    } catch (err) {
      if (err instanceof ApiError && err.status === 422) {
        setPokaYoke(err.data as PokaYokeErrorData);
      } else {
        onError(err instanceof Error ? err.message : "Không ghi nhận được thu hoạch");
      }
    } finally {
      setHarvestBusy(false);
    }
  };

  return (
    <Section
      step="2"
      title="Vụ mùa"
      subtitle={`Thửa ${parcelCode} — chọn vụ mùa đang canh tác, hoặc bắt đầu vụ mới`}
    >
      <div className="chip-list">
        {cropSeasons.map((s) => (
          <button
            key={s.id}
            className={`chip${s.id === selectedId ? " chip-active" : ""}`}
            onClick={() => onSelect(s.id)}
          >
            {s.riceVariety} · {s.startDate}
            <Badge tone={s.status === "ACTIVE" ? "success" : "neutral"}> {s.status}</Badge>
          </button>
        ))}
        {cropSeasons.length === 0 && <p className="empty-hint">Thửa này chưa có vụ mùa nào.</p>}
      </div>

      {canManage && !showForm && (
        <button className="btn btn-link" onClick={() => setShowForm(true)}>
          + Bắt đầu vụ mùa mới
        </button>
      )}

      {canManage && showForm && (
        <form className="inline-form" onSubmit={submit}>
          <input
            placeholder="Giống lúa"
            value={riceVariety}
            onChange={(e) => setRiceVariety(e.target.value)}
            required
          />
          <label className="field-label">
            Ngày bắt đầu
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          </label>
          <label className="field-label">
            Dự kiến thu hoạch
            <input
              type="date"
              value={expectedHarvestDate}
              onChange={(e) => setExpectedHarvestDate(e.target.value)}
            />
          </label>
          <div className="form-actions">
            <button type="submit" className="btn btn-primary" disabled={submitting}>
              {submitting ? "Đang lưu…" : "Tạo vụ mùa"}
            </button>
            <button type="button" className="btn" onClick={() => setShowForm(false)}>
              Huỷ
            </button>
          </div>
        </form>
      )}

      {selected && (
        <div className="harvest-box">
          <div>
            <strong>Vụ đang chọn:</strong> {selected.riceVariety} ({selected.status})
            {selected.harvestedAt && <span> — đã thu hoạch lúc {new Date(selected.harvestedAt).toLocaleString("vi-VN")}</span>}
          </div>
          <div className="form-actions">
            <button className="btn" onClick={askIsolation} disabled={isolationBusy}>
              {isolationBusy ? "Đang hỏi…" : "Hỏi: ruộng này còn cách ly không?"}
            </button>
            {canManage && selected.status === "ACTIVE" && (
              <button className="btn btn-secondary" onClick={harvest} disabled={harvestBusy}>
                {harvestBusy ? "Đang xử lý…" : "Khai báo đã thu hoạch"}
              </button>
            )}
          </div>
          {isolationStatus && (
            <div className={`banner ${isolationStatus.isolated ? "banner-danger" : "banner-success"}`}>
              <p>{isolationStatus.message}</p>
            </div>
          )}
          {pokaYoke && (
            <div className="banner banner-danger">
              <strong>🔒 Poka-yoke chặn thu hoạch — {pokaYoke.errorCode}</strong>
              <p>
                Ruộng còn trong thời gian cách ly (PHI) đến hết ngày{" "}
                <b>{pokaYoke.harvestLockedUntil}</b>. Backend trả HTTP 422, UI khoá thao tác thu hoạch
                theo đúng nguyên tắc "defense-in-depth" của VietGAP.
              </p>
            </div>
          )}
        </div>
      )}
    </Section>
  );
}
