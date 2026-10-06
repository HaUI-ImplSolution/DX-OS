import { useEffect, useState } from "react";
import {
  listCropSeasons,
  listDiseaseReports,
  listDiseases,
  listHouseholds,
  listParcels,
} from "./api";
import {
  callbackError,
  clearCallbackParams,
  getCachedUser,
  getCurrentUser,
  handleCallback,
  isCallbackRedirect,
  onSessionEnded,
  signIn,
  signOut,
  type CurrentUser,
} from "./auth";
import CropSeasonPanel from "./components/CropSeasonPanel";
import DiagnosisPanel from "./components/DiagnosisPanel";
import DiseaseReportPanel from "./components/DiseaseReportPanel";
import ParcelPanel from "./components/ParcelPanel";
import ReferencePanel from "./components/ReferencePanel";
import type { CropSeason, Diagnosis, Disease, DiseaseReport, Household, Parcel } from "./types";

type Tab = "flow" | "reference";

const ROLE_LABEL: Record<CurrentUser["role"], string> = {
  FARMER: "Nông dân",
  ENGINEER: "Kỹ sư",
  ADMIN: "Quản trị viên",
};

export default function App() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [authReady, setAuthReady] = useState(false);
  const [authError, setAuthError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("flow");
  const [error, setError] = useState<string | null>(null);

  const [households, setHouseholds] = useState<Household[]>([]);
  const [parcels, setParcels] = useState<Parcel[]>([]);
  const [selectedParcelCode, setSelectedParcelCode] = useState<string | null>(null);

  const [cropSeasons, setCropSeasons] = useState<CropSeason[]>([]);
  const [selectedCropSeasonId, setSelectedCropSeasonId] = useState<number | null>(null);

  const [reports, setReports] = useState<DiseaseReport[]>([]);
  const [selectedReportId, setSelectedReportId] = useState<number | null>(null);

  const [diseases, setDiseases] = useState<Disease[]>([]);

  // Token hết hạn -> api.ts đã xoá session OIDC (401), đồng bộ lại state để quay về màn đăng nhập.
  const showError = (message: string) => {
    setError(message);
    window.scrollTo({ top: 0, behavior: "smooth" });
    if (!getCachedUser()) setUser(null);
  };

  const logout = () => {
    void signOut();
  };

  useEffect(() => {
    // Callback từ Keycloak thì đổi code lấy token, ngược lại đọc session OIDC hiện có.
    // Callback lỗi (huỷ đăng nhập, tài khoản bị khoá...) thì dừng lại báo lỗi, không auto-redirect
    // lại kẻo lặp vô hạn.
    (async () => {
      const failure = callbackError();
      if (failure) {
        clearCallbackParams();
        setAuthError(failure);
        setAuthReady(true);
        return;
      }
      try {
        const loggedInUser = isCallbackRedirect() ? await handleCallback() : await getCurrentUser();
        setUser(loggedInUser);
      } finally {
        setAuthReady(true);
      }
    })();
  }, []);

  useEffect(() => {
    // Chưa đăng nhập (và không phải do vừa lỗi) -> tự redirect sang Keycloak ngay, không bắt
    // người dùng bấm thêm nút "Đăng nhập" - vào thẳng từ portal là vào được app sau khi qua SSO.
    if (authReady && !user && !authError) void signIn();
  }, [authReady, user, authError]);

  useEffect(() => {
    // Đăng xuất ở app khác (Superset) hoặc Keycloak Account Console cũng kết thúc phiên ở đây -
    // single logout, xem sso/README.md.
    return onSessionEnded(() => setUser(null));
  }, []);

  useEffect(() => {
    // Chưa đăng nhập thì chưa có token -> chờ user đăng nhập xong mới gọi API,
    // và tải lại ngay sau khi đăng nhập (không chỉ chạy đúng 1 lần lúc mount).
    // Farmers use the mobile app only, so no business API is called for them.
    if (!user || user.role === "FARMER") return;
    listParcels().then(setParcels).catch((err) => showError(err.message));
    listDiseases().then(setDiseases).catch((err) => showError(err.message));
    if (user.role === "ADMIN") {
      listHouseholds().then(setHouseholds).catch((err) => showError(err.message));
    }
  }, [user]);

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

  if (!authReady) {
    return <div className="login-screen" />;
  }

  if (authError) {
    return (
      <div className="login-screen">
        <section className="section login-card">
          <header className="section-header">
            <div>
              <h2>Đăng nhập thất bại</h2>
              <p className="section-subtitle">{authError}</p>
            </div>
          </header>
          <div className="form-actions">
            <button
              className="btn btn-primary"
              onClick={() => {
                setAuthError(null);
                void signIn();
              }}
            >
              Thử lại
            </button>
          </div>
        </section>
      </div>
    );
  }

  if (!user) {
    // Đang tự redirect sang Keycloak (xem effect ở trên) - không cần render gì thêm.
    return <div className="login-screen" />;
  }

  if (user.role === "FARMER") {
    return (
      <div className="login-screen">
        <section className="section login-card">
          <header className="section-header">
            <div>
              <h2>Vui lòng dùng ứng dụng di động</h2>
              <p className="section-subtitle">
                Tài khoản nông dân ({user.fullName}) chỉ sử dụng được trên ứng dụng DX-Farm di động.
                Trang web dành cho kỹ sư và quản trị viên.
              </p>
            </div>
          </header>
          <div className="form-actions">
            <button className="btn btn-primary" onClick={logout}>
              Đăng xuất
            </button>
          </div>
        </section>
      </div>
    );
  }

  return (
    <div className="app">
      <header className="app-header">
        <div>
          <h1>DX-Farm — Demo Spine Flow (Phase 0)</h1>
          <p>Báo bệnh bằng ảnh → chẩn đoán → tra cứu quy tắc → kỹ sư duyệt → ghi nhận xử lý → khoá thu hoạch (PHI)</p>
        </div>
        <div className="user-bar">
          <span>
            {user.fullName} ({ROLE_LABEL[user.role]})
          </span>
          <button className="btn" onClick={logout}>
            Đăng xuất
          </button>
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
            households={user.role === "ADMIN" ? households : null}
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
              canManage={user.role === "ADMIN"}
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
              reports={reports}
              selectedId={selectedReportId}
              onSelect={setSelectedReportId}
            />
          )}

          {selectedReport && (
            <DiagnosisPanel
              report={selectedReport}
              diseases={diseases}
              currentRole={user.role}
              onUpdated={updateDiagnosisInReport}
              onError={showError}
            />
          )}
        </main>
      )}
    </div>
  );
}
