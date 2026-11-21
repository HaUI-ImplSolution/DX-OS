// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { MenuItem, TextField } from "@mui/material";
import type { Project } from "./api";
import { t } from "../shell/t";

export const ALL = "__all__";
export const NONE = "__none__";

// Whether an item of the given project shows under the chosen filter.
export const inFilter = (filter: string, project: string | null) =>
  filter === ALL || (filter === NONE ? project === null : project === filter);

// Chooses all projects, the unclassified, or one project.
export function ProjectFilter({ projects, value, onChange }: { projects: Project[]; value: string; onChange: (value: string) => void }) {
  return (
    <TextField select size="small" label={t("Dự án")} value={value} onChange={(event) => onChange(event.target.value)} sx={{ width: 260, maxWidth: "100%" }}>
      <MenuItem value={ALL}>{t("Tất cả dự án")}</MenuItem>
      <MenuItem value={NONE}>{t("Chưa phân loại")}</MenuItem>
      {projects.map((project) => (
        <MenuItem key={project.name} value={project.name}>
          {project.label}
        </MenuItem>
      ))}
    </TextField>
  );
}

// Sets the project of one model or application; the empty choice leaves it unclassified.
export function ProjectSelect({
  projects,
  value,
  disabled,
  onChange,
}: {
  projects: Project[];
  value: string | null;
  disabled?: boolean;
  onChange: (value: string | null) => void;
}) {
  return (
    <TextField
      select
      size="small"
      value={value ?? ""}
      disabled={disabled}
      onChange={(event) => onChange(event.target.value === "" ? null : event.target.value)}
      slotProps={{ select: { displayEmpty: true } }}
      sx={{ minWidth: 180 }}
    >
      <MenuItem value="">{t("Chưa phân loại")}</MenuItem>
      {projects.map((project) => (
        <MenuItem key={project.name} value={project.name}>
          {project.label}
        </MenuItem>
      ))}
    </TextField>
  );
}

// The label of a project by its name, or the name when the project is not in the list.
export const projectLabel = (projects: Project[], name: string | null) =>
  name === null ? t("Chưa phân loại") : (projects.find((project) => project.name === name)?.label ?? name);
