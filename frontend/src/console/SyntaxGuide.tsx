// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { Accordion, AccordionDetails, AccordionSummary, Box, Card, Chip, Typography } from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import GuideBlocks from "./GuideBlocks";
import { GUIDE, SCHEMA_HELP, type Section } from "./formGuide";
import { FAMILY_LABEL } from "./labels";
import { useFamilies } from "./useFamilies";
import type { NavTarget, ProcessPart } from "./navModel";
import { conditionSource } from "../admin/condition";
import { t, ta } from "../shell/t";

const PART_TITLE: Record<Section, string> = {
  general: t("Tên mô hình và kết nối gửi sự kiện"),
  roles: t("Vai trò"),
  units: t("Đơn vị"),
  entities: t("Thực thể"),
  fields: t("Trường dữ liệu"),
  access: t("Quyền truy cập theo vai trò"),
  process: t("Quy trình"),
};

const EXAMPLE = [
  "process:",
  `  family: work_item          # ${t("mẫu quy trình có sẵn; bỏ dòng này nếu tự khai đủ states và actions")}`,
  `  handler: [AGENT, LEAD]     # ${t("một vai, hoặc danh sách vai")}`,
  "  states:",
  `    escalated: {label: ${t("Đã chuyển cấp")}}`,
  "  actions:",
  `    escalate: {label: ${t("Chuyển cấp")}, from: [in_progress], to: escalated, role: LEAD}`,
  `    resolve: {require: [resolution]}   # ${t("ghi đè hành động của mẫu quy trình")}`,
  `    reopen: null                       # ${t("bỏ hành động của mẫu quy trình")}`,
  `  deadline: 72h              # ${t("thời gian xử lý, quá hạn thì bị đánh dấu")}`,
  `  calendar: office           # ${t("hạn chỉ đếm trong giờ làm của lịch này, khai ở calendars đầu tệp")}`,
  `  escalate:                  # ${t("quá hạn thì báo thêm vai khác, tối đa 5 cấp")}`,
  "    - {after: 24h, to: MANAGER}",
  `    - {after: 48h, to: [DIRECTOR], repeat: {every: 12h, times: 3}}   # ${t("nhắc lại 3 lần")}`,
  "  auto_close: {after: 7d, action: confirm}",
].join("\n");

const CALENDAR_EXAMPLE = [
  `calendars:                   # ${t("khai ở đầu tệp, ngang với roles")}`,
  "  office:",
  `    days: [mon, tue, wed, thu, fri]                # ${t("các ngày làm")}`,
  `    hours: ['08:00-12:00', '13:30-17:30']          # ${t("giờ làm, nhiều khoảng để có nghỉ trưa")}`,
  `    holidays: [2026-12-25, 2027-01-01]             # ${t("ngày nghỉ, không tính giờ làm")}`,
].join("\n");

const mono = { fontFamily: "ui-monospace, SFMono-Regular, Menlo, monospace", fontSize: 12 };

// What the guide shows for the part of the form being edited: the parts of the file that part is
// written in, the keys of one step of a process, or the keys of the assistant tasks.
export interface Focus {
  part: string;
  sections: Section[];
  process?: ProcessPart;
  assistant?: boolean;
}

// The keys of `process` each step of the process page edits.
const STEP_KEYS: Record<ProcessPart, string[]> = {
  overview: ["family", "handler", "initial", "allow_self_decision", "booking"],
  states: ["states", "initial"],
  actions: ["actions"],
  deadline: ["deadline", "calendar", "escalate", "auto_close"],
  automation: ["after"],
};

export function focusOf(nav: NavTarget): Focus {
  if (nav.kind === "general") return { part: PART_TITLE.general, sections: ["general"] };
  if (nav.kind === "roles") return { part: PART_TITLE.roles, sections: ["roles", "access"] };
  if (nav.kind === "units") return { part: PART_TITLE.units, sections: ["units"] };
  if (nav.kind === "entities") return { part: PART_TITLE.entities, sections: ["entities"] };
  if (nav.part === "fields") return { part: PART_TITLE.fields, sections: ["fields"] };
  if (nav.part === "access") return { part: PART_TITLE.access, sections: ["access"] };
  if (nav.part === "ai") return { part: t("Trợ lý nghiệp vụ"), sections: [], assistant: true };
  if (nav.part === "process") return { part: PART_TITLE.process, sections: [], process: nav.step ?? "overview" };
  return { part: PART_TITLE.entities, sections: ["entities"] };
}

