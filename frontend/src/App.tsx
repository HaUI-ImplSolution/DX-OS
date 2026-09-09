import { useEffect, useState } from "react";
import { listCropSeasons, listDiseaseReports, listDiseases, listParcels } from "./api";
import CropSeasonPanel from "./components/CropSeasonPanel";
import DiagnosisPanel from "./components/DiagnosisPanel";
import DiseaseReportPanel from "./components/DiseaseReportPanel";
import ParcelPanel from "./components/ParcelPanel";
import ReferencePanel from "./components/ReferencePanel";
import type { CropSeason, Diagnosis, Disease, DiseaseReport, Parcel } from "./types";

type Tab = "flow" | "reference";

export default function App() {
  const [tab, setTab] = useState<Tab>("flow");
  const [error, setError] = useState<string | null>(null);

  const [parcels, setParcels] = useState<Parcel[]>([]);
  const [selectedParcelCode, setSelectedParcelCode] = useState<string | null>(null);

  const [cropSeasons, setCropSeasons] = useState<CropSeason[]>([]);
  const [selectedCropSeasonId, setSelectedCropSeasonId] = useState<number | null>(null);

  const [reports, setReports] = useState<DiseaseReport[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);

  const [diseases, setDiseases] = useState<Disease[]>([]);

  const showError = (message: string) => setError(message);

  useEffect(() => {
    listParcels().then(setParcels).catch((err) => showError(err.message));
    listDiseases().then(setDiseases).catch((err) => showError(err.message));
  }, []);

  useEffect(() => {
    if (!selectedParcelCode) {
      setCropSeasons([]);
      return;
    }
    listCropSeasons(selectedParcelCode).then(setCropSeasons).catch((err) => showError(err.message));
  }, [selectedParcelCode]);

  useEffect(() => {
    if (!selectedCropSeasonId) {
      setReports([]);
      return;
    }
    listDiseaseReports(selectedCropSeasonId).then(setReports).catch((err) => showError(err.message));
  }, [selectedCropSeasonId]);

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
      prev.map((r) => (r.id === diagnosis.diseaseReportId ? { ...r, diagnosis } : r)),
    );
  };

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>DX-Farm — Demo Spine Flow (Phase 0)</h1>
          <p>Báo bệnh bằng ảnh → chẩn đoán → tra cứu quy tắc → kỹ sư duyệt → ghi nhận xử lý → khoá thu hoạch (PHI)</p>
        </div>
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
      </header>

      {error && (
        <div className="banner banner-danger banner-dismissible">
          <span>{error}</span>
          <button onClick={() => setError(null)}>✕</button>
        </div>
      )}

      {tab === "reference" && <ReferencePanel onError={showError} />}

      {tab === "flow" && (
        <main className="flow">
          <ParcelPanel
            parcels={parcels}
            selectedCode={selectedParcelCode}
            onSelect={selectParcel}
            onCreated={(p) => {
              setParcels((prev) => [...prev, p]);
              selectParcel(p.code);
            }}
            onError={showError}
          />

          {selectedParcelCode && (
            <CropSeasonPanel
              parcelCode={selectedParcelCode}
              cropSeasons={cropSeasons}
              selectedId={selectedCropSeasonId}
              onSelect={selectCropSeason}
              onCreated={(s) => {
                setCropSeasons((prev) => [...prev, s]);
                selectCropSeason(s.id);
              }}
              onHarvested={(s) =>
                setCropSeasons((prev) => prev.map((cs) => (cs.id === s.id ? s : cs)))
              }
              onError={showError}
            />
          )}

          {selectedCropSeasonId && (
            <DiseaseReportPanel
              cropSeasonId={selectedCropSeasonId}
              reports={reports}
              selectedId={selectedReportId}
              onSelect={setSelectedReportId}
              onCreated={(r) => {
                setReports((prev) => [...prev, r]);
                setSelectedReportId(r.id);
              }}
              onError={showError}
            />
          )}

          {selectedReport && (
            <DiagnosisPanel
              report={selectedReport}
              diseases={diseases}
              onUpdated={updateDiagnosisInReport}
              onError={showError}
            />
          )}
        </main>
      )}
    </div>
  );
}
