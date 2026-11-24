// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Autocomplete, Box, Button, IconButton, MenuItem, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { DENSE, Empty, FormGrid, Group, GuideBox, list, rec, text, texts, type Rec } from "./formParts";
import { t } from "../shell/t";

// The columns every record has beside the fields of its entity; the status and the assignee exist with a process.
export const RECORD_COLUMNS = ["status", "assignee", "created_at", "updated_at"];

// A row of controls that wraps under itself when the page is narrow.
const ROW = { display: "flex", flexWrap: "wrap", gap: 1.5, alignItems: "flex-start", py: 0.75 };

const COLUMN_LABEL: Record<string, string> = {
  status: t("Trạng thái"),
  assignee: t("Người xử lý"),
  created_at: t("Ngày tạo"),
  updated_at: t("Cập nhật lần cuối"),
};

// The ui block as it is written to the file: a part with nothing in it is left out, and so is the block itself.
function tidyUi(ui: Rec): Rec | undefined {
  const next: Rec = { ...ui };
  const menu = compactPart(rec(ui.menu));
  const listView = compactPart(rec(ui.list));
  const hints = compactPart(rec(ui.hints));
  const groups = list(rec(ui.form).groups);
  if (menu) next.menu = menu;
  else delete next.menu;
  if (listView) next.list = listView;
  else delete next.list;
  if (hints) next.hints = hints;
  else delete next.hints;
  if (groups.length > 0) next.form = { groups };
  else delete next.form;
  if (next.icon === "" || next.icon === undefined) delete next.icon;
  return Object.keys(next).length > 0 ? next : undefined;
}

function compactPart(part: Rec): Rec | undefined {
  const kept: Rec = {};
  Object.entries(part).forEach(([key, value]) => {
    if (value === undefined || value === "" || (Array.isArray(value) && value.length === 0)) return;
    kept[key] = value;
  });
  return Object.keys(kept).length > 0 ? kept : undefined;
}

