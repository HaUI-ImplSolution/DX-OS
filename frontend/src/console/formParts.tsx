// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState, type ReactNode } from "react";
import {
  Box,
  Button,
  Collapse,
  Dialog,
  DialogActions,
  DialogContent,
  DialogTitle,
  MenuItem,
  TextField,
  Typography,
} from "@mui/material";
import ExpandLessIcon from "@mui/icons-material/ExpandLess";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import GuideBlocks from "./GuideBlocks";
import { GUIDE, type Section } from "./formGuide";
import { conditionSource } from "../admin/condition";
import type { ProcessCondition } from "../admin/types";
import { t } from "../shell/t";

export type Rec = Record<string, unknown>;

export const rec = (value: unknown): Rec =>
  value !== null && typeof value === "object" && !Array.isArray(value) ? (value as Rec) : {};
export const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
export const text = (value: unknown): string => (value === undefined || value === null ? "" : String(value));
export const texts = (value: unknown): string[] => list(value).map(text);
export const asList = (item: unknown): string[] => (typeof item === "string" ? [item] : texts(item));
// The condition of an action as it is written in the file: the field, the operator and the value.
export const conditionOf = (when?: ProcessCondition | null): string => conditionSource(when);

// A copy without the keys that hold nothing, so the file does not fill with empty entries.
export function compact(source: Rec): Rec {
  const kept: Rec = {};
  Object.entries(source).forEach(([key, item]) => {
    if (item !== undefined && item !== "" && item !== false) kept[key] = item;
  });
  return kept;
}

// The same mapping with one key renamed, in the same place.
export function renameKey(source: Rec, from: string, to: string): Rec {
  const renamed: Rec = {};
  Object.entries(source).forEach(([key, item]) => {
    renamed[key === from ? to : key] = item;
  });
  return renamed;
}

export function uniqueName(base: string, taken: string[]): string {
  if (!taken.includes(base)) return base;
  let n = 2;
  while (taken.includes(`${base}_${n}`)) n++;
  return `${base}_${n}`;
}

// The values the schema of the model file allows at a place, such as the types of a field.
export function allowed(schema: Rec, path: string[], fallback: string[]): string[] {
  let node: unknown = schema;
  for (const key of path) node = rec(node)[key];
  const values = rec(node).enum;
  return Array.isArray(values) ? values.map(String) : fallback;
}

// True when a condition written in the file names the field.
export const mentions = (condition: unknown, field: string): boolean =>
  new RegExp(`(^|[^a-z0-9_])${field}([^a-z0-9_]|$)`).test(text(condition));

export const NAME = /^[a-z][a-z0-9_]{0,39}$/;
export const ROLE = /^[A-Z][A-Z0-9_]{0,39}$/;
const DURATION = /^([0-9]{1,6})([mhd])$/;

export const DENSE = { "& .MuiInputBase-input": { py: 0.9, fontSize: 14 } };

// The common grid of a form: every input takes one column of the same width.
export function FormGrid({ children }: { children: ReactNode }) {
  return (
    <Box sx={{ display: "grid", gap: 1.5, alignItems: "start", gridTemplateColumns: "repeat(auto-fill, minmax(220px, 1fr))", py: 1 }}>
      {children}
    </Box>
  );
}

// An item of the grid that takes the whole row.
export function Wide({ children }: { children: ReactNode }) {
  return <Box sx={{ gridColumn: "1 / -1" }}>{children}</Box>;
}

