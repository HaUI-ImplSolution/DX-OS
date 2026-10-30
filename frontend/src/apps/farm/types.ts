// SPDX-License-Identifier: Apache-2.0
import type { GeneratedAnswer } from "../../shell";
import type { ApprovalRequest as BaseApprovalRequest, KnowledgePassage } from "../../shell";

export type {
  ApiResponse,
  ApprovalRequestDetail,
  ApprovalRequestFilter,
  CaseStatus,
  KnowledgeAnswer,
  KnowledgePassage,
  KnowledgeSource,
  Page,
  WorkflowEvent,
} from "../../shell";

export type Role = "FARMER" | "ENGINEER" | "ADMIN";

export interface Household {
  id: number;
  code: string;
  fullName: string;
  phone: string | null;
  address: string | null;
  accountLinked: boolean;
  createdAt: string;
}

export interface HouseholdCreateRequest {
  fullName: string;
  username: string;
  phone?: string;
  address?: string;
  email?: string;
}

export interface HouseholdCreateResult {
  household: Household;
  username: string;
  temporaryPassword: string;
}

export interface Parcel {
  id: number;
  code: string;
  householdCode: string;
  farmerName: string;
  areaHectares: number;
  location: string | null;
  latitude: number | null;
  longitude: number | null;
  gpsAccuracyMeters: number | null;
  locationMissing: boolean;
  approvalStatus: ApprovalStatus;
  rejectionReason: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface ParcelCreateRequest {
  householdCode: string;
  areaHectares: number;
  location?: string;
  latitude: number;
  longitude: number;
}

export type CropSeasonStatus = "ACTIVE" | "HARVESTED";

export type SeasonType = "DONG_XUAN" | "HE_THU" | "THU_DONG";

export interface CropSeason {
  id: number;
  code: string | null;
  parcelCode: string;
  riceVariety: string;
  riceVarietyCode: string | null;
  seasonType: SeasonType | null;
  startDate: string;
  expectedHarvestDate: string | null;
  reasonForOverride: string | null;
  status: CropSeasonStatus;
  approvalStatus: ApprovalStatus;
  rejectionReason: string | null;
  harvestedAt: string | null;
  actualHarvestDate: string | null;
  yieldKg: number | null;
  harvestedAreaHectares: number | null;
  harvestedByName: string | null;
  earlyReason: string | null;
  harvestOnBehalfReason: string | null;
}

export interface HarvestRequest {
  actualHarvestDate?: string;
  yieldKg: number;
  harvestedAreaHectares?: number;
  confirmEarly?: boolean;
  earlyReason?: string;
  reasonOnBehalf: string;
}

export interface HarvestReadiness {
  cropSeasonId: number;
  expectedHarvestDate: string | null;
  daysBeforeExpected: number;
  earlyConfirmationRequired: boolean;
  isolation: IsolationStatus;
  warnings: { code: string; message: string }[];
}

export interface CropSeasonCreateRequest {
  riceVarietyCode: string;
  startDate: string;
  seasonType?: SeasonType;
  expectedHarvestDate?: string;
  reasonForOverride?: string;
}

export interface RiceVariety {
  code: string;
  name: string;
  growthDaysMin: number;
  growthDaysMax: number;
  source: string;
  reviewStatus: "APPROVED" | "IN_REVIEW";
}

export interface CropSeasonSuggestion {
  seasonType: SeasonType | null;
  expectedHarvestDate: string;
  earliestHarvestDate: string;
  latestHarvestDate: string;
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

export interface Weather {
  parcelCode: string;
  latitude: number;
  longitude: number;
  isSnapshot: boolean;
  source: string;
  fetchedAt: string;
  note: string | null;
  current: {
    temperatureC: number;
    humidityPercent: number;
    precipitationMm: number;
    windSpeedKmh: number;
    weatherCode: number;
  };
  daily: {
    date: string;
    tempMaxC: number;
    tempMinC: number;
    precipitationMm: number;
    precipitationProbabilityPercent: number;
  }[];
}

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

// For FARMER only id, diseaseReportId and approvalStatus are guaranteed;
// REJECTED adds decidedAt and rejectionReason, and the rest is set once APPROVED.
export interface Diagnosis {
  generatedExplanation?: GeneratedAnswer | null;
  id: number;
  diseaseReportId: number;
  diseaseCode: string | null;
  diseaseName: string | null;
  confidence: number | null;
  modelVersion: string | null;
  diagnosedAt: string | null;
  recommendationText: string | null;
  approvalStatus: ApprovalStatus;
  decidedBy: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  treatmentRule: TreatmentRule | null;
  // Reference passages, null while a farmer may not see the diagnosis
  knowledgeEvidence: KnowledgePassage[] | null;
}

export interface DiseaseReport {
  id: number;
  cropSeasonId: number;
  parcelCode: string;
  imageUrl: string;
  reportedBy: string;
  reportedAt: string;
  diagnosis: Diagnosis | null;
  status: "PENDING" | "NEEDS_RETAKE" | "PROCESSED";
  retakeReason: string | null;
  retakeOf: number | null;
  retakeIds: number[];
  quality: ImagePrecheck | null;
}

// A report nobody has diagnosed yet, as the engineers' to-do list shows it.
export interface AwaitingReport {
  reportId: number;
  cropSeasonId: number;
  parcelCode: string;
  farmerName: string | null;
  riceVariety: string | null;
  reportedBy: string;
  reportedAt: string;
}

export interface DiagnosisCreateRequest {
  symptomText?: string;
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
  confirmationStatus: "PENDING_CONFIRMATION" | "CONFIRMED" | "REJECTED";
  reportedByFarmer: boolean;
  rejectionReason: string | null;
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

export interface ImagePrecheck {
  passed: boolean;
  reasons: string[];
  width: number;
  height: number;
  sharpness: number;
  brightness: number;
}

export interface SymptomSuggestion {
  disease_code: string;
  disease_name: string;
  score: number;
  evidence: string;
  source_ids: string[];
}

export type CaseType =
  | "PARCEL_APPROVAL"
  | "PARCEL_EDIT_REQUEST"
  | "CROP_SEASON_APPROVAL"
  | "DIAGNOSIS_APPROVAL"
  | "TREATMENT_CONFIRMATION";

// Statuses of the standard approval flow.
export type KnownCaseStatus = ApprovalStatus | "CANCELLED";

export interface ApprovalRequest extends BaseApprovalRequest {
  caseType: CaseType;
  householdCode: string | null;
  farmerName: string | null;
}
