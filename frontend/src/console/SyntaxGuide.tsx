// SPDX-License-Identifier: Apache-2.0
import { Accordion, AccordionDetails, AccordionSummary, Box, Card, Chip, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import GuideBlocks from "./GuideBlocks";
import { GUIDE, SCHEMA_HELP, type Section } from "./formGuide";
import { FAMILY_LABEL } from "./labels";
import { useFamilies } from "./useFamilies";

const PART_TITLE: Record<Section, string> = {
  general: "Tên mô hình và đích gửi lịch sử",
  roles: "Vai",
  units: "Đơn vị",
  entities: "Thực thể",
  fields: "Trường",
  access: "Quyền theo vai",
  process: "Quy trình",
};

const EXAMPLE = `process:
  family: work_item          # khung có sẵn; bỏ dòng này nếu tự khai đủ states và actions
  handler: [AGENT, LEAD]     # một vai, hoặc danh sách vai
  states:
    escalated: {label: Đã chuyển cấp}
  actions:
    escalate: {label: Chuyển cấp, from: [in_progress], to: escalated, role: LEAD}
    resolve: {require: [resolution]}   # ghi đè hành động của khung
    reopen: null                       # bỏ hành động của khung
  auto_close: {after: 7d, action: confirm}`;

const mono = { fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: 12 };

// The range of what a model file accepts, shown beside the editor: the parts of the file, the
// states and actions of the ready made families (the names a file can override), and the keys of a
// process with what each one does.
export default function SyntaxGuide() {
  const families = useFamilies();
  const processKeys = Object.entries(SCHEMA_HELP).filter(([path]) => path.startsWith("entities.process."));
  return (
    <Card sx={{ p: 0, overflow: "hidden" }}>
      <Box sx={{ px: 2.5, pt: 2, pb: 1 }}>
        <Typography variant="h6">Viết được gì trong tệp</Typography>
        <Typography variant="body2" color="text.secondary">
          Hệ thống chấp nhận những khóa nào, mở rộng được đến đâu và tự tạo được gì. Rê chuột lên một khóa trong
          trình soạn YAML cũng hiện giải thích.
        </Typography>
      </Box>
      <Accordion disableGutters elevation={0} square>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">Khung quy trình có sẵn và tên có thể ghi đè</Typography>
        </AccordionSummary>
        <AccordionDetails>
          {families.length === 0 && <Typography variant="body2">Đang tải…</Typography>}
          {families.map((family) => (
            <Box key={family.name} sx={{ mb: 1.5 }}>
              <Typography variant="body2" sx={{ fontWeight: 700 }}>
                {FAMILY_LABEL[family.name] ?? family.name} <span style={mono}>family: {family.name}</span>
              </Typography>
              <Box sx={{ display: "flex", gap: 0.5, flexWrap: "wrap", my: 0.5 }}>
                {family.states.map((state) => (
                  <Chip
                    key={state.name}
                    size="small"
                    variant={state.terminal ? "filled" : "outlined"}
                    label={<span style={mono}>{state.name}{state.terminal ? " (cuối)" : ""}</span>}
                    title={state.label}
                  />
                ))}
              </Box>
              <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
                {family.actions.map((action) => (
                  <li key={action.name}>
                    <span style={mono}>{action.name}</span> ({action.label}): {action.from.join(", ")} → {action.to}, do{" "}
                    {action.by === "OWNER" ? "người nộp" : "vai xử lý"}
                    {action.needsNote ? ", cần lý do" : ""}
                    {action.when ? `, khi ${action.when.field} ${action.when.op} ${action.when.value}` : ""}
                  </li>
                ))}
              </Box>
            </Box>
          ))}
        </AccordionDetails>
      </Accordion>
      <Accordion disableGutters elevation={0} square>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">Mở rộng hoặc tự khai báo quy trình</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <GuideBlocks guide={GUIDE.process} />
          <Typography variant="caption" sx={{ display: "block", mt: 1.5, fontWeight: 700 }}>
            VÍ DỤ
          </Typography>
          <Box component="pre" sx={{ ...mono, m: 0, p: 1.25, bgcolor: "action.hover", borderRadius: 1, overflowX: "auto" }}>
            {EXAMPLE}
          </Box>
          <Typography variant="caption" sx={{ display: "block", mt: 1.5, fontWeight: 700 }}>
            CÁC KHÓA CỦA QUY TRÌNH
          </Typography>
          <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
            {processKeys.map(([path, help]) => (
              <li key={path}>
                <span style={mono}>{path.replace("entities.process.", "")}</span>: {help}
              </li>
            ))}
          </Box>
        </AccordionDetails>
      </Accordion>
      {(["general", "roles", "units", "entities", "fields", "access"] as Section[]).map((section) => (
        <Accordion key={section} disableGutters elevation={0} square>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="subtitle2">{PART_TITLE[section]}</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Typography variant="body2">{GUIDE[section].summary}</Typography>
            <GuideBlocks guide={GUIDE[section]} />
          </AccordionDetails>
        </Accordion>
      ))}
    </Card>
  );
}
