import { clearSession, getToken } from "./auth";
import type {
  ApiResponse,
  ApprovalRequest,
  ApprovalRequestDetail,
  ApprovalRequestFilter,
  CropSeason,
  CropSeasonCreateRequest,
  CropSeasonSuggestion,
  Diagnosis,
  DiagnosisApplyRequest,
  HarvestReadiness,
  HarvestRequest,
  DiagnosisCreateRequest,
  DiagnosisRejectRequest,
  Disease,
  DiseaseReport,
  SymptomSuggestion,
  Household,
  HouseholdCreateRequest,
  HouseholdCreateResult,
  IsolationStatus,
  KnowledgeAnswer,
  Page,
  Parcel,
  ParcelCreateRequest,
  Pesticide,
  RiceVariety,
  TreatmentApplication,
  TreatmentRule,
} from "./types";

const API_BASE = (import.meta.env.VITE_API_BASE_URL ?? "http://localhost:9080").replace(/\/$/, "");
const API_V1 = `${API_BASE}/api/v1`;

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
  const token = await getToken();
  const headers = new Headers(init?.headers);
  if (token) headers.set("Authorization", `Bearer ${token}`);

  const response = await fetch(`${API_V1}${path}`, { ...init, headers });
  const contentType = response.headers.get("content-type") ?? "";
  const body: ApiResponse<T> | null = contentType.includes("application/json")
    ? await response.json()
    : null;

  if (response.status === 401) {
    clearSession();
  }
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
export const listHouseholds = () => request<Household[]>("/households");
export const linkHouseholdAccount = (code: string, username: string) =>
  request<Household>(`/households/${encodeURIComponent(code)}/account`, json("PATCH", { username }));
export const createHousehold = (payload: HouseholdCreateRequest) =>
  request<HouseholdCreateResult>("/households", json("POST", payload));

export const listParcels = () => request<Parcel[]>("/parcels");
export const createParcel = (payload: ParcelCreateRequest) =>
  request<Parcel>("/parcels", json("POST", payload));

export const deleteParcel = (id: number) => request<null>(`/parcels/${id}`, { method: "DELETE" });
export const deleteCropSeason = (id: number) =>
  request<null>(`/crop-seasons/${id}`, { method: "DELETE" });
export const deleteDiseaseReport = (id: number) =>
  request<null>(`/disease-reports/${id}`, { method: "DELETE" });

// Crop season
export const listCropSeasons = (parcelCode: string) =>
  request<CropSeason[]>(`/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`);
export const createCropSeason = (parcelCode: string, payload: CropSeasonCreateRequest) =>
  request<CropSeason>(`/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`, json("POST", payload));
export const harvestCropSeason = (id: number, payload: HarvestRequest) =>
  request<CropSeason>(`/crop-seasons/${id}/harvest`, json("POST", payload));
export const getHarvestReadiness = (id: number) =>
  request<HarvestReadiness>(`/crop-seasons/${id}/harvest-readiness`);
export const getIsolationStatus = (id: number) =>
  request<IsolationStatus>(`/crop-seasons/${id}/isolation-status`);

export const listRiceVarieties = () => request<RiceVariety[]>("/rice-varieties");
export const suggestCropSeason = (riceVarietyCode: string, startDate: string) =>
  request<CropSeasonSuggestion>(
    `/crop-seasons/suggestion?${new URLSearchParams({ riceVarietyCode, startDate })}`,
  );

// Requests waiting for a decision (ENGINEER, ADMIN)
export const listApprovalRequests = (filter: ApprovalRequestFilter) => {
  const params = new URLSearchParams();
  Object.entries(filter).forEach(([key, value]) => {
    if (value !== undefined && value !== "") params.set(key, String(value));
  });
  return request<Page<ApprovalRequest>>(`/approval-requests?${params}`);
};
export const getApprovalRequest = (caseId: number) =>
  request<ApprovalRequestDetail>(`/approval-requests/${caseId}`);
export const assignCase = (caseId: number) =>
  request<unknown>(`/workflow-cases/${caseId}/assign`, { method: "PATCH" });
export const approveCase = (caseId: number) =>
  request<unknown>(`/workflow-cases/${caseId}/approve`, { method: "PATCH" });
export const rejectCase = (caseId: number, reason: string) =>
  request<unknown>(`/workflow-cases/${caseId}/reject`, json("PATCH", { reason }));

// Disease report (reportedBy lấy từ user đang đăng nhập, không gửi từ client)
export const listDiseaseReports = (cropSeasonId: number) =>
  request<DiseaseReport[]>(`/crop-seasons/${cropSeasonId}/disease-reports`);
export const getDiseaseReport = (id: number) => request<DiseaseReport>(`/disease-reports/${id}`);
export const createDiseaseReport = (cropSeasonId: number, image: File) => {
  const form = new FormData();
  form.append("image", image);
  return request<DiseaseReport>(`/crop-seasons/${cropSeasonId}/disease-reports`, {
    method: "POST",
    body: form,
  });
};

// Diagnosis (approvedBy/rejectedBy lấy từ kỹ sư đang đăng nhập, không gửi từ client)
export const createDiagnosis = (reportId: number, payload: DiagnosisCreateRequest) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis`, json("POST", payload));
export const createAiDiagnosis = (reportId: number) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis/ai`, { method: "POST" });
export const approveDiagnosis = (reportId: number) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis/approve`, { method: "POST" });
export const rejectDiagnosis = (reportId: number, payload: DiagnosisRejectRequest) =>
  request<Diagnosis>(`/disease-reports/${reportId}/diagnosis/reject`, json("POST", payload));
export const confirmTreatment = (reportId: number) =>
  request<TreatmentApplication>(`/disease-reports/${reportId}/treatment-application/confirm`, {
    method: "POST",
  });
export const rejectTreatment = (reportId: number, payload: DiagnosisRejectRequest) =>
  request<TreatmentApplication>(
    `/disease-reports/${reportId}/treatment-application/reject`,
    json("POST", payload),
  );
export const applyDiagnosis = (reportId: number, payload: DiagnosisApplyRequest) =>
  request<TreatmentApplication>(`/disease-reports/${reportId}/diagnosis/apply`, json("POST", payload));

// Structured knowledge (read-only reference)
export const listDiseases = () => request<Disease[]>("/diseases");
export const listPesticides = () => request<Pesticide[]>("/pesticides");
export const listTreatmentRules = () => request<TreatmentRule[]>("/treatment-rules");

export const askKnowledge = (question: string, diseaseCode?: string) =>
  request<KnowledgeAnswer>("/knowledge/ask", json("POST", { question, diseaseCode: diseaseCode || null }));

export const suggestDiagnosis = (reportId: number, symptomText: string) =>
  request<SymptomSuggestion[]>(`/disease-reports/${reportId}/diagnosis/suggest`, json("POST", { symptomText }));
export const requestReportRetake = (reportId: number, reason: string) =>
  request<DiseaseReport>(`/disease-reports/${reportId}/request-retake`, json("PATCH", { reason }));
export const diagnosisHistory = (reportId: number) =>
  request<Diagnosis[]>(`/disease-reports/${reportId}/diagnosis/history`);
