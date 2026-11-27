// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Autocomplete, Box, Button, IconButton, MenuItem, TextField, Tooltip, Typography } from "@mui/material";
import AddIcon from "@mui/icons-material/Add";
import ArrowDownwardIcon from "@mui/icons-material/ArrowDownward";
import ArrowUpwardIcon from "@mui/icons-material/ArrowUpward";
import DeleteOutlineIcon from "@mui/icons-material/DeleteOutline";
import { DENSE, Empty, FormGrid, Group, GuideBox, list, NAME, NameInput, rec, renameKey, text, texts, uniqueName, Wide, type Rec } from "./formParts";
import { t } from "../shell/t";

export const MAX_DOCUMENTS = 10;
const MAX_BLOCKS = 60;
const MAX_SIGNATURES = 4;
const MAX_TEXT = 2000;

type Kind = "heading" | "text" | "fields" | "signatures";
const KINDS: Kind[] = ["heading", "text", "fields", "signatures"];

const KIND_LABEL: Record<Kind, string> = {
  heading: t("Tiêu đề"),
  text: t("Văn bản"),
  fields: t("Bảng trường"),
  signatures: t("Dòng ký"),
};

// A block of the body has exactly one key, and that key is its kind.
const kindOf = (block: Rec): Kind => KINDS.find((kind) => kind in block) ?? "text";
const emptyBlock = (kind: Kind): Rec => ({ [kind]: kind === "fields" || kind === "signatures" ? [] : "" });

