import type {
  ApiResponse,
  CropSeason,
  CropSeasonCreateRequest,
  Diagnosis,
  DiagnosisApplyRequest,
  DiagnosisApproveRequest,
  DiagnosisCreateRequest,
  DiagnosisRejectRequest,
  Disease,
  DiseaseReport,
  Parcel,
  ParcelCreateRequest,
  Pesticide,
  TreatmentApplication,
  TreatmentRule,
} from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:8081").replace(/\/$/, "");
const API_V1 = `${API_BASE}/api/v1`;

export const staticUrl = (path: string) => `${API_BASE}/${path.replace(/^\//, "")}`;

export class ApiError extends Error {
  status: number;
  data: unknown;

  constructor(message: string, status: number, data: unknown) {
    super(message);
    this.name = "ApiError";
    this.status = status;
    this.data = data;
  }
}

async function request<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(`${API_V1}${path}`, init);
  const contentType = response.headers.get("content-type") ?? "";
  const body: ApiResponse<T> | null = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (!response.ok || !body?.success) {
    const message = body?.message ?? `Lỗi HTTP ${response.status}`;
    throw new ApiError(message, response.status, body?.data);
  }
  return body.data;
}

function json(method: string, payload: unknown): RequestInit {
  return {
    method,
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(payload),
  };
}

// Parcel
export const listParcels = () => request<Parcel[]>("/parcels");
export const createParcel = (payload: ParcelCreateRequest) =>
  request<Parcel>("/parcels", json("POST", payload));

// Crop season
export const listCropSeasons = (parcelCode: string) =>
  request<CropSeason[]>(`/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`);
export const createCropSeason = (parcelCode: string, payload: CropSeasonCreateRequest) =>
  request<CropSeason>(`/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`, json("POST", payload));
export const harvestCropSeason = (id: number) =>
  request<CropSeason>(`/crop-seasons/${id}/harvest`, { method: "POST" });

// Disease report
export const listDiseaseReports = (cropSeasonId: number) =>
  request<DiseaseReport[]>(`/crop-seasons/${cropSeasonId}/disease-reports`);
export const getDiseaseReport = (id: number) => request<DiseaseReport>(`/disease-reports/${id}`);
export const createDiseaseReport = (cropSeasonId: number, image: File, reportedBy: string) => {
  const form = new FormData();
  form.append("image", image);
  const query = new URLSearchParams({ reportedBy });
  return request<DiseaseReport>(
    `/crop-seasons/${cropSeasonId}/disease-reports?${query.toString()}`,
    { method: "POST", body: form },
  );
};

// Diagnosis
export const createDiagnosis = (reportId: number, payload: DiagnosisCreateRequest) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis`, json("POST", payload));
export const approveDiagnosis = (reportId: number, payload: DiagnosisApproveRequest) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis/approve`, json("POST", payload));
export const rejectDiagnosis = (reportId: number, payload: DiagnosisRejectRequest) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis/reject`, json("POST", payload));
export const applyDiagnosis = (reportId: number, payload: DiagnosisApplyRequest) =>
  request<TreatmentApplication>(`/disease-reports/${reportId}/diagnosis/apply`, json("POST", payload));

// Structured knowledge (read-only reference)
export const listDiseases = () => request<Disease[]>("/diseases");
export const listPesticides = () => request<Pesticide[]>("/pesticides");
export const listTreatmentRules = () => request<TreatmentRule[]>("/treatment-rules");
