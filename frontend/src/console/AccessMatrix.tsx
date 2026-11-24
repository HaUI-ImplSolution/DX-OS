// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Box, MenuItem, Table, TableBody, TableCell, TableHead, TableRow, TextField, Tooltip, Typography } from "@mui/material";
import { SCOPE_HELP, SCOPE_LABEL } from "./formGuide";
import { asList, DENSE, Empty, rec, text, type Rec } from "./formParts";
import { t } from "../shell/t";

const VIEWING = ["all", "read", "unit", "assigned"];

// The access of every role to every entity on one table; a cell changes the access of one entity.
export default function AccessMatrix({
  roleNames,
  entities,
  scopes,
  onChange,
}: {
  roleNames: string[];
  entities: Rec;
  scopes: string[];
  onChange: (entity: string, role: string, scope: string) => void;
}) {
  const keys = Object.keys(entities);
  if (roleNames.length === 0 || keys.length === 0) {
    return <Empty>{t("Ma trận hiện khi mô hình có ít nhất một vai trò và một thực thể.")}</Empty>;
  }
  return (
    <>
      <Box sx={{ overflowX: "auto" }}>
        <Table size="small" sx={{ "& td, & th": { whiteSpace: "nowrap" } }}>
          <TableHead>
            <TableRow>
              <TableCell>{t("Vai trò")}</TableCell>
              {keys.map((key) => (
                <TableCell key={key}>{text(rec(entities[key]).label) || key}</TableCell>
              ))}
            </TableRow>
          </TableHead>
          <TableBody>
            {roleNames.map((role) => (
              <TableRow key={role}>
                <TableCell sx={{ fontFamily: "ui-monospace, monospace", fontWeight: 600 }}>{role}</TableCell>
                {keys.map((key) => {
                  const entity = rec(entities[key]);
                  const scope = text(rec(entity.access)[role]);
                  const handles = entity.process !== undefined && asList(rec(entity.process).handler).includes(role);
                  const blind = handles && !VIEWING.includes(scope);
                  return (
                    <TableCell key={key} sx={{ minWidth: 150 }}>
                      <Tooltip title={blind ? t("Vai trò này xử lý quy trình của thực thể nhưng không xem được hồ sơ") : handles ? t("Vai trò xử lý quy trình") : ""}>
                        <TextField
                          select
                          size="small"
                          fullWidth
                          value={scope}
                          error={blind}
                          sx={DENSE}
                          slotProps={{ select: { displayEmpty: true } }}
                          aria-label={t("Quyền của {role} trên {entity}", { role, entity: key })}
                          onChange={(event) => onChange(key, role, event.target.value)}
                        >
                          <MenuItem value="">{t("Không có quyền")}</MenuItem>
                          {scopes.map((item) => (
                            <MenuItem key={item} value={item}>
                              {SCOPE_LABEL[item] ?? item}
                            </MenuItem>
                          ))}
                        </TextField>
                      </Tooltip>
                      {handles && (
                        <Typography variant="caption" color={blind ? "error" : "text.secondary"} sx={{ display: "block" }}>
                          {t("xử lý quy trình")}
                        </Typography>
                      )}
                    </TableCell>
                  );
                })}
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </Box>
      <ScopeLegend scopes={scopes} />
    </>
  );
}

// What every level of access lets a person do, in plain words.
export function ScopeLegend({ scopes }: { scopes: string[] }) {
  return (
    <Box component="dl" sx={{ display: "grid", gridTemplateColumns: "max-content 1fr", columnGap: 2, rowGap: 0.5, mt: 1.5, mb: 0, fontSize: 13 }}>
      {scopes.map((scope) => (
        <Box key={scope} sx={{ display: "contents" }}>
          <Box component="dt" sx={{ fontWeight: 600 }}>
            {SCOPE_LABEL[scope] ?? scope}
          </Box>
          <Box component="dd" sx={{ m: 0, color: "text.secondary" }}>
            {SCOPE_HELP[scope] ?? ""}
          </Box>
        </Box>
      ))}
    </Box>
  );
}
