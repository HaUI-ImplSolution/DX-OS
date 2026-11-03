// SPDX-License-Identifier: Apache-2.0
import { useNavigate, useParams } from "react-router-dom";
import { alpha, Box, Card, Chip, Typography, useTheme } from "@mui/material";
import dayGridPlugin from "@fullcalendar/daygrid";
import interactionPlugin from "@fullcalendar/interaction";
import FullCalendar from "@fullcalendar/react";
import timeGridPlugin from "@fullcalendar/timegrid";
import viLocale from "@fullcalendar/core/locales/vi";
import type { EventInput } from "@fullcalendar/core";
import { Title } from "react-admin";
import PageHeader from "../shell/PageHeader";
import { queryString, request } from "../shell/api";
import { TONES } from "./StatusChip";
import { splitResource, type EntityMeta, type Page } from "./types";

// The bookings of an entity of the reservation family on a calendar.
export default function CalendarPage({ byResource }: { byResource: Record<string, EntityMeta> }) {
  const { resource = "" } = useParams();
  const navigate = useNavigate();
  const theme = useTheme();
  const entity = byResource[resource];
  const booking = entity?.process?.booking;
  if (!entity || !booking) return <Typography sx={{ p: 2 }}>Thực thể này không có lịch.</Typography>;
  const { model, entity: name } = splitResource(resource);
  const states = entity.process?.states ?? [];
  const label = (status: string) => states.find((state) => state.name === status)?.label ?? status;
  const title = entity.fields.find((field) => field.type === "text");
  const what = entity.label.charAt(0).toLowerCase() + entity.label.slice(1);

  // The colour of a booking is the tone of its state, the same as the label in the lists.
  const color = (status: string) => {
    const tone = TONES[status];
    return tone && tone !== "default" ? theme.palette[tone].main : theme.palette.grey[500];
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
    <Box>
      <Title title={`Lịch ${what}`} />
      <PageHeader
        title={`Lịch ${what}`}
        subtitle={entity.canWrite ? "Kéo chọn một khoảng giờ để tạo lượt đặt mới, bấm vào một lượt để xem chi tiết." : "Bấm vào một lượt để xem chi tiết."}
      />
      <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", mb: 2 }}>
        {states.map((state) => (
          <Chip
            key={state.name}
            size="small"
            variant="outlined"
            label={state.label}
            sx={{ borderColor: color(state.name), color: color(state.name) }}
          />
        ))}
      </Box>
      <Card
        sx={{
          p: 2,
          "& .fc": {
            fontFamily: "inherit",
            "--fc-border-color": theme.palette.divider,
            "--fc-page-bg-color": theme.palette.background.paper,
            "--fc-neutral-bg-color": theme.palette.action.hover,
            "--fc-today-bg-color": alpha(theme.palette.primary.main, 0.06),
            "--fc-button-bg-color": theme.palette.primary.main,
            "--fc-button-border-color": theme.palette.primary.main,
            "--fc-button-hover-bg-color": theme.palette.primary.dark,
            "--fc-button-hover-border-color": theme.palette.primary.dark,
            "--fc-button-active-bg-color": theme.palette.primary.dark,
            "--fc-button-active-border-color": theme.palette.primary.dark,
          },
          "& .fc .fc-toolbar-title": { fontSize: 18, fontWeight: 700 },
          "& .fc .fc-button": { textTransform: "none", fontWeight: 600, borderRadius: 8 },
          "& .fc .fc-event": { borderRadius: 6, border: "none", padding: "1px 4px" },
        }}
      >
        <FullCalendar
          plugins={[dayGridPlugin, timeGridPlugin, interactionPlugin]}
          initialView="timeGridWeek"
          locale={viLocale}
          firstDay={1}
          headerToolbar={{ left: "prev,next today", center: "title", right: "dayGridMonth,timeGridWeek,timeGridDay" }}
          buttonText={{ today: "Hôm nay", month: "Tháng", week: "Tuần", day: "Ngày" }}
          allDaySlot={false}
          scrollTime="07:00:00"
          nowIndicator
          height="auto"
          events={(info, success, failure) => load(info).then(success, failure)}
          eventClick={(info) => navigate(`/${resource}/${info.event.id}/show`)}
          selectable={entity.canWrite}
          select={(info) =>
            navigate(`/${resource}/create`, { state: { record: { [booking.starts]: info.startStr, [booking.ends]: info.endStr } } })
          }
        />
      </Card>
    </Box>
  );
}
