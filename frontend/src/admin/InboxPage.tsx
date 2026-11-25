// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState, type MouseEvent } from "react";
import {
  Alert,
  Box,
  Button,
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
  alpha,
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
import { reviewRecords, selectInboxReview, useAssistantModel, type ClassifierReview, type RecordTarget } from "../assistant/api";
import { listInbox } from "./api";
import { StatusChip } from "./StatusChip";
import { resourceName, type InboxItem, type ModelMeta, type Page } from "./types";
import { t } from "../shell/t";

const PAGE_SIZE = 50;
const REVIEW_SIZE = 25;
const REVIEW_TTL = 120_000;

type ReviewBatch = { checked: string[]; reviewed: number; flagged: Map<string, ClassifierReview[]>; expires: number };
const targetKey = (item: RecordTarget) => `${item.model}/${item.entity}/${item.id}`;
const reviewKey = (item: InboxItem) => `${targetKey(item)}/${item.updatedAt}`;
const reviewBackground = (theme: Theme) => alpha(theme.palette.warning.main, theme.palette.mode === "dark" ? 0.18 : 0.10);
const reviewBorder = { borderLeft: 4, borderLeftColor: (theme: Theme) =>
  theme.palette.mode === "dark" ? theme.palette.warning.main : theme.palette.warning.dark };

function ReviewFlags({ item, flags }: { item: InboxItem; flags: ClassifierReview[] }) {
  return flags.map((review) => (
    <Box key={review.task} sx={{ mt: 0.5, overflowWrap: "anywhere" }}>
      <Link
        to={`/${resourceName(item.model, item.entity)}/${item.id}/show`}
        state={{ fromInboxReview: true }}
        onClick={(event: MouseEvent<HTMLAnchorElement>) => {
          if (event.button === 0 && !event.metaKey && !event.altKey && !event.ctrlKey && !event.shiftKey) selectInboxReview(review);
        }}
        sx={{ display: "inline-flex", maxWidth: "100%" }}
      >
        <Chip size="small" color="warning" label={`${t("Cần rà")} · ${review.suggested}`}
          sx={{ color: "common.black", height: "auto", minHeight: 24, "& .MuiChip-label": { whiteSpace: "normal" } }} />
      </Link>
      <Typography variant="caption" component="div" color="text.secondary">
        {review.evidence.join(" · ")}
      </Typography>
    </Box>
  ));
}

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
  const [data, setData] = useState<{ page: number; result: Page<InboxItem> } | null>(null);
  const [page, setPage] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const [reload, setReload] = useState(0);
  const [batches, setBatches] = useState<ReviewBatch[]>([]);
  const [reviewing, setReviewing] = useState(false);
  const [reviewError, setReviewError] = useState<string | null>(null);
  const reviewRequest = useRef<AbortController | null>(null);
  const items = data?.page === page ? data.result.items : null;
  const phone = useMediaQuery((theme: Theme) => theme.breakpoints.down("sm"));

  // Another person's action can add or remove a record here, so the list is read again when one changes.
  useEffect(() => onLiveChange(() => setReload((value) => value + 1)), []);

  useEffect(() => {
    let active = true;
    void listInbox(page, PAGE_SIZE).then((result) => {
      if (!active) return;
      setData({ page, result });
      setError(null);
    }, (e: unknown) => {
      if (active) setError(e instanceof Error ? e.message : t("Không đọc được hộp việc"));
    });
    return () => { active = false; };
  }, [page, reload]);
  useEffect(() => () => reviewRequest.current?.abort(), []);
  const expires = batches[0]?.expires;
  useEffect(() => {
    if (expires === undefined) return;
    const timer = window.setTimeout(() => setBatches((value) => value.filter((batch) => batch.expires > expires)),
      Math.max(0, expires - Date.now()));
    return () => window.clearTimeout(timer);
  }, [expires]);

  const entityOf = (item: InboxItem) =>
    models.find((model) => model.name === item.model)?.entities.find((entity) => entity.name === item.entity);
  const declared = models.filter((model) => !model.held).flatMap((model) => model.entities.filter((entity) => entity.canWrite)
    .flatMap((entity) => Object.values(entity.aiTasks ?? {}).filter((task) => task.engine === "classifier").map((task) => task.classifier)));
  const assistantModel = useAssistantModel(declared.length > 0);
  const classifiers = new Set(assistantModel?.classifiers);
  const canReview = declared.some((name) => !!name && classifiers.has(name));
  const checked = new Set(batches.flatMap((batch) => batch.checked));
  const flagsByItem = new Map(canReview ? batches.flatMap((batch) => [...batch.flagged]) : []);
  const orderedItems = (items ?? []).map((item) => ({ item, flags: flagsByItem.get(reviewKey(item)) }))
    .sort((a, b) => Number(!!b.flags) - Number(!!a.flags));
  const latestBatch = batches[batches.length - 1];
  const nextBatch = canReview ? (items ?? []).filter((item) => {
    const entity = entityOf(item);
    return entity?.canWrite && !checked.has(reviewKey(item)) &&
      !entity.process?.states.some((state) => state.name === item.status && state.terminal) &&
      Object.values(entity.aiTasks ?? {}).some((task) => task.engine === "classifier" && !!task.classifier && classifiers.has(task.classifier));
  }).slice(0, REVIEW_SIZE) : [];

  const reviewNextBatch = async () => {
    if (!nextBatch.length || reviewRequest.current) return;
    const controller = new AbortController();
    reviewRequest.current = controller;
    setReviewing(true);
    setReviewError(null);
    const targets = nextBatch.map(({ model, entity, id }) => ({ model, entity, id }));
    const keys = new Map(nextBatch.map((item) => [targetKey(item), reviewKey(item)]));
    try {
      const result = await reviewRecords(targets, controller.signal);
      if (controller.signal.aborted) return;
      const flagged = new Map<string, ClassifierReview[]>();
      const reviewed = new Set<string>();
      for (const review of result.items) {
        const key = keys.get(targetKey(review));
        if (!key) continue;
        reviewed.add(key);
        if (review.mismatch) flagged.set(key, [...(flagged.get(key) ?? []), review]);
      }
      setBatches((value) => [...value, { checked: [...keys.values()], reviewed: reviewed.size, flagged, expires: Date.now() + REVIEW_TTL }]);
    } catch (e) {
      if (!controller.signal.aborted) setReviewError(e instanceof Error ? e.message : t("Không thể rà loại hồ sơ."));
    } finally {
      if (reviewRequest.current === controller) {
        reviewRequest.current = null;
        setReviewing(false);
      }
    }
  };
  const overdue = items?.filter((item) => item.overdue).length ?? 0;
  // The entities the user may create, offered when nothing waits for them.
  const creatable = models
    .filter((model) => !model.held)
    .flatMap((model) => model.entities.filter((entity) => entity.canWrite).map((entity) => ({ model: model.name, entity })));

  return (
    <Box>
      <Title title={t("Hộp việc")} />
      <PageHeader title={t("Hộp việc")} subtitle={t("Những hồ sơ đang chờ bạn xử lý.")}
        actions={canReview && (
          <Box sx={{ textAlign: { xs: "left", sm: "right" } }}>
            <Button variant="outlined" disabled={reviewing || !nextBatch.length} onClick={() => void reviewNextBatch()}>
              {reviewing ? t("Đang rà loại hồ sơ…") : t("Rà loại hồ sơ")}
            </Button>
            <Typography component="div" variant="body2" color="text.secondary" role="status">
              {latestBatch && t("Đã rà {n} hồ sơ, {m} hồ sơ cần rà", { n: latestBatch.reviewed, m: latestBatch.flagged.size })}
            </Typography>
          </Box>
        )} />
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
        <Stat icon={<AssignmentTurnedInOutlinedIcon />} label={t("Việc đang chờ")} value={data?.result.totalElements ?? 0} tone="primary" />
        <Stat icon={<ReportProblemOutlinedIcon />} label={t("Quá hạn")} value={overdue} tone="error" />
        <Stat icon={<HourglassTopIcon />} label={t("Trong hạn")} value={(items?.length ?? 0) - overdue} tone="success" />
      </Box>
      <Card>
        {reviewError && <Alert severity="error">{reviewError}</Alert>}
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
            {creatable.length > 0 && (
              <Box sx={{ mt: 3, display: "flex", gap: 1, flexWrap: "wrap", justifyContent: "center", px: 2 }}>
                {creatable.map(({ model, entity }) => (
                  <Button key={`${model}-${entity.name}`} variant="outlined" component={Link} to={`/${resourceName(model, entity.name)}/create`}>
                    {t("Tạo {name}", { name: entity.label })}
                  </Button>
                ))}
              </Box>
            )}
          </Box>
        )}
        {items && items.length > 0 && phone && (
          <Box>
            {orderedItems.map(({ item, flags }) => {
              const entity = entityOf(item);
              return (
                <Box
                  key={`${item.model}-${item.entity}-${item.id}`}
                  sx={{ p: 2, display: "grid", gap: 0.75, borderBottom: 1, borderColor: "divider", "&:last-of-type": { borderBottom: 0 },
                    ...(flags && { bgcolor: reviewBackground, ...reviewBorder }) }}
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
                    {timeAgo(item.updatedAt)} · {formatDateTime(item.updatedAt)}
                  </Typography>
                  {flags && <ReviewFlags item={item} flags={flags} />}
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
                <TableCell>{t("Cập nhật lúc")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {orderedItems.map(({ item, flags }) => {
                const entity = entityOf(item);
                return (
                  <TableRow key={`${item.model}-${item.entity}-${item.id}`} hover sx={flags && {
                    bgcolor: reviewBackground, "&.MuiTableRow-hover:hover": { bgcolor: reviewBackground },
                    "& > :first-of-type": reviewBorder,
                  }}>
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
                      {flags && <ReviewFlags item={item} flags={flags} />}
                    </TableCell>
                    <TableCell>
                      {entity ? <StatusChip entity={entity} status={item.status} overdue={item.overdue} /> : item.status}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{timeAgo(item.updatedAt)}</Typography>
                      <Typography variant="caption" color="text.secondary">
                        {formatDateTime(item.updatedAt)}
                      </Typography>
                    </TableCell>
                  </TableRow>
                );
              })}
            </TableBody>
          </Table>
        )}
      </Card>
      {data && data.result.totalPages > 1 && (
        <Pagination sx={{ mt: 2 }} count={data.result.totalPages} page={page + 1} onChange={(_, next) => {
          reviewRequest.current?.abort();
          reviewRequest.current = null;
          setReviewing(false);
          setReviewError(null);
          setBatches([]);
          setError(null);
          setPage(next - 1);
        }} />
      )}
    </Box>
  );
}
