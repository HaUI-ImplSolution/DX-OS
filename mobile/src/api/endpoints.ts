import { json, request, upload, type UploadFile } from "./client";
import type {
  CropSeason,
  CropSeasonInput,
  CropSeasonSuggestion,
  DiseaseReport,
  ImagePrecheck,
  IsolationStatus,
  Parcel,
  ParcelInput,
  RiceVariety,
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
