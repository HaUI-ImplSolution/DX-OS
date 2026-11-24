// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from "react";
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
  InputAdornment,
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
import CloseIcon from "@mui/icons-material/Close";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import ErrorOutlineIcon from "@mui/icons-material/ErrorOutline";
import SearchIcon from "@mui/icons-material/Search";
import type { Family } from "./api";
import { FIELD_TYPE_LABEL } from "./formGuide";
import {
  Advanced,
  ConfirmRemove,
  DENSE,
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
  compact,
  type Rec,
} from "./formParts";
import { fieldUses } from "./modelRefs";
import { t } from "../shell/t";

// The fields of an entity as a table; the field picked in it opens below with every setting its type takes.
export default function FieldsEditor({
  entity,
  entities,
  fieldTypes,
  family,
  selected,
  flagged,
  onSelect,
  setEntity,
}: {
  entity: Rec;
  entities: Rec;
  fieldTypes: string[];
  family: Family | undefined;
  selected: string;
  // Fields with a problem the builder found, marked on their rows.
  flagged: string[];
  onSelect: (field: string) => void;
  setEntity: (changes: Rec) => void;
}) {
  const fields = rec(entity.fields);
  const fieldKeys = Object.keys(fields);
  const [query, setQuery] = useState("");
  const [removing, setRemoving] = useState<string | null>(null);
  const detail = useRef<HTMLDivElement | null>(null);
  const current = fieldKeys.includes(selected) ? selected : "";

  useEffect(() => {
    if (current) detail.current?.scrollIntoView({ block: "nearest", behavior: "smooth" });
  }, [current]);

  const setField = (key: string, changes: Rec) => setEntity({ fields: { ...fields, [key]: compact({ ...rec(fields[key]), ...changes }) } });

  // A field renamed here is renamed everywhere the entity names it, so nothing points at the old name.
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
    const ai = rec(entity.ai);
    const tasks = rec(ai.tasks);
    if (Object.keys(tasks).length) {
      next.ai = {
        ...ai,
        tasks: Object.fromEntries(
          Object.entries(tasks).map(([name, raw]) => {
            const task = rec(raw);
            return [name, { ...task, input_fields: texts(task.input_fields).map((field) => (field === from ? to : field)), output_field: task.output_field === from ? to : task.output_field }];
          }),
        ),
      };
    }
    setEntity(next);
    onSelect(to);
  };

  const remove = (key: string) => {
    const rest = { ...fields };
    delete rest[key];
    setEntity({ fields: rest });
    if (current === key) onSelect("");
  };

  const add = () => {
    const key = uniqueName("truong_moi", fieldKeys);
    setEntity({ fields: { ...fields, [key]: { type: "text", label: t("Trường mới") } } });
    onSelect(key);
  };

  const needle = query.trim().toLowerCase();
  const shown = fieldKeys.filter((key) => !needle || key.includes(needle) || text(rec(fields[key]).label).toLowerCase().includes(needle));

  return (
    <>
      <GuideBox section="fields" />
      <Group
        title={t("Danh sách trường dữ liệu")}
        action={
          <Button size="small" startIcon={<AddIcon />} onClick={add}>
            {t("Thêm trường dữ liệu")}
          </Button>
        }
      >
        {fieldKeys.length > 5 && (
          <TextField
            size="small"
            placeholder={t("Tìm theo tên hiển thị hoặc định danh")}
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            sx={{ ...DENSE, mb: 1, width: 320, maxWidth: "100%" }}
            slotProps={{ input: { startAdornment: <InputAdornment position="start"><SearchIcon fontSize="small" /></InputAdornment> } }}
          />
        )}
        {fieldKeys.length === 0 ? (
          <Empty>{t("Chưa có trường dữ liệu. Thêm một trường để bắt đầu.")}</Empty>
        ) : (
          <Box sx={{ overflowX: "auto" }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>{t("Tên hiển thị")}</TableCell>
                  <TableCell>{t("Định danh")}</TableCell>
                  <TableCell>{t("Kiểu dữ liệu")}</TableCell>
                  <TableCell>{t("Bắt buộc")}</TableCell>
                  <TableCell padding="checkbox" />
                </TableRow>
              </TableHead>
              <TableBody>
                {shown.map((key) => {
                  const field = rec(fields[key]);
                  const type = text(field.type);
                  return (
                    <TableRow key={key} hover selected={key === current} onClick={() => onSelect(key === current ? "" : key)} sx={{ cursor: "pointer" }}>
                      <TableCell>
                        <Box sx={{ display: "flex", alignItems: "center", gap: 0.5 }}>
                          {flagged.includes(key) && (
                            <Tooltip title={t("Trường này có cấu hình nên kiểm lại")}>
                              <ErrorOutlineIcon fontSize="small" color="warning" />
                            </Tooltip>
                          )}
                          {text(field.label) || <Typography variant="body2" color="text.secondary">{t("(chưa đặt)")}</Typography>}
                        </Box>
                      </TableCell>
                      <TableCell sx={{ fontFamily: "ui-monospace, monospace" }}>{key}</TableCell>
                      <TableCell>
                        {FIELD_TYPE_LABEL[type] ?? type}
                        {type === "reference" && field.to ? ` → ${text(rec(entities[text(field.to)]).label) || text(field.to)}` : ""}
                        {field.multiple === true ? t(" (nhiều)") : ""}
                      </TableCell>
                      <TableCell>{field.required === true ? t("Có") : field.required_when ? t("Theo điều kiện") : t("Không")}</TableCell>
                      <TableCell padding="checkbox">
                        <Tooltip title={t("Xóa trường dữ liệu")}>
                          <IconButton
                            size="small"
                            aria-label={t("Xóa trường dữ liệu")}
                            onClick={(event) => {
                              event.stopPropagation();
                              if (fieldUses(entity, key, family).length > 0) setRemoving(key);
                              else remove(key);
                            }}
                          >
                            <DeleteOutlineIcon fontSize="small" />
                          </IconButton>
                        </Tooltip>
                      </TableCell>
                    </TableRow>
                  );
                })}
                {shown.length === 0 && (
                  <TableRow>
                    <TableCell colSpan={5}>
                      <Empty>{t("Không có trường nào khớp.")}</Empty>
                    </TableCell>
                  </TableRow>
                )}
              </TableBody>
            </Table>
          </Box>
        )}
      </Group>
      {current && (
        <Card variant="outlined" ref={detail} sx={{ p: 2, scrollMarginTop: 16 }}>
          <FieldDetail
            name={current}
            field={rec(fields[current])}
            fieldKeys={fieldKeys}
            entities={entities}
            fieldTypes={fieldTypes}
            uses={fieldUses(entity, current, family)}
            onChange={(changes) => setField(current, changes)}
            onRename={(to) => renameField(current, to)}
            onClose={() => onSelect("")}
          />
        </Card>
      )}
      {removing && (
        <ConfirmRemove
          what={t("trường {name}", { name: removing })}
          uses={fieldUses(entity, removing, family)}
          onConfirm={() => remove(removing)}
          onClose={() => setRemoving(null)}
        />
      )}
    </>
  );
}

