export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
}

export type Role = "FARMER" | "ENGINEER" | "ADMIN";

export interface Parcel {
  id: number;
  code: string;
  farmerName: string;
  areaHectares: number;
  location: string | null;
  latitude: number | null;
  longitude: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface ParcelCreateRequest {
  code: string;
  farmerName: string;
  areaHectares: number;
  location?: string;
  latitude?: number;
  longitude?: number;
}

export type CropSeasonStatus = "ACTIVE" | "HARVESTED";

export interface CropSeason {
  id: number;
  parcelCode: string;
  riceVariety: string;
  startDate: string;
  expectedHarvestDate: string | null;
  status: CropSeasonStatus;
  harvestedAt: string | null;
}

export interface CropSeasonCreateRequest {
  riceVariety: string;
  startDate: string;
  expectedHarvestDate?: string;
}

export interface IsolationStatus {
  cropSeasonId: number;
  parcelCode: string;
  isolated: boolean;
  harvestLockedUntil: string | null;
  daysRemaining: number | null;
  pesticideCode: string | null;
  message: string;
}

export type ApprovalStatus = "PENDING" | "APPROVED" | "REJECTED";

export interface TreatmentRule {
  id: number;
  diseaseCode: string;
  pesticideCode: string;
  doseMin: number;
  doseMax: number;
  doseUnit: string;
  phiDays: number;
  notes: string | null;
  source: string | null;
  version: string | null;
}

export interface Diagnosis {
  id: number;
  diseaseReportId: number;
  diseaseCode: string;
  diseaseName: string;
  confidence: number;
  modelVersion: string;
  diagnosedAt: string;
  recommendationText: string;
  approvalStatus: ApprovalStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  treatmentRule: TreatmentRule | null;
}

export interface DiseaseReport {
  id: number;
  cropSeasonId: number;
  imageRef: string;
  reportedBy: string;
  reportedAt: string;
  diagnosis: Diagnosis | null;
}

export interface DiagnosisCreateRequest {
  diseaseCode: string;
  confidence: number;
  modelVersion: string;
}

export interface DiagnosisRejectRequest {
  reason: string;
}

export interface DiagnosisApplyRequest {
  doseAmount: number;
  appliedAt?: string;
}

export interface TreatmentApplication {
  id: number;
  cropSeasonId: number;
  diagnosisId: number | null;
  pesticideCode: string;
  doseAmountSnapshot: number;
  doseUnitSnapshot: string;
  phiDaysSnapshot: number;
  appliedAt: string;
  harvestLockedUntil: string;
}

export interface Disease {
  id: number;
  code: string;
  name: string;
  description: string | null;
}

export interface Pesticide {
  id: number;
  code: string;
  name: string;
  unit: string;
}

export interface PokaYokeErrorData {
  errorCode: string;
  harvestLockedUntil: string | null;
}
