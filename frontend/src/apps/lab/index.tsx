import type { AppUiModule, Notification } from "../../shell";
import { LAB_LABELS, ROLE_LABEL } from "./labels";
import Assets from "./pages/Assets";
import Bookings from "./pages/Bookings";
import Catalog from "./pages/Catalog";
import Home from "./pages/Home";
import Knowledge from "./pages/Knowledge";
import Loans from "./pages/Loans";
import Queue from "./pages/Queue";
import Stats from "./pages/Stats";
import TicketDetail from "./pages/TicketDetail";
import Tickets from "./pages/Tickets";

const isStaff = (role: string) => role === "TECHNICIAN" || role === "ADMIN";

// Where a notification leads: the ticket itself, or the list that holds the loan or booking.
function notificationPath(notification: Notification): string | null {
  switch (notification.relatedEntityType) {
    case "TICKET":
      return notification.relatedEntityRef ? `/tickets/${notification.relatedEntityRef}` : "/tickets";
    case "DEVICE_LOAN":
      return "/device-loans";
    case "ROOM_BOOKING":
      return "/room-bookings";
    default:
      return null;
  }
}

const labModule: AppUiModule = {
  tagline: "Yêu cầu hỗ trợ CNTT, thiết bị, phòng máy và quy chế của khoa",
  roleLabels: ROLE_LABEL,
  labels: LAB_LABELS,
  notificationPath,
  routes: [
    { path: "/", render: ({ user, onError }) => <Home user={user} onError={onError} /> },
    {
      path: "/tickets",
      render: ({ user, onError }) => <Tickets isStaff={isStaff(user.role)} onError={onError} />,
    },
    {
      path: "/tickets/:id",
      render: ({ user, onError }) => <TicketDetail userId={user.id} role={user.role} onError={onError} />,
    },
    {
      path: "/device-loans",
      render: ({ user, onError }) => <Loans userId={user.id} role={user.role} onError={onError} />,
    },
    {
      path: "/room-bookings",
      render: ({ user, onError }) => <Bookings userId={user.id} role={user.role} onError={onError} />,
    },
    { path: "/knowledge", render: ({ onError }) => <Knowledge onError={onError} /> },
    {
      path: "/queue",
      render: ({ user, onError }) => <Queue userId={user.id} role={user.role} onError={onError} />,
    },
    { path: "/assets", render: ({ onError }) => <Assets onError={onError} /> },
    {
      path: "/stats",
      render: ({ onError }) => <Stats onError={onError} />,
    },
    {
      path: "/catalog",
      render: ({ manifest, onError }) => (
        <Catalog workspaceTypes={manifest.workspaceTypes} onError={onError} />
      ),
    },
  ],
};

export default labModule;