// A titled part of a page; its add button always sits at the right of the title.
export function Group({ title, hint, action, children }: { title: string; hint?: string; action?: ReactNode; children: ReactNode }) {
  return (
    <Box sx={{ mb: 3 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1, borderBottom: 1, borderColor: "divider", pb: 0.5, mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
          {title}
        </Typography>
        {action}
      </Box>
      {hint && (
        <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
          {hint}
        </Typography>
      )}
      {children}
    </Box>
  );
}

// Settings most models do not need, closed until asked for.
// It starts open when something in it is already set, so a setting is never hidden from view.
export function Advanced({ children, label = t("Cấu hình nâng cao"), open: initial = false }: { children: ReactNode; label?: string; open?: boolean }) {
  const [open, setOpen] = useState(initial);
  return (
    <Box sx={{ mt: 1 }}>
      <Button size="small" onClick={() => setOpen(!open)} endIcon={open ? <ExpandLessIcon /> : <ExpandMoreIcon />}>
        {label}
      </Button>
      <Collapse in={open}>{children}</Collapse>
    </Box>
  );
}

export function Empty({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
      {children}
    </Typography>
  );
}

// A name that is checked as it is typed; only a valid name reaches the model.
export function NameInput({
  value,
  onCommit,
  pattern,
  label,
  taken = [],
  upper = false,
  help,
}: {
  value: string;
  onCommit: (name: string) => void;
  pattern: RegExp;
  label: string;
  taken?: string[];
  upper?: boolean;
  help?: string;
}) {
  const [draft, setDraft] = useState(value);
  useEffect(() => setDraft(value), [value]);
  const duplicate = draft !== value && taken.includes(draft);
  const problem = !pattern.test(draft)
    ? upper
      ? t("Chữ in hoa, số, gạch dưới; bắt đầu bằng chữ; tối đa 40 ký tự")
      : t("Chữ thường, số, gạch dưới; bắt đầu bằng chữ; tối đa 40 ký tự")
    : duplicate
      ? t("Tên này đã có")
      : "";
  return (
    <TextField
      variant="outlined"
      size="small"
      fullWidth
      required
      label={label}
      value={draft}
      error={problem !== ""}
      helperText={problem || help}
      sx={DENSE}
      slotProps={{ htmlInput: { style: { fontFamily: "ui-monospace, monospace" } } }}
      onChange={(event) => {
        const next = upper ? event.target.value.toUpperCase() : event.target.value;
        setDraft(next);
        if (pattern.test(next) && !(next !== value && taken.includes(next))) onCommit(next);
      }}
      onBlur={() => setDraft(value)}
    />
  );
}

// A length of time written as a number and a unit, such as 8 hours for 8h.
export function DurationInput({
  value,
  onChange,
  label,
  help,
}: {
  value: string;
  onChange: (value: string | undefined) => void;
  label: string;
  help?: string;
}) {
  const match = DURATION.exec(value);
  const [unit, setUnit] = useState(match?.[2] ?? "h");
  useEffect(() => {
    if (match) setUnit(match[2]);
  }, [match]);
  const number = match?.[1] ?? "";
  return (
    <Box sx={{ display: "flex", gap: 0.5 }}>
      <TextField
        variant="outlined"
        size="small"
        label={label}
        value={number}
        helperText={help}
        sx={{ ...DENSE, flex: 1, minWidth: 0 }}
        slotProps={{ htmlInput: { inputMode: "numeric" } }}
        onChange={(event) => {
          const digits = event.target.value.replace(/\D/g, "").slice(0, 6);
          onChange(digits && Number(digits) > 0 ? `${Number(digits)}${unit}` : undefined);
        }}
      />
      <TextField
        variant="outlined"
        select
        size="small"
        value={unit}
        sx={{ ...DENSE, width: 92, flexShrink: 0 }}
        onChange={(event) => {
          setUnit(event.target.value);
          if (number) onChange(`${number}${event.target.value}`);
        }}
      >
        <MenuItem value="m">{t("phút")}</MenuItem>
        <MenuItem value="h">{t("giờ")}</MenuItem>
        <MenuItem value="d">{t("ngày")}</MenuItem>
      </TextField>
    </Box>
  );
}

// What the system accepts, what can be extended and what can be created, for one part of the file.
export function GuideBox({ section }: { section: Section }) {
  const guide = GUIDE[section];
  const [open, setOpen] = useState(false);
  return (
    <Box sx={{ bgcolor: "action.hover", borderRadius: 1, px: 1.5, py: 0.75, mb: 2 }}>
      <Box sx={{ display: "flex", alignItems: "center", gap: 1 }}>
        <InfoOutlinedIcon fontSize="small" color="primary" />
        <Typography variant="body2" sx={{ flexGrow: 1 }}>
          {guide.summary}
        </Typography>
        <Button size="small" onClick={() => setOpen(!open)} sx={{ whiteSpace: "nowrap" }}>
          {open ? t("Thu gọn") : t("Phạm vi có thể làm")}
        </Button>
      </Box>
      <Collapse in={open}>
        <GuideBlocks guide={guide} />
      </Collapse>
    </Box>
  );
}

// Asks before removing something other parts of the model still use.
export function ConfirmRemove({
  what,
  uses,
  onConfirm,
  onClose,
}: {
  what: string;
  uses: string[];
  onConfirm: () => void;
  onClose: () => void;
}) {
  return (
    <Dialog open onClose={onClose} maxWidth="sm" fullWidth>
      <DialogTitle>{t("Xóa {what}?", { what })}</DialogTitle>
      <DialogContent>
        <Typography variant="body2" sx={{ mb: 1 }}>
          {t("Các chỗ sau vẫn đang dùng, sẽ báo lỗi khi xem kế hoạch nếu không sửa theo:")}
        </Typography>
        <Box component="ul" sx={{ m: 0, pl: 2.5 }}>
          {uses.map((use) => (
            <li key={use}>
              <Typography variant="body2">{use}</Typography>
            </li>
          ))}
        </Box>
      </DialogContent>
      <DialogActions>
        <Button onClick={onClose}>{t("Hủy")}</Button>
        <Button
          color="error"
          variant="contained"
          onClick={() => {
            onConfirm();
            onClose();
          }}
        >
          {t("Vẫn xóa")}
        </Button>
      </DialogActions>
    </Dialog>
  );
}
