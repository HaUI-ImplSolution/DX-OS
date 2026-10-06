import { request, upload, type UploadFile } from "./client";
import type { CropSeason, DiseaseReport, IsolationStatus, Parcel, ImagePrecheck } from "@/types";

export const listParcels = () => request<Parcel[]>("/parcels");

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
