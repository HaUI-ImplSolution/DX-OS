// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Box, Table, TableBody, TableCell, TableHead, TableRow, Typography } from "@mui/material";
import type { FieldLineage, LineageEdge } from "./api";
import { SCOPE_LABEL } from "./formGuide";
import { t } from "../shell/t";

// One line of plain words for an edge of the lineage of a field.
function edgeText({ kind, name, detail }: LineageEdge): string {
  switch (kind) {
    case "input":
      return t("Nhập bởi vai {name} ({scope})", { name, scope: SCOPE_LABEL[detail ?? ""] ?? detail ?? "" });
    case "default":
      return t("Giá trị mặc định: {name}", { name });
    case "reference":
      return t("Lấy từ hồ sơ của {name}", { name });
    case "unit":
      return t("Chọn từ các đơn vị: {name}", { name: detail ?? "" });
    case "file":
      return t("Tệp lưu ở kho tệp");
    case "generated":
      return t("Sinh khi tạo hồ sơ: {name}", { name });
    case "renamed_from":
      return t("Trước đây là trường {name}", { name });
    case "required_when":
      return t("Quyết định trường {name} có bắt buộc không ({detail})", { name, detail: detail ?? "" });
    case "require":
      return t("Hành động {name} đòi phải có giá trị", { name });
    case "when":
      return t("Điều kiện của hành động {name} ({detail})", { name, detail: detail ?? "" });
    case "owner":
      return t("Xác định chủ sở hữu qua {name}", { name });
    case "unit_scope":
      return t("Vai {name} chỉ xem hồ sơ cùng đơn vị", { name });
    case "history":
      return t("Dòng lịch sử mỗi khi giá trị đổi");
    case "feed":
      return t("Dòng sự kiện");
    case "webhook":
      return t("Gửi tới hệ thống ngoài: {name}", { name });
    case "archive":
      return t("Lưu trữ dài hạn");
    default:
      return `${kind}: ${name}`;
  }
}

function Lines({ edges }: { edges: LineageEdge[] }) {
  if (edges.length === 0) return <Typography variant="body2">—</Typography>;
  return (
    <>
      {edges.map((edge, index) => (
        <Typography variant="body2" key={`${edge.kind}-${edge.name}-${index}`}>
          {edgeText(edge)}
        </Typography>
      ))}
    </>
  );
}

// Where the value of each field of one entity comes from, what reads it and where it goes.
export default function FieldLineagePanel({ fields }: { fields: FieldLineage[] }) {
  return (
    <Box sx={{ overflowX: "auto" }}>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>
        {t("Đọc từ mô hình: lõi giữ nguyên giá trị người dùng nhập, không biến đổi qua bước nào.")}
      </Typography>
      <Table size="small">
        <TableHead>
          <TableRow>
            <TableCell>{t("Trường")}</TableCell>
            <TableCell>{t("Đi vào từ")}</TableCell>
            <TableCell>{t("Được dùng bởi")}</TableCell>
            <TableCell>{t("Đi ra tới")}</TableCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {fields.map((field) => (
            <TableRow key={field.field} sx={{ verticalAlign: "top" }}>
              <TableCell>{field.label || field.field}</TableCell>
              <TableCell>
                <Lines edges={field.origins} />
              </TableCell>
              <TableCell>
                <Lines edges={field.uses} />
              </TableCell>
              <TableCell>
                <Lines edges={field.destinations} />
              </TableCell>
            </TableRow>
          ))}
        </TableBody>
      </Table>
    </Box>
  );
}
