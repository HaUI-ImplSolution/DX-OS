// SPDX-License-Identifier: Apache-2.0
import { useMemo } from "react";
import { Box, Button, Card, Chip, Typography } from "@mui/material";
import EventSeatOutlinedIcon from "@mui/icons-material/EventSeatOutlined";
import FactCheckOutlinedIcon from "@mui/icons-material/FactCheckOutlined";
import SupportAgentOutlinedIcon from "@mui/icons-material/SupportAgentOutlined";
import TableChartOutlinedIcon from "@mui/icons-material/TableChartOutlined";
import type { Template } from "./api";
import { FAMILY_LABEL, summarize } from "./labels";

interface Props {
  templates: Template[];
  onUse: (template: Template) => void;
}

const ICONS: Record<string, JSX.Element> = {
  approval: <FactCheckOutlinedIcon />,
  work_item: <SupportAgentOutlinedIcon />,
  reservation: <EventSeatOutlinedIcon />,
};

// The ready-made models; each one opens in the editor to be changed and applied.
export default function TemplatesTab({ templates, onUse }: Props) {
  const summaries = useMemo(() => Object.fromEntries(templates.map((t) => [t.id, summarize(t.content)])), [templates]);
  return (
    <Box>
      <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>
        Chọn một mẫu gần với nghiệp vụ của bạn, sửa trong trình soạn rồi áp dụng. Mỗi mẫu là một tệp mô hình hoàn chỉnh.
      </Typography>
      <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fill, minmax(min(320px, 100%), 1fr))" }}>
        {templates.map((template) => {
          const summary = summaries[template.id];
          return (
            <Card key={template.id} sx={{ p: 2.5, display: "flex", flexDirection: "column", gap: 1.5 }}>
              <Box sx={{ display: "flex", alignItems: "center", gap: 1.5 }}>
                <Box sx={{ width: 40, height: 40, borderRadius: 2, display: "grid", placeItems: "center", color: "primary.main", bgcolor: "action.hover" }}>
                  {ICONS[template.family] ?? <TableChartOutlinedIcon />}
                </Box>
                <Box>
                  <Typography variant="subtitle1" sx={{ fontWeight: 700, lineHeight: 1.2 }}>
                    {template.name}
                  </Typography>
                  <Typography variant="caption" color="text.secondary">
                    {FAMILY_LABEL[template.family] ?? template.family}
                  </Typography>
                </Box>
              </Box>
              <Typography variant="body2" color="text.secondary" sx={{ flex: 1 }}>
                {template.description}
              </Typography>
              {summary && (
                <Box sx={{ display: "flex", gap: 0.75, flexWrap: "wrap" }}>
                  <Chip size="small" variant="outlined" label={`${summary.entities.length} thực thể`} />
                  <Chip size="small" variant="outlined" label={`${summary.roles} vai`} />
                  {summary.units > 0 && <Chip size="small" variant="outlined" label={`${summary.units} đơn vị`} />}
                </Box>
              )}
              <Button variant="contained" onClick={() => onUse(template)} sx={{ alignSelf: "flex-start" }}>
                Dùng mẫu này
              </Button>
            </Card>
          );
        })}
      </Box>
    </Box>
  );
}
