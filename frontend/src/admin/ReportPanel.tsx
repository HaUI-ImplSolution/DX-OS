// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState } from "react";
import { Alert, Box, Card, MenuItem, Skeleton, Table, TableBody, TableCell, TableHead, TableRow, TextField, Typography } from "@mui/material";
import { getReport, type EntityReport } from "./api";
import type { EntityMeta, ModelMeta } from "./types";
import { t } from "../shell/t";

const FUNCTIONS = {
  sum: (label: string) => t("Tổng của {label}", { label }),
  avg: (label: string) => t("Trung bình của {label}", { label }),
  min: (label: string) => t("Nhỏ nhất của {label}", { label }),
  max: (label: string) => t("Lớn nhất của {label}", { label }),
};

// The records grouped by one field with a figure over a number field, for the rows the person may see.
export default function ReportPanel({ model, entity }: { model: ModelMeta; entity: EntityMeta }) {
  const groupings = useMemo(() => {
    const options: { value: string; label: string }[] = [];
    if (entity.hasProcess) options.push({ value: "status", label: t("Trạng thái") });
    for (const field of entity.fields) {
      if (["choice", "unit", "boolean"].includes(field.type) && !field.multiple) options.push({ value: field.name, label: field.label });
      if (field.type === "date" || field.type === "datetime") options.push({ value: `${field.name}:month`, label: t("{label}, theo tháng", { label: field.label }) });
    }
    options.push({ value: "created_at:month", label: t("Ngày tạo, theo tháng") });
    return options;
  }, [entity]);
  const figures = useMemo(() => {
    const options: { value: string; label: string }[] = [{ value: "count", label: t("Số hồ sơ") }];
    for (const field of entity.fields.filter((candidate) => candidate.type === "number")) {
      for (const [fn, describe] of Object.entries(FUNCTIONS)) {
        options.push({ value: `${fn}(${field.name})`, label: describe(field.label) });
      }
    }
    return options;
  }, [entity]);

  const [groupBy, setGroupBy] = useState(groupings[0].value);
  const [figure, setFigure] = useState("count");
  const [report, setReport] = useState<EntityReport | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    setReport(null);
    getReport(model.name, entity.name, groupBy, figure === "count" ? ["count"] : ["count", figure]).then(
      (loaded) => {
        setReport(loaded);
        setError(null);
      },
      (e: unknown) => setError(e instanceof Error ? e.message : t("Không đọc được báo cáo")),
    );
  }, [model.name, entity.name, groupBy, figure]);

  const keyLabel = (key: string | null) => {
    if (key === null) return t("Chưa có giá trị");
    if (groupBy === "status") return t(entity.process?.states.find((state) => state.name === key)?.label ?? key);
    return model.units.find((unit) => unit.name === key)?.label ?? key;
  };
  const figureLabel = figures.find((option) => option.value === figure)?.label ?? figure;
  const show = (value: number | string | boolean | null | undefined) => (value === null || value === undefined ? "—" : String(value));

  return (
    <Card sx={{ p: 2 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 700, mb: 1.5 }}>
        {t("Báo cáo theo trường")}
      </Typography>
      <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap", mb: 2 }}>
        <TextField select size="small" label={t("Nhóm theo")} value={groupBy} onChange={(e) => setGroupBy(e.target.value)} sx={{ minWidth: 220 }}>
          {groupings.map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
        <TextField select size="small" label={t("Tính")} value={figure} onChange={(e) => setFigure(e.target.value)} sx={{ minWidth: 260 }}>
          {figures.map((option) => (
            <MenuItem key={option.value} value={option.value}>
              {option.label}
            </MenuItem>
          ))}
        </TextField>
      </Box>
      {error && <Alert severity="error">{error}</Alert>}
      {!report && !error && <Skeleton variant="rounded" height={120} />}
      {report && (
        <Box sx={{ overflowX: "auto" }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t("Nhóm")}</TableCell>
                <TableCell align="right">{t("Số hồ sơ")}</TableCell>
                {figure !== "count" && <TableCell align="right">{figureLabel}</TableCell>}
              </TableRow>
            </TableHead>
            <TableBody>
              {report.groups.map((group) => (
                <TableRow key={group.key ?? "none"}>
                  <TableCell>{keyLabel(group.key)}</TableCell>
                  <TableCell align="right">{show(group.count)}</TableCell>
                  {figure !== "count" && <TableCell align="right">{show(group[figure])}</TableCell>}
                </TableRow>
              ))}
              <TableRow>
                <TableCell sx={{ fontWeight: 700 }}>{t("Tất cả")}</TableCell>
                <TableCell align="right" sx={{ fontWeight: 700 }}>
                  {show(report.totals.count)}
                </TableCell>
                {figure !== "count" && (
                  <TableCell align="right" sx={{ fontWeight: 700 }}>
                    {show(report.totals[figure])}
                  </TableCell>
                )}
              </TableRow>
            </TableBody>
          </Table>
          {report.truncated && (
            <Typography variant="caption" color="text.secondary">
              {t("Chỉ hiện {n} nhóm đầu.", { n: report.groups.length })}
            </Typography>
          )}
        </Box>
      )}
    </Card>
  );
}
