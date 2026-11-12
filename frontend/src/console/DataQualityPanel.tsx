// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Alert, Box, Chip, CircularProgress, MenuItem, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { getDataQuality, type DataQuality } from "./api";
import { ApiError } from "../shell/api";
import { formatDateTime } from "../shell/format";
import { t } from "../shell/t";

const WARN = { color: "warning.main", fontWeight: 600 };

const STALE_CHOICES = [30, 90, 180, 365];

const percent = (rate: number | null) => (rate === null ? "–" : `${(rate * 100).toFixed(1)}%`);

function Section({ title, children }: { title: string; children: React.ReactNode }) {
  return (
    <Box sx={{ mb: 2 }}>
      <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
        {title}
      </Typography>
      <Box sx={{ overflowX: "auto" }}>{children}</Box>
    </Box>
  );
}

// The quality figures of one entity, measured on the records the caller's role may see.
export default function DataQualityPanel({ model, entity }: { model: string; entity: string }) {
  const [quality, setQuality] = useState<DataQuality | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [denied, setDenied] = useState(false);
  const [staleDays, setStaleDays] = useState(90);

  useEffect(() => {
    setError(null);
    setDenied(false);
    getDataQuality(model, entity, staleDays).then(setQuality, (e: unknown) => {
      if (e instanceof ApiError && e.status === 403) setDenied(true);
      else setError(e instanceof Error ? e.message : t("Không xử lý được"));
    });
  }, [model, entity, staleDays]);

  if (denied) return <Alert severity="info">{t("Vai của bạn không xem được chất lượng dữ liệu của thực thể này")}</Alert>;
  if (error) return <Alert severity="error">{error}</Alert>;
  if (!quality) return <CircularProgress size={20} />;

  const { completeness, requiredWhen, constraints, freshness } = quality.metrics;
  const requiredWhenRows = (requiredWhen?.fields ?? []).filter((row) => row.missing > 0);
  const constraintRows = (constraints?.fields ?? []).filter((row) => row.violating > 0);

  return (
    <Box>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1, mb: 2 }}>
        <Chip size="small" label={t("Đo lúc {time}", { time: formatDateTime(quality.measuredAt) })} />
        <Chip size="small" label={t("{n} bản ghi được đo", { n: quality.sampleSize })} />
      </Box>
      {completeness && completeness.fields.length > 0 && (
        <Section title={t("Độ điền theo trường")}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t("Trường")}</TableCell>
                <TableCell>{t("Đã điền")}</TableCell>
                <TableCell>{t("Còn trống")}</TableCell>
                <TableCell>{t("Tỉ lệ điền")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {completeness.fields.map((row) => (
                <TableRow key={row.field}>
                  <TableCell>{row.required ? `${row.field} (${t("bắt buộc")})` : row.field}</TableCell>
                  <TableCell>{row.filled}</TableCell>
                  <TableCell sx={row.missing > 0 ? WARN : undefined}>{row.missing}</TableCell>
                  <TableCell>{percent(row.rate)}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Section>
      )}
      {requiredWhenRows.length > 0 && (
        <Section title={t("Thiếu theo điều kiện")}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t("Trường")}</TableCell>
                <TableCell>{t("Điều kiện")}</TableCell>
                <TableCell>{t("Bản ghi áp dụng")}</TableCell>
                <TableCell>{t("Còn thiếu")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {requiredWhenRows.map((row) => (
                <TableRow key={`${row.field}-${row.when}`}>
                  <TableCell>{row.field}</TableCell>
                  <TableCell>{row.when}</TableCell>
                  <TableCell>{row.applicable}</TableCell>
                  <TableCell sx={WARN}>{row.missing}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Section>
      )}
      {constraintRows.length > 0 && (
        <Section title={t("Vi phạm ràng buộc")}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t("Trường")}</TableCell>
                <TableCell>{t("Ràng buộc")}</TableCell>
                <TableCell>{t("Số bản ghi vi phạm")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {constraintRows.map((row) => (
                <TableRow key={row.field}>
                  <TableCell>{row.field}</TableCell>
                  <TableCell>{row.rules.join(", ")}</TableCell>
                  <TableCell sx={WARN}>{row.violating}</TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Section>
      )}
      {freshness && (
        <Section title={t("Độ mới")}>
          <TextField select size="small" label={t("Không cập nhật quá (ngày)")} value={staleDays} onChange={(e) => setStaleDays(Number(e.target.value))} sx={{ minWidth: 220, mb: 1 }}>
            {STALE_CHOICES.map((days) => (
              <MenuItem key={days} value={days}>
                {days}
              </MenuItem>
            ))}
          </TextField>
          <Typography variant="body2">
            {t("{n} bản ghi chưa xong không cập nhật quá {days} ngày", { n: freshness.stale, days: freshness.staleDays })}
          </Typography>
          <Typography variant="body2" color="text.secondary">
            {freshness.oldest && freshness.newest
              ? t("Cập nhật cũ nhất {oldest}, mới nhất {newest}", { oldest: formatDateTime(freshness.oldest), newest: formatDateTime(freshness.newest) })
              : t("Chưa có bản ghi")}
          </Typography>
        </Section>
      )}
    </Box>
  );
}
