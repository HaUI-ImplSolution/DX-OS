export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  errorCode?: string;
}

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
}

export interface DiseaseReport {
  id: number;
  cropSeasonId: number;
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

// Statuses of the standard approval flow; a case type may declare others, so a status is a string.
export type KnownCaseStatus = ApprovalStatus | "CANCELLED";

export type CaseStatus = string;

export interface ApprovalRequest {
  caseId: number;
  caseType: CaseType;
  status: CaseStatus;
  title: string;
  householdCode: string | null;
  farmerName: string | null;
  details: Record<string, string | number | boolean | null>;
  createdAt: string;
  assignedTo: string | null;
  assignedToName: string | null;
  updatedAt: string;
}

export interface WorkflowEvent {
  id: number;
  action: string;
  fromStatus: string | null;
  toStatus: string;
  actorUserId: string | null;
  actorDisplayName: string | null;
  reason: string | null;
  occurredAt: string;
  metadata: Record<string, unknown> | null;
}

export interface ApprovalRequestDetail {
  request: ApprovalRequest;
  events: WorkflowEvent[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface ApprovalRequestFilter {
  caseType?: CaseType;
  status?: CaseStatus;
  householdCode?: string;
  assignedTo?: string;
  unassigned?: boolean;
  from?: string;
  to?: string;
  page?: number;
  size?: number;
}

export interface KnowledgeSource {
  sourceId: string;
  title: string;
  organization: string | null;
  url: string | null;
  license: string | null;
  reviewStatus: string | null;
}

export interface KnowledgePassage {
  content: string;
  documentTitle: string;
  diseaseCode: string | null;
  score: number;
  sources: KnowledgeSource[];
}

export interface KnowledgeAnswer {
  question: string;
  passages: KnowledgePassage[];
}
