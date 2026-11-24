// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useMemo, useState } from "react";
import {
  Alert,
  AlertTitle,
  Autocomplete,
  Box,
  Breadcrumbs,
  Button,
  Checkbox,
  Collapse,
  FormControlLabel,
  IconButton,
  Link,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Tooltip,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import AccessMatrix, { ScopeLegend } from "./AccessMatrix";
import type { Diagrams } from "./diagramState";
import FieldsEditor from "./FieldsEditor";
import { FIELD_TYPE_LABEL, SCOPE_LABEL } from "./formGuide";
import {
  allowed,
  compact,
  ConfirmRemove,
  DENSE,
  Empty,
  FormGrid,
  Group,
  GuideBox,
  NAME,
  NameInput,
  ROLE,
  rec,
  renameKey,
  text,
  texts,
  uniqueName,
  list,
  type Rec,
} from "./formParts";
import { entityUses, renameRoleIn, roleUses } from "./modelRefs";
import { localIssues, navKey, targetOfKey, type Issue, type NavTarget } from "./navModel";
import ModelNav, { ENTITY_PART_LABEL, PROCESS_PART_LABEL } from "./ModelNav";
import ProcessEditor from "./ProcessEditor";
import { FAMILY_LABEL } from "./labels";
import { useFamilies } from "./useFamilies";
import { t } from "../shell/t";

interface Props {
  schema: Rec;
  value: Rec;
  onChange: (value: Rec) => void;
  nav: NavTarget;
  onNav: (target: NavTarget) => void;
  diagrams: Diagrams;
  // Opens the diagram of the entity being edited in a large window.
  onZoom: () => void;
  // The problems the backend found, placed on the part of the builder that edits them.
  problems: Issue[];
}

function AiTaskFields({ value, fields, onChange }: { value: unknown; fields: Rec; onChange: (value: unknown) => void }) {
  const tasks = rec(rec(value).tasks);
  const change = (name: string, patch: Rec) => onChange({ tasks: { ...tasks, [name]: { ...rec(tasks[name]), ...patch } } });
  return <Box sx={{ display: "grid", gap: 2 }}>
    <Typography variant="body2">{t("Nhiệm vụ chỉ tạo đề xuất; quyền ghi vẫn do Core kiểm tra sau xác nhận. Chỉ có tác dụng khi bản cài bật trợ lý.")}</Typography>
    {Object.entries(tasks).map(([name, raw]) => {
      const task = rec(raw);
      return <Box key={name} sx={{ p: 2, border: 1, borderColor: "divider", display: "grid", gap: 2 }}>
        <Typography variant="subtitle2">{name}</Typography>
        <TextField size="small" label={t("Tên hiển thị")} value={text(task.label)} onChange={e => change(name, { label: e.target.value })} />
        <Autocomplete multiple options={Object.keys(fields).filter(key => !["file","reference","user"].includes(text(rec(fields[key]).type)))}
          value={texts(task.input_fields)} onChange={(_, input_fields) => change(name, { input_fields })}
          renderInput={params => <TextField {...params} label={t("Trường đầu vào")} />} />
        <TextField select label={t("Trường đề xuất")} value={text(task.output_field)} onChange={e => change(name, { output_field: e.target.value })}>
          {Object.keys(fields).filter(key => ["text","choice"].includes(text(rec(fields[key]).type)) && !rec(fields[key]).multiple).map(key => <MenuItem key={key} value={key}>{text(rec(fields[key]).label) || key}</MenuItem>)}
        </TextField>
        <TextField multiline minRows={2} label={t("Hướng dẫn nhiệm vụ")} value={text(task.instruction)} onChange={e => change(name, { instruction: e.target.value })} />
        <Autocomplete multiple freeSolo options={[]} value={texts(task.sources)} onChange={(_, sources) => change(name, { sources })}
          renderInput={params => <TextField {...params} label={t("Mã nguồn tri thức có phiên bản")} helperText={t("Nhập mã nguồn do người vận hành cung cấp, nhấn Enter để thêm.")} />} />
        <TextField type="number" label={t("Độ dài đề xuất tối đa")} placeholder="500" value={task.max_length ?? ""} slotProps={{ htmlInput: { min: 1, max: 2000 } }}
          onChange={e => {
            if (e.target.value !== "") return change(name, { max_length: Number(e.target.value) });
            const { max_length: _unset, ...rest } = task;
            onChange({ tasks: { ...tasks, [name]: rest } });
          }} />
        <Button color="error" onClick={() => {
          const remaining = { ...tasks }; delete remaining[name]; onChange(Object.keys(remaining).length ? { tasks: remaining } : undefined);
        }}>{t("Xóa nhiệm vụ")}</Button>
      </Box>;
    })}
    <Button onClick={() => change(uniqueName("task", Object.keys(tasks)), { label: t("Nhiệm vụ mới"), input_fields: [], output_field: "", instruction: "", sources: [], max_length: 500 })}>{t("Thêm nhiệm vụ")}</Button>
  </Box>;
}

// One row of a list of named items (roles, units): the name, its description and its buttons.
const ITEM_ROW = { display: "grid", gap: 1.5, alignItems: "start", gridTemplateColumns: { xs: "1fr", sm: "minmax(180px, 240px) minmax(0, 1fr) auto" }, py: 0.75 };

// The model builder: a tree of the model on the left, and the part picked in it on the right. It offers
// the roles, fields and entities already declared wherever one is named, and says what each part accepts.
export default function ModelForm({ schema, value, onChange, nav, onNav, diagrams, onZoom, problems }: Props) {
  const families = useFamilies();
  const [field, setField] = useState("");
  const [state, setState] = useState("");
  const [action, setAction] = useState("");
  const [removing, setRemoving] = useState<{ what: string; uses: string[]; remove: () => void } | null>(null);

  const roles = list(value.roles).map(rec);
  const roleNames = roles.map((role) => text(role.name));
  const calendarNames = Object.keys(rec(value.calendars));
  const units = list(value.units).map(rec);
  const unitNames = units.map((unit) => text(unit.name));
  const entities = rec(value.entities);
  const entityKeys = Object.keys(entities);

  // A place that no longer exists, such as an entity just deleted, falls back to the list of entities.
  const target: NavTarget = nav.kind === "entity" && !entityKeys.includes(nav.entity) ? { kind: "entities" } : nav;
  const entityKey = target.kind === "entity" ? target.entity : "";
  const entity = rec(entities[entityKey]);

  // Going to another entity clears what was picked in this one.
  const go = (next: NavTarget) => {
    if (next.kind !== "entity" || next.entity !== entityKey) {
      setField("");
      setState("");
      setAction("");
    }
    onNav(next);
  };

  const fieldTypes = allowed(schema, ["properties", "entities", "additionalProperties", "properties", "fields", "additionalProperties", "properties", "type"], Object.keys(FIELD_TYPE_LABEL));
  const scopes = allowed(schema, ["properties", "entities", "additionalProperties", "properties", "access", "additionalProperties"], Object.keys(SCOPE_LABEL));
  const familyOptions = allowed(schema, ["properties", "entities", "additionalProperties", "properties", "process", "properties", "family"], families.map((item) => item.name));

  const issues = useMemo(() => [...problems, ...localIssues(value)], [problems, value]);
  const here = issues.filter((issue) => issue.key === navKey(target));
  const [listed, setListed] = useState(false);

  // The name of a place of the builder, as the tree shows it.
  const placeName = (place: NavTarget): string => {
    if (place.kind === "general") return t("Thông tin chung");
    if (place.kind === "roles") return t("Vai trò");
    if (place.kind === "units") return t("Đơn vị");
    if (place.kind === "entities") return t("Thực thể");
    const name = text(rec(entities[place.entity]).label) || place.entity;
    return place.part === "process" ? `${name} › ${ENTITY_PART_LABEL.process} › ${PROCESS_PART_LABEL[place.step ?? "overview"]}` : `${name} › ${ENTITY_PART_LABEL[place.part]}`;
  };
  const errors = issues.filter((issue) => issue.level === "error").length;
  const warnings = issues.length - errors;

  const set = (changes: Rec) => onChange({ ...value, ...changes });
  const setEntity = (key: string, changes: Rec) => set({ entities: { ...entities, [key]: compact({ ...rec(entities[key]), ...changes }) } });
  const ask = (what: string, uses: string[], remove: () => void) => (uses.length > 0 ? setRemoving({ what, uses, remove }) : remove());

  // Roles ------------------------------------------------------------------------------------------

  const renameRole = (index: number, to: string) =>
    set({ roles: roles.map((role, i) => (i === index ? { ...role, name: to } : role)), entities: renameRoleIn(entities, roleNames[index], to) });
  const setRole = (index: number, changes: Rec) => set({ roles: roles.map((item, i) => (i === index ? compact({ ...item, ...changes }) : item)) });
  const setAccess = (key: string, role: string, scope: string) => {
    const access = { ...rec(rec(entities[key]).access) };
    if (scope === "") delete access[role];
    else access[role] = scope;
    setEntity(key, { access });
  };

  const rolesPart = (
    <>
      <GuideBox section="roles" />
      <Group
        title={t("Danh sách vai trò")}
        action={
          <Button size="small" startIcon={<AddIcon />} onClick={() => set({ roles: [...roles, { name: uniqueName("VAI_MOI", roleNames) }] })}>
            {t("Thêm vai trò")}
          </Button>
        }
      >
        {roles.length === 0 && <Empty>{t("Chưa có vai trò nào. Mỗi mô hình cần ít nhất một vai trò.")}</Empty>}
        {roles.map((role, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: a role renamed keeps its row, so its name input keeps the focus.
          <Box key={index} sx={ITEM_ROW}>
            <NameInput label={t("Tên vai trò")} value={roleNames[index]} pattern={ROLE} upper taken={roleNames} onCommit={(name) => renameRole(index, name)} />
            <TextField variant="outlined" size="small" fullWidth label={t("Mô tả")} value={text(role.description)} sx={DENSE} onChange={(event) => setRole(index, { description: event.target.value })} />
            <Box sx={{ display: "flex", alignItems: "center" }}>
              <Tooltip title={t("Người giữ vai trò này đăng nhập thêm bằng mã xác thực một lần")}>
                <FormControlLabel
                  sx={{ mr: 0 }}
                  control={<Checkbox size="small" checked={role.staff === true} onChange={(event) => setRole(index, { staff: event.target.checked })} />}
                  label={<Typography variant="body2">{t("Nhân viên")}</Typography>}
                />
              </Tooltip>
              <Tooltip title={t("Xóa vai trò")}>
                <IconButton
                  size="small"
                  aria-label={t("Xóa vai trò")}
                  onClick={() => ask(t("vai trò {name}", { name: roleNames[index] }), roleUses(entities, roleNames[index]), () => set({ roles: roles.filter((_, i) => i !== index) }))}
                >
                  <DeleteOutlineIcon fontSize="small" color="error" />
                </IconButton>
              </Tooltip>
            </Box>
          </Box>
        ))}
      </Group>
      <Group title={t("Quyền truy cập theo vai trò")} hint={t("Mỗi ô là quyền của một vai trò trên một thực thể. Ô báo đỏ là vai trò xử lý quy trình nhưng không xem được hồ sơ.")}>
        <AccessMatrix roleNames={roleNames} entities={entities} scopes={scopes} onChange={setAccess} />
      </Group>
    </>
  );

  // Units ------------------------------------------------------------------------------------------

  const unitsPart = (
    <>
      <GuideBox section="units" />
      <Group
        title={t("Danh sách đơn vị")}
        action={
          <Button size="small" startIcon={<AddIcon />} onClick={() => set({ units: [...units, { name: uniqueName("DON_VI_MOI", unitNames) }] })}>
            {t("Thêm đơn vị")}
          </Button>
        }
      >
        {units.length === 0 && <Empty>{t("Chưa có đơn vị. Mô hình không cần đơn vị thì để trống.")}</Empty>}
        {units.map((unit, index) => (
          // biome-ignore lint/suspicious/noArrayIndexKey: a unit renamed keeps its row, so its name input keeps the focus.
          <Box key={index} sx={ITEM_ROW}>
            <NameInput
              label={t("Tên đơn vị")}
              value={unitNames[index]}
              pattern={ROLE}
              upper
              taken={unitNames}
              onCommit={(name) => set({ units: units.map((item, i) => (i === index ? { ...item, name } : item)) })}
            />
            <TextField
              variant="outlined"
              size="small"
              fullWidth
              label={t("Nhãn hiển thị")}
              value={text(unit.label)}
              sx={DENSE}
              onChange={(event) => set({ units: units.map((item, i) => (i === index ? compact({ ...item, label: event.target.value }) : item)) })}
            />
            <Tooltip title={t("Xóa đơn vị")}>
              <IconButton size="small" aria-label={t("Xóa đơn vị")} onClick={() => set({ units: units.filter((_, i) => i !== index) })}>
                <DeleteOutlineIcon fontSize="small" color="error" />
              </IconButton>
            </Tooltip>
          </Box>
        ))}
      </Group>
    </>
  );

  // Entities ---------------------------------------------------------------------------------------

  const open = (key: string) => go({ kind: "entity", entity: key, part: "general" });

  const renameEntity = (to: string) => {
    const renamed: Rec = {};
    Object.entries(renameKey(entities, entityKey, to)).forEach(([key, raw]) => {
      const item = rec(raw);
      const fields: Rec = {};
      Object.entries(rec(item.fields)).forEach(([name, spec]) => {
        const f = rec(spec);
        fields[name] = f.to === entityKey ? { ...f, to } : f;
      });
      renamed[key] = { ...item, fields };
    });
    set({ entities: renamed });
    if (target.kind === "entity") onNav({ ...target, entity: to });
  };

  const addEntity = () => {
    const key = uniqueName("thuc_the_moi", entityKeys);
    const first = roleNames[0] ?? "";
    set({
      entities: {
        ...entities,
        [key]: { fields: { ten: { type: "text", label: t("Tên"), required: true } }, access: first ? { [first]: "all" } : {} },
      },
    });
    open(key);
  };

  const removeEntity = (key: string) =>
    ask(t("thực thể {name}", { name: text(rec(entities[key]).label) || key }), entityUses(entities, key), () => {
      const rest = { ...entities };
      delete rest[key];
      set({ entities: rest });
      go({ kind: "entities" });
    });

  const entitiesPart = (
    <>
      <GuideBox section="entities" />
      <Group
        title={t("Danh sách thực thể")}
        action={
          <Button size="small" startIcon={<AddIcon />} onClick={addEntity}>
            {t("Thêm thực thể")}
          </Button>
        }
      >
        {entityKeys.length === 0 ? (
          <Empty>{t("Chưa có thực thể. Thêm một thực thể để bắt đầu.")}</Empty>
        ) : (
          <Box sx={{ overflowX: "auto" }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t("Tên hiển thị")}</TableCell>
                  <TableCell>{t("Định danh")}</TableCell>
                  <TableCell>{t("Trường dữ liệu")}</TableCell>
                  <TableCell>{t("Quy trình")}</TableCell>
                  <TableCell padding="checkbox" />
                </TableRow>
              </TableHead>
              <TableBody>
                {entityKeys.map((key) => {
                  const item = rec(entities[key]);
                  const process = rec(item.process);
                  return (
                    <TableRow key={key} hover sx={{ cursor: "pointer" }} onClick={() => open(key)}>
                      <TableCell>{text(item.label) || key}</TableCell>
                      <TableCell sx={{ fontFamily: "ui-monospace, monospace" }}>{key}</TableCell>
                      <TableCell>{Object.keys(rec(item.fields)).length}</TableCell>
                      <TableCell>
                        {item.process === undefined ? t("Không có") : process.family ? (FAMILY_LABEL[text(process.family)] ?? text(process.family)) : t("Tự khai báo")}
                      </TableCell>
                      <TableCell padding="checkbox">
                        <Tooltip title={t("Xóa thực thể")}>
                          <IconButton
                            size="small"
                            aria-label={t("Xóa thực thể")}
                            onClick={(event) => {
                              event.stopPropagation();
                              removeEntity(key);
                            }}
                          >
                            <DeleteOutlineIcon fontSize="small" color="error" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
              </TableBody>
            </Table>
          </Box>
        )}
      </Group>
    </>
  );

  const fields = rec(entity.fields);
  const generalOfEntity = () => {
    const references = Object.keys(fields).filter((key) => rec(fields[key]).type === "reference");
    return (
      <>
        <GuideBox section="entities" />
        <Group
          title={t("Thông tin chung")}
          action={
            <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} onClick={() => removeEntity(entityKey)}>
              {t("Xóa thực thể này")}
            </Button>
          }
        >
          <FormGrid>
            <TextField variant="outlined" size="small" fullWidth label={t("Tên hiển thị")} value={text(entity.label)} sx={DENSE} onChange={(event) => setEntity(entityKey, { label: event.target.value })} />
            <NameInput label={t("Định danh")} value={entityKey} pattern={NAME} taken={entityKeys} onCommit={renameEntity} help={t("Dùng trong API và tên bảng")} />
            <TextField
              variant="outlined"
              select
              size="small"
              fullWidth
              label={t("Kế thừa quyền từ hồ sơ liên quan")}
              value={text(entity.owner_via)}
              sx={DENSE}
              helperText={t("Dành cho thực thể con: chọn trường tham chiếu tới hồ sơ cha, người có quyền trên hồ sơ cha có quyền trên hồ sơ này")}
              slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
              onChange={(event) => setEntity(entityKey, { owner_via: event.target.value })}
            >
              <MenuItem value="">{t("(không)")}</MenuItem>
              {references.map((key) => (
                <MenuItem key={key} value={key}>
                  {text(rec(fields[key]).label) || key}
                </MenuItem>
              ))}
            </TextField>
          </FormGrid>
        </Group>
      </>
    );
  };

  const accessOfEntity = () => {
    const access = rec(entity.access);
    const unknown = Object.keys(access).filter((name) => !roleNames.includes(name));
    return (
      <>
        <GuideBox section="access" />
        <Group title={t("Quyền truy cập theo vai trò")} hint={t("Quyền của mọi vai trò trên mọi thực thể xem cùng lúc ở mục Vai trò.")}>
          {roleNames.length === 0 && <Empty>{t("Khai báo vai trò ở mục Vai trò trước, rồi quay lại đây để cấp quyền.")}</Empty>}
          {[...roleNames, ...unknown].map((name) => (
            <Box key={name} sx={{ ...ITEM_ROW, gridTemplateColumns: { xs: "1fr", sm: "minmax(180px, 240px) minmax(220px, 320px)" } }}>
              <Box sx={{ pt: 0.75 }}>
                <Typography variant="body2" sx={{ fontWeight: 600, fontFamily: "ui-monospace, monospace" }}>
                  {name}
                </Typography>
                {unknown.includes(name) && (
                  <Typography variant="caption" color="error">
                    {t("Vai trò chưa khai báo")}
                  </Typography>
                )}
              </Box>
              <TextField
                variant="outlined"
                select
                size="small"
                fullWidth
                value={text(access[name])}
                sx={DENSE}
                slotProps={{ select: { displayEmpty: true } }}
                aria-label={t("Quyền của {role} trên {entity}", { role: name, entity: entityKey })}
                onChange={(event) => setAccess(entityKey, name, event.target.value)}
              >
                <MenuItem value="">{t("Không có quyền")}</MenuItem>
                {scopes.map((scope) => (
                  <MenuItem key={scope} value={scope}>
                    {SCOPE_LABEL[scope] ?? scope}
                  </MenuItem>
                ))}
              </TextField>
            </Box>
          ))}
          <ScopeLegend scopes={scopes} />
        </Group>
      </>
    );
  };

  const generalPart = (
    <>
      <GuideBox section="general" />
      <Group title={t("Thông tin chung")}>
        <FormGrid>
          <NameInput label={t("Tên mô hình")} value={text(value.name)} pattern={NAME} onCommit={(name) => set({ name })} help={t("Đặt tên bảng dữ liệu và API")} />
          <Box sx={{ gridColumn: { sm: "span 2" } }}>
            <TextField
              variant="outlined"
              size="small"
              fullWidth
              label={t("Kết nối gửi sự kiện")}
              value={text(value.webhook)}
              error={text(value.webhook) !== "" && !NAME.test(text(value.webhook))}
              helperText={t("Tên kết nối đã cấu hình ở máy chủ, nhận lịch sử hồ sơ của mô hình; để trống thì dùng kết nối mặc định")}
              sx={DENSE}
              onChange={(event) => set({ webhook: event.target.value })}
            />
          </Box>
        </FormGrid>
      </Group>
    </>
  );

  const content = () => {
    if (target.kind === "general") return generalPart;
    if (target.kind === "roles") return rolesPart;
    if (target.kind === "units") return unitsPart;
    if (target.kind === "entities") return entitiesPart;
    if (target.part === "general") return generalOfEntity();
    if (target.part === "fields") {
      return (
        <FieldsEditor
          entity={entity}
          entities={entities}
          fieldTypes={fieldTypes}
          family={families.find((item) => item.name === text(rec(entity.process).family))}
          selected={field}
          flagged={here.flatMap((issue) => (issue.field ? [issue.field] : []))}
          onSelect={setField}
          setEntity={(changes) => setEntity(entityKey, changes)}
        />
      );
    }
    if (target.part === "access") return accessOfEntity();
    if (target.part === "ai") return <AiTaskFields key={entityKey} value={entity.ai} fields={fields} onChange={(ai) => setEntity(entityKey, { ai })} />;
    return (
      <ProcessEditor
        entity={entity}
        roleNames={roleNames}
        calendarNames={calendarNames}
        families={families}
        familyOptions={familyOptions}
        step={target.step ?? "overview"}
        diagrams={diagrams}
        onZoom={onZoom}
        view={diagrams.processes.find((item) => item.entity === entityKey)}
        selectedState={state}
        selectedAction={action}
        onStep={(step) => onNav({ kind: "entity", entity: entityKey, part: "process", step })}
        onSelectState={setState}
        onSelectAction={setAction}
        setEntity={(changes) => setEntity(entityKey, changes)}
      />
    );
  };

  // Where the page being edited sits in the model, each step a way back up.
  const trail = () => {
    const root = <Link key="root" component="button" type="button" underline="hover" color="inherit" onClick={() => go({ kind: "general" })}>{t("Mô hình nghiệp vụ")}</Link>;
    if (target.kind !== "entity") {
      const title = { general: t("Thông tin chung"), roles: t("Vai trò"), units: t("Đơn vị"), entities: t("Thực thể") }[target.kind];
      return [root, <Typography key="here" variant="body2" color="text.primary">{title}</Typography>];
    }
    const steps = [
      root,
      <Link key="entities" component="button" type="button" underline="hover" color="inherit" onClick={() => go({ kind: "entities" })}>{t("Thực thể")}</Link>,
      <Link key="entity" component="button" type="button" underline="hover" color="inherit" onClick={() => open(entityKey)}>{text(entity.label) || entityKey}</Link>,
    ];
    if (target.part === "process" && entity.process !== undefined) {
      steps.push(
        <Link key="process" component="button" type="button" underline="hover" color="inherit" onClick={() => onNav({ kind: "entity", entity: entityKey, part: "process", step: "overview" })}>{ENTITY_PART_LABEL.process}</Link>,
        <Typography key="here" variant="body2" color="text.primary">{PROCESS_PART_LABEL[target.step ?? "overview"]}</Typography>,
      );
    } else steps.push(<Typography key="here" variant="body2" color="text.primary">{ENTITY_PART_LABEL[target.part]}</Typography>);
    return steps;
  };

  return (
    <Box sx={{ display: "grid", gap: 2, alignItems: "start", gridTemplateColumns: { xs: "1fr", md: "250px minmax(0, 1fr)" } }}>
      <Box
        sx={{
          position: { md: "sticky" },
          top: { md: 16 },
          maxHeight: { md: "calc(100vh - 32px)" },
          overflowY: "auto",
          border: 1,
          borderColor: "divider",
          borderRadius: 1,
          p: 0.5,
        }}
      >
        <ModelNav
          target={target}
          entities={entityKeys.map((key) => ({
            key,
            label: text(rec(entities[key]).label) || key,
            fields: Object.keys(rec(rec(entities[key]).fields)).length,
            process: rec(entities[key]).process !== undefined,
          }))}
          counts={{ roles: roles.length, units: units.length }}
          issues={issues}
          onSelect={go}
          onAddEntity={addEntity}
        />
      </Box>
      <Box sx={{ minWidth: 0 }}>
        {issues.length > 0 && (
          <Alert
            severity={errors > 0 ? "error" : "warning"}
            sx={{ mb: 1.5 }}
            action={
              <Button color="inherit" size="small" onClick={() => setListed(!listed)}>
                {listed ? t("Thu gọn") : t("Xem danh sách")}
              </Button>
            }
          >
            {errors > 0 && warnings > 0
              ? t("{errors} lỗi chặn áp dụng và {warnings} cảnh báo.", { errors, warnings })
              : errors > 0
                ? t("{n} lỗi chặn áp dụng.", { n: errors })
                : t("{n} cảnh báo; Core quyết định khi xem kế hoạch.", { n: warnings })}
            <Collapse in={listed}>
              <Box component="ul" sx={{ m: 0, mt: 0.5, pl: 2.5 }}>
                {issues.map((issue) => (
                  <li key={`${issue.key}:${issue.message}`}>
                    <Link component="button" type="button" color="inherit" sx={{ textAlign: "left", verticalAlign: "baseline" }} onClick={() => {
                      go(targetOfKey(issue.key));
                      if (issue.field) setField(issue.field);
                    }}>
                      {placeName(targetOfKey(issue.key))}
                    </Link>
                    {`: ${issue.message}`}
                    {issue.level === "warning" ? t(" (cảnh báo)") : ""}
                  </li>
                ))}
              </Box>
            </Collapse>
          </Alert>
        )}
        <Breadcrumbs sx={{ mb: 1.5, "& .MuiBreadcrumbs-li": { fontSize: 14 } }}>{trail()}</Breadcrumbs>
        {(["error", "warning"] as const).map((level) => {
          const messages = here.filter((issue) => issue.level === level).map((issue) => issue.message);
          return messages.length > 0 && (
            <Alert key={level} severity={level} sx={{ mb: 1.5 }}>
              <AlertTitle>{level === "error" ? t("Core từ chối tệp vì:") : t("Có thể bị từ chối khi xem kế hoạch:")}</AlertTitle>
              {messages.map((message) => (
                <div key={message}>{message}</div>
              ))}
            </Alert>
          );
        })}
        {content()}
      </Box>
      {removing && <ConfirmRemove what={removing.what} uses={removing.uses} onConfirm={removing.remove} onClose={() => setRemoving(null)} />}
    </Box>
  );
}

