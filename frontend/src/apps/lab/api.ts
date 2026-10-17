import { json, queryString, request } from "../../shell";
import type { Page } from "../../shell";
import type {
  Booking,
  BookingCreateRequest,
  Device,
  DeviceRequest,
  LabStats,
  Loan,
  Maintenance,
  MaintenanceKind,
  ReturnCondition,
  Room,
  RoomRequest,
  RoomStatus,
  RoomStatusChange,
  Schedule,
  SlaPolicy,
  TechnicianLoad,
  Ticket,
  TicketAttachment,
  TicketCategory,
  TicketCategoryRequest,
  TicketComment,
  TicketCreateRequest,
  TicketDetail,
  TicketFilter,
  TicketPriority,
  TicketSuggestion,
  TicketUpdateRequest,
} from "./types";

// Tickets
export const listTickets = (filter: TicketFilter) =>
  request<Page<Ticket>>(`/tickets${queryString({ ...filter })}`);
export const getTicket = (id: number) => request<TicketDetail>(`/tickets/${id}`);
export const createTicket = (payload: TicketCreateRequest, files: File[]) => {
  const form = new FormData();
  form.append("ticket", new Blob([JSON.stringify(payload)], { type: "application/json" }));
  files.forEach((file) => form.append("files", file));
  return request<TicketDetail>("/tickets", {
    method: "POST",
    body: form,
    headers: { "Idempotency-Key": crypto.randomUUID() },
  });
};
export const updateTicket = (id: number, payload: TicketUpdateRequest) =>
  request<Ticket>(`/tickets/${id}`, json("PUT", payload));
export const removeTicketAttachment = (id: number, attachmentId: number) =>
  request<null>(`/tickets/${id}/attachments/${attachmentId}`, { method: "DELETE" });
export const suggestTicket = (title: string, description: string) =>
  request<TicketSuggestion>("/tickets/suggest", json("POST", { title, description }));
export const listTechnicians = () => request<TechnicianLoad[]>("/tickets/technicians");
export const addTicketComment = (id: number, body: string, internal: boolean) =>
  request<TicketComment>(`/tickets/${id}/comments`, json("POST", { body, internal }));
export const addTicketAttachment = (id: number, file: File) => {
  const form = new FormData();
  form.append("file", file);
  return request<TicketAttachment>(`/tickets/${id}/attachments`, { method: "POST", body: form });
};
export const assignTicket = (id: number, assignee?: { assigneeId: string; assigneeName: string }) =>
  request<Ticket>(`/tickets/${id}/assign`, assignee ? json("POST", assignee) : { method: "POST" });
export const startTicket = (id: number) => request<Ticket>(`/tickets/${id}/start`, { method: "POST" });
export const waitTicket = (id: number, message: string) =>
  request<Ticket>(`/tickets/${id}/wait`, json("POST", { message }));
export const resolveTicket = (id: number, resolutionNote: string, rootCause: string) =>
  request<Ticket>(`/tickets/${id}/resolve`, json("POST", { resolutionNote, rootCause }));
export const confirmTicket = (id: number, rating: number, comment: string) =>
  request<Ticket>(`/tickets/${id}/confirm`, json("POST", { rating, comment: comment || null }));
export const reopenTicket = (id: number, reason: string) =>
  request<Ticket>(`/tickets/${id}/reopen`, json("POST", { reason }));
export const cancelTicket = (id: number) => request<Ticket>(`/tickets/${id}/cancel`, { method: "POST" });
export const rejectTicket = (id: number, reason: string) =>
  request<Ticket>(`/tickets/${id}/reject`, json("POST", { reason }));

// Catalogue
export const listCategories = (includeInactive = false) =>
  request<TicketCategory[]>(`/ticket-categories${queryString({ includeInactive })}`);
export const createCategory = (payload: TicketCategoryRequest) =>
  request<TicketCategory>("/ticket-categories", json("POST", payload));
export const updateCategory = (id: number, payload: TicketCategoryRequest) =>
  request<TicketCategory>(`/ticket-categories/${id}`, json("PUT", payload));
