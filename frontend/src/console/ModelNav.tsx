// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState, type ReactNode } from "react";
import { Box, Collapse, IconButton, List, ListItemButton, ListItemText, Tooltip } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ChevronRightIcon from "@mui/icons-material/ChevronRight";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { levelUnder, navKey, type EntityPart, type Issue, type Level, type NavTarget, type ProcessPart } from "./navModel";
import { t } from "../shell/t";

export interface NavEntity {
  key: string;
  label: string;
  fields: number;
  documents: number;
  process: boolean;
}

export const ENTITY_PART_LABEL: Record<EntityPart, string> = {
  general: t("Thông tin chung"),
  fields: t("Trường dữ liệu"),
  access: t("Quyền truy cập"),
  process: t("Quy trình"),
  ui: t("Giao diện"),
  documents: t("Mẫu in"),
  ai: t("Trợ lý nghiệp vụ"),
};

export const PROCESS_PART_LABEL: Record<ProcessPart, string> = {
  overview: t("Tổng quan"),
  states: t("Trạng thái"),
  actions: t("Hành động"),
  deadline: t("Thời hạn"),
  automation: t("Tự động hóa"),
};

const ENTITY_PARTS: EntityPart[] = ["general", "fields", "access", "process", "ui", "documents", "ai"];
const PROCESS_PARTS: ProcessPart[] = ["overview", "states", "actions", "deadline", "automation"];

function Item({
  label,
  depth,
  selected,
  warn,
  open,
  onToggle,
  onClick,
  action,
}: {
  label: string;
  depth: number;
  selected: boolean;
  warn: Level | null | false;
  open?: boolean;
  onToggle?: () => void;
  onClick: () => void;
  action?: ReactNode;
}) {
  return (
    <ListItemButton selected={selected} onClick={onClick} sx={{ pl: 0.5 + depth * 1.75, pr: 0.5, py: 0.25, borderRadius: 1, minHeight: 32 }}>
      <Box sx={{ width: 24, display: "flex", flexShrink: 0 }}>
        {onToggle && (
          <IconButton
            size="small"
            sx={{ p: 0.25 }}
            aria-label={open ? t("Thu gọn") : t("Mở rộng")}
            onClick={(event) => {
              event.stopPropagation();
              onToggle();
            }}
          >
            {open ? <ExpandMoreIcon fontSize="small" /> : <ChevronRightIcon fontSize="small" />}
          </IconButton>
        )}
      </Box>
      <ListItemText
        primary={label}
        slotProps={{ primary: { variant: "body2", noWrap: true, title: label, sx: { fontWeight: selected ? 600 : 400 } } }}
      />
      {warn && (
        <Tooltip title={warn === "error" ? t("Có lỗi chặn áp dụng") : t("Có cấu hình nên kiểm lại")}>
          <ErrorOutlineIcon fontSize="small" color={warn} sx={{ ml: 0.5 }} />
        </Tooltip>
      )}
      {action}
    </ListItemButton>
  );
}

// The tree of the model: its common parts, then every entity with the parts it is edited in.
export default function ModelNav({
  target,
  entities,
  counts,
  issues,
  onSelect,
  onAddEntity,
}: {
  target: NavTarget;
  entities: NavEntity[];
  counts: { roles: number; units: number };
  issues: Issue[];
  onSelect: (target: NavTarget) => void;
  onAddEntity: () => void;
}) {
  const current = navKey(target);
  const [open, setOpen] = useState<string[]>(() => ["entities", ...(target.kind === "entity" ? [`entity:${target.entity}`] : [])]);
  const toggle = (key: string) => setOpen((keys) => (keys.includes(key) ? keys.filter((item) => item !== key) : [...keys, key]));
  // The branch of the place being edited stays open, also when the place is reached from elsewhere.
  useEffect(() => {
    if (target.kind !== "entity") return;
    const branch = [`entity:${target.entity}`, ...(target.part === "process" ? [`entity:${target.entity}:process`] : [])];
    setOpen((keys) => (branch.every((key) => keys.includes(key)) ? keys : [...new Set([...keys, "entities", ...branch])]));
  }, [target]);

  const top = (kind: "general" | "roles" | "units", label: string) => (
    <Item label={label} depth={0} selected={current === kind} warn={levelUnder(issues, kind)} onClick={() => onSelect({ kind })} />
  );

  return (
    <List dense disablePadding aria-label={t("Cấu trúc mô hình")}>
      {top("general", t("Thông tin chung"))}
      {top("roles", t("Vai trò ({n})", { n: counts.roles }))}
      {top("units", t("Đơn vị ({n})", { n: counts.units }))}
      <Item
        label={t("Thực thể ({n})", { n: entities.length })}
        depth={0}
        selected={current === "entities"}
        warn={!open.includes("entities") && levelUnder(issues, "entity")}
        open={open.includes("entities")}
        onToggle={() => toggle("entities")}
        onClick={() => onSelect({ kind: "entities" })}
        action={
          <Tooltip title={t("Thêm thực thể")}>
            <IconButton
              size="small"
              sx={{ p: 0.25 }}
              aria-label={t("Thêm thực thể")}
              onClick={(event) => {
                event.stopPropagation();
                onAddEntity();
              }}
            >
              <AddIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        }
      />
      <Collapse in={open.includes("entities")}>
        {entities.map((entity) => {
          const branch = `entity:${entity.key}`;
          const expanded = open.includes(branch);
          return (
            <Box key={entity.key}>
              <Item
                label={entity.label}
                depth={1}
                selected={false}
                warn={!expanded && levelUnder(issues, branch)}
                open={expanded}
                onToggle={() => toggle(branch)}
                onClick={() => {
                  if (!expanded) toggle(branch);
                  onSelect({ kind: "entity", entity: entity.key, part: "general" });
                }}
              />
              <Collapse in={expanded}>
                {ENTITY_PARTS.map((part) => {
                  const key = `${branch}:${part}`;
                  const label =
                    part === "fields"
                      ? `${ENTITY_PART_LABEL.fields} (${entity.fields})`
                      : part === "documents"
                        ? `${ENTITY_PART_LABEL.documents} (${entity.documents})`
                        : part === "process" && !entity.process
                          ? t("Quy trình (chưa bật)")
                          : ENTITY_PART_LABEL[part];
                  if (part !== "process" || !entity.process) {
                    return (
                      <Item
                        key={part}
                        label={label}
                        depth={2}
                        selected={current === key || (part === "process" && current.startsWith(`${key}:`))}
                        warn={levelUnder(issues, key)}
                        onClick={() => onSelect({ kind: "entity", entity: entity.key, part })}
                      />
                    );
                  }
                  const steps = open.includes(key);
                  return (
                    <Box key={part}>
                      <Item
                        label={label}
                        depth={2}
                        selected={false}
                        warn={!steps && levelUnder(issues, key)}
                        open={steps}
                        onToggle={() => toggle(key)}
                        onClick={() => {
                          if (!steps) toggle(key);
                          onSelect({ kind: "entity", entity: entity.key, part: "process", step: "overview" });
                        }}
                      />
                      <Collapse in={steps}>
                        {PROCESS_PARTS.map((step) => (
                          <Item
                            key={step}
                            label={PROCESS_PART_LABEL[step]}
                            depth={3}
                            selected={current === `${key}:${step}`}
                            warn={levelUnder(issues, `${key}:${step}`)}
                            onClick={() => onSelect({ kind: "entity", entity: entity.key, part: "process", step })}
                          />
                        ))}
                      </Collapse>
                    </Box>
                  );
                })}
              </Collapse>
            </Box>
          );
        })}
      </Collapse>
    </List>
  );
}
