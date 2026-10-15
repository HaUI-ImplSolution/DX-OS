import { useEffect, useState } from "react";
import { useSearchParams } from "react-router-dom";
import {
  getDiseaseReport,
  listCropSeasons,
  listDiseaseReports,
  listDiseases,
  listHouseholds,
  listParcels,
} from "../api";
import CropSeasonPanel from "../components/CropSeasonPanel";
import DiagnosisPanel from "../components/DiagnosisPanel";
import DiseaseReportPanel from "../components/DiseaseReportPanel";
import ParcelPanel from "../components/ParcelPanel";
import ReferencePanel from "../components/ReferencePanel";
import WeatherPanel from "../components/WeatherPanel";
import type {
  CropSeason,
  Diagnosis,
  Disease,
  DiseaseReport,
  Household,
  Parcel,
  Role,
} from "../types";

type Tab = "flow" | "reference";

interface DemoFlowProps {
  role: Role;
  onError: (message: string) => void;
}

// Step by step walk through parcel, crop season, report and diagnosis.
export default function DemoFlow({ role, onError }: DemoFlowProps) {
  const [tab, setTab] = useState<Tab>("flow");

  const [households, setHouseholds] = useState<Household[]>([]);
  const [parcels, setParcels] = useState<Parcel[]>([]);
  const [selectedParcelCode, setSelectedParcelCode] = useState<string | null>(null);

  const [cropSeasons, setCropSeasons] = useState<CropSeason[]>([]);
  const [selectedCropSeasonId, setSelectedCropSeasonId] = useState<number | null>(null);

  const [reports, setReports] = useState<DiseaseReport[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);

  const [diseases, setDiseases] = useState<Disease[]>([]);

  // A link such as /?report=42 (from a chat message or the to-do list) opens that report.
  const [searchParams, setSearchParams] = useSearchParams();
  const linkedReportId = Number(searchParams.get("report")) || null;

  useEffect(() => {
    if (!linkedReportId) return;
    getDiseaseReport(linkedReportId)
      .then((report) => {
        setTab("flow");
        setSelectedParcelCode(report.parcelCode);
        setSelectedCropSeasonId(report.cropSeasonId);
        setSelectedReportId(report.id);
      })
      .catch((err) => onError(err.message))
      .finally(() => setSearchParams({}, { replace: true }));
  }, [linkedReportId, onError, setSearchParams]);

  useEffect(() => {
    listParcels().then(setParcels).catch((err) => onError(err.message));
    listDiseases().then(setDiseases).catch((err) => onError(err.message));
    if (role === "ADMIN") {
      listHouseholds().then(setHouseholds).catch((err) => onError(err.message));
    }
  }, [role, onError]);

  useEffect(() => {
    if (!selectedParcelCode) {
      setCropSeasons([]);
      return;
    }
    listCropSeasons(selectedParcelCode).then(setCropSeasons).catch((err) => onError(err.message));
  }, [selectedParcelCode, onError]);

  useEffect(() => {
    if (!selectedCropSeasonId) {
      setReports([]);
      return;
    }
    listDiseaseReports(selectedCropSeasonId).then(setReports).catch((err) => onError(err.message));
  }, [selectedCropSeasonId, onError]);

  const selectParcel = (code: string) => {
    setSelectedParcelCode(code);
    setSelectedCropSeasonId(null);
    setSelectedReportId(null);
  };

  const selectCropSeason = (id: number) => {
    setSelectedCropSeasonId(id);
    setSelectedReportId(null);
  };

  const selectedReport = reports.find((r) => r.id === selectedReportId) ?? null;

  const updateDiagnosisInReport = (diagnosis: Diagnosis) => {
    setReports((prev) =>
      prev.map((r) => (r.id === diagnosis.diseaseReportId ? { ...r, diagnosis, status: "PROCESSED" } : r)),
    );
  };

  return (
    <>
      <nav className="tabs">
        <button className={tab === "flow" ? "tab tab-active" : "tab"} onClick={() => setTab("flow")}>
          Luồng demo
        </button>
        <button
          className={tab === "reference" ? "tab tab-active" : "tab"}
          onClick={() => setTab("reference")}
        >
          Danh mục tham chiếu
        </button>
      </nav>

      {tab === "reference" && <ReferencePanel onError={onError} />}

      {tab === "flow" && (
        <main className="flow">
          <ParcelPanel
            parcels={parcels}
            households={role === "ADMIN" ? households : null}
            selectedCode={selectedParcelCode}
            onSelect={selectParcel}
            onCreated={(p) => {
              setParcels((prev) => [...prev, p]);
              selectParcel(p.code);
            }}
            onDeleted={(id) => {
              setParcels((prev) => prev.filter((p) => p.id !== id));
              setSelectedParcelCode(null);
              setSelectedCropSeasonId(null);
              setSelectedReportId(null);
            }}
            onError={onError}
          />

          {selectedParcelCode && <WeatherPanel parcelCode={selectedParcelCode} />}

          {selectedParcelCode && (
            <CropSeasonPanel
              parcelCode={selectedParcelCode}
              cropSeasons={cropSeasons}
              canManage={role === "ADMIN"}
              selectedId={selectedCropSeasonId}
              onSelect={selectCropSeason}
              onCreated={(s) => {
                setCropSeasons((prev) => [...prev, s]);
                selectCropSeason(s.id);
              }}
              onHarvested={(s) =>
                setCropSeasons((prev) => prev.map((cs) => (cs.id === s.id ? s : cs)))
              }
              onDeleted={(id) => {
                setCropSeasons((prev) => prev.filter((cs) => cs.id !== id));
                setSelectedCropSeasonId(null);
                setSelectedReportId(null);
              }}
              onError={onError}
            />
          )}

          {selectedCropSeasonId && (
            <DiseaseReportPanel
              reports={reports}
              selectedId={selectedReportId}
              onSelect={setSelectedReportId}
              onDeleted={
                role === "ADMIN"
                  ? (id) => {
                      setReports((prev) => prev.filter((r) => r.id !== id));
                      setSelectedReportId(null);
                    }
                  : null
              }
              onError={onError}
            />
          )}

          {selectedReport && (
            <DiagnosisPanel
              onReportUpdated={(updated) => setReports((items) => items.map((item) => item.id === updated.id ? updated : item))}
              report={selectedReport}
              diseases={diseases}
              currentRole={role}
              onUpdated={updateDiagnosisInReport}
              onError={onError}
            />
          )}
        </main>
      )}
    </>
  );
}
