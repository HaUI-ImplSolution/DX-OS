// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Card,
  Chip,
  Pagination,
  Skeleton,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
  useMediaQuery,
  type Theme,
} from "@mui/material";
import AssignmentTurnedInOutlinedIcon from "@mui/icons-material/AssignmentTurnedInOutlined";
import HourglassTopIcon from "@mui/icons-material/HourglassTop";
import ReportProblemOutlinedIcon from "@mui/icons-material/ReportProblemOutlined";
import TaskAltIcon from "@mui/icons-material/TaskAlt";
import { Link, Title } from "react-admin";
import PageHeader from "../shell/PageHeader";
import { formatDateTime, timeAgo } from "../shell/format";
import { onLiveChange } from "../shell/liveChanges";
import { listInbox } from "./api";
import { StatusChip } from "./StatusChip";
import { resourceName, type InboxItem, type ModelMeta, type Page } from "./types";
import { t } from "../shell/t";

const PAGE_SIZE = 50;

function Stat({ icon, label, value, tone }: { icon: JSX.Element; label: string; value: number; tone: string }) {
  return (
    <Card sx={{ flex: "1 1 200px", p: 2, display: "flex", alignItems: "center", gap: 2 }}>
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
    </Card>
  );
}

// The records waiting for the signed-in user, on every model.
export default function InboxPage({ models }: { models: ModelMeta[] }) {
  const [data, setData] = useState<Page<InboxItem> | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const items = data?.items ?? null;
  const phone = useMediaQuery((theme: Theme) => theme.breakpoints.down("sm"));

  // Another person's action can add or remove a record here, so the list is read again when one changes.
  useEffect(() => onLiveChange(() => setReload((value) => value + 1)), []);

  useEffect(() => {
    listInbox(page, PAGE_SIZE).then(setData, (e: unknown) => setError(e instanceof Error ? e.message : t("Không đọc được hộp việc")));
  }, [page, reload]);

  const entityOf = (item: InboxItem) =>
    models.find((model) => model.name === item.model)?.entities.find((entity) => entity.name === item.entity);
  const overdue = items?.filter((item) => item.overdue).length ?? 0;

  return (
    <Box>
      <Title title={t("Hộp việc")} />
      <PageHeader title={t("Hộp việc")} subtitle={t("Những hồ sơ đang chờ bạn xử lý, việc quá hạn lên đầu.")} />
      {models.some((model) => model.held) && (
        <Alert severity="warning" sx={{ mb: 2.5 }}>
          {t("Mô hình {names} đang được áp dụng hoặc cần quản trị viên xử lý nên tạm thời chưa dùng được. Tải lại trang sau ít phút.", {
            names: models
              .filter((model) => model.held)
              .map((model) => model.name)
              .join(", "),
          })}
        </Alert>
      )}
      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 2.5 }}>
        <Stat icon={<AssignmentTurnedInOutlinedIcon />} label={t("Việc đang chờ")} value={data?.totalElements ?? 0} tone="primary" />
        <Stat icon={<ReportProblemOutlinedIcon />} label={t("Quá hạn")} value={overdue} tone="error" />
        <Stat icon={<HourglassTopIcon />} label={t("Trong hạn")} value={(items?.length ?? 0) - overdue} tone="success" />
      </Box>
      <Card>
        {error && (
          <Typography color="error" sx={{ p: 2 }}>
            {error}
          </Typography>
        )}
        {!items && !error && (
          <Box sx={{ p: 2 }}>
            <Skeleton height={36} />
            <Skeleton height={36} />
            <Skeleton height={36} />
          </Box>
        )}
        {items?.length === 0 && (
          <Box sx={{ py: 8, textAlign: "center", color: "text.secondary" }}>
            <TaskAltIcon sx={{ fontSize: 48, color: "success.main", mb: 1 }} />
            <Typography variant="h6" color="text.primary">
              {t("Không có việc nào đang chờ bạn")}
            </Typography>
            <Typography variant="body2">{t("Hồ sơ mới giao cho bạn sẽ hiện ở đây.")}</Typography>
          </Box>
        )}
        {items && items.length > 0 && phone && (
          <Box>
            {items.map((item) => {
              const entity = entityOf(item);
              return (
                <Box
                  key={`${item.model}-${item.entity}-${item.id}`}
                  sx={{ p: 2, display: "grid", gap: 0.75, borderBottom: 1, borderColor: "divider", "&:last-of-type": { borderBottom: 0 } }}
                >
                  <Link
                    to={`/${resourceName(item.model, item.entity)}/${item.id}/show`}
                    sx={{ fontWeight: 600, color: "primary.main", textDecoration: "none", overflowWrap: "anywhere" }}
                  >
                    {item.title ?? `#${item.id}`}
                  </Link>
                  <Box sx={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 0.75 }}>
                    <Chip size="small" label={item.entityLabel} />
                    {entity ? <StatusChip entity={entity} status={item.status} overdue={item.overdue} /> : item.status}
                  </Box>
                  <Typography variant="caption" color="text.secondary">
                    {timeAgo(item.createdAt)} · {formatDateTime(item.createdAt)}
                  </Typography>
                </Box>
              );
            })}
          </Box>
        )}
        {items && items.length > 0 && !phone && (
          <Table size="medium">
            <TableHead>
              <TableRow>
                <TableCell>{t("Loại")}</TableCell>
                <TableCell>{t("Hồ sơ")}</TableCell>
                <TableCell>{t("Trạng thái")}</TableCell>
                <TableCell>{t("Nộp lúc")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {items.map((item) => {
                const entity = entityOf(item);
                return (
                  <TableRow key={`${item.model}-${item.entity}-${item.id}`} hover>
                    <TableCell>
                      <Chip size="small" label={item.entityLabel} />
                    </TableCell>
                    <TableCell>
                      <Link
                        to={`/${resourceName(item.model, item.entity)}/${item.id}/show`}
                        sx={{ fontWeight: 600, color: "primary.main", textDecoration: "none", "&:hover": { textDecoration: "underline" } }}
                      >
                        {item.title ?? `#${item.id}`}
                      </Link>
                    </TableCell>
                    <TableCell>
                      {entity ? <StatusChip entity={entity} status={item.status} overdue={item.overdue} /> : item.status}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{timeAgo(item.createdAt)}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatDateTime(item.createdAt)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Card>
      {data && data.totalPages > 1 && (
        <Pagination sx={{ mt: 2 }} count={data.totalPages} page={page + 1} onChange={(_, next) => setPage(next - 1)} />
      )}
    </Box>
  );
}
