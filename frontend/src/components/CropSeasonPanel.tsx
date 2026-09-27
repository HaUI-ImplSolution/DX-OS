import { useEffect, useState } from "react";
import {
  ApiError,
  createCropSeason,
  deleteCropSeason,
  getIsolationStatus,
  harvestCropSeason,
  listRiceVarieties,
  suggestCropSeason,
} from "../api";
import type {
  CropSeason,
  CropSeasonSuggestion,
  IsolationStatus,
  PokaYokeErrorData,
  RiceVariety,
  SeasonType,
} from "../types";
import Badge from "./Badge";
import DeleteButton from "./DeleteButton";
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
  onDeleted: (id: number) => void;
  onError: (message: string) => void;
}

const SEASON_LABEL: Record<SeasonType, string> = {
  DONG_XUAN: "Đông Xuân",
  HE_THU: "Hè Thu",
  THU_DONG: "Thu Đông",
};

export default function CropSeasonPanel({
  parcelCode,
  cropSeasons,
  canManage,
  selectedId,
  onSelect,
  onCreated,
  onHarvested,
  onDeleted,
  onError,
}: CropSeasonPanelProps) {
  const [showForm, setShowForm] = useState(false);
  const [varieties, setVarieties] = useState<RiceVariety[]>([]);
  const [riceVarietyCode, setRiceVarietyCode] = useState("");
  const [seasonType, setSeasonType] = useState<SeasonType | "">("");
  const [suggestion, setSuggestion] = useState<CropSeasonSuggestion | null>(null);
  const [reasonForOverride, setReasonForOverride] = useState("");
  const [startDate, setStartDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [expectedHarvestDate, setExpectedHarvestDate] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [harvestBusy, setHarvestBusy] = useState(false);
  const [pokaYoke, setPokaYoke] = useState<PokaYokeErrorData | null>(null);
  const [isolationStatus, setIsolationStatus] = useState<IsolationStatus | null>(null);
  const [isolationBusy, setIsolationBusy] = useState(false);

  const selected = cropSeasons.find((s) => s.id === selectedId) ?? null;

  useEffect(() => {
    if (!canManage) return;
    listRiceVarieties()
      .then((list) => {
        setVarieties(list);
        setRiceVarietyCode((current) => current || list[0]?.code || "");
      })
      .catch((err) => onError(err.message));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [canManage]);

  // The backend owns the suggestion, so the form never recomputes it.
  useEffect(() => {
    if (!riceVarietyCode || !startDate) return;
    suggestCropSeason(riceVarietyCode, startDate)
      .then((result) => {
        setSuggestion(result);
        setSeasonType(result.seasonType ?? "");
      })
      .catch(() => setSuggestion(null));
  }, [riceVarietyCode, startDate]);

  const outsideRange =
    suggestion !== null &&
    expectedHarvestDate !== "" &&
    (expectedHarvestDate < suggestion.earliestHarvestDate ||
      expectedHarvestDate > suggestion.latestHarvestDate);

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
        riceVarietyCode,
        startDate,
        seasonType: seasonType || undefined,
        expectedHarvestDate: expectedHarvestDate || undefined,
        reasonForOverride: outsideRange ? reasonForOverride : undefined,
      });
      onCreated(season);
      setShowForm(false);
      setExpectedHarvestDate("");
      setReasonForOverride("");
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
            {s.code ?? s.riceVariety} · {s.startDate}
            <Badge tone={s.status === "ACTIVE" ? "success" : "neutral"}> {s.status}</Badge>
            {s.approvalStatus !== "APPROVED" && (
              <Badge tone={s.approvalStatus === "PENDING" ? "warning" : "danger"}>
                {" "}
                {s.approvalStatus === "PENDING" ? "Chờ duyệt" : "Bị từ chối"}
              </Badge>
            )}
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
          <select value={riceVarietyCode} onChange={(e) => setRiceVarietyCode(e.target.value)} required>
            {varieties.map((v) => (
              <option key={v.code} value={v.code}>
                {v.name} ({v.growthDaysMin}-{v.growthDaysMax} ngày)
                {v.reviewStatus === "IN_REVIEW" ? " — chưa xác nhận" : ""}
              </option>
            ))}
          </select>
          <label className="field-label">
            Ngày bắt đầu
            <input type="date" value={startDate} onChange={(e) => setStartDate(e.target.value)} required />
          </label>
          <label className="field-label">
            Loại vụ
            <select value={seasonType} onChange={(e) => setSeasonType(e.target.value as SeasonType | "")} required>
              <option value="">— Chọn loại vụ —</option>
              {(Object.keys(SEASON_LABEL) as SeasonType[]).map((type) => (
                <option key={type} value={type}>
                  {SEASON_LABEL[type]}
                  {suggestion?.seasonType === type ? " (gợi ý)" : ""}
                </option>
              ))}
            </select>
          </label>
          <label className="field-label">
            Dự kiến thu hoạch
            <input
              type="date"
              value={expectedHarvestDate}
              placeholder={suggestion?.expectedHarvestDate}
              onChange={(e) => setExpectedHarvestDate(e.target.value)}
            />
          </label>
          {suggestion && (
            <p className="section-subtitle">
              Gợi ý thu hoạch: {suggestion.expectedHarvestDate} (trong khoảng {suggestion.earliestHarvestDate} đến{" "}
              {suggestion.latestHarvestDate}). Bỏ trống để dùng ngày gợi ý.
            </p>
          )}
          {outsideRange && (
            <input
              placeholder="Lý do chọn ngày ngoài khoảng gợi ý"
              value={reasonForOverride}
              onChange={(e) => setReasonForOverride(e.target.value)}
              required
            />
          )}
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
            {canManage && (
              <DeleteButton
                label="Xóa vụ đang chọn"
                confirmMessage={`Xóa vụ ${selected.code ?? selected.riceVariety}?`}
                onDelete={async () => {
                  await deleteCropSeason(selected.id);
                  onDeleted(selected.id);
                }}
                onError={onError}
              />
            )}
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
