// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { useNavigate, useParams } from "react-router-dom";
import { Alert, Box, ButtonBase, Card, Skeleton, Typography } from "@mui/material";
import AssignmentOutlinedIcon from "@mui/icons-material/AssignmentOutlined";
import HourglassBottomOutlinedIcon from "@mui/icons-material/HourglassBottomOutlined";
import ReportProblemOutlinedIcon from "@mui/icons-material/ReportProblemOutlined";
import TimerOutlinedIcon from "@mui/icons-material/TimerOutlined";
import { Title } from "react-admin";
import PageHeader from "../shell/PageHeader";
import { formatDateTime, formatDuration } from "../shell/format";
import { getStats, type EntityStats } from "./api";
import { TONES } from "./StatusChip";
import { splitResource, type ModelMeta } from "./types";

type Filter = Record<string, string>;

function Tile({ icon, label, value, tone, onClick }: { icon: JSX.Element; label: string; value: number | string; tone: string; onClick?: () => void }) {
  return (
    <Card sx={{ flex: "1 1 200px" }}>
      <ButtonBase onClick={onClick} disabled={!onClick} sx={{ width: "100%", p: 2, display: "flex", justifyContent: "flex-start", gap: 2, textAlign: "left" }}>
        <Box
          sx={{
            width: 44,
            height: 44,
            borderRadius: 2,
            display: "grid",
            placeItems: "center",
            color: `${tone}.main`,
            bgcolor: (theme) => `${theme.palette[tone as "primary"].main}1a`,
          }}
        >
          {icon}
        </Box>
        <Box>
          <Typography variant="h5" sx={{ lineHeight: 1.1 }}>
            {value}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {label}
          </Typography>
        </Box>
      </ButtonBase>
    </Card>
  );
}

// One line per value: its name, a bar as long as its share of the total, and the count.
function Breakdown({ title, rows, total }: { title: string; rows: { key: string; label: string; count: number; tone: string; filter: Filter }[]; total: number }) {
  const navigate = useNavigate();
  const { resource = "" } = useParams();
  return (
    <Card sx={{ p: 2, flex: "1 1 320px" }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1 }}>
        {title}
      </Typography>
      {rows.length === 0 && (
        <Typography variant="body2" color="text.secondary">
          Chưa có hồ sơ nào.
        </Typography>
      )}
      {rows.map((row) => (
        <ButtonBase
          key={row.key}
          onClick={() => navigate(listPath(resource, row.filter))}
          sx={{ width: "100%", display: "grid", gridTemplateColumns: "minmax(90px, 160px) 1fr auto", alignItems: "center", gap: 1.5, py: 0.75, textAlign: "left", borderRadius: 1 }}
        >
          <Typography variant="body2" noWrap title={row.label}>
            {row.label}
          </Typography>
          <Box sx={{ height: 8, borderRadius: 4, bgcolor: "action.hover", overflow: "hidden" }}>
            <Box sx={{ height: "100%", width: `${total ? (row.count / total) * 100 : 0}%`, bgcolor: `${row.tone}.main` }} />
          </Box>
          <Typography variant="body2" sx={{ fontWeight: 700, minWidth: 32, textAlign: "right" }}>
            {row.count}
          </Typography>
        </ButtonBase>
      ))}
    </Card>
  );
}

const listPath = (resource: string, filter: Filter) =>
  Object.keys(filter).length ? `/${resource}?filter=${encodeURIComponent(JSON.stringify(filter))}` : `/${resource}`;

// The counts of an entity with a process, over the records the user may see.
export default function StatsPage({ models }: { models: ModelMeta[] }) {
  const { resource = "" } = useParams();
  const navigate = useNavigate();
  const { model: modelName, entity: entityName } = splitResource(resource);
  const model = models.find((candidate) => candidate.name === modelName);
  const entity = model?.entities.find((candidate) => candidate.name === entityName);
  const [stats, setStats] = useState<EntityStats | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!entity?.hasProcess) return;
    setStats(null);
    getStats(modelName, entityName).then(
      (loaded) => {
        setStats(loaded);
        setError(null);
      },
      (e: unknown) => setError(e instanceof Error ? e.message : "Không đọc được số liệu"),
    );
  }, [modelName, entityName, entity?.hasProcess]);

  if (!model || !entity?.hasProcess) return <Typography sx={{ p: 2 }}>Thực thể này không có thống kê.</Typography>;

  const what = entity.label.charAt(0).toLowerCase() + entity.label.slice(1);
  const stateLabel = (status: string) => entity.process?.states.find((state) => state.name === status)?.label ?? status;
  const unitLabel = (unit: string) => model.units.find((candidate) => candidate.name === unit)?.label ?? unit;
  const tone = (status: string) => {
    const value = TONES[status];
    return value && value !== "default" ? value : "secondary";
  };

  return (
    <Box>
      <Title title={`Thống kê ${what}`} />
      <PageHeader
        title={`Thống kê ${what}`}
        subtitle={
          stats
            ? `Tính trên các hồ sơ bạn được xem, lúc ${formatDateTime(stats.computedAt)}. Bấm vào một số để mở danh sách tương ứng.`
            : "Tính trên các hồ sơ bạn được xem."
        }
      />
      {error && <Alert severity="error">{error}</Alert>}
      {!stats && !error && <Skeleton variant="rounded" height={160} />}
      {stats && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Tile icon={<AssignmentOutlinedIcon />} label="Tổng số hồ sơ" value={stats.total} tone="primary" onClick={() => navigate(listPath(resource, {}))} />
            {entity.process?.deadlineSeconds != null && (
              <Tile
                icon={<ReportProblemOutlinedIcon />}
                label="Quá hạn"
                value={stats.overdue}
                tone="error"
                onClick={() => navigate(listPath(resource, { "handle_by.lt": stats.computedAt }))}
              />
            )}
            {stats.avgDecisionSeconds != null && (
              <Tile icon={<TimerOutlinedIcon />} label="Thời gian xử lý trung bình" value={formatDuration(stats.avgDecisionSeconds)} tone="success" />
            )}
            {stats.avgOpenAgeSeconds != null && (
              <Tile icon={<HourglassBottomOutlinedIcon />} label="Tuổi trung bình hồ sơ đang mở" value={formatDuration(stats.avgOpenAgeSeconds)} tone="info" />
            )}
            {stats.oldestOpenAgeSeconds != null && (
              <Tile icon={<HourglassBottomOutlinedIcon />} label="Hồ sơ đang mở lâu nhất" value={formatDuration(stats.oldestOpenAgeSeconds)} tone="warning" />
            )}
          </Box>
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <Breakdown
              title="Theo trạng thái"
              total={stats.total}
              rows={stats.byStatus.map((row) => ({
                key: row.status,
                label: stateLabel(row.status),
                count: row.count,
                tone: tone(row.status),
                filter: { status: row.status },
              }))}
            />
            {entity.unitField && stats.byUnit && (
              <Breakdown
                title="Theo đơn vị"
                total={stats.total}
                rows={stats.byUnit.map((row) => ({
                  key: row.unit ?? "",
                  label: row.unit ? unitLabel(row.unit) : "Chưa có đơn vị",
                  count: row.count,
                  tone: "info",
                  filter: row.unit ? { [entity.unitField as string]: row.unit } : {},
                }))}
              />
            )}
          </Box>
        </Box>
      )}
    </Box>
  );
}
