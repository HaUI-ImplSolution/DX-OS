// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import {
  Alert,
  Box,
  Button,
  Checkbox,
  Chip,
  FormControlLabel,
  List,
  ListItem,
  ListItemText,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  Typography,
} from "@mui/material";
import type { Plan } from "./api";
import { CHANGE_KIND, RULE, label } from "./labels";
import { t } from "../shell/t";

const LEVELS: Record<string, { label: string; color: "success" | "warning" | "error"; text: string }> = {
  SAFE: { label: t("An toàn"), color: "success", text: t("Áp dụng được ngay, không cần chuyển dữ liệu.") },
  MIGRATION: { label: t("Cần chuyển dữ liệu"), color: "warning", text: t("Dữ liệu hiện có được chuyển sang cấu trúc mới.") },
  LOSS: {
    label: t("Mất dữ liệu"),
    color: "error",
    text: t("Một số dữ liệu sẽ bị xóa. Hãy sao lưu bằng deploy/backup.sh trước khi xác nhận."),
  },
};

// A role declared again while people still hold it gives them its access back.
const REGRANT = {
  label: t("Cần xác nhận"),
  color: "warning" as const,
  text: t("Người đang giữ vai được khai báo lại sẽ có lại quyền của vai đó."),
};

// Tables that no longer match a file that did not change are put back in line with it.
const REPAIR = {
  label: t("Cần xác nhận"),
  color: "warning" as const,
  text: t("Cấu trúc bảng đã lệch khỏi mô hình; áp dụng sẽ sửa lại cho khớp."),
};

const confirmLabel = (level: string, moves: boolean, regrants: boolean, repairs: boolean) => {
  if (level === "LOSS") return t("Tôi hiểu dữ liệu sẽ mất và đã sao lưu");
  const parts = [
    ...(moves ? [t("chuyển dữ liệu")] : []),
    ...(regrants ? [t("trả lại quyền cho người đang giữ vai")] : []),
    ...(repairs ? [t("sửa cấu trúc bảng cho khớp mô hình")] : []),
  ];
  return t("Tôi xác nhận {parts}", { parts: parts.length ? parts.join(t(" và ")) : t("chuyển dữ liệu") });
};

interface Props {
  plan: Plan;
  busy: boolean;
  applyLabel?: string;
  onApply: (accept?: string) => void;
}

// What applying a model would do, and the button that does it once the risk is accepted.
const FLOW_PARTS: [string, string][] = [
  ["states added: ", t("Thêm trạng thái: ")],
  ["states removed: ", t("Bỏ trạng thái: ")],
  ["actions added: ", t("Thêm hành động: ")],
  ["actions removed: ", t("Bỏ hành động: ")],
];

// What the plan says changed in the steps of a process, in the language of the interface.
function flowDetail(text: string): string {
  return text
    .split("; ")
    .map((part) => {
      if (part === "steps of existing actions") return t("Đổi nơi đi, nơi đến hoặc người làm của hành động có sẵn");
      const known = FLOW_PARTS.find(([english]) => part.startsWith(english));
      return known ? known[1] + part.slice(known[0].length) : part;
    })
    .join("; ");
}

