// SPDX-License-Identifier: Apache-2.0
import { Box, Button, Card, Chip, Skeleton, Typography } from "@mui/material";
import { Link, useCreatePath, useGetList, useListContext, useResourceContext } from "react-admin";
import type { EntityMeta } from "./types";
import { timeAgo } from "../shell/format";
import { t } from "../shell/t";

// Records shown in a column; the rest are one click away in the list of that state.
const PER_COLUMN = 10;

function Column({
  resource,
  titleField,
  state,
  onAll,
}: {
  resource: string;
  titleField: string;
  state: { name: string; label: string };
  onAll: (status: string) => void;
}) {
  const { filterValues } = useListContext();
  const createPath = useCreatePath();
  const { data, total, isPending } = useGetList(resource, {
    pagination: { page: 1, perPage: PER_COLUMN },
    sort: { field: "id", order: "DESC" },
    filter: { ...filterValues, status: state.name },
  });
  return (
    <Box sx={{ flex: "0 0 270px", minWidth: 0 }}>
      <Box sx={{ display: "flex", alignItems: "center", justifyContent: "space-between", px: 0.5, mb: 1 }}>
        <Typography variant="subtitle2" sx={{ fontWeight: 700 }}>
          {t(state.label)}
        </Typography>
        <Chip size="small" label={total ?? 0} />
      </Box>
      <Box sx={{ display: "grid", gap: 1 }}>
        {isPending && <Skeleton variant="rounded" height={64} />}
        {(data ?? []).map((record) => (
          <Card
            key={record.id}
            variant="outlined"
            component={Link}
            to={createPath({ resource, type: "show", id: record.id })}
            sx={{ p: 1.5, textDecoration: "none", color: "inherit", "&:hover": { borderColor: "primary.main" } }}
          >
            <Typography variant="body2" sx={{ fontWeight: 600, overflowWrap: "anywhere" }}>
              {String(record[titleField] ?? `#${record.id}`)}
            </Typography>
            <Box sx={{ display: "flex", alignItems: "center", gap: 0.5, mt: 0.5, flexWrap: "wrap" }}>
              <Typography variant="caption" color="text.secondary">
                #{record.id} · {timeAgo(record.created_at)}
              </Typography>
              {record.overdue && <Chip size="small" color="error" label={t("Quá hạn")} />}
            </Box>
          </Card>
        ))}
        {!isPending && (data ?? []).length === 0 && (
          <Typography variant="caption" color="text.secondary" sx={{ px: 0.5 }}>
            {t("Không có hồ sơ")}
          </Typography>
        )}
        {(total ?? 0) > PER_COLUMN && (
          <Button size="small" onClick={() => onAll(state.name)}>
            {t("Xem cả {n} hồ sơ", { n: total ?? 0 })}
          </Button>
        )}
      </Box>
    </Box>
  );
}

// The records of an entity with a process laid out by state, read only: a card opens the record.
export default function KanbanBoard({ entity, titleField, onAll }: { entity: EntityMeta; titleField: string; onAll: (status: string) => void }) {
  const resource = useResourceContext() ?? "";
  const states = entity.process?.states ?? [];
  return (
    // A zero width with a full minimum width lets the columns scroll inside the page instead of widening it.
    <Box sx={{ width: 0, minWidth: "100%", overflowX: "auto" }}>
      <Box sx={{ display: "flex", gap: 2, p: 2, alignItems: "flex-start", width: "max-content" }}>
        {states.map((state) => (
          <Column key={state.name} resource={resource} titleField={titleField} state={state} onAll={onAll} />
        ))}
      </Box>
    </Box>
  );
}
