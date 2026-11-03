// SPDX-License-Identifier: Apache-2.0
import { Box, Button, Card, CardActions, CardContent, Chip, Typography } from "@mui/material";
import type { Family, Template } from "./api";

interface Props {
  templates: Template[];
  families: Family[];
  onUse: (template: Template) => void;
}

const FAMILY_LABEL: Record<string, string> = {
  approval: "Chuỗi duyệt",
  work_item: "Công việc cần xử lý",
  reservation: "Chiếm dụng theo thời gian",
  none: "Không có quy trình",
};

// The ready-made models; each one opens in the editor to be changed and applied.
export default function TemplatesTab({ templates, onUse }: Props) {
  return (
    <Box sx={{ display: "grid", gap: 2, gridTemplateColumns: "repeat(auto-fill, minmax(300px, 1fr))" }}>
      {templates.map((template) => (
        <Card key={template.id} variant="outlined">
          <CardContent>
            <Typography variant="h6">{template.name}</Typography>
            <Chip size="small" label={FAMILY_LABEL[template.family] ?? template.family} sx={{ my: 1 }} />
            <Typography variant="body2">{template.description}</Typography>
          </CardContent>
          <CardActions>
            <Button onClick={() => onUse(template)}>Dùng mẫu này</Button>
          </CardActions>
        </Card>
      ))}
    </Box>
  );
}