export default function PlanPanel({ plan, busy, applyLabel = t("Áp dụng"), onApply }: Props) {
  const [accepted, setAccepted] = useState(false);
  const confirmed = plan.changes.filter((change) => change.level === "MIGRATION");
  const regrants = confirmed.some((change) => change.kind === "ADD_ROLE");
  const repairs = confirmed.some((change) => change.kind === "REPAIR_SCHEMA");
  const moves = confirmed.some((change) => change.kind !== "ADD_ROLE" && change.kind !== "REPAIR_SCHEMA");
  const level =
    plan.level === "MIGRATION" && !moves ? (regrants ? REGRANT : REPAIR) : (LEVELS[plan.level] ?? LEVELS.SAFE);
  const unchanged = plan.action === "unchanged";
  const blocked = plan.action === "rejected";
  const needsConfirm = !unchanged && !blocked && plan.level !== "SAFE";
  const open = Object.entries(plan.openRecords);

  return (
    <Box>
      <Box sx={{ display: "flex", gap: 1, alignItems: "center", flexWrap: "wrap", mb: 1.5 }}>
        <Typography variant="h6">{t("Kế hoạch")}</Typography>
        <Chip size="small" color={level.color} label={level.label} />
        <Typography variant="body2" color="text.secondary">
          {t("{model}: phiên bản {from} → {to}", { model: plan.model, from: plan.fromVersion, to: plan.version })}
        </Typography>
      </Box>
      {unchanged && <Alert severity="info">{t("Mô hình đang chạy đã giống tệp này, không có gì để áp dụng.")}</Alert>}
      {plan.action === "repair" && (
        <Alert severity="info" sx={{ mb: 1 }}>
          {t("Tệp không đổi nhưng bảng hoặc tài khoản Keycloak của mô hình đã lệch khỏi tệp; áp dụng sẽ sửa phần lệch và giữ nguyên phiên bản.")}
        </Alert>
      )}
      {plan.rejections.map((text) => (
        <Alert key={text} severity="error" sx={{ mb: 1 }}>
          {text}
        </Alert>
      ))}
      {plan.questions.map((text) => (
        <Alert key={text} severity="warning" sx={{ mb: 1 }}>
          {text}
        </Alert>
      ))}
      {plan.violations.length > 0 && (
        <Alert severity="error" sx={{ mb: 1 }}>
          {t("Dữ liệu hiện có vi phạm quy tắc mới:")}
          <List dense>
            {plan.violations.map((violation) => (
              <ListItem key={`${violation.entity}-${violation.field}-${violation.rule}`}>
                <ListItemText
                  primary={`${violation.entity}${violation.field ? `.${violation.field}` : ""}: ${label(RULE, violation.rule)}, ${t("{n} dòng", { n: violation.rows })}`}
                  secondary={violation.sample.length ? t("Ví dụ mã dòng: {ids}", { ids: violation.sample.join(", ") }) : undefined}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}
      {!unchanged && !blocked && <Alert severity={level.color}>{level.text}</Alert>}
      {open.length > 0 && (
        <Alert severity="info" sx={{ mt: 1 }}>
          {t("Hồ sơ đang xử lý bị ảnh hưởng: {list}.", { list: open.map(([entity, count]) => `${entity} ${count}`).join(", ") })}
        </Alert>
      )}
      {plan.changes.length > 0 && (
        <Table size="small" sx={{ mt: 1.5 }}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Thay đổi")}</TableCell>
              <TableCell>{t("Thực thể")}</TableCell>
              <TableCell>{t("Trường")}</TableCell>
              <TableCell>{t("Mức")}</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {plan.changes.map((change, index) => {
              const changeLevel =
                change.level === "MIGRATION" && change.kind === "ADD_ROLE"
                  ? REGRANT
                  : change.level === "MIGRATION" && change.kind === "REPAIR_SCHEMA"
                    ? REPAIR
                    : (LEVELS[change.level] ?? LEVELS.SAFE);
              return (
                <TableRow key={`${change.kind}-${change.entity}-${change.field}-${index}`}>
                  <TableCell>
                    {label(CHANGE_KIND, change.kind)}
                    {change.detail && (
                      <Typography variant="caption" color="text.secondary" display="block">
                        {change.kind === "CHANGE_FLOW" ? flowDetail(change.detail) : change.detail}
                      </Typography>
                    )}
                  </TableCell>
                  <TableCell>{change.entity}</TableCell>
                  <TableCell>{change.field ?? "—"}</TableCell>
                  <TableCell>
                    <Chip size="small" variant="outlined" color={changeLevel.color} label={changeLevel.label} />
                  </TableCell>
                </TableRow>
              );
            })}
          </TableBody>
        </Table>
      )}
      {plan.statements.length > 0 && (
        <details style={{ marginTop: 12 }}>
          <summary style={{ cursor: "pointer" }}>{t("Câu lệnh SQL sẽ chạy ({n})", { n: plan.statements.length })}</summary>
          <pre style={{ overflow: "auto", maxHeight: 220, fontSize: 12 }}>{plan.statements.join(";\n")}</pre>
        </details>
      )}
      {needsConfirm && (
        <FormControlLabel
          sx={{ mt: 1 }}
          control={<Checkbox checked={accepted} onChange={(event) => setAccepted(event.target.checked)} />}
          label={confirmLabel(plan.level, moves, regrants, repairs)}
        />
      )}
      {!unchanged && !blocked && (
        <Box sx={{ mt: 1.5 }}>
          <Button
            variant="contained"
            color={plan.level === "LOSS" ? "error" : "primary"}
            disabled={busy || (needsConfirm && !accepted)}
            onClick={() => onApply(needsConfirm ? plan.level : undefined)}
          >
            {plan.action === "repair" ? t("Sửa phần lệch") : applyLabel}
          </Button>
        </Box>
      )}
    </Box>
  );
}
