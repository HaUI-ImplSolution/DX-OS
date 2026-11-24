// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, type ReactNode } from "react";
import {
  Alert,
  Autocomplete,
  type AutocompleteRenderGetTagProps,
  Box,
  Button,
  Card,
  Checkbox,
  Chip,
  FormControlLabel,
  IconButton,
  MenuItem,
  Switch,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import CloseIcon from "@mui/icons-material/Close";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import OpenInFullIcon from "@mui/icons-material/OpenInFull";
import type { Family, ProcessView } from "./api";
import { DiagramStatus, type Diagrams } from "./diagramState";
import { COMPLETION_LABEL, PARAM_HELP } from "./formGuide";
import {
  Advanced,
  compact,
  DENSE,
  DurationInput,
  Empty,
  FormGrid,
  Group,
  GuideBox,
  list,
  NAME,
  NameInput,
  rec,
  renameKey,
  text,
  texts,
  uniqueName,
  Wide,
  asList,
  type Rec,
} from "./formParts";
import type { ProcessPart } from "./navModel";
import { processRows, writeSpec, type ActionRow, type Origin, type StateRow } from "./processModel";
import { FAMILY_LABEL } from "./labels";
import ProcessDiagram from "./ProcessDiagram";
import { conditionSource } from "../admin/condition";
import { t, ta } from "../shell/t";

const VIEWING = ["all", "read", "unit", "assigned"];

const ORIGIN_LABEL: Record<Origin, string> = {
  base: t("Theo mẫu"),
  changed: t("Đã sửa"),
  added: t("Tự thêm"),
  removed: t("Đã bỏ"),
};
const ORIGIN_COLOR: Record<Origin, "default" | "info" | "success" | "warning"> = {
  base: "default",
  changed: "info",
  added: "success",
  removed: "warning",
};

function OriginChip({ origin }: { origin: Origin }) {
  return <Chip size="small" variant="outlined" color={ORIGIN_COLOR[origin]} label={ORIGIN_LABEL[origin]} />;
}

const chips = (label: (item: string) => string) => (items: string[], getItemProps: AutocompleteRenderGetTagProps) =>
  items.map((item, index) => {
    const { key, ...itemProps } = getItemProps({ index });
    return <Chip key={key} size="small" label={label(item)} {...itemProps} />;
  });

// The process of one entity, edited in steps: the template and handlers with the diagram, the states,
// the actions, the time limits and what runs when a record is finished.
export default function ProcessEditor({
  entity,
  roleNames,
  calendarNames,
  families,
  familyOptions,
  step,
  diagrams,
  onZoom,
  view,
  selectedState,
  selectedAction,
  onStep,
  onSelectState,
  onSelectAction,
  setEntity,
}: {
  entity: Rec;
  roleNames: string[];
  calendarNames: string[];
  families: Family[];
  familyOptions: string[];
  step: ProcessPart;
  diagrams: Diagrams;
  onZoom: () => void;
  view: ProcessView | undefined;
  selectedState: string;
  selectedAction: string;
  onStep: (step: ProcessPart) => void;
  onSelectState: (state: string) => void;
  onSelectAction: (action: string) => void;
  setEntity: (changes: Rec) => void;
}) {
  const process = rec(entity.process);
  const hasProcess = entity.process !== undefined;
  const fields = rec(entity.fields);
  const fieldKeys = Object.keys(fields);
  const access = rec(entity.access);
  const family = families.find((item) => item.name === text(process.family));
  const params = family?.params.map((param) => param.name) ?? [];
  const { states, actions, live, stateBase, actionBase } = processRows(process, family);
  const terminal = live.filter((state) => state.terminal);
  const initial = text(process.initial) || family?.initial || live.find((state) => !state.terminal)?.name || "";
  const stateName = (name: string) => states.find((state) => state.name === name)?.label || name;

  const setProcess = (changes: Rec) => setEntity({ process: compact({ ...process, ...changes }) });
  const handlers = asList(process.handler);
  const setHandlers = (names: string[]) => setProcess({ handler: names.length === 1 ? names[0] : names.length === 0 ? undefined : names });

  const changeState = (row: StateRow, changes: Rec) =>
    setProcess({ states: writeSpec(rec(process.states), row.name, { label: row.label, terminal: row.terminal, ...changes }, stateBase(row.name)) });
  const changeAction = (row: ActionRow, changes: Rec) =>
    setProcess({
      actions: writeSpec(
        rec(process.actions),
        row.name,
        { label: row.label, from: row.from, to: row.to, by: row.by, role: row.by === "owner" ? [] : row.role, needs_note: row.needsNote, require: row.require, when: row.when, ...changes },
        actionBase(row.name),
      ),
    });
  const setStateSpec = (name: string, spec: Rec | null | undefined) => setProcess({ states: writeSpec(rec(process.states), name, spec) });
  const setActionSpec = (name: string, spec: Rec | null | undefined) => setProcess({ actions: writeSpec(rec(process.actions), name, spec) });

  // A state or action renamed here is renamed in the actions and the first state that point at it.
  const renameStep = (key: "states" | "actions", from: string, to: string) => {
    const changes: Rec = { [key]: renameKey(rec(process[key]), from, to) };
    if (key === "states") {
      const next: Rec = {};
      Object.entries(rec(process.actions)).forEach(([name, raw]) => {
        if (raw === null) {
          next[name] = null;
          return;
        }
        const spec = { ...rec(raw) };
        if (spec.from !== undefined) spec.from = asList(spec.from).map((item) => (item === from ? to : item));
        if (spec.to === from) spec.to = to;
        next[name] = spec;
      });
      changes.actions = Object.keys(next).length > 0 ? next : undefined;
      if (process.initial === from) changes.initial = to;
      onSelectState(to);
    } else onSelectAction(to);
    setProcess(changes);
  };

  // The diagram opens the step where the thing picked on it is edited.
  const pickState = (name: string) => {
    onSelectState(name);
    onStep("states");
  };
  const pickAction = (name: string) => {
    if (name === "auto_close") {
      onStep("deadline");
      return;
    }
    onSelectAction(name);
    onStep("actions");
  };

  if (!hasProcess || step === "overview") {
    return (
      <>
        <GuideBox section="process" />
        <FormControlLabel
          control={
            <Switch
              checked={hasProcess}
              onChange={(event) => {
                if (!event.target.checked) setEntity({ process: undefined });
                else setEntity({ process: { family: familyOptions[0], handler: roleNames[0] } });
              }}
            />
          }
          label={t("Hồ sơ của thực thể này đi qua một quy trình")}
        />
        {!hasProcess && <Empty>{t("Không có quy trình thì thực thể chỉ là danh mục để nhập và xem.")}</Empty>}
        {hasProcess && (
          <>
            <Group title={t("Mẫu quy trình và người xử lý")}>
              <FormGrid>
                <TextField
                  variant="outlined"
                  select
                  size="small"
                  fullWidth
                  label={t("Mẫu quy trình")}
                  value={text(process.family)}
                  sx={DENSE}
                  slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
                  onChange={(event) =>
                    event.target.value === ""
                      ? setProcess({
                          family: undefined,
                          auto_close: undefined,
                          booking: undefined,
                          after: undefined,
                          initial: "moi",
                          states: { moi: { label: t("Mới") }, xong: { label: t("Đã xong"), terminal: true } },
                          actions: { hoan_tat: { label: t("Hoàn tất"), from: ["moi"], to: "xong", by: "handler" } },
                        })
                      : setProcess({ family: event.target.value, auto_close: undefined, booking: undefined, after: undefined, initial: undefined, states: undefined, actions: undefined })
                  }
                >
                  <MenuItem value="">{t("Tự khai báo, không dùng mẫu")}</MenuItem>
                  {familyOptions.map((item) => (
                    <MenuItem key={item} value={item}>
                      {FAMILY_LABEL[item] ?? item}
                    </MenuItem>
                  ))}
                </TextField>
                <Box sx={{ gridColumn: { sm: "span 2" } }}>
                  <Autocomplete
                    multiple
                    size="small"
                    options={roleNames}
                    value={handlers}
                    renderTags={chips((item) => item)}
                    renderInput={(inputParams) => (
                      <TextField variant="outlined" {...inputParams} required label={t("Vai trò xử lý")} helperText={t("Vai trò được duyệt hoặc xử lý hồ sơ")} sx={DENSE} />
                    )}
                    onChange={(_, names) => setHandlers(names)}
                  />
                </Box>
                <TextField
                  variant="outlined"
                  select
                  size="small"
                  fullWidth
                  label={t("Trạng thái ban đầu")}
                  value={text(process.initial)}
                  sx={DENSE}
                  slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
                  onChange={(event) => setProcess({ initial: event.target.value })}
                >
                  <MenuItem value="">{family ? t("(theo mẫu)") : t("(trạng thái đầu tiên)")}</MenuItem>
                  {live
                    .filter((state) => !state.terminal)
                    .map((state) => (
                      <MenuItem key={state.name} value={state.name}>
                        {t(state.label || state.name)}
                      </MenuItem>
                    ))}
                </TextField>
                <Wide>
                  <Tooltip title={PARAM_HELP.allow_self_decision}>
                    <FormControlLabel
                      control={<Switch size="small" checked={process.allow_self_decision === true} onChange={(event) => setProcess({ allow_self_decision: event.target.checked })} />}
                      label={<Typography variant="body2">{t("Người tạo được tự quyết định")}</Typography>}
                    />
                  </Tooltip>
                </Wide>
              </FormGrid>
              {handlers.some((name) => !roleNames.includes(name)) && (
                <Alert severity="error" sx={{ mt: 1 }}>
                  {t("Có vai trò xử lý chưa khai báo ở mục Vai trò: {roles}", { roles: handlers.filter((name) => !roleNames.includes(name)).join(", ") })}
                </Alert>
              )}
              {handlers.filter((name) => roleNames.includes(name)).some((name) => !VIEWING.includes(text(access[name]))) && (
                <Alert severity="warning" sx={{ mt: 1 }}>
                  {t("Vai trò xử lý cần quyền Tất cả, Chỉ đọc, Của đơn vị hoặc Được giao ở mục Quyền truy cập của thực thể này: {roles}", {
                    roles: handlers.filter((name) => roleNames.includes(name) && !VIEWING.includes(text(access[name]))).join(", "),
                  })}
                </Alert>
              )}
            </Group>
            {params.includes("booking") && <BookingGroup process={process} fields={fields} setProcess={setProcess} />}
            <Group
              title={t("Sơ đồ quy trình")}
              hint={t("Bấm vào một trạng thái hoặc mũi tên để mở chỗ sửa nó.")}
              action={
                view && (
                  <Tooltip title={t("Mở to")}>
                    <IconButton size="small" onClick={onZoom} aria-label={t("Mở to sơ đồ")}>
                      <OpenInFullIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                )
              }
            >
              <DiagramStatus state={diagrams.state} problems={diagrams.problems} drawn={view !== undefined} />
              {view ? (
                <ProcessDiagram
                  family={view}
                  height={320}
                  selectedState={selectedState}
                  selectedAction={selectedAction}
                  onSelectState={pickState}
                  onSelectAction={pickAction}
                />
              ) : (
                <Empty>{t("Sơ đồ hiện khi tệp hợp lệ.")}</Empty>
              )}
            </Group>
            {family && (
              <Advanced label={t("Xem mẫu quy trình gốc")}>
                <Box sx={{ border: 1, borderColor: "divider", borderRadius: 1, p: 1.5, mt: 1 }}>
                  <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                    {t('Mẫu "{name}" có sẵn', { name: FAMILY_LABEL[family.name] ?? family.name })}
                  </Typography>
                  <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mb: 1 }}>
                    {family.states.map((state) => (
                      <Chip key={state.name} size="small" variant={state.terminal ? "filled" : "outlined"} label={`${t(state.label)} · ${state.name}${state.terminal ? t(" (cuối)") : ""}`} />
                    ))}
                  </Box>
                  <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
                    {family.actions.map((action) => (
                      <li key={action.name}>
                        <b>{ta(action.label)}</b> <code>{action.name}</code>:{" "}
                        {t("{from} → {to}, do {who}", {
                          from: action.from.map((from) => t(family.states.find((state) => state.name === from)?.label ?? from)).join(t(" hoặc ")),
                          to: t(family.states.find((state) => state.name === action.to)?.label ?? action.to),
                          who: action.by === "OWNER" ? t("người tạo hồ sơ") : t("vai trò xử lý"),
                        })}
                        {action.needsNote ? t(", bắt buộc ghi lý do") : ""}
                        {action.when ? t(", chỉ khi {condition}", { condition: conditionSource(action.when) }) : ""}
                      </li>
                    ))}
                  </Box>
                </Box>
              </Advanced>
            )}
          </>
        )}
      </>
    );
  }

  if (step === "states") {
    return (
      <Group
        title={t("Trạng thái")}
        hint={t("Mỗi hồ sơ ở một trạng thái. Sửa nhãn, thêm trạng thái mới hoặc bỏ trạng thái của mẫu. Đổi các mục này khi còn hồ sơ đang xử lý sẽ bị từ chối.")}
        action={
          <Button size="small" startIcon={<AddIcon />} onClick={() => setStateSpec(uniqueName("trang_thai_moi", states.map((state) => state.name)), { label: t("Trạng thái mới") })}>
            {t("Thêm trạng thái")}
          </Button>
        }
      >
        <StatesTable
          rows={states}
          initial={initial}
          selected={selectedState}
          onSelect={onSelectState}
          onChange={changeState}
          onRename={(from, to) => renameStep("states", from, to)}
          onRemove={(row) => setStateSpec(row.name, row.base ? null : undefined)}
          onRestore={(row) => setStateSpec(row.name, undefined)}
        />
      </Group>
    );
  }

  if (step === "actions") {
    const current = actions.find((action) => action.name === selectedAction);
    return (
      <>
        <Group
          title={t("Hành động")}
          hint={t("Hành động chuyển hồ sơ từ trạng thái này sang trạng thái khác. Người nộp chỉ rút lại được hồ sơ ở các trạng thái khai trong hành động withdraw; không khai withdraw thì không rút lại được.")}
          action={
            <Button
              size="small"
              startIcon={<AddIcon />}
              onClick={() => {
                const name = uniqueName("hanh_dong_moi", actions.map((action) => action.name));
                setActionSpec(name, { label: t("Hành động mới"), from: live[0] ? [live[0].name] : [], to: live[live.length - 1]?.name ?? "", by: "handler" });
                onSelectAction(name);
              }}
            >
              {t("Thêm hành động")}
            </Button>
          }
        >
          {actions.length === 0 ? (
            <Empty>{t("Chưa có hành động nào.")}</Empty>
          ) : (
            <Box sx={{ overflowX: "auto" }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>{t("Nhãn nút")}</TableCell>
                    <TableCell>{t("Định danh")}</TableCell>
                    <TableCell>{t("Chuyển trạng thái")}</TableCell>
                    <TableCell>{t("Người thực hiện")}</TableCell>
                    <TableCell>{t("Nguồn")}</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {actions.map((row) => (
                    <TableRow
                      key={row.name}
                      hover
                      selected={row.name === selectedAction}
                      sx={{ cursor: "pointer", opacity: row.removed ? 0.6 : 1 }}
                      onClick={() => onSelectAction(row.name === selectedAction ? "" : row.name)}
                    >
                      <TableCell>{ta(row.label || row.name)}</TableCell>
                      <TableCell sx={{ fontFamily: "ui-monospace, monospace" }}>{row.name}</TableCell>
                      <TableCell>{`${row.from.map(stateName).join(", ")} → ${stateName(row.to)}`}</TableCell>
                      <TableCell>{row.by === "owner" ? t("Người nộp") : row.role.length ? row.role.join(", ") : t("Vai trò xử lý")}</TableCell>
                      <TableCell>
                        <OriginChip origin={row.origin} />
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>
          )}
        </Group>
        {current && (
          <ActionDetail
            row={current}
            names={actions.map((action) => action.name)}
            live={live}
            roleNames={roleNames}
            fieldKeys={fieldKeys.filter((key) => rec(fields[key]).type !== "file")}
            stateName={stateName}
            onChange={(changes) => changeAction(current, changes)}
            onRename={(to) => renameStep("actions", current.name, to)}
            onRemove={() => {
              setActionSpec(current.name, current.base ? null : undefined);
              if (!current.base) onSelectAction("");
            }}
            onRestore={() => setActionSpec(current.name, undefined)}
            onClose={() => onSelectAction("")}
          />
        )}
      </>
    );
  }

  if (step === "deadline") {
    const autoClose = process.auto_close;
    const autoAfter = typeof autoClose === "string" ? autoClose : text(rec(autoClose).after);
    const autoAction = typeof autoClose === "string" ? (family?.name === "work_item" ? "confirm" : "") : text(rec(autoClose).action);
    // The short form `auto_close: 7d` stands for the action a work_item record waits for.
    const setAuto = (after: string | undefined, action: string) => {
      if (!after) setProcess({ auto_close: undefined });
      else if (family?.name === "work_item" && (action === "confirm" || action === "")) setProcess({ auto_close: after });
      else setProcess({ auto_close: action ? { after, action } : { after } });
    };
    const unknownCalendar = process.calendar !== undefined && !calendarNames.includes(text(process.calendar));
    const levels = list(process.escalate).length;
    return (
      <>
        <Group title={t("Hạn xử lý")} hint={`${PARAM_HELP.deadline}. ${PARAM_HELP.calendar}.`}>
          <FormGrid>
            <DurationInput label={t("Hạn xử lý")} value={text(process.deadline)} onChange={(deadline) => setProcess({ deadline })} />
            {(calendarNames.length > 0 || process.calendar !== undefined) && (
              <TextField
                variant="outlined"
                select
                size="small"
                fullWidth
                label={t("Lịch làm việc")}
                value={text(process.calendar)}
                sx={DENSE}
                error={unknownCalendar}
                helperText={
                  unknownCalendar
                    ? t("Lịch chưa khai ở calendars: {calendar}", { calendar: text(process.calendar) })
                    : process.calendar === undefined
                      ? t("Không chọn thì hạn đếm liên tục")
                      : t("Hạn chỉ đếm trong giờ làm")
                }
                onChange={(event) => setProcess({ calendar: event.target.value || undefined })}
              >
                <MenuItem value="">{t("Không có (đếm liên tục)")}</MenuItem>
                {[...new Set([...calendarNames, ...(process.calendar !== undefined ? [text(process.calendar)] : [])])].map((name) => (
                  <MenuItem key={name} value={name}>
                    {name}
                  </MenuItem>
                ))}
              </TextField>
            )}
          </FormGrid>
          <Typography variant="body2" color="text.secondary">
            {levels > 0 ? t("Tệp đang khai {n} cấp báo khi quá hạn; sửa các cấp này ở chế độ YAML.", { n: levels }) : `${PARAM_HELP.escalate}.`}
          </Typography>
        </Group>
        <Group title={t("Tự đóng hồ sơ")} hint={`${PARAM_HELP.auto_close}.`}>
          <FormGrid>
            <DurationInput label={t("Tự đóng sau")} value={autoAfter} onChange={(after) => setAuto(after, autoAction)} />
            {autoAfter !== "" && (
              <TextField
                variant="outlined"
                select
                size="small"
                fullWidth
                label={t("Hành động tự đóng")}
                value={autoAction}
                sx={DENSE}
                helperText={t("Hệ thống làm thay người nộp")}
                onChange={(event) => setAuto(autoAfter, event.target.value)}
              >
                {actions
                  .filter((action) => !action.removed && action.by === "owner")
                  .map((action) => (
                    <MenuItem key={action.name} value={action.name}>
                      {ta(action.label || action.name)}
                    </MenuItem>
                  ))}
              </TextField>
            )}
          </FormGrid>
        </Group>
      </>
    );
  }

  const after = rec(process.after);
  const userFields = fieldKeys.filter((key) => rec(fields[key]).type === "user");
  const groupFields = fieldKeys.filter((key) => ["choice", "text"].includes(text(rec(fields[key]).type)));
  const fieldOption = (key: string) => (
    <MenuItem key={key} value={key}>
      {text(rec(fields[key]).label) || key} ({key})
    </MenuItem>
  );
  const setCompletions = (state: string, items: Rec[]) => {
    const next = { ...after };
    if (items.length === 0) delete next[state];
    else next[state] = items;
    setProcess({ after: Object.keys(next).length > 0 ? next : undefined });
  };
  return (
    <Group title={t("Hành động hoàn tất")} hint={t("Chạy khi hồ sơ đến một trạng thái cuối: thêm hoặc bỏ một người khỏi nhóm.")}>
      {terminal.length === 0 && <Empty>{t("Quy trình chưa có trạng thái cuối nào.")}</Empty>}
      {terminal.map((state) => {
        const items = list(after[state.name]).map(rec);
        const update = (index: number, changes: Rec) => setCompletions(state.name, items.map((item, i) => (i === index ? compact({ ...item, ...changes }) : item)));
        return (
          <Box key={state.name} sx={{ mb: 2 }}>
            <Box sx={{ display: "flex", alignItems: "center" }}>
              <Typography variant="body2" sx={{ fontWeight: 600, flexGrow: 1 }}>
                {t("Khi hồ sơ {state}", { state: t(state.label || state.name).toLowerCase() })}
              </Typography>
              <Button size="small" startIcon={<AddIcon />} onClick={() => setCompletions(state.name, [...items, { do: "add_to_group", user: "created_by", group: "" }])}>
                {t("Thêm hành động")}
              </Button>
            </Box>
            {items.length === 0 && <Empty>{t("Không chạy gì.")}</Empty>}
            {items.map((item, index) => {
              const byField = item.group_from !== undefined;
              return (
                // biome-ignore lint/suspicious/noArrayIndexKey: completions have no name of their own.
                <Box key={index} sx={{ display: "flex", gap: 1, alignItems: "flex-start" }}>
                  <FormGrid>
                    <TextField variant="outlined" select size="small" fullWidth label={t("Thao tác")} value={text(item.do)} sx={DENSE} onChange={(event) => update(index, { do: event.target.value })}>
                      {Object.entries(COMPLETION_LABEL).map(([key, label]) => (
                        <MenuItem key={key} value={key}>
                          {label}
                        </MenuItem>
                      ))}
                    </TextField>
                    <TextField variant="outlined" select size="small" fullWidth label={t("Người")} value={text(item.user)} sx={DENSE} onChange={(event) => update(index, { user: event.target.value })}>
                      <MenuItem value="created_by">{t("Người tạo hồ sơ")}</MenuItem>
                      {userFields.map(fieldOption)}
                    </TextField>
                    <ToggleButtonGroup
                      size="small"
                      exclusive
                      fullWidth
                      value={byField ? "field" : "fixed"}
                      onChange={(_, next) => next && update(index, next === "field" ? { group: undefined, group_from: groupFields[0] ?? "" } : { group_from: undefined, group: "" })}
                    >
                      <ToggleButton value="fixed">{t("Nhóm cố định")}</ToggleButton>
                      <ToggleButton value="field">{t("Nhóm theo trường")}</ToggleButton>
                    </ToggleButtonGroup>
                    {byField ? (
                      <TextField variant="outlined" select size="small" fullWidth label={t("Trường chứa tên nhóm")} value={text(item.group_from)} sx={DENSE} onChange={(event) => update(index, { group_from: event.target.value })}>
                        {groupFields.map(fieldOption)}
                      </TextField>
                    ) : (
                      <TextField variant="outlined" size="small" fullWidth label={t("Nhóm người dùng (Keycloak)")} value={text(item.group)} sx={DENSE} onChange={(event) => update(index, { group: event.target.value })} />
                    )}
                  </FormGrid>
                  <Tooltip title={t("Xóa hành động")}>
                    <IconButton size="small" sx={{ mt: 1.5 }} aria-label={t("Xóa hành động")} onClick={() => setCompletions(state.name, items.filter((_, i) => i !== index))}>
                      <DeleteOutlineIcon fontSize="small" color="error" />
                    </IconButton>
                  </Tooltip>
                </Box>
              );
            })}
          </Box>
        );
      })}
    </Group>
  );
}

function BookingGroup({ process, fields, setProcess }: { process: Rec; fields: Rec; setProcess: (changes: Rec) => void }) {
  const booking = rec(process.booking);
  const keys = Object.keys(fields);
  const option = (key: string) => (
    <MenuItem key={key} value={key}>
      {text(rec(fields[key]).label) || key} ({key})
    </MenuItem>
  );
  const pick = (name: string, label: string, type: string) => (
    <TextField variant="outlined" select required size="small" fullWidth label={label} value={text(booking[name])} sx={DENSE} onChange={(event) => setProcess({ booking: { ...booking, [name]: event.target.value } })}>
      {keys.filter((key) => rec(fields[key]).type === type).map(option)}
    </TextField>
  );
  return (
    <Group title={t("Đặt chỗ")} hint={t("Lượt đặt trùng thời gian vượt sức chứa của tài nguyên bị từ chối.")}>
      <FormGrid>
        {pick("resource", t("Trường chỉ tài nguyên"), "reference")}
        {pick("starts", t("Trường bắt đầu"), "datetime")}
        {pick("ends", t("Trường kết thúc"), "datetime")}
        <TextField
          variant="outlined"
          size="small"
          fullWidth
          label={t("Sức chứa")}
          value={text(booking.capacity)}
          sx={DENSE}
          helperText={t("Số lượt cùng lúc")}
          slotProps={{ htmlInput: { inputMode: "numeric" } }}
          onChange={(event) => {
            const digits = event.target.value.replace(/\D/g, "").slice(0, 6);
            setProcess({ booking: compact({ ...booking, capacity: digits ? Math.max(1, Number(digits)) : undefined }) });
          }}
        />
      </FormGrid>
    </Group>
  );
}

function StatesTable({
  rows,
  initial,
  selected,
  onSelect,
  onChange,
  onRename,
  onRemove,
  onRestore,
}: {
  rows: StateRow[];
  initial: string;
  selected: string;
  onSelect: (name: string) => void;
  onChange: (row: StateRow, changes: Rec) => void;
  onRename: (from: string, to: string) => void;
  onRemove: (row: StateRow) => void;
  onRestore: (row: StateRow) => void;
}) {
  const refs = useRef<Record<string, HTMLTableRowElement | null>>({});
  useEffect(() => {
    if (selected) refs.current[selected]?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [selected]);
  if (rows.length === 0) return <Empty>{t("Chưa có trạng thái nào.")}</Empty>;
  const names = rows.map((row) => row.name);
  return (
    <Box sx={{ overflowX: "auto" }}>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell sx={{ width: 200 }}>{t("Định danh")}</TableCell>
            <TableCell>{t("Nhãn")}</TableCell>
            <TableCell>{t("Trạng thái cuối")}</TableCell>
            <TableCell>{t("Nguồn")}</TableCell>
            <TableCell padding="checkbox" />
          </TableRow>
        </TableHead>
        <TableBody>
          {rows.map((row, index) => (
            <TableRow
              // biome-ignore lint/suspicious/noArrayIndexKey: a state renamed keeps its row, so its name input keeps the focus.
              key={index}
              ref={(node) => {
                refs.current[row.name] = node;
              }}
              selected={row.name === selected}
              onClick={() => onSelect(row.name)}
              sx={{ opacity: row.removed ? 0.6 : 1 }}
            >
              <TableCell>
                {row.base ? (
                  <Typography variant="body2" sx={{ fontFamily: "ui-monospace, monospace" }}>
                    {row.name}
                  </Typography>
                ) : (
                  <NameInput label={t("Định danh")} value={row.name} pattern={NAME} taken={names} onCommit={(to) => onRename(row.name, to)} />
                )}
                {row.name === initial && !row.removed && <Chip size="small" color="primary" label={t("Bắt đầu")} sx={{ mt: 0.5 }} />}
              </TableCell>
              <TableCell>
                {row.removed ? (
                  <Typography variant="body2" color="text.secondary">
                    {t("Đã bỏ khỏi quy trình này")}
                  </Typography>
                ) : (
                  <TextField variant="outlined" size="small" fullWidth value={row.label} sx={DENSE} aria-label={t("Nhãn")} onChange={(event) => onChange(row, { label: event.target.value })} />
                )}
              </TableCell>
              <TableCell>{!row.removed && <Checkbox size="small" checked={row.terminal} aria-label={t("Trạng thái cuối")} onChange={(event) => onChange(row, { terminal: event.target.checked })} />}</TableCell>
              <TableCell>
                <OriginChip origin={row.origin} />
              </TableCell>
              <TableCell padding="checkbox">
                {row.removed ? (
                  <Button size="small" onClick={() => onRestore(row)}>
                    {t("Khôi phục")}
                  </Button>
                ) : (
                  <Tooltip title={row.base ? t("Bỏ trạng thái của mẫu") : t("Xóa trạng thái")}>
                    <IconButton size="small" aria-label={t("Xóa trạng thái")} onClick={() => onRemove(row)}>
                      <DeleteOutlineIcon fontSize="small" color="error" />
                    </IconButton>
                  </Tooltip>
                )}
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}

function ActionDetail({
  row,
  names,
  live,
  roleNames,
  fieldKeys,
  stateName,
  onChange,
  onRename,
  onRemove,
  onRestore,
  onClose,
}: {
  row: ActionRow;
  names: string[];
  live: StateRow[];
  roleNames: string[];
  fieldKeys: string[];
  stateName: (name: string) => string;
  onChange: (changes: Rec) => void;
  onRename: (to: string) => void;
  onRemove: () => void;
  onRestore: () => void;
  onClose: () => void;
}) {
  const card = useRef<HTMLDivElement | null>(null);
  useEffect(() => {
    card.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [row.name]);
  const header: ReactNode = (
    <Box sx={{ display: "flex", alignItems: "center", gap: 1, mb: 1 }}>
      <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
        {t("Hành động: {label}", { label: ta(row.label || row.name) })}
      </Typography>
      <OriginChip origin={row.origin} />
      {row.removed ? (
        <Button size="small" onClick={onRestore}>
          {t("Khôi phục")}
        </Button>
      ) : (
        <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} onClick={onRemove}>
          {row.base ? t("Bỏ hành động của mẫu") : t("Xóa hành động")}
        </Button>
      )}
      <IconButton size="small" aria-label={t("Đóng")} onClick={onClose}>
        <CloseIcon fontSize="small" />
      </IconButton>
    </Box>
  );
  return (
    <Card variant="outlined" ref={card} sx={{ p: 2, scrollMarginTop: 16 }}>
      {header}
      {row.removed ? (
        <Empty>{t("Đã bỏ khỏi quy trình này")}</Empty>
      ) : (
        <>
          <FormGrid>
            <TextField
              variant="outlined"
              size="small"
              fullWidth
              label={t("Nhãn nút")}
              value={row.label}
              helperText={row.label ? undefined : t("Để trống thì nút hiện mã hành động")}
              sx={DENSE}
              onChange={(event) => onChange({ label: event.target.value })}
            />
            {row.base ? (
              <TextField variant="outlined" size="small" fullWidth disabled label={t("Định danh")} value={row.name} sx={DENSE} helperText={t("Định danh của mẫu không đổi được")} />
            ) : (
              <NameInput label={t("Định danh")} value={row.name} pattern={NAME} taken={names} onCommit={onRename} />
            )}
            <TextField variant="outlined" select size="small" fullWidth label={t("Người thực hiện")} value={row.by} sx={DENSE} onChange={(event) => onChange({ by: event.target.value })}>
              <MenuItem value="handler">{t("Vai trò xử lý")}</MenuItem>
              <MenuItem value="owner">{t("Người nộp")}</MenuItem>
            </TextField>
            <Box sx={{ gridColumn: { sm: "span 2" } }}>
              <Autocomplete
                multiple
                size="small"
                options={live.map((state) => state.name)}
                getOptionLabel={stateName}
                value={row.from}
                renderTags={chips(stateName)}
                renderInput={(inputParams) => <TextField variant="outlined" {...inputParams} required label={t("Từ trạng thái")} sx={DENSE} />}
                onChange={(_, items) => onChange({ from: items })}
              />
            </Box>
            <TextField variant="outlined" select required size="small" fullWidth label={t("Sang trạng thái")} value={row.to} sx={DENSE} onChange={(event) => onChange({ to: event.target.value })}>
              {live.map((state) => (
                <MenuItem key={state.name} value={state.name}>
                  {t(state.label || state.name)}
                </MenuItem>
              ))}
            </TextField>
            <Wide>
              <FormControlLabel
                control={<Checkbox size="small" checked={row.needsNote} onChange={(event) => onChange({ needs_note: event.target.checked })} />}
                label={<Typography variant="body2">{t("Cần ghi lý do")}</Typography>}
              />
            </Wide>
          </FormGrid>
          <Advanced key={row.name} label={t("Điều kiện thực hiện")} open={row.role.length > 0 || row.require.length > 0 || row.when !== ""}>
            <FormGrid>
              {row.by === "handler" && (
                <Autocomplete
                  multiple
                  size="small"
                  options={roleNames}
                  value={row.role}
                  renderTags={chips((item) => item)}
                  renderInput={(inputParams) => (
                    <TextField variant="outlined" {...inputParams} label={t("Chỉ vai trò")} placeholder={row.role.length ? "" : t("mọi vai trò xử lý")} sx={DENSE} />
                  )}
                  onChange={(_, items) => onChange({ role: items })}
                />
              )}
              <Autocomplete
                multiple
                size="small"
                options={fieldKeys}
                value={row.require}
                renderTags={chips((item) => item)}
                renderInput={(inputParams) => <TextField variant="outlined" {...inputParams} label={t("Bắt buộc điền trường")} sx={DENSE} />}
                onChange={(_, items) => onChange({ require: items })}
              />
              {row.by === "handler" && (
                <TextField
                  variant="outlined"
                  size="small"
                  fullWidth
                  label={t("Chỉ khi")}
                  placeholder="amount > 5000000"
                  helperText={t("Một phép so sánh trên trường của hồ sơ")}
                  value={row.when}
                  sx={DENSE}
                  onChange={(event) => onChange({ when: event.target.value })}
                />
              )}
            </FormGrid>
          </Advanced>
          {texts(row.role).some((name) => !roleNames.includes(name)) && (
            <Alert severity="error" sx={{ mt: 1 }}>
              {t("Vai trò chưa khai báo: {roles}", { roles: row.role.filter((name) => !roleNames.includes(name)).join(", ") })}
            </Alert>
          )}
        </>
      )}
    </Card>
  );
}
