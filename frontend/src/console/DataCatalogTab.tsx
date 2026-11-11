// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Alert,
  Box,
  Card,
  Chip,
  MenuItem,
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableRow,
  TextField,
  Typography,
} from "@mui/material";
import { getDataCatalog, listApplied, type DataCatalog, type DataPoint } from "./api";
import { SCOPE_LABEL } from "./formGuide";
import { t } from "../shell/t";

const message = (error: unknown) => (error instanceof Error ? error.message : t("Không xử lý được"));

const POINT_LABEL: Record<string, string> = {
  role: t("Người dùng với vai"),
  app: t("Ứng dụng ngoài"),
  webhook: t("Gửi tới hệ thống ngoài"),
  completion: t("Hành động khi hoàn tất"),
  feed: t("Dòng sự kiện"),
  archive: t("Lưu trữ dài hạn"),
};

const LINK_LABEL: Record<string, string> = {
  reference: t("tham chiếu"),
  owner: t("chủ sở hữu theo"),
};

function Points({ title, points }: { title: string; points: DataPoint[] }) {
  return (
    <Card sx={{ p: 2, flex: 1, minWidth: 260 }}>
      <Typography variant="subtitle2" sx={{ mb: 1 }}>
        {title}
      </Typography>
      <Box sx={{ display: "flex", flexWrap: "wrap", gap: 1 }}>
        {points.length === 0 && <Typography variant="body2">{t("Chưa có")}</Typography>}
        {points.map((point, index) => (
          <Chip
            key={`${point.kind}-${point.name}-${index}`}
            size="small"
            variant="outlined"
            label={`${POINT_LABEL[point.kind] ?? point.kind}: ${point.name}${point.detail ? ` (${point.detail})` : ""}`}
          />
        ))}
      </Box>
    </Card>
  );
}

// Where the data of one model comes from, what it holds, who may reach it and where it goes.
export default function DataCatalogTab() {
  const [models, setModels] = useState<string[]>([]);
  const [model, setModel] = useState("");
  const [catalog, setCatalog] = useState<DataCatalog | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    listApplied().then(
      (items) => {
        const names = items.map((item) => item.model);
        setModels(names);
        setModel((current) => current || names[0] || "");
      },
      (e: unknown) => setError(message(e)),
    );
  }, []);

  useEffect(() => {
    if (!model) return;
    setCatalog(null);
    getDataCatalog(model).then(
      (value) => {
        setError(null);
        setCatalog(value);
      },
      (e: unknown) => setError(message(e)),
    );
  }, [model]);

  return (
    <Box>
      <Alert severity="info" sx={{ mb: 2 }}>
        {t("Danh mục này đọc thẳng từ mô hình đang chạy: dữ liệu nằm ở đâu, ai được xem, tham chiếu lẫn nhau ra sao, đi vào từ đâu và đi ra đâu.")}
      </Alert>
      {error && (
        <Alert severity="error" sx={{ mb: 2 }}>
          {error}
        </Alert>
      )}
      <TextField select size="small" label={t("Mô hình")} value={model} onChange={(e) => setModel(e.target.value)} sx={{ minWidth: 240, mb: 2 }}>
        {models.map((name) => (
          <MenuItem key={name} value={name}>
            {name}
          </MenuItem>
        ))}
      </TextField>
      {catalog && (
        <Box sx={{ display: "flex", flexDirection: "column", gap: 2 }}>
          <Box sx={{ display: "flex", flexWrap: "wrap", gap: 2 }}>
            <Points title={t("Dữ liệu đi vào từ")} points={catalog.origins} />
            <Points title={t("Dữ liệu đi ra tới")} points={catalog.destinations} />
          </Box>
          {catalog.links.length > 0 && (
            <Card sx={{ p: 2 }}>
              <Typography variant="subtitle2" sx={{ mb: 1 }}>
                {t("Liên kết giữa các thực thể")}
              </Typography>
              {catalog.links.map((link) => (
                <Typography variant="body2" key={`${link.from}-${link.via}`}>
                  {`${link.from}.${link.via} → ${link.to} (${LINK_LABEL[link.kind] ?? link.kind})`}
                </Typography>
              ))}
            </Card>
          )}
          {catalog.entities.map((entity) => (
            <Card key={entity.name} sx={{ p: 2 }}>
              <Box sx={{ display: "flex", flexWrap: "wrap", alignItems: "center", gap: 1, mb: 1 }}>
                <Typography variant="subtitle1" sx={{ fontWeight: 600 }}>
                  {entity.label || entity.name}
                </Typography>
                <Typography variant="body2" color="text.secondary">
                  {entity.name}
                </Typography>
                <Chip size="small" label={entity.rows < 0 ? t("Chưa đọc được số bản ghi") : t("{n} bản ghi", { n: entity.rows })} />
                {entity.hasProcess && <Chip size="small" color="primary" variant="outlined" label={t("Có quy trình: {family}", { family: entity.family ?? "" })} />}
              </Box>
              <Box sx={{ display: "flex", flexWrap: "wrap", gap: 0.5, mb: 1 }}>
                {Object.entries(entity.access).map(([role, scope]) => (
                  <Chip key={role} size="small" variant="outlined" label={`${role}: ${SCOPE_LABEL[scope] ?? scope}`} />
                ))}
                {entity.handlers.map((role) => (
                  <Chip key={`h-${role}`} size="small" color="secondary" variant="outlined" label={t("{role} xử lý", { role })} />
                ))}
              </Box>
              <Box sx={{ overflowX: "auto" }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>{t("Trường")}</TableCell>
                      <TableCell>{t("Nhãn")}</TableCell>
                      <TableCell>{t("Kiểu")}</TableCell>
                      <TableCell>{t("Ràng buộc")}</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {entity.fields.map((field) => (
                      <TableRow key={field.name}>
                        <TableCell>{field.name}</TableCell>
                        <TableCell>{field.label}</TableCell>
                        <TableCell>{field.reference ? `${field.type} → ${field.reference}` : field.type}{field.multiple ? "[]" : ""}</TableCell>
                        <TableCell>{[field.required ? t("bắt buộc") : "", field.unique ? t("không trùng") : ""].filter(Boolean).join(", ")}</TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            </Card>
          ))}
        </Box>
      )}
    </Box>
  );
}
