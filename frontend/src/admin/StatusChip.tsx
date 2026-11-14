// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Chip, type ChipProps } from "@mui/material";
import type { EntityMeta } from "./types";
import { t } from "../shell/t";

// How a state of any process family reads at a glance: the tone follows the meaning of the state.
export const TONES: Record<string, ChipProps["color"]> = {
  submitted: "primary",
  requested: "primary",
  in_progress: "info",
  confirmed: "info",
  in_use: "info",
  returned: "warning",
  no_show: "warning",
  resolved: "success",
  approved: "success",
  completed: "success",
  closed: "success",
  rejected: "error",
  withdrawn: "default",
};

export function StatusChip({ entity, status, overdue }: { entity: EntityMeta; status: string; overdue?: boolean }) {
  const state = entity.process?.states.find((candidate) => candidate.name === status);
  return (
    <>
      <Chip size="small" variant="outlined" color={TONES[status] ?? "default"} label={state ? t(state.label) : status} />
      {overdue && <Chip size="small" color="error" label={t("Quá hạn")} sx={{ ml: 0.75 }} />}
    </>
  );
}
