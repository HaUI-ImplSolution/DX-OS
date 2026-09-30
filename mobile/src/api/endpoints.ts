import { request, staticUrl } from "./client";
import type { CropSeason, DiseaseReport, IsolationStatus, Parcel } from "@/types";

export const listParcels = () => request<Parcel[]>("/parcels");

export const listCropSeasons = (parcelCode: string) =>
  request<CropSeason[]>(`/parcels/${encodeURIComponent(parcelCode)}/crop-seasons`);

export const getIsolationStatus = (cropSeasonId: number) =>
  request<IsolationStatus>(`/crop-seasons/${cropSeasonId}/isolation-status`);

export const listDiseaseReports = (cropSeasonId: number) =>
  request<DiseaseReport[]>(`/crop-seasons/${cropSeasonId}/disease-reports`);

export const getDiseaseReport = (id: number) => request<DiseaseReport>(`/disease-reports/${id}`);

export const reportImageUrl = (imageRef: string) => staticUrl(`uploads/${imageRef}`);
