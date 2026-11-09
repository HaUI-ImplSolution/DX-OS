// SPDX-License-Identifier: Apache-2.0
import { useEffect, useMemo, useState, type ReactNode } from "react";
import {
  Autocomplete,
  Box,
  Button,
  Checkbox,
  Chip,
  Collapse,
  Divider,
  FormControlLabel,
  IconButton,
  MenuItem,
  Switch,
  Tab,
  Tabs,
  TextField,
  ToggleButton,
  ToggleButtonGroup,
  Tooltip,
  Typography,
} from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import InfoOutlinedIcon from "@mui/icons-material/InfoOutlined";
import TuneIcon from "@mui/icons-material/Tune";
import GuideBlocks from "./GuideBlocks";
import { useFamilies } from "./useFamilies";
import {
  COMPLETION_LABEL,
  FIELD_TYPE_LABEL,
  GUIDE,
  PARAM_HELP,
  SCOPE_LABEL,
  type Section,
} from "./formGuide";
import { FAMILY_LABEL } from "./labels";
import { t, ta } from "../shell/t";

type Rec = Record<string, unknown>;

const rec = (value: unknown): Rec =>
  value !== null && typeof value === "object" && !Array.isArray(value) ? (value as Rec) : {};
const list = (value: unknown): unknown[] => (Array.isArray(value) ? value : []);
const text = (value: unknown): string => (value === undefined || value === null ? "" : String(value));
const texts = (value: unknown): string[] => list(value).map(text);
// The condition of an action as it is written in the file: the field, the operator and the value.
const conditionOf = (when?: { field: string; op: string; value: number | string } | null): string => (when ? `${when.field} ${when.op} ${when.value}` : "");

// A copy without the keys that hold nothing, so the file does not fill with empty entries.
function compact(source: Rec): Rec {
  const kept: Rec = {};
  Object.entries(source).forEach(([key, item]) => {
    if (item !== undefined && item !== "" && item !== false) kept[key] = item;
  });
  return kept;
}

// The same mapping with one key renamed, in the same place.
function renameKey(source: Rec, from: string, to: string): Rec {
  const renamed: Rec = {};
  Object.entries(source).forEach(([key, item]) => (renamed[key === from ? to : key] = item));
  return renamed;
}

function uniqueName(base: string, taken: string[]): string {
  if (!taken.includes(base)) return base;
  let n = 2;
  while (taken.includes(`${base}_${n}`)) n++;
  return `${base}_${n}`;
}

// The values the schema of the model file allows at a place, such as the types of a field.
function allowed(schema: Rec, path: string[], fallback: string[]): string[] {
  let node: unknown = schema;
  for (const key of path) node = rec(node)[key];
  const values = rec(node).enum;
  return Array.isArray(values) ? values.map(String) : fallback;
}

const NAME = /^[a-z][a-z0-9_]{0,39}$/;
const ROLE = /^[A-Z][A-Z0-9_]{0,39}$/;
const DURATION = /^([0-9]{1,6})([mhd])$/;

const DENSE = { "& .MuiInputBase-input": { py: 0.9, fontSize: 14 } };

