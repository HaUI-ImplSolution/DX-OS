// SPDX-License-Identifier: Apache-2.0
import { useNavigate, useParams } from "react-router-dom";
import { Box, Typography } from "@mui/material";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import FullCalendar from "@fullcalendar/react";
import timeGridPlugin from "@fullcalendar/timegrid";
import type { EventInput } from "@fullcalendar/core";
import { Title } from "react-admin";
import { queryString, request } from "../shell/api";
import { splitResource, type EntityMeta, type Page } from "./types";

// The bookings of an entity of the reservation family on a calendar.
export default function CalendarPage({ byResource }: { byResource: Record<string, EntityMeta> }) {
  const { resource = "" } = useParams();
  const navigate = useNavigate();
  const entity = byResource[resource];
  const booking = entity?.process?.booking;
  if (!entity || !booking) return <Typography sx={{ p: 2 }}>Thực thể này không có lịch.</Typography>;
  const { model, entity: name } = splitResource(resource);
  const states = entity.process?.states ?? [];
  const label = (status: string) => states.find((state) => state.name === status)?.label ?? status;
  const title = entity.fields.find((field) => field.type === "text");

  const color = (status: string) => {
    const state = states.find((item) => item.name === status);
    if (!state) return "#1976d2";
    if (state.terminal) return "#9e9e9e";
    return status === entity.process?.states[0]?.name ? "#ed6c02" : "#1976d2";
  };

  const load = async (range: { start: Date; end: Date }): Promise<EventInput[]> => {
    const page = await request<Page<Record<string, unknown>>>(
      `/data/${model}/${name}${queryString({
        [`filter.${booking.starts}.lt`]: range.end.toISOString(),
        [`filter.${booking.ends}.gt`]: range.start.toISOString(),
        size: 200,
      })}`,
    );
    return page.items.map((row) => ({
      id: String(row.id),
      title: `${title ? String(row[title.name] ?? "") : `#${row.id}`} · ${label(String(row.status))}`,
      start: String(row[booking.starts]),
      end: String(row[booking.ends]),
      color: color(String(row.status)),
    }));
  };

  return (
    <Box sx={{ p: 2 }}>
      <Title title={`Lịch ${entity.label}`} />
      <Typography variant="h5" sx={{ mb: 1 }}>
        Lịch {entity.label}
      </Typography>
      <FullCalendar
        plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
        initialView="timeGridWeek"
        locale="vi"
        firstDay={1}
        headerToolbar={{ left: "prev,next today", center: "title", right: "dayGridMonth,timeGridWeek,timeGridDay" }}
        buttonText={{ today: "Hôm nay", month: "Tháng", week: "Tuần", day: "Ngày" }}
        allDaySlot={false}
        height="auto"
        events={(info, success, failure) => load(info).then(success, failure)}
        eventClick={(info) => navigate(`/${resource}/${info.event.id}/show`)}
        selectable={entity.canWrite}
        select={(info) =>
          navigate(`/${resource}/create`, { state: { record: { [booking.starts]: info.startStr, [booking.ends]: info.endStr } } })
        }
      />
    </Box>
  );
}
