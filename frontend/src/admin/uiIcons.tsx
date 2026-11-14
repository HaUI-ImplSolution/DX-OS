// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import AssignmentOutlinedIcon from "@mui/icons-material/AssignmentOutlined";
import BuildOutlinedIcon from "@mui/icons-material/BuildOutlined";
import CalendarMonthOutlinedIcon from "@mui/icons-material/CalendarMonthOutlined";
import ChatBubbleOutlineIcon from "@mui/icons-material/ChatBubbleOutline";
import ComputerOutlinedIcon from "@mui/icons-material/ComputerOutlined";
import DescriptionOutlinedIcon from "@mui/icons-material/DescriptionOutlined";
import EventOutlinedIcon from "@mui/icons-material/EventOutlined";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import GroupsOutlinedIcon from "@mui/icons-material/GroupsOutlined";
import InboxOutlinedIcon from "@mui/icons-material/InboxOutlined";
import Inventory2OutlinedIcon from "@mui/icons-material/Inventory2Outlined";
import ListAltOutlinedIcon from "@mui/icons-material/ListAltOutlined";
import LocalShippingOutlinedIcon from "@mui/icons-material/LocalShippingOutlined";
import MeetingRoomOutlinedIcon from "@mui/icons-material/MeetingRoomOutlined";
import PaymentsOutlinedIcon from "@mui/icons-material/PaymentsOutlined";
import PersonOutlineIcon from "@mui/icons-material/PersonOutline";
import SchoolOutlinedIcon from "@mui/icons-material/SchoolOutlined";
import SettingsOutlinedIcon from "@mui/icons-material/SettingsOutlined";
import ShieldOutlinedIcon from "@mui/icons-material/ShieldOutlined";
import SupportAgentOutlinedIcon from "@mui/icons-material/SupportAgentOutlined";

// The icon names a model file may give an entity, drawn with the icon of the same meaning. The list is
// the one the model reader accepts; a name that is not here shows the icon the entity would have had.
const ICONS: Record<string, JSX.Element> = {
  description: <DescriptionOutlinedIcon />,
  folder: <FolderOutlinedIcon />,
  inbox: <InboxOutlinedIcon />,
  person: <PersonOutlineIcon />,
  group: <GroupsOutlinedIcon />,
  school: <SchoolOutlinedIcon />,
  assignment: <AssignmentOutlinedIcon />,
  event: <EventOutlinedIcon />,
  room: <MeetingRoomOutlinedIcon />,
  computer: <ComputerOutlinedIcon />,
  build: <BuildOutlinedIcon />,
  inventory: <Inventory2OutlinedIcon />,
  payments: <PaymentsOutlinedIcon />,
  support: <SupportAgentOutlinedIcon />,
  shield: <ShieldOutlinedIcon />,
  settings: <SettingsOutlinedIcon />,
  list: <ListAltOutlinedIcon />,
  calendar: <CalendarMonthOutlinedIcon />,
  chat: <ChatBubbleOutlineIcon />,
  local_shipping: <LocalShippingOutlinedIcon />,
};

export function uiIcon(name: string | null | undefined): JSX.Element | undefined {
  if (!name) return undefined;
  const icon = ICONS[name];
  if (!icon) console.warn(`No icon is drawn for the name "${name}" given in a model file`);
  return icon;
}