function FieldDetail({
  name,
  field,
  fieldKeys,
  entities,
  fieldTypes,
  uses,
  onChange,
  onRename,
  onClose,
}: {
  name: string;
  field: Rec;
  fieldKeys: string[];
  entities: Rec;
  fieldTypes: string[];
  uses: string[];
  onChange: (changes: Rec) => void;
  onRename: (to: string) => void;
  onClose: () => void;
}) {
  const type = text(field.type);
  const chips = (items: string[], getItemProps: AutocompleteRenderGetTagProps) =>
    items.map((item, index) => {
      const { key: itemKey, ...itemProps } = getItemProps({ index });
      return <Chip key={itemKey} size="small" label={item} {...itemProps} />;
    });
  return (
    <>
      <Box sx={{ display: "flex", alignItems: "center", mb: 1 }}>
        <Typography variant="subtitle1" sx={{ fontWeight: 600, flexGrow: 1 }}>
          {t("Trường dữ liệu: {label}", { label: text(field.label) || name })}
        </Typography>
        <IconButton size="small" aria-label={t("Đóng")} onClick={onClose}>
          <CloseIcon fontSize="small" />
        </IconButton>
      </Box>
      {uses.length > 0 && (
        <Alert severity="info" sx={{ mb: 1 }}>
          {t("Trường này đang được dùng ở: {uses}. Đổi định danh ở đây thì các chỗ đó đổi theo; đổi kiểu dữ liệu thì kiểm lại các chỗ đó.", { uses: uses.join("; ") })}
        </Alert>
      )}
      <FormGrid>
        <TextField variant="outlined" size="small" fullWidth label={t("Tên hiển thị")} value={text(field.label)} sx={DENSE} onChange={(event) => onChange({ label: event.target.value })} />
        <NameInput label={t("Định danh")} value={name} pattern={NAME} taken={fieldKeys} onCommit={onRename} help={t("Tên cột trong cơ sở dữ liệu và khóa trong API")} />
        <TextField variant="outlined" select required size="small" fullWidth label={t("Kiểu dữ liệu")} value={type} sx={DENSE} onChange={(event) => onChange({ type: event.target.value })}>
          {fieldTypes.map((item) => (
            <MenuItem key={item} value={item}>
              {FIELD_TYPE_LABEL[item] ?? item}
            </MenuItem>
          ))}
        </TextField>
        {type === "reference" && (
          <TextField variant="outlined" select required size="small" fullWidth label={t("Tham chiếu tới thực thể")} value={text(field.to)} sx={DENSE} onChange={(event) => onChange({ to: event.target.value })}>
            {Object.keys(entities).map((item) => (
              <MenuItem key={item} value={item}>
                {text(rec(entities[item]).label) || item} ({item})
              </MenuItem>
            ))}
          </TextField>
        )}
        {type === "choice" && (
          <Wide>
            <Autocomplete
              multiple
              freeSolo
              size="small"
              options={[] as string[]}
              value={texts(field.options)}
              renderTags={chips}
              renderInput={(params) => <TextField variant="outlined" {...params} required label={t("Các lựa chọn")} placeholder={t("Gõ một lựa chọn rồi nhấn Enter")} sx={DENSE} />}
              onChange={(_, items) => onChange({ options: items.length > 0 ? items.map((item) => item.trim()).filter(Boolean) : undefined })}
            />
          </Wide>
        )}
        <Wide>
          <Box sx={{ display: "flex", gap: 2, flexWrap: "wrap" }}>
            <FormControlLabel
              control={<Checkbox size="small" checked={field.required === true} onChange={(event) => onChange({ required: event.target.checked })} />}
              label={<Typography variant="body2">{t("Bắt buộc nhập")}</Typography>}
            />
            <Tooltip title={t("Hai hồ sơ không được có cùng giá trị ở trường này")}>
              <FormControlLabel
                control={<Checkbox size="small" checked={field.unique === true} onChange={(event) => onChange({ unique: event.target.checked })} />}
                label={<Typography variant="body2">{t("Không trùng")}</Typography>}
              />
            </Tooltip>
            {(type === "choice" || type === "unit") && (
              <FormControlLabel
                control={<Checkbox size="small" checked={field.multiple === true} onChange={(event) => onChange({ multiple: event.target.checked ? true : undefined, default: undefined })} />}
                label={<Typography variant="body2">{t("Chọn nhiều giá trị")}</Typography>}
              />
            )}
          </Box>
        </Wide>
      </FormGrid>
      <Advanced key={name} open={["default", "renamed_from", "min", "max", "pattern", "required_when"].some((key) => field[key] !== undefined)}>
        <FormGrid>
          {type === "choice" && field.multiple === true ? (
            <Autocomplete
              multiple
              size="small"
              options={texts(field.options)}
              value={texts(field.default)}
              renderTags={chips}
              renderInput={(params) => <TextField variant="outlined" {...params} label={t("Giá trị mặc định")} helperText={t("Các lựa chọn điền sẵn cho hồ sơ mới")} sx={DENSE} />}
              onChange={(_, items) => onChange({ default: items.length > 0 ? items : undefined })}
            />
          ) : (
            <TextField
              variant="outlined"
              size="small"
              fullWidth
              label={t("Giá trị mặc định")}
              value={text(field.default)}
              helperText={t("Điền cho hồ sơ cũ khi thêm trường bắt buộc")}
              sx={DENSE}
              onChange={(event) => onChange({ default: type === "number" && event.target.value !== "" && !Number.isNaN(Number(event.target.value)) ? Number(event.target.value) : event.target.value })}
            />
          )}
          <TextField
            variant="outlined"
            size="small"
            fullWidth
            label={t("Tên cũ của trường")}
            value={text(field.renamed_from)}
            helperText={t("Khi đổi định danh mà muốn giữ dữ liệu")}
            error={text(field.renamed_from) !== "" && !NAME.test(text(field.renamed_from))}
            sx={DENSE}
            onChange={(event) => onChange({ renamed_from: event.target.value })}
          />
          {(type === "number" || type === "date") &&
            (["min", "max"] as const).map((bound) => (
              <TextField
                key={bound}
                variant="outlined"
                size="small"
                fullWidth
                type={type === "date" ? "date" : "text"}
                label={bound === "min" ? t("Nhỏ nhất") : t("Lớn nhất")}
                value={text(field[bound])}
                slotProps={{ inputLabel: { shrink: true } }}
                sx={DENSE}
                onChange={(event) => {
                  const value = event.target.value;
                  onChange({ [bound]: value === "" ? undefined : type === "number" && !Number.isNaN(Number(value)) ? Number(value) : value });
                }}
              />
            ))}
          {type === "text" && (
            <TextField
              variant="outlined"
              size="small"
              fullWidth
              label={t("Mẫu chuỗi (pattern)")}
              value={text(field.pattern)}
              helperText={t("Cả chuỗi phải khớp, ví dụ [A-Z]{3}-[0-9]{4}")}
              sx={DENSE}
              onChange={(event) => onChange({ pattern: event.target.value === "" ? undefined : event.target.value })}
            />
          )}
          {type !== "boolean" && type !== "file" && (
            <Wide>
              <TextField
                variant="outlined"
                size="small"
                fullWidth
                label={t("Bắt buộc khi")}
                value={text(field.required_when)}
                helperText={t("Một phép so sánh hoặc nhiều phép nối bằng and, or, not; ví dụ loai == Hoãn thi")}
                sx={DENSE}
                onChange={(event) => onChange({ required_when: event.target.value === "" ? undefined : event.target.value })}
              />
            </Wide>
          )}
        </FormGrid>
      </Advanced>
    </>
  );
}