export const listSlaPolicies = () => request<SlaPolicy[]>("/sla-policies");
export const saveSlaPolicy = (
  priority: TicketPriority,
  responseMinutes: number,
  resolutionMinutes: number,
) => request<SlaPolicy>(`/sla-policies/${priority}`, json("PUT", { responseMinutes, resolutionMinutes }));

// Rooms and devices
export const listRooms = () => request<Room[]>("/rooms");
export const createRoom = (payload: RoomRequest) => request<Room>("/rooms", json("POST", payload));
export const updateRoom = (id: number, payload: RoomRequest) =>
  request<Room>(`/rooms/${id}`, json("PUT", payload));
export const changeRoomStatus = (id: number, status: RoomStatus) =>
  request<RoomStatusChange>(`/rooms/${id}/status`, json("PATCH", { status }));
export const getRoomSchedule = (id: number, from: string, to: string) =>
  request<Schedule>(`/rooms/${id}/schedule${queryString({ from, to })}`);

export const listDevices = (filter: { roomId?: number; status?: string; deviceType?: string } = {}) =>
  request<Device[]>(`/devices${queryString(filter)}`);
export const createDevice = (payload: DeviceRequest) =>
  request<Device>("/devices", json("POST", payload));
export const updateDevice = (id: number, payload: DeviceRequest) =>
  request<Device>(`/devices/${id}`, json("PUT", payload));
export const retireDevice = (id: number) => request<Device>(`/devices/${id}/retire`, { method: "POST" });
export const listMaintenance = (deviceId: number) =>
  request<Maintenance[]>(`/devices/${deviceId}/maintenance`);
export const startMaintenance = (
  deviceId: number,
  payload: { kind: MaintenanceKind; note: string; ticketId?: number },
) => request<Maintenance>(`/devices/${deviceId}/maintenance`, json("POST", payload));
export const completeMaintenance = (deviceId: number, maintenanceId: number, note: string) =>
  request<Maintenance>(
    `/devices/${deviceId}/maintenance/${maintenanceId}/complete`,
    json("POST", { note: note || null }),
  );

// Device loans
export const listLoans = (filter: {
  status?: string;
  deviceId?: number;
  mine?: boolean;
  toApprove?: boolean;
  page?: number;
  size?: number;
}) =>
  request<Page<Loan>>(`/device-loans${queryString(filter)}`);
export const createLoan = (deviceId: number, purpose: string, dueDate: string) =>
  request<Loan>("/device-loans", json("POST", { deviceId, purpose, dueDate }));
export const approveLoan = (id: number) => request<Loan>(`/device-loans/${id}/approve`, { method: "POST" });
export const rejectLoan = (id: number, reason: string) =>
  request<Loan>(`/device-loans/${id}/reject`, json("POST", { reason }));
export const cancelLoan = (id: number) => request<Loan>(`/device-loans/${id}/cancel`, { method: "POST" });
export const returnLoan = (id: number, condition: ReturnCondition, note: string) =>
  request<Loan>(`/device-loans/${id}/return`, json("POST", { condition, note: note || null }));

// Room bookings
export const listBookings = (filter: { status?: string; roomId?: number; page?: number; size?: number }) =>
  request<Page<Booking>>(`/room-bookings${queryString(filter)}`);
export const createBooking = (payload: BookingCreateRequest) =>
  request<Booking>("/room-bookings", json("POST", payload));
export const approveBooking = (id: number) =>
  request<Booking>(`/room-bookings/${id}/approve`, { method: "POST" });
export const rejectBooking = (id: number, reason: string) =>
  request<Booking>(`/room-bookings/${id}/reject`, json("POST", { reason }));
export const cancelBooking = (id: number) =>
  request<Booking>(`/room-bookings/${id}/cancel`, { method: "POST" });

// Statistics
export const getLabStats = (from?: string, to?: string) =>
  request<LabStats>(`/lab-stats/summary${queryString({ from, to })}`);