// The ui block of an entity as a form: the icon and the menu, the list, the groups of the form and the hint of each field.
export default function UiEditor({ entity, icons, setEntity }: { entity: Rec; icons: string[]; setEntity: (changes: Rec) => void }) {
  const ui = rec(entity.ui);
  const fields = rec(entity.fields);
  const fieldKeys = Object.keys(fields);
  const hasProcess = entity.process !== undefined;
  const columns = [...fieldKeys, ...RECORD_COLUMNS.filter((column) => hasProcess || column.endsWith("_at"))];
  const nameOf = (key: string) => (fieldKeys.includes(key) ? text(rec(fields[key]).label) || key : (COLUMN_LABEL[key] ?? key));
  const write = (changes: Rec) => setEntity({ ui: tidyUi({ ...ui, ...changes }) });

  const menu = rec(ui.menu);
  const listView = rec(ui.list);
  const sort = text(listView.sort);
  const sortColumn = sort.replace(/^-/, "");
  const groups = list(rec(ui.form).groups).map(rec);
  const hints = rec(ui.hints);
  const inputs = fieldKeys.filter((key) => rec(fields[key]).type !== "code");

  const setGroups = (next: Rec[]) => write({ form: { groups: next } });
  const setGroup = (index: number, changes: Rec) => setGroups(groups.map((group, i) => (i === index ? { ...group, ...changes } : group)));
  const move = (index: number, by: number) => {
    const next = [...groups];
    [next[index], next[index + by]] = [next[index + by], next[index]];
    setGroups(next);
  };
  const setHint = (key: string, value: string) => {
    const next = { ...hints };
    if (value === "") delete next[key];
    else next[key] = value;
    write({ hints: next });
  };

  return (
    <>
      <GuideBox section="ui" />
      <Group title={t("Biểu tượng và menu")}>
        <FormGrid>
          <TextField
            variant="outlined"
            select
            size="small"
            fullWidth
            label={t("Biểu tượng")}
            value={text(ui.icon)}
            sx={DENSE}
            slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
            onChange={(event) => write({ icon: event.target.value })}
          >
            <MenuItem value="">{t("(mặc định)")}</MenuItem>
            {icons.map((icon) => (
              <MenuItem key={icon} value={icon}>
                {icon}
              </MenuItem>
            ))}
          </TextField>
          <TextField
            variant="outlined"
            size="small"
            fullWidth
            label={t("Nhóm trong menu")}
            value={text(menu.group)}
            helperText={t("Các thực thể cùng nhóm đứng chung một mục")}
            sx={DENSE}
            slotProps={{ htmlInput: { maxLength: 40 } }}
            onChange={(event) => write({ menu: { ...menu, group: event.target.value } })}
          />
          <TextField
            variant="outlined"
            size="small"
            fullWidth
            type="number"
            label={t("Thứ tự trong menu")}
            value={menu.order ?? ""}
            helperText={t("Số từ 0 đến 9999, nhỏ đứng trước")}
            sx={DENSE}
            slotProps={{ htmlInput: { min: 0, max: 9999 } }}
            onChange={(event) => write({ menu: { ...menu, order: event.target.value === "" ? undefined : Math.min(9999, Math.max(0, Math.trunc(Number(event.target.value)))) } })}
          />
        </FormGrid>
      </Group>

      <Group title={t("Danh sách hồ sơ")} hint={t("Không chọn gì thì danh sách mở theo mã giảm dần, hoặc theo ngày cập nhật khi thực thể có quy trình.")}>
        <Box sx={{ display: "grid", gap: 1.5, py: 1 }}>
          <Autocomplete
            multiple
            size="small"
            options={columns}
            value={texts(listView.columns)}
            getOptionLabel={nameOf}
            onChange={(_, selected) => write({ list: { ...listView, columns: selected } })}
            renderInput={(params) => <TextField {...params} label={t("Cột hiện trong danh sách")} helperText={t("Cột hiện theo thứ tự chọn")} />}
          />
          <FormGrid>
            <TextField
              variant="outlined"
              select
              size="small"
              fullWidth
              label={t("Sắp xếp theo")}
              value={sortColumn}
              sx={DENSE}
              slotProps={{ select: { displayEmpty: true }, inputLabel: { shrink: true } }}
              onChange={(event) => write({ list: { ...listView, sort: event.target.value === "" ? undefined : `${sort.startsWith("-") ? "-" : ""}${event.target.value}` } })}
            >
              <MenuItem value="">{t("(mặc định)")}</MenuItem>
              {columns.map((column) => (
                <MenuItem key={column} value={column}>
                  {nameOf(column)}
                </MenuItem>
              ))}
            </TextField>
            <TextField
              variant="outlined"
              select
              size="small"
              fullWidth
              label={t("Chiều sắp xếp")}
              value={sort.startsWith("-") ? "desc" : "asc"}
              disabled={sortColumn === ""}
              sx={DENSE}
              onChange={(event) => write({ list: { ...listView, sort: `${event.target.value === "desc" ? "-" : ""}${sortColumn}` } })}
            >
              <MenuItem value="asc">{t("Tăng dần")}</MenuItem>
              <MenuItem value="desc">{t("Giảm dần")}</MenuItem>
            </TextField>
          </FormGrid>
        </Box>
      </Group>

      <Group
        title={t("Nhóm trường trên form")}
        hint={t("Trường không nằm trong nhóm nào xếp vào nhóm cuối; mỗi trường chỉ nằm trong một nhóm.")}
        action={
          <Button size="small" startIcon={<AddIcon />} onClick={() => setGroups([...groups, { label: t("Nhóm {n}", { n: groups.length + 1 }), fields: [] }])}>
            {t("Thêm nhóm")}
          </Button>
        }
      >
        {groups.length === 0 && <Empty>{t("Chưa chia nhóm: form xếp các trường theo thứ tự khai báo.")}</Empty>}
        {groups.map((group, index) => {
          const others = new Set(groups.flatMap((other, i) => (i === index ? [] : texts(other.fields))));
          const mine = texts(group.fields);
          return (
            // biome-ignore lint/suspicious/noArrayIndexKey: a group renamed keeps its row, so its label input keeps the focus.
            <Box key={index} sx={ROW}>
              <Box sx={{ flex: "1 1 180px", maxWidth: { sm: 260 } }}>
                <TextField
                  variant="outlined"
                  size="small"
                  fullWidth
                  label={t("Nhãn nhóm")}
                  value={text(group.label)}
                  error={text(group.label).trim() === ""}
                  sx={DENSE}
                  slotProps={{ htmlInput: { maxLength: 60 } }}
                  onChange={(event) => setGroup(index, { label: event.target.value })}
                />
              </Box>
              <Box sx={{ flex: "3 1 260px", minWidth: 0 }}>
                <Autocomplete
                  multiple
                  size="small"
                  options={inputs.filter((key) => mine.includes(key) || !others.has(key))}
                  value={mine}
                  getOptionLabel={nameOf}
                  onChange={(_, selected) => setGroup(index, { fields: selected })}
                  renderInput={(params) => <TextField {...params} label={t("Trường trong nhóm")} error={mine.length === 0} helperText={mine.length === 0 ? t("Chọn ít nhất một trường") : undefined} />}
                />
              </Box>
              <Box sx={{ display: "flex", alignItems: "center", flex: "none" }}>
                <Tooltip title={t("Lên")}>
                  <span>
                    <IconButton size="small" aria-label={t("Đưa nhóm lên")} disabled={index === 0} onClick={() => move(index, -1)}>
                      <ArrowUpwardIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title={t("Xuống")}>
                  <span>
                    <IconButton size="small" aria-label={t("Đưa nhóm xuống")} disabled={index === groups.length - 1} onClick={() => move(index, 1)}>
                      <ArrowDownwardIcon fontSize="small" />
                    </IconButton>
                  </span>
                </Tooltip>
                <Tooltip title={t("Xóa nhóm")}>
                  <IconButton size="small" aria-label={t("Xóa nhóm")} onClick={() => setGroups(groups.filter((_, i) => i !== index))}>
                    <DeleteOutlineIcon fontSize="small" color="error" />
                  </IconButton>
                </Tooltip>
              </Box>
            </Box>
          );
        })}
      </Group>

      <Group title={t("Gợi ý của trường")} hint={t("Một dòng gợi ý hiện dưới ô nhập của trường, tối đa 200 ký tự.")}>
        <Box sx={{ overflowX: "auto" }}>
          <Table size="small">
            <TableHead>
              <TableRow>
                <TableCell>{t("Trường")}</TableCell>
                <TableCell sx={{ width: "65%" }}>{t("Gợi ý")}</TableCell>
              </TableRow>
            </TableHead>
            <TableBody>
              {fieldKeys.map((key) => (
                <TableRow key={key}>
                  <TableCell>
                    <Typography variant="body2">{nameOf(key)}</Typography>
                    <Typography variant="caption" color="text.secondary" sx={{ fontFamily: "ui-monospace, monospace" }}>
                      {key}
                    </Typography>
                  </TableCell>
                  <TableCell>
                    <TextField
                      variant="outlined"
                      size="small"
                      fullWidth
                      value={text(hints[key])}
                      sx={DENSE}
                      slotProps={{ htmlInput: { maxLength: 200, "aria-label": t("Gợi ý của {field}", { field: key }) } }}
                      onChange={(event) => setHint(key, event.target.value)}
                    />
                  </TableCell>
                </TableRow>
              ))}
            </TableBody>
          </Table>
        </Box>
      </Group>
    </>
  );
}
