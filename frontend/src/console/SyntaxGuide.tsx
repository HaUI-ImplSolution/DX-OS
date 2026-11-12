// SPDX-License-Identifier: Apache-2.0
import { Accordion, AccordionDetails, AccordionSummary, Box, Card, Chip, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import GuideBlocks from "./GuideBlocks";
import { GUIDE, SCHEMA_HELP, type Section } from "./formGuide";
import { FAMILY_LABEL } from "./labels";
import { useFamilies } from "./useFamilies";
import { t, ta } from "../shell/t";

const PART_TITLE: Record<Section, string> = {
  general: t("Tên mô hình và đích gửi lịch sử"),
  roles: t("Vai"),
  units: t("Đơn vị"),
  entities: t("Thực thể"),
  fields: t("Trường"),
  access: t("Quyền theo vai"),
  process: t("Quy trình"),
};

const EXAMPLE = [
  "process:",
  `  family: work_item          # ${t("khung có sẵn; bỏ dòng này nếu tự khai đủ states và actions")}`,
  `  handler: [AGENT, LEAD]     # ${t("một vai, hoặc danh sách vai")}`,
  "  states:",
  `    escalated: {label: ${t("Đã chuyển cấp")}}`,
  "  actions:",
  `    escalate: {label: ${t("Chuyển cấp")}, from: [in_progress], to: escalated, role: LEAD}`,
  `    resolve: {require: [resolution]}   # ${t("ghi đè hành động của khung")}`,
  `    reopen: null                       # ${t("bỏ hành động của khung")}`,
  `  deadline: 72h              # ${t("thời gian xử lý, quá hạn thì bị đánh dấu")}`,
  `  escalate:                  # ${t("quá hạn thì báo thêm vai khác, tối đa 5 cấp")}`,
  "    - {after: 24h, to: MANAGER}",
  `    - {after: 48h, to: [DIRECTOR], repeat: {every: 12h, times: 3}}   # ${t("nhắc lại 3 lần")}`,
  "  auto_close: {after: 7d, action: confirm}",
].join("\n");

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
        <Typography variant="h6">{t("Viết được gì trong tệp")}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t("Hệ thống chấp nhận những khóa nào, mở rộng được đến đâu và tự tạo được gì. Rê chuột lên một khóa trong trình soạn YAML cũng hiện giải thích.")}
        </Typography>
      </Box>
      <Accordion disableGutters elevation={0} square>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t("Khung quy trình có sẵn và tên có thể ghi đè")}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          {families.length === 0 && <Typography variant="body2">{t("Đang tải…")}</Typography>}
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
                    label={<span style={mono}>{state.name}{state.terminal ? t(" (cuối)") : ""}</span>}
                    title={t(state.label)}
                  />
                ))}
              </Box>
              <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
                {family.actions.map((action) => (
                  <li key={action.name}>
                    <span style={mono}>{action.name}</span> ({ta(action.label)}): {t("{from} → {to}, do {who}", { from: action.from.join(", "), to: action.to, who: action.by === "OWNER" ? t("người nộp") : t("vai xử lý") })}
                    {action.needsNote ? t(", cần lý do") : ""}
                    {action.when ? t(", khi {field} {op} {value}", { field: action.when.field, op: action.when.op, value: action.when.value }) : ""}
                  </li>
                ))}
              </Box>
            </Box>
          ))}
        </AccordionDetails>
      </Accordion>
      <Accordion disableGutters elevation={0} square>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t("Mở rộng hoặc tự khai báo quy trình")}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <GuideBlocks guide={GUIDE.process} />
          <Typography variant="caption" sx={{ display: "block", mt: 1.5, fontWeight: 700 }}>
            {t("VÍ DỤ")}
          </Typography>
          <Box component="pre" sx={{ ...mono, m: 0, p: 1.25, bgcolor: "action.hover", borderRadius: 1, overflowX: "auto" }}>
            {EXAMPLE}
          </Box>
          <Typography variant="caption" sx={{ display: "block", mt: 1.5, fontWeight: 700 }}>
            {t("CÁC KHÓA CỦA QUY TRÌNH")}
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
