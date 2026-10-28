export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
  errorCode?: string;
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

// latitude, longitude and gpsAccuracyMeters come from one GPS fix; mockedLocation is set when
// the phone reports the position as faked.
export interface ParcelInput {
  areaHectares: number;
  location?: string;
  latitude: number;
  longitude: number;
  gpsAccuracyMeters: number;
  mockedLocation: boolean;
  reason?: string;
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

export interface CropSeasonInput {
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

export interface AppManifest {
  id: string;
  name: string;
  settings: Record<string, string>;
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

export interface GeneratedExplanation {
  version: 1;
  mode: "generated" | "extractive" | "insufficient_context";
  summary: string;
  steps: string[];
  note?: string | null;
  sourceIds: string[];
  reason?: "insufficient_evidence" | "out_of_scope" | "requires_authority" | "generation_failed" | "conflicting_output" | null;
}

// For FARMER only id, diseaseReportId and approvalStatus are guaranteed;
// REJECTED adds decidedAt and rejectionReason, and the rest is set once APPROVED.
export interface Diagnosis {
  generatedExplanation?: GeneratedExplanation | null;
  id: number;
  diseaseReportId: number;
  approvalStatus: ApprovalStatus;
  diseaseCode: string | null;
  diseaseName: string | null;
  confidence: number | null;
  modelVersion: string | null;
  diagnosedAt: string | null;
  recommendationText: string | null;
  decidedBy: string | null;
  decidedAt: string | null;
  rejectionReason: string | null;
  treatmentRule: TreatmentRule | null;
  knowledgeEvidence: KnowledgePassage[] | null;
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

export interface ImagePrecheck {
  passed: boolean;
  reasons: string[];
  width: number;
  height: number;
  sharpness: number;
  brightness: number;
}

export interface HarvestWarning {
  code: "PENDING_REPORTS" | "UNTREATED_RECOMMENDATIONS";
  message: string;
}

export interface HarvestReadiness {
  cropSeasonId: number;
  expectedHarvestDate: string | null;
  daysBeforeExpected: number;
  earlyConfirmationRequired: boolean;
  isolation: IsolationStatus;
  warnings: HarvestWarning[];
}

export interface HarvestInput {
  actualHarvestDate?: string;
  yieldKg: number;
  harvestedAreaHectares?: number;
  confirmEarly?: boolean;
  earlyReason?: string;
}

export interface HarvestReschedule {
  id: number;
  previousDate: string;
  newDate: string;
  reason: string;
  rescheduledByName: string | null;
  rescheduledAt: string;
}

export type TreatmentConfirmationStatus = "PENDING_CONFIRMATION" | "CONFIRMED" | "REJECTED";

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
  confirmationStatus: TreatmentConfirmationStatus;
  reportedByFarmer: boolean;
  rejectionReason: string | null;
}

export interface TreatmentInput {
  doseAmount: number;
  appliedAt?: string;
}

export type NotificationType =
  | "PARCEL_APPROVED"
  | "PARCEL_REJECTED"
  | "PARCEL_EDIT_APPROVED"
  | "PARCEL_EDIT_REJECTED"
  | "CROP_SEASON_APPROVED"
  | "CROP_SEASON_REJECTED"
  | "DIAGNOSIS_APPROVED"
  | "DIAGNOSIS_REJECTED"
  | "TREATMENT_CONFIRMED"
  | "TREATMENT_REJECTED"
  | "HARVEST_REMINDER"
  | "HARVEST_RECORDED"
  | "RETAKE_REQUESTED";

// An app may register other types; one the client does not know is shown by its title and body.
export interface AppNotification {
  id: number;
  type: NotificationType | (string & {});
  title: string;
  body: string;
  relatedEntityType: "PARCEL" | "CROP_SEASON" | "DISEASE_REPORT" | (string & {}) | null;
  relatedEntityRef: string | null;
  readAt: string | null;
  createdAt: string;
}

export interface Weather {
  parcelCode: string;
  isSnapshot: boolean;
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

export interface KnowledgeSource {
  sourceId: string;
  title: string;
  organization: string | null;
  url: string | null;
  license: string | null;
  reviewStatus: string | null;
}

export interface KnowledgePassage {
  documentTitle: string;
  content: string;
  score: number;
  sources: KnowledgeSource[];
}

export interface KnowledgeAnswer {
  passages: KnowledgePassage[];
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
}