// The mẫu in of an entity: each one a name, the states it prints in, a title and a body of blocks.
export default function DocumentsEditor({
  entity,
  states,
  setEntity,
}: {
  entity: Rec;
  // The states of the process, which a document can be limited to.
  states: { name: string; label: string }[];
  setEntity: (changes: Rec) => void;
}) {
  const documents = rec(entity.documents);
  const names = Object.keys(documents);
  const fields = rec(entity.fields);
  const fieldKeys = Object.keys(fields);
  const hasProcess = entity.process !== undefined;
  // What a placeholder or a table of fields can name: a field, or a column every record has.
  const columns = ["id", "created_at", "updated_at", ...(hasProcess ? ["status", "decided_at"] : [])];
  const available = [...fieldKeys, ...columns];

  const write = (next: Rec) => setEntity({ documents: Object.keys(next).length > 0 ? next : undefined });
  const setDocument = (name: string, changes: Rec) => {
    const next: Rec = { ...rec(documents[name]), ...changes };
    Object.keys(next).forEach((key) => {
      if (next[key] === undefined || next[key] === "") delete next[key];
    });
    write({ ...documents, [name]: next });
  };
  const add = () => {
    const name = uniqueName("mau_in", names);
    const first = fieldKeys[0];
    write({ ...documents, [name]: { label: t("Mẫu in mới"), body: [first ? { fields: [first] } : { text: "" }] } });
  };

  return (
    <>
      <GuideBox section="documents" />
      <Group
        title={t("Mẫu in")}
        hint={t("Chỗ trống dùng được: {names}. Viết {{tên}} trong tên tệp, tiêu đề, văn bản và dòng ký để thay bằng giá trị của hồ sơ.", { names: available.join(", ") })}
        action={
          <Button size="small" startIcon={<AddIcon />} disabled={names.length >= MAX_DOCUMENTS} onClick={add}>
            {t("Thêm mẫu in")}
          </Button>
        }
      >
        {names.length === 0 && <Empty>{t("Chưa có mẫu in. Thêm một mẫu để người dùng tải hồ sơ thành tệp PDF.")}</Empty>}
        {names.map((name) => {
          const document = rec(documents[name]);
          const body = list(document.body).map(rec);
          const when = texts(Array.isArray(document.when) ? document.when : document.when === undefined ? [] : [document.when]);
          const setBody = (next: Rec[]) => setDocument(name, { body: next });
          const setBlock = (index: number, block: Rec) => setBody(body.map((item, i) => (i === index ? block : item)));
          const moveBlock = (index: number, by: number) => {
            const next = [...body];
            [next[index], next[index + by]] = [next[index + by], next[index]];
            setBody(next);
          };
          return (
            <Box key={name} sx={{ border: 1, borderColor: "divider", borderRadius: 1, p: 2, mb: 2 }}>
              <FormGrid>
                <NameInput
                  label={t("Tên mẫu")}
                  value={name}
                  pattern={NAME}
                  taken={names}
                  help={t("Dùng trong đường dẫn của mẫu")}
                  onCommit={(to) => write(renameKey(documents, name, to))}
                />
                <TextField variant="outlined" size="small" fullWidth label={t("Tên mẫu ở màn hình hồ sơ")} value={text(document.label)} sx={DENSE} onChange={(event) => setDocument(name, { label: event.target.value })} />
                <TextField
                  variant="outlined"
                  size="small"
                  fullWidth
                  label={t("Tên tệp tải về")}
                  value={text(document.file)}
                  helperText={t("Không kèm .pdf, tối đa 80 ký tự")}
                  sx={DENSE}
                  slotProps={{ htmlInput: { maxLength: 80 } }}
                  onChange={(event) => setDocument(name, { file: event.target.value })}
                />
                <Wide>
                  <TextField variant="outlined" size="small" fullWidth label={t("Tiêu đề đầu trang")} value={text(document.title)} sx={DENSE} onChange={(event) => setDocument(name, { title: event.target.value })} />
                </Wide>
                {hasProcess && (
                  <Wide>
                    <Autocomplete
                      multiple
                      size="small"
                      options={states.map((state) => state.name)}
                      value={when}
                      getOptionLabel={(state) => states.find((item) => item.name === state)?.label || state}
                      onChange={(_, selected) => setDocument(name, { when: selected.length === 0 ? undefined : selected.length === 1 ? selected[0] : selected })}
                      renderInput={(params) => <TextField {...params} label={t("Chỉ in được ở trạng thái")} helperText={t("Bỏ trống thì in ở mọi trạng thái")} />}
                    />
                  </Wide>
                )}
              </FormGrid>

              <Typography variant="subtitle2" sx={{ mt: 1.5, mb: 0.5 }}>
                {t("Nội dung")}
              </Typography>
              {body.map((block, index) => {
                const kind = kindOf(block);
                return (
                  // biome-ignore lint/suspicious/noArrayIndexKey: a block edited keeps its row, so its input keeps the focus.
                  <Box key={index} sx={{ display: "flex", flexWrap: "wrap", gap: 1.5, alignItems: "flex-start", py: 0.75 }}>
                    <Box sx={{ flex: "1 1 140px", maxWidth: { sm: 170 } }}>
                      <TextField
                        variant="outlined"
                        select
                        size="small"
                        fullWidth
                        label={t("Loại khối")}
                        value={kind}
                        sx={DENSE}
                        onChange={(event) => setBlock(index, emptyBlock(event.target.value as Kind))}
                      >
                        {KINDS.map((item) => (
                          <MenuItem key={item} value={item}>
                            {KIND_LABEL[item]}
                          </MenuItem>
                        ))}
                      </TextField>
                    </Box>
                    <Box sx={{ flex: "3 1 260px", minWidth: 0 }}>
                      {kind === "heading" || kind === "text" ? (
                        <TextField
                          variant="outlined"
                          size="small"
                          fullWidth
                          multiline={kind === "text"}
                          minRows={kind === "text" ? 2 : undefined}
                          label={KIND_LABEL[kind]}
                          value={text(block[kind])}
                          sx={DENSE}
                          slotProps={{ htmlInput: { maxLength: MAX_TEXT } }}
                          onChange={(event) => setBlock(index, { [kind]: event.target.value })}
                        />
                      ) : kind === "fields" ? (
                        <Autocomplete
                          multiple
                          size="small"
                          options={available}
                          value={texts(block.fields)}
                          getOptionLabel={(key) => (fieldKeys.includes(key) ? text(rec(fields[key]).label) || key : key)}
                          onChange={(_, selected) => setBlock(index, { fields: selected })}
                          renderInput={(params) => <TextField {...params} label={t("Trường in thành bảng")} error={texts(block.fields).length === 0} helperText={texts(block.fields).length === 0 ? t("Chọn ít nhất một trường") : undefined} />}
                        />
                      ) : (
                        <Autocomplete
                          multiple
                          freeSolo
                          size="small"
                          options={[]}
                          value={texts(block.signatures)}
                          onChange={(_, selected) => setBlock(index, { signatures: selected.map(String).slice(0, MAX_SIGNATURES) })}
                          renderInput={(params) => (
                            <TextField
                              {...params}
                              label={t("Tiêu đề ký")}
                              error={texts(block.signatures).length === 0}
                              helperText={t("Gõ tiêu đề rồi nhấn Enter; từ một đến bốn tiêu đề")}
                            />
                          )}
                        />
                      )}
                    </Box>
                    <Box sx={{ display: "flex", alignItems: "center", flex: "none" }}>
                      <Tooltip title={t("Lên")}>
                        <span>
                          <IconButton size="small" aria-label={t("Đưa khối lên")} disabled={index === 0} onClick={() => moveBlock(index, -1)}>
                            <ArrowUpwardIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title={t("Xuống")}>
                        <span>
                          <IconButton size="small" aria-label={t("Đưa khối xuống")} disabled={index === body.length - 1} onClick={() => moveBlock(index, 1)}>
                            <ArrowDownwardIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                      <Tooltip title={t("Xóa khối")}>
                        <IconButton size="small" aria-label={t("Xóa khối")} onClick={() => setBody(body.filter((_, i) => i !== index))}>
                          <DeleteOutlineIcon fontSize="small" color="error" />
                        </IconButton>
                      </Tooltip>
                    </Box>
                  </Box>
                );
              })}
              <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", mt: 0.5 }}>
                {KINDS.map((kind) => (
                  <Button key={kind} size="small" startIcon={<AddIcon />} disabled={body.length >= MAX_BLOCKS} onClick={() => setBody([...body, emptyBlock(kind)])}>
                    {KIND_LABEL[kind]}
                  </Button>
                ))}
                <Box sx={{ flexGrow: 1 }} />
                <Button size="small" color="error" startIcon={<DeleteOutlineIcon />} onClick={() => write(Object.fromEntries(Object.entries(documents).filter(([key]) => key !== name)))}>
                  {t("Xóa mẫu này")}
                </Button>
              </Box>
            </Box>
          );
        })}
      </Group>
    </>
  );
}
