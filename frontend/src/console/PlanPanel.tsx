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

const LEVELS: Record<string, { label: string; color: "success" | "warning" | "error"; text: string }> = {
  SAFE: { label: "An toàn", color: "success", text: "Áp dụng được ngay, không cần chuyển dữ liệu." },
  MIGRATION: { label: "Cần chuyển dữ liệu", color: "warning", text: "Dữ liệu hiện có được chuyển sang cấu trúc mới." },
  LOSS: {
    label: "Mất dữ liệu",
    color: "error",
    text: "Một số dữ liệu sẽ bị xóa. Hãy sao lưu bằng deploy/backup.sh trước khi xác nhận.",
  },
};

interface Props {
  plan: Plan;
  busy: boolean;
  applyLabel?: string;
  onApply: (accept?: string) => void;
}

// What applying a model would do, and the button that does it once the risk is accepted.
export default function PlanPanel({ plan, busy, applyLabel = "Áp dụng", onApply }: Props) {
  const [accepted, setAccepted] = useState(false);
  const level = LEVELS[plan.level] ?? LEVELS.SAFE;
  const unchanged = plan.action === "unchanged";
  const blocked = plan.action === "rejected";
  const needsConfirm = !unchanged && !blocked && plan.level !== "SAFE";
  const open = Object.entries(plan.openRecords);

  return (
    <Box sx={{ mt: 2 }}>
      <Box sx={{ display: "flex", gap: 1, alignItems: "center", mb: 1 }}>
        <Typography variant="h6">Kế hoạch</Typography>
        <Chip size="small" color={level.color} label={level.label} />
        <Typography variant="body2">
          {plan.model}: phiên bản {plan.fromVersion} → {plan.version}
        </Typography>
      </Box>
      {unchanged && <Alert severity="info">Mô hình đang chạy đã giống tệp này, không có gì để áp dụng.</Alert>}
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
          Dữ liệu hiện có vi phạm quy tắc mới:
          <List dense>
            {plan.violations.map((violation) => (
              <ListItem key={`${violation.entity}-${violation.field}-${violation.rule}`}>
                <ListItemText
                  primary={`${violation.entity}${violation.field ? `.${violation.field}` : ""}: ${violation.rule}, ${violation.rows} dòng`}
                  secondary={violation.sample.length ? `Ví dụ mã dòng: ${violation.sample.join(", ")}` : undefined}
                />
              </ListItem>
            ))}
          </List>
        </Alert>
      )}
      {!unchanged && !blocked && <Alert severity={level.color === "success" ? "success" : level.color}>{level.text}</Alert>}
      {open.length > 0 && (
        <Alert severity="info" sx={{ mt: 1 }}>
          Hồ sơ đang xử lý bị ảnh hưởng: {open.map(([entity, count]) => `${entity} ${count}`).join(", ")}.
        </Alert>
      )}
      {plan.changes.length > 0 && (
        <Table size="small" sx={{ mt: 1 }}>
          <TableHead>
            <TableRow>
              <TableCell>Thay đổi</TableCell>
              <TableCell>Thực thể</TableCell>
              <TableCell>Trường</TableCell>
              <TableCell>Chi tiết</TableCell>
              <TableCell>Mức</TableCell>
            </TableRow>
          </TableHead>
          <TableBody>
            {plan.changes.map((change, index) => (
              <TableRow key={`${change.kind}-${change.entity}-${change.field}-${index}`}>
                <TableCell>{change.kind}</TableCell>
                <TableCell>{change.entity}</TableCell>
                <TableCell>{change.field ?? ""}</TableCell>
                <TableCell>{change.detail ?? ""}</TableCell>
                <TableCell>{(LEVELS[change.level] ?? LEVELS.SAFE).label}</TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      )}
      {plan.statements.length > 0 && (
        <details style={{ marginTop: 8 }}>
          <summary>Câu lệnh sẽ chạy ({plan.statements.length})</summary>
          <pre style={{ overflow: "auto", maxHeight: 220 }}>{plan.statements.join(";\n")}</pre>
        </details>
      )}
      {needsConfirm && (
        <FormControlLabel
          sx={{ mt: 1 }}
          control={<Checkbox checked={accepted} onChange={(event) => setAccepted(event.target.checked)} />}
          label={plan.level === "LOSS" ? "Tôi hiểu dữ liệu sẽ mất và đã sao lưu" : "Tôi xác nhận chuyển dữ liệu"}
        />
      )}
      {!unchanged && !blocked && (
        <Box sx={{ mt: 1 }}>
          <Button
            variant="contained"
            color={plan.level === "LOSS" ? "error" : "primary"}
            disabled={busy || (needsConfirm && !accepted)}
            onClick={() => onApply(needsConfirm ? plan.level : undefined)}
          >
            {applyLabel}
          </Button>
        </Box>
      )}
    </Box>
  );
}