// A name that is checked as it is typed; only a valid name reaches the model.
function NameInput({
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
    <TextField variant="outlined"
      size="small"
      label={label}
      value={draft}
      error={problem !== ""}
      helperText={problem || help}
      sx={DENSE}
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
function DurationInput({
  value,
  onChange,
  label,
}: {
  value: string;
  onChange: (value: string | undefined) => void;
  label: string;
}) {
  const match = DURATION.exec(value);
  const [unit, setUnit] = useState(match?.[2] ?? "h");
  useEffect(() => {
    if (match) setUnit(match[2]);
  }, [match]);
  const number = match?.[1] ?? "";
  return (
    <Box sx={{ display: "flex", gap: 0.5 }}>
      <TextField variant="outlined"
        size="small"
        label={label}
        value={number}
        sx={{ ...DENSE, flex: 1, minWidth: 90 }}
        slotProps={{ htmlInput: { inputMode: "numeric" } }}
        onChange={(event) => {
          const digits = event.target.value.replace(/\D/g, "").slice(0, 6);
          onChange(digits && Number(digits) > 0 ? `${Number(digits)}${unit}` : undefined);
        }}
      />
      <TextField variant="outlined"
        select
        size="small"
        value={unit}
        sx={{ ...DENSE, width: 92 }}
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
function GuideBox({ section }: { section: Section }) {
  const guide = GUIDE[section];
  const [open, setOpen] = useState(false);
  return (
    <Box sx={{ bgcolor: "action.hover", borderRadius: 1, px: 1.5, py: 0.75, mb: 1.5 }}>
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

function Row({ children }: { children: ReactNode }) {
  return <Box sx={{ display: "flex", gap: 1, alignItems: "flex-start", flexWrap: "wrap", py: 1 }}>{children}</Box>;
}

function Empty({ children }: { children: ReactNode }) {
  return (
    <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>
      {children}
    </Typography>
  );
}

interface Props {
  schema: Rec;
  value: Rec;
  onChange: (value: Rec) => void;
}

type Part = "general" | "roles" | "units" | "entities";
type EntityPart = "general" | "fields" | "access" | "process";

// A form for the model file. It shows only the part being edited, offers the roles, fields and
// entities already declared wherever one is named, and says what each part accepts.
export default function ModelForm({ schema, value, onChange }: Props) {
  const families = useFamilies();
  const [part, setPart] = useState<Part>("general");
  const [entityPart, setEntityPart] = useState<EntityPart>("general");
  const [selected, setSelected] = useState("");

  const roles = list(value.roles).map(rec);
  const roleNames = roles.map((role) => text(role.name));
  const units = list(value.units).map(rec);
  const entities = rec(value.entities);
  const entityKeys = Object.keys(entities);
  const entityKey = entityKeys.includes(selected) ? selected : (entityKeys[0] ?? "");
  const entity = rec(entities[entityKey]);

  const fieldTypes = allowed(schema, ["properties", "entities", "additionalProperties", "properties", "fields", "additionalProperties", "properties", "type"], Object.keys(FIELD_TYPE_LABEL));
  const scopes = allowed(schema, ["properties", "entities", "additionalProperties", "properties", "access", "additionalProperties"], Object.keys(SCOPE_LABEL));

  const set = (changes: Rec) => onChange({ ...value, ...changes });
  const setEntity = (key: string, changes: Rec) =>
    set({ entities: { ...entities, [key]: compact({ ...rec(entities[key]), ...changes }) } });

  // Roles ------------------------------------------------------------------------------------------

  const renameRole = (index: number, to: string) => {
    const from = roleNames[index];
    const nextRoles = roles.map((role, i) => (i === index ? { ...role, name: to } : role));
    const renamedEntities: Rec = {};
    Object.entries(entities).forEach(([key, raw]) => {
      const item = { ...rec(raw) };
      const access = rec(item.access);
      if (from in access) item.access = renameKey(access, from, to);
      if (item.process) {
        const process = { ...rec(item.process) };
        const handler = process.handler;
        if (handler === from) process.handler = to;
        else if (Array.isArray(handler)) process.handler = handler.map((name) => (name === from ? to : name));
        item.process = process;
      }
      renamedEntities[key] = item;
    });
    set({ roles: nextRoles, entities: renamedEntities });
  };

  const rolesPart = (
    <>
      <GuideBox section="roles" />
      {roles.length === 0 && <Empty>{t("Chưa có vai nào. Mỗi mô hình cần ít nhất một vai.")}</Empty>}
      {roles.map((role, index) => (
        <Row key={index}>
          <Box sx={{ width: 190 }}>
            <NameInput label={t("Tên vai")} value={roleNames[index]} pattern={ROLE} upper taken={roleNames} onCommit={(name) => renameRole(index, name)} />
          </Box>
          <TextField variant="outlined"
            size="small"
            label={t("Mô tả")}
            value={text(role.description)}
            sx={{ ...DENSE, flex: 1, minWidth: 160 }}
            onChange={(event) => set({ roles: roles.map((item, i) => (i === index ? compact({ ...item, description: event.target.value }) : item)) })}
          />
          <Tooltip title={t("Người giữ vai này đăng nhập thêm bằng mã xác thực một lần")}>
            <FormControlLabel
              sx={{ mr: 0 }}
              control={
                <Checkbox
                  size="small"
                  checked={role.staff === true}
                  onChange={(event) => set({ roles: roles.map((item, i) => (i === index ? compact({ ...item, staff: event.target.checked }) : item)) })}
                />
              }
              label={<Typography variant="body2">{t("Nhân viên")}</Typography>}
            />
          </Tooltip>
          <Tooltip title={t("Xóa vai")}>
            <IconButton size="small" aria-label={t("Xóa vai")} onClick={() => set({ roles: roles.filter((_, i) => i !== index) })}>
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Row>
      ))}
      <Button size="small" startIcon={<AddIcon />} onClick={() => set({ roles: [...roles, { name: uniqueName("VAI_MOI", roleNames) }] })}>
        {t("Thêm vai")}
      </Button>
    </>
  );

  // Units ------------------------------------------------------------------------------------------

  const unitNames = units.map((unit) => text(unit.name));
  const unitsPart = (
    <>
      <GuideBox section="units" />
      {units.length === 0 && <Empty>{t("Chưa có đơn vị. Mô hình không cần đơn vị thì để trống.")}</Empty>}
      {units.map((unit, index) => (
        <Row key={index}>
          <Box sx={{ width: 190 }}>
            <NameInput
              label={t("Tên đơn vị")}
              value={unitNames[index]}
              pattern={ROLE}
              upper
              taken={unitNames}
              onCommit={(name) => set({ units: units.map((item, i) => (i === index ? { ...item, name } : item)) })}
            />
          </Box>
          <TextField variant="outlined"
            size="small"
            label={t("Nhãn hiển thị")}
            value={text(unit.label)}
            sx={{ ...DENSE, flex: 1, minWidth: 160 }}
            onChange={(event) => set({ units: units.map((item, i) => (i === index ? compact({ ...item, label: event.target.value }) : item)) })}
          />
          <Tooltip title={t("Xóa đơn vị")}>
            <IconButton size="small" aria-label={t("Xóa đơn vị")} onClick={() => set({ units: units.filter((_, i) => i !== index) })}>
              <DeleteOutlineIcon fontSize="small" />
            </IconButton>
          </Tooltip>
        </Row>
      ))}
      <Button size="small" startIcon={<AddIcon />} onClick={() => set({ units: [...units, { name: uniqueName("DON_VI_MOI", unitNames) }] })}>
        {t("Thêm đơn vị")}
      </Button>
    </>
  );

  // Entities ---------------------------------------------------------------------------------------

  const renameEntity = (to: string) => {
    const renamed: Rec = {};
    Object.entries(renameKey(entities, entityKey, to)).forEach(([key, raw]) => {
      const item = rec(raw);
      const fields: Rec = {};
      Object.entries(rec(item.fields)).forEach(([name, field]) => {
        const f = rec(field);
        fields[name] = f.to === entityKey ? { ...f, to } : f;
      });
      renamed[key] = { ...item, fields };
    });
    set({ entities: renamed });
    setSelected(to);
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
    setSelected(key);
    setEntityPart("general");
  };

  const removeEntity = () => {
    const rest = { ...entities };
    delete rest[entityKey];
    set({ entities: rest });
    setSelected("");
  };

  const entityPartView = () => {
    if (!entityKey) return null;
    if (entityPart === "general") return generalOfEntity();
    if (entityPart === "fields") return fieldsOfEntity();
    if (entityPart === "access") return accessOfEntity();
    return processOfEntity();
  };

  const fields = rec(entity.fields);
  const fieldKeys = Object.keys(fields);

  const generalOfEntity = () => {
    const references = fieldKeys.filter((key) => rec(fields[key]).type === "reference");
    return (
      <Row>
        <Box sx={{ width: 220 }}>
          <NameInput label={t("Mã thực thể")} value={entityKey} pattern={NAME} taken={entityKeys} onCommit={renameEntity} help={t("Dùng trong API và tên bảng")} />
        </Box>
        <TextField variant="outlined"
          size="small"
          label={t("Tên hiển thị")}
          value={text(entity.label)}
          sx={{ ...DENSE, flex: 1, minWidth: 180 }}
          onChange={(event) => setEntity(entityKey, { label: event.target.value })}
        />
        <TextField variant="outlined"
          select
          size="small"
          label={t("Thừa quyền sở hữu từ")}
          value={text(entity.owner_via)}
          sx={{ ...DENSE, width: 230 }}
          helperText={t("Dành cho thực thể con: chọn trường tham chiếu tới thực thể cha")}
          onChange={(event) => setEntity(entityKey, { owner_via: event.target.value })}
        >
          <MenuItem value="">{t("(không)")}</MenuItem>
          {references.map((key) => (
            <MenuItem key={key} value={key}>
              {text(rec(fields[key]).label) || key}
            </MenuItem>
          ))}
        </TextField>
      </Row>
    );
  };

  // Fields -----------------------------------------------------------------------------------------

  const [advanced, setAdvanced] = useState<string[]>([]);

  const setField = (key: string, changes: Rec) =>
    setEntity(entityKey, { fields: { ...fields, [key]: compact({ ...rec(fields[key]), ...changes }) } });

  const renameField = (from: string, to: string) => {
    const next: Rec = { fields: renameKey(fields, from, to) };
    if (entity.owner_via === from) next.owner_via = to;
    if (entity.process) {
      const process = { ...rec(entity.process) };
      const booking = rec(process.booking);
      if (Object.keys(booking).length > 0) {
        process.booking = Object.fromEntries(Object.entries(booking).map(([k, v]) => [k, v === from ? to : v]));
      }
      const after = rec(process.after);
      if (Object.keys(after).length > 0) {
        process.after = Object.fromEntries(
          Object.entries(after).map(([state, items]) => [
            state,
            list(items).map((item) => {
              const completion = { ...rec(item) };
              if (completion.user === from) completion.user = to;
              if (completion.group_from === from) completion.group_from = to;
              return completion;
            }),
          ]),
        );
      }
      next.process = process;
    }
    setEntity(entityKey, next);
    setAdvanced((open) => open.map((key) => (key === from ? to : key)));
  };

  const fieldsOfEntity = () => (
    <>
      <GuideBox section="fields" />
      {fieldKeys.map((key) => {
        const field = rec(fields[key]);
        const type = text(field.type);
        const open = advanced.includes(key);
        return (
          <Box key={key}>
            <Row>
              <Box sx={{ width: 150 }}>
                <NameInput label={t("Mã trường")} value={key} pattern={NAME} taken={fieldKeys} onCommit={(name) => renameField(key, name)} />
              </Box>
              <TextField variant="outlined" select size="small" label={t("Kiểu")} value={type} sx={{ ...DENSE, width: 150 }} onChange={(event) => setField(key, { type: event.target.value })}>
                {fieldTypes.map((item) => (
                  <MenuItem key={item} value={item}>
                    {FIELD_TYPE_LABEL[item] ?? item}
                  </MenuItem>
                ))}
              </TextField>
              <TextField variant="outlined"
                size="small"
                label={t("Tên hiển thị")}
                value={text(field.label)}
                sx={{ ...DENSE, flex: 1, minWidth: 140 }}
                onChange={(event) => setField(key, { label: event.target.value })}
              />
              <FormControlLabel
                sx={{ mr: 0 }}
                control={<Checkbox size="small" checked={field.required === true} onChange={(event) => setField(key, { required: event.target.checked })} />}
                label={<Typography variant="body2">{t("Bắt buộc")}</Typography>}
              />
              <FormControlLabel
                sx={{ mr: 0 }}
                control={<Checkbox size="small" checked={field.unique === true} onChange={(event) => setField(key, { unique: event.target.checked })} />}
                label={<Typography variant="body2">{t("Không trùng")}</Typography>}
              />
              <Tooltip title={t("Nâng cao: giá trị mặc định, tên cũ")}>
                <IconButton
                  size="small"
                  color={open ? "primary" : "default"}
                  aria-label={t("Nâng cao")}
                  onClick={() => setAdvanced(open ? advanced.filter((item) => item !== key) : [...advanced, key])}
                >
                  <TuneIcon fontSize="small" />
                </IconButton>
              </Tooltip>
              <Tooltip title={t("Xóa trường")}>
                <IconButton
                  size="small"
                  aria-label={t("Xóa trường")}
                  onClick={() => {
                    const rest = { ...fields };
                    delete rest[key];
                    setEntity(entityKey, { fields: rest });
                  }}
                >
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </Row>
            {type === "choice" && (
              <Autocomplete
                multiple
                freeSolo
                size="small"
                options={[] as string[]}
                value={texts(field.options)}
                sx={{ mb: 1 }}
                renderTags={(items: string[], getItemProps) =>
                  items.map((item, index) => {
                    const { key: itemKey, ...itemProps } = getItemProps({ index });
                    return <Chip key={itemKey} size="small" label={item} {...itemProps} />;
                  })
                }
                renderInput={(params) => <TextField variant="outlined" {...params} label={t("Các lựa chọn")} placeholder={t("Gõ một lựa chọn rồi nhấn Enter")} sx={DENSE} />}
                onChange={(_, items) => setField(key, { options: items.length > 0 ? items.map((item) => item.trim()).filter(Boolean) : undefined })}
              />
            )}
            {type === "reference" && (
              <TextField variant="outlined"
                select
                size="small"
                label={t("Tham chiếu tới thực thể")}
                value={text(field.to)}
                sx={{ ...DENSE, mb: 1, minWidth: 260 }}
                onChange={(event) => setField(key, { to: event.target.value })}
              >
                {entityKeys.map((item) => (
                  <MenuItem key={item} value={item}>
                    {text(rec(entities[item]).label) || item} ({item})
                  </MenuItem>
                ))}
              </TextField>
            )}
            <Collapse in={open}>
              <Box sx={{ display: "flex", gap: 1, flexWrap: "wrap", pb: 1 }}>
                <TextField variant="outlined"
                  size="small"
                  label={t("Giá trị mặc định")}
                  value={text(field.default)}
                  helperText={t("Điền cho hồ sơ cũ khi thêm trường bắt buộc")}
                  sx={{ ...DENSE, width: 240 }}
                  onChange={(event) => setField(key, { default: type === "number" && event.target.value !== "" && !Number.isNaN(Number(event.target.value)) ? Number(event.target.value) : event.target.value })}
                />
                <TextField variant="outlined"
                  size="small"
                  label={t("Tên cũ của trường")}
                  value={text(field.renamed_from)}
                  helperText={t("Khi đổi mã trường mà muốn giữ dữ liệu")}
                  error={text(field.renamed_from) !== "" && !NAME.test(text(field.renamed_from))}
                  sx={{ ...DENSE, width: 240 }}
                  onChange={(event) => setField(key, { renamed_from: event.target.value })}
                />
              </Box>
            </Collapse>
            <Divider />
          </Box>
        );
      })}
      <Button
        size="small"
        startIcon={<AddIcon />}
        sx={{ mt: 1 }}
        onClick={() => setEntity(entityKey, { fields: { ...fields, [uniqueName("truong_moi", fieldKeys)]: { type: "text", label: t("Trường mới") } } })}
      >
        {t("Thêm trường")}
      </Button>
    </>
  );

  // Access -----------------------------------------------------------------------------------------

  const access = rec(entity.access);
  const accessOfEntity = () => {
    const unknown = Object.keys(access).filter((name) => !roleNames.includes(name));
    return (
      <>
        <GuideBox section="access" />
        {roleNames.length === 0 && <Empty>{t("Khai báo vai ở mục Vai trước, rồi quay lại đây để cho vai quyền.")}</Empty>}
        {[...roleNames, ...unknown].map((name) => (
          <Row key={name}>
            <Box sx={{ width: 190, pt: 0.75 }}>
              <Typography variant="body2" sx={{ fontWeight: 600 }}>
                {name}
              </Typography>
              {unknown.includes(name) && (
                <Typography variant="caption" color="error">
                  {t("Vai chưa khai báo")}
                </Typography>
              )}
            </Box>
            <TextField variant="outlined"
              select
              size="small"
              value={text(access[name])}
              sx={{ ...DENSE, width: 260 }}
              slotProps={{ select: { displayEmpty: true } }}
              onChange={(event) => {
                const next = { ...access };
                if (event.target.value === "") delete next[name];
                else next[name] = event.target.value;
                setEntity(entityKey, { access: next });
              }}
            >
              <MenuItem value="">{t("Không có quyền")}</MenuItem>
              {scopes.map((scope) => (
                <MenuItem key={scope} value={scope}>
                  {SCOPE_LABEL[scope] ?? scope}
                </MenuItem>
              ))}
            </TextField>
          </Row>
        ))}
      </>
    );
  };

  // Process ----------------------------------------------------------------------------------------

  const process = rec(entity.process);
  const hasProcess = entity.process !== undefined;
  const familyOptions = allowed(schema, ["properties", "entities", "additionalProperties", "properties", "process", "properties", "family"], families.map((item) => item.name));
  const family = families.find((item) => item.name === text(process.family));
  const params = family?.params.map((param) => param.name) ?? [];

  const setProcess = (changes: Rec) => setEntity(entityKey, { process: compact({ ...process, ...changes }) });

  const handlers = process.handler === undefined ? [] : Array.isArray(process.handler) ? texts(process.handler) : [text(process.handler)];
  const setHandlers = (names: string[]) => setProcess({ handler: names.length === 1 ? names[0] : names.length === 0 ? undefined : names });

  const references = fieldKeys.filter((key) => rec(fields[key]).type === "reference");
  const datetimes = fieldKeys.filter((key) => rec(fields[key]).type === "datetime");
  const fieldOption = (key: string) => (
    <MenuItem key={key} value={key}>
      {text(rec(fields[key]).label) || key} ({key})
    </MenuItem>
  );
  const booking = rec(process.booking);

  const after = rec(process.after);
  const stateSpec = rec(process.states);
  const actionSpec = rec(process.actions);
  const asList = (item: unknown): string[] => (typeof item === "string" ? [item] : texts(item));

  interface StateRow {
    name: string;
    label: string;
    terminal: boolean;
    base: boolean;
    removed: boolean;
  }
  interface ActionRow {
    name: string;
    label: string;
    from: string[];
    to: string;
    by: "handler" | "owner";
    role: string[];
    needsNote: boolean;
    require: string[];
    when: string;
    base: boolean;
    removed: boolean;
  }

  // The states and actions the entity ends up with: those of the family, changed by what the file declares.
  const baseStates = (family?.states ?? []).filter((state) => state.name !== "withdrawn");
  const baseActions = (family?.actions ?? []).filter((action) => action.name !== "withdraw");
  const stateRows: StateRow[] = [
    ...baseStates.map((state) => {
      const spec = stateSpec[state.name];
      const changed = rec(spec);
      return {
        name: state.name,
        label: text(changed.label) || state.label,
        terminal: changed.terminal === undefined ? state.terminal : changed.terminal === true,
        base: true,
        removed: spec === null,
      };
    }),
    ...Object.keys(stateSpec)
      .filter((name) => name !== "withdrawn" && !baseStates.some((state) => state.name === name))
      .map((name) => ({ name, label: text(rec(stateSpec[name]).label), terminal: rec(stateSpec[name]).terminal === true, base: false, removed: false })),
  ];
  const actionRows: ActionRow[] = [
    ...baseActions.map((action) => {
      const spec = actionSpec[action.name];
      const changed = rec(spec);
      return {
        name: action.name,
        label: text(changed.label) || action.label,
        from: changed.from === undefined ? action.from : asList(changed.from),
        to: text(changed.to) || action.to,
        by: (changed.by === undefined ? action.by.toLowerCase() : text(changed.by)) as "handler" | "owner",
        role: changed.role === undefined ? action.roles : asList(changed.role),
        needsNote: changed.needs_note === undefined ? action.needsNote : changed.needs_note === true,
        require: changed.require === undefined ? action.require : asList(changed.require),
        when: changed.when === undefined ? conditionOf(action.when) : text(changed.when),
        base: true,
        removed: spec === null,
      };
    }),
    ...Object.keys(actionSpec)
      .filter((name) => name !== "withdraw" && !baseActions.some((action) => action.name === name))
      .map((name) => {
        const changed = rec(actionSpec[name]);
        return {
          name,
          label: text(changed.label),
          from: asList(changed.from),
          to: text(changed.to),
          by: (text(changed.by) || "handler") as "handler" | "owner",
          role: asList(changed.role),
          needsNote: changed.needs_note === true,
          require: asList(changed.require),
          when: text(changed.when),
          base: false,
          removed: false,
        };
      }),
  ];
  const liveStates = stateRows.filter((state) => !state.removed);
  const terminal = liveStates.filter((state) => state.terminal).map((state) => ({ name: state.name, label: state.label || state.name }));

  // A change to a row of the family is written as what differs from the family; a row the file adds is written whole.
  const writeSpec = (key: "states" | "actions", name: string, spec: Rec | null | undefined, base?: Rec) => {
    const current = rec(process[key]);
    const next = { ...current };
    if (spec === undefined) delete next[name];
    else if (spec === null) next[name] = null;
    else {
      const kept: Rec = {};
      Object.entries(spec).forEach(([k, v]) => {
        if (base && JSON.stringify(base[k]) === JSON.stringify(v)) return;
        if (v === undefined || v === "" || (Array.isArray(v) && v.length === 0 && !(base && Array.isArray(base[k]) && (base[k] as unknown[]).length > 0))) return;
        if (!base && v === false) return;
        kept[k] = v;
      });
      if (base && Object.keys(kept).length === 0) delete next[name];
      else next[name] = kept;
    }
    setProcess({ [key]: Object.keys(next).length > 0 ? next : undefined });
  };
  const stateBase = (name: string): Rec | undefined => {
    const base = baseStates.find((state) => state.name === name);
    return base ? { label: base.label, terminal: base.terminal } : undefined;
  };
  const actionBase = (name: string): Rec | undefined => {
    const base = baseActions.find((action) => action.name === name);
    return base ? { label: base.label, from: base.from, to: base.to, by: base.by.toLowerCase(), role: base.roles, needs_note: base.needsNote, require: base.require, when: conditionOf(base.when) } : undefined;
  };
  const changeState = (row: StateRow, changes: Partial<Rec>) => {
    const spec = { label: row.label, terminal: row.terminal, ...changes };
    writeSpec("states", row.name, spec, stateBase(row.name));
  };
  const changeAction = (row: ActionRow, changes: Partial<Rec>) => {
    const spec = { label: row.label, from: row.from, to: row.to, by: row.by, role: row.by === "owner" ? [] : row.role, needs_note: row.needsNote, require: row.require, when: row.when, ...changes };
    writeSpec("actions", row.name, spec, actionBase(row.name));
  };
  const addState = () => {
    const name = uniqueName("trang_thai_moi", stateRows.map((state) => state.name));
    writeSpec("states", name, { label: t("Trạng thái mới") });
  };
  const addAction = () => {
    const name = uniqueName("hanh_dong_moi", actionRows.map((action) => action.name));
    const first = liveStates[0]?.name ?? "";
    writeSpec("actions", name, { label: t("Hành động mới"), from: first ? [first] : [], to: liveStates[liveStates.length - 1]?.name ?? "", by: "handler" });
  };
  const renameStep = (key: "states" | "actions", from: string, to: string) => {
    const renamed = renameKey(rec(process[key]), from, to);
    const patchRefs: Rec = { [key]: renamed };
    if (key === "states") {
      const nextActions: Rec = {};
      Object.entries(actionSpec).forEach(([name, raw]) => {
        const spec = rec(raw);
        if (raw === null) {
          nextActions[name] = null;
          return;
        }
        const out = { ...spec };
        if (spec.from !== undefined) out.from = asList(spec.from).map((item) => (item === from ? to : item));
        if (spec.to === from) out.to = to;
        nextActions[name] = out;
      });
      patchRefs.actions = Object.keys(nextActions).length > 0 ? nextActions : undefined;
      if (process.initial === from) patchRefs.initial = to;
    }
    setProcess(patchRefs);
  };
  const stateName = (name: string) => stateRows.find((state) => state.name === name)?.label || name;
  const userFields = fieldKeys.filter((key) => rec(fields[key]).type === "user");
  const groupFields = fieldKeys.filter((key) => ["choice", "text"].includes(text(rec(fields[key]).type)));
  const setCompletions = (state: string, items: Rec[]) => {
    const next = { ...after };
    if (items.length === 0) delete next[state];
    else next[state] = items;
    setProcess({ after: Object.keys(next).length > 0 ? next : undefined });
  };

  const flowEditor = () => (
    <>
      <Typography variant="subtitle2" sx={{ mt: 1.5 }}>
        {t("Trạng thái")}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {t("Mỗi hồ sơ ở một trạng thái. Sửa nhãn, thêm trạng thái mới hoặc bỏ trạng thái của khung. Đổi các mục này khi còn hồ sơ đang xử lý sẽ bị từ chối.")}
      </Typography>
      {stateRows.map((row) => (
        <Row key={row.name}>
          {row.base ? (
            <Typography variant="body2" sx={{ width: 150, pt: 0.75, fontFamily: "ui-monospace, monospace", opacity: row.removed ? 0.5 : 1 }}>
              {row.name}
            </Typography>
          ) : (
            <Box sx={{ width: 150 }}>
              <NameInput label={t("Mã")} value={row.name} pattern={NAME} taken={stateRows.map((state) => state.name)} onCommit={(name) => renameStep("states", row.name, name)} />
            </Box>
          )}
          {row.removed ? (
            <>
              <Typography variant="body2" color="text.secondary" sx={{ flex: 1, pt: 0.75 }}>
                {t("Đã bỏ khỏi quy trình này")}
              </Typography>
              <Button size="small" onClick={() => writeSpec("states", row.name, undefined)}>
                {t("Khôi phục")}
              </Button>
            </>
          ) : (
            <>
              <TextField variant="outlined" size="small" label={t("Nhãn")} value={row.label} sx={{ ...DENSE, flex: 1, minWidth: 140 }} onChange={(event) => changeState(row, { label: event.target.value })} />
              <FormControlLabel
                sx={{ mr: 0 }}
                control={<Checkbox size="small" checked={row.terminal} onChange={(event) => changeState(row, { terminal: event.target.checked })} />}
                label={<Typography variant="body2">{t("Trạng thái cuối")}</Typography>}
              />
              <Tooltip title={row.base ? t("Bỏ trạng thái của khung") : t("Xóa trạng thái")}>
                <IconButton size="small" aria-label={t("Xóa trạng thái")} onClick={() => writeSpec("states", row.name, row.base ? null : undefined)}>
                  <DeleteOutlineIcon fontSize="small" />
                </IconButton>
              </Tooltip>
            </>
          )}
        </Row>
      ))}
      <Row>
        <Button size="small" startIcon={<AddIcon />} onClick={addState}>
          {t("Thêm trạng thái")}
        </Button>
        <TextField
          variant="outlined"
          select
          size="small"
          label={t("Trạng thái ban đầu")}
          value={text(process.initial)}
          sx={{ ...DENSE, width: 240 }}
          slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
          onChange={(event) => setProcess({ initial: event.target.value })}
        >
          <MenuItem value="">{family ? t("(theo khung)") : t("(trạng thái đầu tiên)")}</MenuItem>
          {liveStates.filter((state) => !state.terminal).map((state) => (
            <MenuItem key={state.name} value={state.name}>
              {t(state.label || state.name)}
            </MenuItem>
          ))}
        </TextField>
      </Row>
      <Typography variant="subtitle2" sx={{ mt: 1.5 }}>
        {t("Hành động")}
      </Typography>
      <Typography variant="caption" color="text.secondary">
        {t("Hành động chuyển hồ sơ từ trạng thái này sang trạng thái khác. Người nộp chỉ rút lại được hồ sơ ở các trạng thái khai trong hành động withdraw; không khai withdraw thì không rút lại được.")}
      </Typography>
      {actionRows.map((row) => {
        const live = row.removed ? null : row;
        return (
          <Box key={row.name} sx={{ borderTop: 1, borderColor: "divider", mt: 1, pt: 1 }}>
            <Row>
              {row.base ? (
                <Typography variant="body2" sx={{ width: 150, pt: 0.75, fontFamily: "ui-monospace, monospace", opacity: row.removed ? 0.5 : 1 }}>
                  {row.name}
                </Typography>
              ) : (
                <Box sx={{ width: 150 }}>
                  <NameInput label={t("Mã")} value={row.name} pattern={NAME} taken={actionRows.map((action) => action.name)} onCommit={(name) => renameStep("actions", row.name, name)} />
                </Box>
              )}
              {live ? (
                <>
                  <TextField variant="outlined" size="small" label={t("Nhãn nút")} value={live.label} helperText={live.label ? undefined : t("Để trống thì nút hiện mã hành động")} sx={{ ...DENSE, flex: 1, minWidth: 140 }} onChange={(event) => changeAction(live, { label: event.target.value })} />
                  <TextField variant="outlined" select size="small" label={t("Do")} value={live.by} sx={{ ...DENSE, width: 150 }} onChange={(event) => changeAction(live, { by: event.target.value })}>
                    <MenuItem value="handler">{t("Vai xử lý")}</MenuItem>
                    <MenuItem value="owner">{t("Người nộp")}</MenuItem>
                  </TextField>
                  <Tooltip title={row.base ? t("Bỏ hành động của khung") : t("Xóa hành động")}>
                    <IconButton size="small" aria-label={t("Xóa hành động")} onClick={() => writeSpec("actions", row.name, row.base ? null : undefined)}>
                      <DeleteOutlineIcon fontSize="small" />
                    </IconButton>
                  </Tooltip>
                </>
              ) : (
                <>
                  <Typography variant="body2" color="text.secondary" sx={{ flex: 1, pt: 0.75 }}>
                    {t("Đã bỏ khỏi quy trình này")}
                  </Typography>
                  <Button size="small" onClick={() => writeSpec("actions", row.name, undefined)}>
                    {t("Khôi phục")}
                  </Button>
                </>
              )}
            </Row>
            {live && (
              <Row>
                <Autocomplete
                  multiple
                  size="small"
                  options={liveStates.map((state) => state.name)}
                  getOptionLabel={stateName}
                  value={live.from}
                  sx={{ flex: 1, minWidth: 200 }}
                  renderTags={(items: string[], getItemProps) =>
                    items.map((item, index) => {
                      const { key: itemKey, ...itemProps } = getItemProps({ index });
                      return <Chip key={itemKey} size="small" label={stateName(item)} {...itemProps} />;
                    })
                  }
                  renderInput={(inputParams) => <TextField variant="outlined" {...inputParams} label={t("Từ trạng thái")} sx={DENSE} />}
                  onChange={(_, names) => changeAction(live, { from: names })}
                />
                <TextField variant="outlined" select size="small" label={t("Sang trạng thái")} value={live.to} sx={{ ...DENSE, width: 200 }} onChange={(event) => changeAction(live, { to: event.target.value })}>
                  {liveStates.map((state) => (
                    <MenuItem key={state.name} value={state.name}>
                      {t(state.label || state.name)}
                    </MenuItem>
                  ))}
                </TextField>
                {live.by === "handler" && (
                  <Autocomplete
                    multiple
                    size="small"
                    options={roleNames}
                    value={live.role}
                    sx={{ width: 220 }}
                    renderTags={(items: string[], getItemProps) =>
                      items.map((item, index) => {
                        const { key: itemKey, ...itemProps } = getItemProps({ index });
                        return <Chip key={itemKey} size="small" label={item} {...itemProps} />;
                      })
                    }
                    renderInput={(inputParams) => <TextField variant="outlined" {...inputParams} label={t("Chỉ vai")} placeholder={live.role.length ? "" : t("mọi vai xử lý")} sx={DENSE} />}
                    onChange={(_, names) => changeAction(live, { role: names })}
                  />
                )}
                <Autocomplete
                  multiple
                  size="small"
                  options={fieldKeys.filter((key) => rec(fields[key]).type !== "file")}
                  value={live.require}
                  sx={{ width: 220 }}
                  renderTags={(items: string[], getItemProps) =>
                    items.map((item, index) => {
                      const { key: itemKey, ...itemProps } = getItemProps({ index });
                      return <Chip key={itemKey} size="small" label={item} {...itemProps} />;
                    })
                  }
                  renderInput={(inputParams) => <TextField variant="outlined" {...inputParams} label={t("Bắt buộc điền trường")} sx={DENSE} />}
                  onChange={(_, names) => changeAction(live, { require: names })}
                />
                {live.by === "handler" && (
                  <TextField
                    variant="outlined"
                    size="small"
                    label={t("Chỉ khi")}
                    placeholder="amount > 5000000"
                    value={live.when}
                    sx={{ width: 200, ...DENSE }}
                    onChange={(event) => changeAction(live, { when: event.target.value })}
                  />
                )}
                <FormControlLabel
                  sx={{ mr: 0 }}
                  control={<Checkbox size="small" checked={live.needsNote} onChange={(event) => changeAction(live, { needs_note: event.target.checked })} />}
                  label={<Typography variant="body2">{t("Cần ghi lý do")}</Typography>}
                />
              </Row>
            )}
          </Box>
        );
      })}
      <Button size="small" startIcon={<AddIcon />} onClick={addAction}>
        {t("Thêm hành động")}
      </Button>
    </>
  );

  const autoClose = process.auto_close;
  const autoAfter = typeof autoClose === "string" ? autoClose : text(rec(autoClose).after);
  const autoAction = typeof autoClose === "string" ? (family?.name === "work_item" ? "confirm" : "") : text(rec(autoClose).action);
  // The short form `auto_close: 7d` stands for the action a work_item record waits for.
  const setAuto = (after: string | undefined, action: string) => {
    if (!after) setProcess({ auto_close: undefined });
    else if (family?.name === "work_item" && (action === "confirm" || action === "")) setProcess({ auto_close: after });
    else setProcess({ auto_close: action ? { after, action } : { after } });
  };

  const processOfEntity = () => (
    <>
      <GuideBox section="process" />
      <FormControlLabel
        control={
          <Switch
            checked={hasProcess}
            onChange={(event) => {
              if (!event.target.checked) setEntity(entityKey, { process: undefined });
              else setEntity(entityKey, { process: { family: familyOptions[0], handler: roleNames[0] } });
            }}
          />
        }
        label={t("Hồ sơ của thực thể này đi qua một quy trình")}
      />
      {hasProcess && (
        <>
          <Row>
            <TextField variant="outlined" select size="small" label={t("Khung quy trình")} value={text(process.family)} sx={{ ...DENSE, width: 260 }} onChange={(event) =>
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
            }>
              <MenuItem value="">{t("Tự khai báo, không dùng khung")}</MenuItem>
              {familyOptions.map((item) => (
                <MenuItem key={item} value={item}>
                  {FAMILY_LABEL[item] ?? item}
                </MenuItem>
              ))}
            </TextField>
            <Autocomplete
              multiple
              size="small"
              options={roleNames}
              value={handlers}
              sx={{ flex: 1, minWidth: 240 }}
              renderTags={(items: string[], getItemProps) =>
                items.map((item, index) => {
                  const { key: itemKey, ...itemProps } = getItemProps({ index });
                  return <Chip key={itemKey} size="small" label={item} {...itemProps} />;
                })
              }
              renderInput={(inputParams) => (
                <TextField variant="outlined" {...inputParams} label={t("Vai xử lý")} helperText={t("Vai được duyệt hoặc xử lý hồ sơ, phải có quyền xem hồ sơ ở mục Quyền")} sx={DENSE} />
              )}
              onChange={(_, names) => setHandlers(names)}
            />
          </Row>
          {handlers.some((name) => !roleNames.includes(name)) && (
            <Typography variant="caption" color="error">
              {t("Có vai xử lý chưa khai báo ở mục Vai: {roles}", { roles: handlers.filter((name) => !roleNames.includes(name)).join(", ") })}
            </Typography>
          )}
          {handlers.filter((name) => roleNames.includes(name)).some((name) => !["all", "read", "unit", "assigned"].includes(text(access[name]))) && (
            <Typography variant="caption" color="warning.main" sx={{ display: "block" }}>
              {t("Vai xử lý cần quyền Tất cả, Chỉ đọc, Của đơn vị hoặc Được giao ở mục Quyền của thực thể này.")}
            </Typography>
          )}
          <Row>
            <DurationInput label={t("Hạn xử lý")} value={text(process.deadline)} onChange={(deadline) => setProcess({ deadline })} />
            <DurationInput label={t("Tự đóng sau")} value={autoAfter} onChange={(after) => setAuto(after, autoAction)} />
            {autoAfter !== "" && (
              <TextField
                variant="outlined"
                select
                size="small"
                label={t("Hành động tự đóng")}
                value={autoAction}
                sx={{ ...DENSE, width: 220 }}
                helperText={t("Hệ thống làm thay người nộp")}
                onChange={(event) => setAuto(autoAfter, event.target.value)}
              >
                {actionRows.filter((action) => !action.removed && action.by === "owner").map((action) => (
                  <MenuItem key={action.name} value={action.name}>
                    {ta(action.label || action.name)}
                  </MenuItem>
                ))}
              </TextField>
            )}
            <Tooltip title={PARAM_HELP.allow_self_decision}>
              <FormControlLabel
                control={<Switch size="small" checked={process.allow_self_decision === true} onChange={(event) => setProcess({ allow_self_decision: event.target.checked })} />}
                label={<Typography variant="body2">{t("Người tạo được tự quyết định")}</Typography>}
              />
            </Tooltip>
          </Row>
          {(PARAM_HELP.deadline || PARAM_HELP.auto_close) && (
            <Typography variant="caption" color="text.secondary" sx={{ display: "block" }}>
              {PARAM_HELP.deadline}. {PARAM_HELP.auto_close}.
            </Typography>
          )}
          {params.includes("booking") && (
            <>
              <Typography variant="subtitle2" sx={{ mt: 1.5 }}>
                {t("Đặt chỗ")}
              </Typography>
              <Row>
                <TextField variant="outlined" select size="small" label={t("Trường chỉ tài nguyên")} value={text(booking.resource)} sx={{ ...DENSE, width: 220 }} onChange={(event) => setProcess({ booking: { ...booking, resource: event.target.value } })}>
                  {references.map(fieldOption)}
                </TextField>
                <TextField variant="outlined" select size="small" label={t("Trường bắt đầu")} value={text(booking.starts)} sx={{ ...DENSE, width: 200 }} onChange={(event) => setProcess({ booking: { ...booking, starts: event.target.value } })}>
                  {datetimes.map(fieldOption)}
                </TextField>
                <TextField variant="outlined" select size="small" label={t("Trường kết thúc")} value={text(booking.ends)} sx={{ ...DENSE, width: 200 }} onChange={(event) => setProcess({ booking: { ...booking, ends: event.target.value } })}>
                  {datetimes.map(fieldOption)}
                </TextField>
                <TextField variant="outlined"
                  size="small"
                  label={t("Sức chứa")}
                  value={text(booking.capacity)}
                  sx={{ ...DENSE, width: 100 }}
                  helperText={t("Số lượt cùng lúc")}
                  slotProps={{ htmlInput: { inputMode: "numeric" } }}
                  onChange={(event) => {
                    const digits = event.target.value.replace(/\D/g, "").slice(0, 6);
                    setProcess({ booking: compact({ ...booking, capacity: digits ? Math.max(1, Number(digits)) : undefined }) });
                  }}
                />
              </Row>
            </>
          )}
          {family && (
            <Box sx={{ border: 1, borderColor: "divider", borderRadius: 1, p: 1.5, mt: 1 }}>
              <Typography variant="subtitle2" sx={{ mb: 0.5 }}>
                {t('Khung "{name}" có sẵn', { name: FAMILY_LABEL[family.name] ?? family.name })}
              </Typography>
              <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mb: 1 }}>
                {family.states.map((state) => (
                  <Chip key={state.name} size="small" variant={state.terminal ? "filled" : "outlined"} label={`${t(state.label)} · ${state.name}${state.terminal ? t(" (cuối)") : ""}`} />
                ))}
              </Box>
              <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
                {family.actions.map((action) => (
                  <li key={action.name}>
                    <b>{ta(action.label)}</b> <code>{action.name}</code>: {t("{from} → {to}, do {who}", { from: action.from.map((from) => t(family.states.find((state) => state.name === from)?.label ?? from)).join(t(" hoặc ")), to: t(family.states.find((state) => state.name === action.to)?.label ?? action.to), who: action.by === "OWNER" ? t("người tạo hồ sơ") : t("vai xử lý") })}
                    {action.needsNote ? t(", bắt buộc ghi lý do") : ""}
                    {action.when ? t(", chỉ khi {field} {op} {value}", { field: action.when.field, op: action.when.op, value: action.when.value }) : ""}
                  </li>
                ))}
              </Box>
            </Box>
          )}
          {flowEditor()}
          {terminal.length > 0 && (
            <>
              <Typography variant="subtitle2" sx={{ mt: 1.5 }}>
                {t("Hành động hoàn tất")}
              </Typography>
              <Typography variant="caption" color="text.secondary">
                {t("Chạy khi hồ sơ đến một trạng thái cuối: thêm hoặc bỏ một người khỏi nhóm.")}
              </Typography>
              {terminal.map((state) => {
                const items = list(after[state.name]).map(rec);
                const update = (index: number, changes: Rec) => setCompletions(state.name, items.map((item, i) => (i === index ? compact({ ...item, ...changes }) : item)));
                return (
                  <Box key={state.name} sx={{ mt: 1 }}>
                    <Typography variant="body2" sx={{ fontWeight: 600 }}>
                      {t("Khi hồ sơ {state}", { state: t(state.label).toLowerCase() })}
                    </Typography>
                    {items.map((item, index) => {
                      const byField = item.group_from !== undefined;
                      return (
                        <Row key={index}>
                          <TextField variant="outlined" select size="small" value={text(item.do)} sx={{ ...DENSE, width: 150 }} onChange={(event) => update(index, { do: event.target.value })}>
                            {Object.entries(COMPLETION_LABEL).map(([key, label]) => (
                              <MenuItem key={key} value={key}>
                                {label}
                              </MenuItem>
                            ))}
                          </TextField>
                          <TextField variant="outlined" select size="small" label={t("Người")} value={text(item.user)} sx={{ ...DENSE, width: 200 }} onChange={(event) => update(index, { user: event.target.value })}>
                            <MenuItem value="created_by">{t("Người tạo hồ sơ")}</MenuItem>
                            {userFields.map(fieldOption)}
                          </TextField>
                          <ToggleButtonGroup
                            size="small"
                            exclusive
                            value={byField ? "field" : "fixed"}
                            onChange={(_, next) => next && update(index, next === "field" ? { group: undefined, group_from: groupFields[0] ?? "" } : { group_from: undefined, group: "" })}
                          >
                            <ToggleButton value="fixed">{t("Nhóm cố định")}</ToggleButton>
                            <ToggleButton value="field">{t("Nhóm theo trường")}</ToggleButton>
                          </ToggleButtonGroup>
                          {byField ? (
                            <TextField variant="outlined" select size="small" label={t("Trường chứa tên nhóm")} value={text(item.group_from)} sx={{ ...DENSE, width: 220 }} onChange={(event) => update(index, { group_from: event.target.value })}>
                              {groupFields.map(fieldOption)}
                            </TextField>
                          ) : (
                            <TextField variant="outlined" size="small" label={t("Tên nhóm Keycloak")} value={text(item.group)} sx={{ ...DENSE, width: 220 }} onChange={(event) => update(index, { group: event.target.value })} />
                          )}
                          <IconButton size="small" aria-label={t("Xóa hành động")} onClick={() => setCompletions(state.name, items.filter((_, i) => i !== index))}>
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        </Row>
                      );
                    })}
                    <Button size="small" startIcon={<AddIcon />} onClick={() => setCompletions(state.name, [...items, { do: "add_to_group", user: "created_by", group: "" }])}>
                      {t("Thêm hành động")}
                    </Button>
                  </Box>
                );
              })}
            </>
          )}
        </>
      )}
    </>
  );

  const entitiesPart = (
    <>
      <GuideBox section="entities" />
      <Box sx={{ display: "flex", alignItems: "center", gap: 0.75, flexWrap: "wrap", mb: 1 }}>
        {entityKeys.map((key) => (
          <Chip
            key={key}
            label={text(rec(entities[key]).label) || key}
            color={key === entityKey ? "primary" : "default"}
            variant={key === entityKey ? "filled" : "outlined"}
            onClick={() => setSelected(key)}
          />
        ))}
        <Button size="small" startIcon={<AddIcon />} onClick={addEntity}>
          {t("Thêm thực thể")}
        </Button>
        <Box sx={{ flexGrow: 1 }} />
        {entityKey && (
          <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} onClick={removeEntity}>
            {t("Xóa thực thể này")}
          </Button>
        )}
      </Box>
      {entityKeys.length === 0 && <Empty>{t("Chưa có thực thể. Thêm một thực thể để bắt đầu.")}</Empty>}
      {entityKey && (
        <>
          <Tabs value={entityPart} onChange={(_, next) => setEntityPart(next)} sx={{ minHeight: 36, mb: 1, "& .MuiTab-root": { minHeight: 36, py: 0 } }}>
            <Tab value="general" label={t("Chung")} />
            <Tab value="fields" label={t("Trường ({n})", { n: fieldKeys.length })} />
            <Tab value="access" label={t("Quyền ({n})", { n: Object.keys(access).length })} />
            <Tab value="process" label={hasProcess ? t("Quy trình") : t("Quy trình (không có)")} />
          </Tabs>
          {entityPartView()}
        </>
      )}
    </>
  );

  const generalPart = (
    <>
      <GuideBox section="general" />
      <Row>
        <Box sx={{ width: 260 }}>
          <NameInput label={t("Tên mô hình")} value={text(value.name)} pattern={NAME} onCommit={(name) => set({ name })} help={t("Đặt tên bảng dữ liệu và API")} />
        </Box>
        <TextField variant="outlined"
          size="small"
          label={t("Đích gửi lịch sử hồ sơ")}
          value={text(value.webhook)}
          error={text(value.webhook) !== "" && !NAME.test(text(value.webhook))}
          helperText={t("Tên đích đã cấu hình ở máy chủ; để trống thì dùng đích mặc định")}
          sx={{ ...DENSE, flex: 1, minWidth: 240 }}
          onChange={(event) => set({ webhook: event.target.value })}
        />
      </Row>
    </>
  );

  const counts = useMemo(() => ({ roles: roles.length, units: units.length, entities: entityKeys.length }), [roles.length, units.length, entityKeys.length]);

  return (
    <Box>
      <Tabs value={part} onChange={(_, next) => setPart(next)} variant="scrollable" scrollButtons="auto" sx={{ mb: 1.5, borderBottom: 1, borderColor: "divider" }}>
        <Tab value="general" label={t("Chung")} />
        <Tab value="roles" label={t("Vai ({n})", { n: counts.roles })} />
        <Tab value="units" label={t("Đơn vị ({n})", { n: counts.units })} />
        <Tab value="entities" label={t("Thực thể ({n})", { n: counts.entities })} />
      </Tabs>
      {part === "general" && generalPart}
      {part === "roles" && rolesPart}
      {part === "units" && unitsPart}
      {part === "entities" && entitiesPart}
    </Box>
  );
}