// The range of what a model file accepts, shown beside the editor: the parts of the file, the
// states and actions of the ready made families (the names a file can override), and the keys of a
// process with what each one does.
export default function SyntaxGuide({ focus }: { focus?: Focus }) {
  const families = useFamilies();
  const step = focus?.process;
  const processKeys = Object.entries(SCHEMA_HELP).filter(([path]) => {
    if (!path.startsWith("entities.process.")) return false;
    const key = path.slice("entities.process.".length).split(".")[0];
    return !step || STEP_KEYS[step].includes(key);
  });
  const assistantKeys = Object.entries(SCHEMA_HELP).filter(([path]) => path.startsWith("entities.ai"));
  // Without a focus the guide shows everything; with one it shows only what the part being edited is written in.
  const showFamilies = !focus || step === "overview" || step === "states" || step === "actions";
  const showProcess = !focus || step !== undefined;
  const sections = (focus ? focus.sections : (["general", "roles", "units", "entities", "fields", "access"] as Section[]));
  const open = focus !== undefined;
  return (
    <Card sx={{ p: 0, overflow: "hidden" }}>
      <Box sx={{ px: 2.5, pt: 2, pb: 1 }}>
        <Typography variant="h6">{t("Viết được gì trong tệp")}</Typography>
        <Typography variant="body2" color="text.secondary">
          {t("Hệ thống chấp nhận những khóa nào, mở rộng được đến đâu và tự tạo được gì. Rê chuột lên một khóa trong trình soạn YAML cũng hiện giải thích.")}
        </Typography>
        {focus && (
          <Typography variant="body2" sx={{ mt: 0.5, fontWeight: 600 }}>
            {t("Phần đang sửa: {part}", { part: focus.part })}
          </Typography>
        )}
      </Box>
      {showFamilies && (
      <Accordion disableGutters elevation={0} square defaultExpanded={open}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t("Mẫu quy trình có sẵn và tên có thể ghi đè")}</Typography>
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
                    {action.when ? t(", khi {condition}", { condition: conditionSource(action.when) }) : ""}
                  </li>
                ))}
              </Box>
            </Box>
          ))}
        </AccordionDetails>
      </Accordion>
      )}
      {showProcess && (
      <Accordion disableGutters elevation={0} square defaultExpanded={open}>
        <AccordionSummary expandIcon={<ExpandMoreIcon />}>
          <Typography variant="subtitle2">{t("Mở rộng hoặc tự khai báo quy trình")}</Typography>
        </AccordionSummary>
        <AccordionDetails>
          <GuideBlocks guide={GUIDE.process} />
          {(!step || step === "overview" || step === "states" || step === "actions" || step === "deadline") && (
            <Typography variant="caption" sx={{ display: "block", mt: 1.5, fontWeight: 700 }}>
              {t("VÍ DỤ")}
            </Typography>
          )}
          {(!step || step === "overview" || step === "states" || step === "actions") && (
            <Box component="pre" sx={{ ...mono, m: 0, p: 1.25, bgcolor: "action.hover", borderRadius: 1, overflowX: "auto" }}>
              {EXAMPLE}
            </Box>
          )}
          {(!step || step === "deadline") && (
            <Box component="pre" sx={{ ...mono, m: 0, mt: 1, p: 1.25, bgcolor: "action.hover", borderRadius: 1, overflowX: "auto" }}>
              {CALENDAR_EXAMPLE}
            </Box>
          )}
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
      )}
      {focus?.assistant && (
        <Accordion disableGutters elevation={0} square defaultExpanded>
          <AccordionSummary expandIcon={<ExpandMoreIcon />}>
            <Typography variant="subtitle2">{t("Trợ lý nghiệp vụ")}</Typography>
          </AccordionSummary>
          <AccordionDetails>
            <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
              {assistantKeys.map(([path, help]) => (
                <li key={path}>
                  <span style={mono}>{path.replace("entities.", "")}</span>: {help}
                </li>
              ))}
            </Box>
          </AccordionDetails>
        </Accordion>
      )}
      {sections.map((section) => (
        <Accordion key={section} disableGutters elevation={0} square defaultExpanded={open}>
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
