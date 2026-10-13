import type { KnowledgeSource, WorkflowEvent } from "../../shell";

export type LabRole = "STUDENT" | "LECTURER" | "TECHNICIAN" | "ADMIN";

export type TicketPriority = "URGENT" | "HIGH" | "NORMAL" | "LOW";

export type TicketStatus =
  | "NEW"
  | "ASSIGNED"
  | "IN_PROGRESS"
  | "WAITING_USER"
  | "RESOLVED"
  | "CLOSED"
  | "REJECTED"
  | "CANCELLED";

export interface Ticket {
  id: number;
  code: string;
  title: string;
  description: string;
  categoryCode: string;
  categoryName: string;
  priority: TicketPriority;
  status: TicketStatus;
  roomId: number | null;
  roomCode: string | null;
  deviceId: number | null;
  deviceAssetCode: string | null;
  requesterId: string;
  requesterName: string;
  assigneeId: string | null;
  assigneeName: string | null;
  responseDueAt: string | null;
  resolutionDueAt: string | null;
  firstRespondedAt: string | null;
  resolvedAt: string | null;
  closedAt: string | null;
  resolutionNote: string | null;
  rootCause: string | null;
  reopenCount: number;
  rating: number | null;
  ratingComment: string | null;
  overdue: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface TicketComment {
  id: number;
  authorId: string;
  authorName: string;
  staff: boolean;
  internal: boolean;
  body: string;
  createdAt: string;
}

export interface TicketAttachment {
  id: number;
  filename: string;
  contentType: string;
  sizeBytes: number;
  url: string;
  createdAt: string;
}

export interface TicketDetail {
  ticket: Ticket;
  comments: TicketComment[];
  attachments: TicketAttachment[];
  timeline: WorkflowEvent[];
}

export interface TicketCreateRequest {
  title: string;
  description: string;
  categoryCode: string;
  priority?: TicketPriority;
  roomId?: number;
  deviceId?: number;
}

export interface TicketFilter {
  status?: string;
  categoryCode?: string;
  priority?: string;
  assignedTo?: string;
  unassigned?: boolean;
  overdue?: boolean;
  requesterId?: string;
  page?: number;
  size?: number;
}

export interface RelatedPassage {
  content: string;
  documentTitle: string;
  score: number;
  sources: KnowledgeSource[];
}

export interface TicketSuggestion {
  categoryCode: string;
  categoryName: string;
  priority: TicketPriority;
  matchedKeywords: string[];
  reasons: string[];
  knowledgeAvailable: boolean;
  relatedKnowledge: RelatedPassage[];
}

export interface TechnicianLoad {
  userId: string;
  name: string;
  openTickets: number;
}

export interface TicketCategory {
  id: number;
  code: string;
  name: string;
  defaultPriority: TicketPriority;
  keywords: string | null;
  active: boolean;
}

export interface TicketCategoryRequest {
  code: string;
  name: string;
  defaultPriority: TicketPriority;
  keywords?: string;
  active?: boolean;
}

export interface SlaPolicy {
  priority: TicketPriority;
  responseMinutes: number;
  resolutionMinutes: number;
  updatedAt: string | null;
}

export type RoomType = "COMPUTER_LAB" | "CLASSROOM" | "MEETING_ROOM";
export type RoomStatus = "AVAILABLE" | "MAINTENANCE";

export interface Room {
  id: number;
  code: string;
  building: string;
  name: string;
  roomType: RoomType;
  capacity: number;
  status: RoomStatus;
}

export interface RoomRequest {
  code: string;
  building: string;
  name: string;
  roomType: RoomType;
  capacity: number;
}

export interface RoomStatusChange {
  room: Room;
  affectedBookings: Booking[];
}

export type DeviceStatus = "AVAILABLE" | "ON_LOAN" | "UNDER_REPAIR" | "RETIRED";

export interface Device {
  id: number;
  assetCode: string;
  deviceType: string;
  model: string | null;
  serialNumber: string | null;
  roomId: number | null;
  roomCode: string | null;
  status: DeviceStatus;
  purchasedOn: string | null;
  warrantyUntil: string | null;
}

export interface DeviceRequest {
  assetCode: string;
  deviceType: string;
  model?: string;
  serialNumber?: string;
  roomId?: number | null;
  purchasedOn?: string;
  warrantyUntil?: string;
}

export type MaintenanceKind = "REPAIR" | "INSPECTION" | "REPLACEMENT";

export interface Maintenance {
  id: number;
  deviceId: number;
  kind: MaintenanceKind;
  note: string;
  ticketId: number | null;
  performedById: string;
  performedByName: string;
  startedAt: string;
  completedAt: string | null;
  completionNote: string | null;
}

export type ReturnCondition = "GOOD" | "DAMAGED";

export interface Loan {
  id: number;
  deviceId: number;
  deviceAssetCode: string;
  deviceType: string;
  borrowerId: string;
  borrowerName: string;
  borrowerRole: string;
  purpose: string;
  dueDate: string;
  status: string;
  approvedByName: string | null;
  approvedAt: string | null;
  rejectionReason: string | null;
  returnedAt: string | null;
  returnCondition: ReturnCondition | null;
  returnNote: string | null;
  overdue: boolean;
  createdAt: string;
}

export interface Booking {
  id: number;
  roomId: number;
  roomCode: string;
  requesterId: string;
  requesterName: string;
  requesterRole: string;
  title: string;
  attendees: number;
  startsAt: string;
  endsAt: string;
  status: string;
  rejectionReason: string | null;
  createdAt: string;
}

export interface BookingCreateRequest {
  roomId: number;
  title: string;
  attendees: number;
  startsAt: string;
  endsAt: string;
}

export interface ScheduleEntry {
  bookingId: number;
  title: string;
  startsAt: string;
  endsAt: string;
  status: string;
  attendees: number;
  requesterName: string;
}

export interface Schedule {
  roomId: number;
  roomCode: string;
  from: string;
  to: string;
  entries: ScheduleEntry[];
}

export interface CountEntry {
  key: string;
  count: number;
}

export interface TechnicianStat {
  userId: string;
  name: string;
  openTickets: number;
  closedTickets: number;
}

export interface RoomUsage {
  roomId: number;
  roomCode: string;
  bookedHours: number;
  utilization: number;
}

export interface LabStats {
  from: string;
  to: string;
  ticketsCreated: number;
  ticketsByStatus: CountEntry[];
  ticketsByCategory: CountEntry[];
  slaAttainment: number | null;
  averageFirstResponseMinutes: number | null;
  averageResolutionMinutes: number | null;
  overdueTickets: number;
  averageRating: number | null;
  ratedTickets: number;
  technicians: TechnicianStat[];
  roomUsage: RoomUsage[];
  devicesByStatus: Record<string, number>;
  overdueLoans: number;
}
