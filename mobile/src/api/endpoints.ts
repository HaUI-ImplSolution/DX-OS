// SPDX-License-Identifier: Apache-2.0
import { ApiError, json, request, upload, type UploadFile } from "./client";
import type {
  AppManifest,
  AppNotification,
  CropSeason,
  CropSeasonInput,
  CropSeasonSuggestion,
  DiseaseReport,
  ImagePrecheck,
  HarvestInput,
  HarvestReadiness,
  HarvestReschedule,
  IsolationStatus,
  KnowledgeAnswer,
  Page,
  Parcel,
  ParcelInput,
  RiceVariety,
  TreatmentApplication,
  TreatmentInput,
  Weather,
} from "@/types";

export const listParcels = () => request<Parcel[]>("/parcels");

export const getParcel = (code: string) =>
  request<Parcel>(`/parcels/${encodeURIComponent(code)}`);

const withKey = (method: string, payload: unknown, idempotencyKey?: string): RequestInit => {
  const init = json(method, payload);
  return idempotencyKey
    ? { ...init, headers: { ...(init.headers as Record<string, string>), "Idempotency-Key": idempotencyKey } }
    : init;
};

export const createParcel = (input: ParcelInput, idempotencyKey?: string) =>
  request<Parcel>("/parcels", withKey("POST", input, idempotencyKey));

export const resubmitParcel = (id: number, input: ParcelInput) =>
  request<Parcel>(`/parcels/${id}/resubmit`, json("POST", input));

export const requestParcelEdit = (id: number, input: ParcelInput) =>
  request<Parcel>(`/parcels/${id}/edit-requests`, json("POST", input));

export const createCropSeason = (
  parcelCode: string,
  input: CropSeasonInput,
  idempotencyKey?: string,
) =>
  request<CropSeason>(
    `/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`,
    withKey("POST", input, idempotencyKey),
  );

export const resubmitCropSeason = (id: number, input: CropSeasonInput) =>
  request<CropSeason>(`/crop-seasons/${id}/resubmit`, json("POST", input));

export const listRiceVarieties = () => request<RiceVariety[]>("/rice-varieties");

export const suggestCropSeason = (riceVarietyCode: string, startDate: string) =>
  request<CropSeasonSuggestion>(
    `/crop-seasons/suggestion?${new URLSearchParams({ riceVarietyCode, startDate })}`,
  );

export const listCropSeasons = (parcelCode: string) =>
  request<CropSeason[]>(`/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`);

export const getAppManifest = () => request<AppManifest>("/app");

export const listIsolatedSeasons = () => request<IsolationStatus[]>("/crop-seasons/isolated");

export const getIsolationStatus = (cropSeasonId: number) =>
  request<IsolationStatus>(`/crop-seasons/${cropSeasonId}/isolation-status`);

export const listDiseaseReports = (cropSeasonId: number) =>
  request<DiseaseReport[]>(`/crop-seasons/${cropSeasonId}/disease-reports`);

export const getDiseaseReport = (id: number) => request<DiseaseReport>(`/disease-reports/${id}`);

export const createDiseaseReport = (
  cropSeasonId: number,
  image: UploadFile,
  onProgress?: (fraction: number) => void,
  idempotencyKey?: string,
  retakeOf?: number,
) =>
  upload<DiseaseReport>(
    `/crop-seasons/${cropSeasonId}/disease-reports${retakeOf ? `?retakeOf=${retakeOf}` : ""}`,
    "image",
    image,
    onProgress,
    idempotencyKey ? { "Idempotency-Key": idempotencyKey } : {},
  );

export const precheckDiseaseReport = (image: UploadFile) =>
  upload<ImagePrecheck>("/disease-reports/precheck", "image", image);

export const getCropSeason = (id: number) => request<CropSeason>(`/crop-seasons/${id}`);

export const getHarvestReadiness = (cropSeasonId: number) =>
  request<HarvestReadiness>(`/crop-seasons/${cropSeasonId}/harvest-readiness`);

export const harvestCropSeason = (cropSeasonId: number, input: HarvestInput) =>
  request<CropSeason>(`/crop-seasons/${cropSeasonId}/harvest`, json("POST", input));

export const rescheduleHarvest = (
  cropSeasonId: number,
  input: { newExpectedHarvestDate: string; reason: string },
) => request<CropSeason>(`/crop-seasons/${cropSeasonId}/reschedule-harvest`, json("PATCH", input));

export const listHarvestReschedules = (cropSeasonId: number) =>
  request<HarvestReschedule[]>(`/crop-seasons/${cropSeasonId}/harvest-reschedules`);

// Resolves to null while nothing was sprayed for the report.
export const getTreatmentApplication = async (reportId: number) => {
  try {
    return await request<TreatmentApplication>(`/disease-reports/${reportId}/treatment-application`);
  } catch (e) {
    if (e instanceof ApiError && e.status === 404) return null;
    throw e;
  }
};

export const reportSpraying = (reportId: number, input: TreatmentInput) =>
  request<TreatmentApplication>(
    `/disease-reports/${reportId}/treatment-application/self-report`,
    json("POST", input),
  );

export const getParcelWeather = (code: string) =>
  request<Weather>(`/parcels/${encodeURIComponent(code)}/weather`);

export const askKnowledge = (question: string) =>
  request<KnowledgeAnswer>("/knowledge/ask", json("POST", { question }));

export const listNotifications = (page = 0, size = 30) =>
  request<Page<AppNotification>>(`/notifications?${new URLSearchParams({ page: String(page), size: String(size) })}`);

export const getUnreadCount = () => request<{ unread: number }>("/notifications/unread-count");

export const markNotificationRead = (id: number) =>
  request<AppNotification>(`/notifications/${id}/read`, { method: "PATCH" });

export const markAllNotificationsRead = () =>
  request<{ updated: number }>("/notifications/read-all", { method: "PATCH" });
