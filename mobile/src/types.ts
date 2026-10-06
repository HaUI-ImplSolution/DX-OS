export interface ApiResponse<T> {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
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
