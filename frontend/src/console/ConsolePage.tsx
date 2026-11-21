// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useEffect, useRef, useState } from "react";
import { Alert, Box, MenuItem, Skeleton, Snackbar, Tab, Tabs, TextField } from "@mui/material";
import AccountTreeOutlinedIcon from "@mui/icons-material/AccountTreeOutlined";
import AutoAwesomeMosaicOutlinedIcon from "@mui/icons-material/AutoAwesomeMosaicOutlined";
import EditNoteOutlinedIcon from "@mui/icons-material/EditNoteOutlined";
import FolderOutlinedIcon from "@mui/icons-material/FolderOutlined";
import LayersOutlinedIcon from "@mui/icons-material/LayersOutlined";
import ApiOutlinedIcon from "@mui/icons-material/ApiOutlined";
import PeopleOutlineIcon from "@mui/icons-material/PeopleOutline";
import SyncAltIcon from "@mui/icons-material/SyncAlt";
import { Title } from "react-admin";
import PageHeader from "../shell/PageHeader";
import { getProjectScope, getSchema, listTemplates, type ProjectScope, type Template } from "./api";
import { describeSchema } from "./formGuide";
import EditorTab from "./EditorTab";
import ModelsTab from "./ModelsTab";
import { registerModelSchema } from "./monacoSetup";
import OutboxTab from "./OutboxTab";
import ProjectMembersPanel from "./ProjectMembersPanel";
import ProjectsTab from "./ProjectsTab";
import TemplatesTab from "./TemplatesTab";
import AppsTab from "./AppsTab";
import DataCatalogTab from "./DataCatalogTab";
import UsersTab from "./UsersTab";
import { guardUnsavedWork } from "../shell/locale";
import { t } from "../shell/t";

const BLANK = `# SPDX-License-Identifier: Apache-2.0
format: 1
name: my_model
roles:
  - name: USER
entities:
  item:
    fields:
      title: {type: text, label: ${t("Tiêu đề")}, required: true}
    access: {USER: all}
`;

// The whole control panel for the platform administrator; an administrator of projects gets only
// the people of those projects.
export default function ConsolePage() {
  const [scope, setScope] = useState<ProjectScope | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    getProjectScope().then(setScope, (e: unknown) => setError(e instanceof Error ? e.message : t("Không đọc được dữ liệu của bảng điều khiển")));
  }, []);

  if (error) return <Alert severity="error">{error}</Alert>;
  if (!scope) return <Skeleton height={48} />;
  if (scope.global) return <FullConsole />;
  const administered = scope.projects.filter((project) => project.level === "ADMIN");
  if (administered.length === 0) return <Alert severity="warning">{t("Tài khoản này không quản trị dự án nào.")}</Alert>;
  return <ScopedConsole projects={administered} />;
}

// What an administrator of projects does: gives the roles of their models and keeps the members.
function ScopedConsole({ projects }: { projects: ProjectScope["projects"] }) {
  const [tab, setTab] = useState(0);
  const [project, setProject] = useState(projects[0].name);
  return (
    <Box sx={{ contain: "inline-size" }}>
      <Title title={t("Bảng điều khiển")} />
      <PageHeader title={t("Bảng điều khiển")} subtitle={t("Quản lý người dùng của các dự án bạn quản trị.")} />
      <Tabs value={tab} onChange={(_, next) => setTab(next)} sx={{ mb: 2.5, borderBottom: 1, borderColor: "divider" }}>
        <Tab icon={<PeopleOutlineIcon fontSize="small" />} iconPosition="start" label={t("Người dùng")} />
        <Tab icon={<FolderOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Thành viên dự án")} />
      </Tabs>
      {tab === 0 && <UsersTab limited />}
      {tab === 1 && (
        <Box>
          {projects.length > 1 && (
            <TextField select size="small" label={t("Dự án")} value={project} onChange={(event) => setProject(event.target.value)} sx={{ width: 260, maxWidth: "100%", mb: 2 }}>
              {projects.map((item) => (
                <MenuItem key={item.name} value={item.name}>
                  {item.label}
                </MenuItem>
              ))}
            </TextField>
          )}
          <ProjectMembersPanel project={project} global={false} />
        </Box>
      )}
    </Box>
  );
}

// The control panel of the implementer: pick a template, write the model, see the plan, apply.
function FullConsole() {
  const [tab, setTab] = useState(0);
  const [schema, setSchema] = useState<Record<string, unknown> | null>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [text, setText] = useState(BLANK);
  const [source, setSource] = useState(t("Tệp mới"));
  const [refresh, setRefresh] = useState(0);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  // The text as it was loaded or last applied; a different text in the editor is work not yet applied.
  const textRef = useRef(text);
  const baselineRef = useRef(text);
  textRef.current = text;

  useEffect(() => guardUnsavedWork(() => textRef.current !== baselineRef.current), []);

  useEffect(() => {
    Promise.all([getSchema(), listTemplates()]).then(
      ([loadedSchema, loadedTemplates]) => {
        const described = describeSchema(loadedSchema);
        registerModelSchema(described);
        setSchema(described);
        setTemplates(loadedTemplates);
      },
      (e: unknown) => setError(e instanceof Error ? e.message : t("Không đọc được dữ liệu của bảng điều khiển")),
    );
  }, []);

  const edit = (content: string, label: string) => {
    baselineRef.current = content;
    setText(content);
    setSource(label);
    setTab(2);
  };

  if (error) return <Alert severity="error">{error}</Alert>;

  // The layout grows to the narrowest width of its content, so the page stays as wide as the screen.
  return (
    <Box sx={{ contain: "inline-size" }}>
      <Title title={t("Bảng điều khiển")} />
      <PageHeader
        title={t("Bảng điều khiển")}
        subtitle={t("Dựng, thay đổi và theo dõi các mô hình nghiệp vụ. Sửa tệp mô hình là đổi nghiệp vụ, không đổi mã.")}
      />
      <Tabs
        value={tab}
        onChange={(_, next) => setTab(next)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{ mb: 2.5, borderBottom: 1, borderColor: "divider" }}
      >
        <Tab icon={<LayersOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Mô hình đang chạy")} />
        <Tab icon={<AutoAwesomeMosaicOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Thư viện mẫu")} />
        <Tab icon={<EditNoteOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Soạn và áp dụng")} />
        <Tab icon={<SyncAltIcon fontSize="small" />} iconPosition="start" label={t("Hàng đợi sự kiện")} />
        <Tab icon={<PeopleOutlineIcon fontSize="small" />} iconPosition="start" label={t("Người dùng")} />
        <Tab icon={<ApiOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Ứng dụng ngoài")} />
        <Tab icon={<AccountTreeOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Danh mục dữ liệu")} />
        <Tab icon={<FolderOutlinedIcon fontSize="small" />} iconPosition="start" label={t("Dự án")} />
      </Tabs>
      {!schema && (
        <Box>
          <Skeleton height={48} />
          <Skeleton height={48} />
          <Skeleton height={48} />
        </Box>
      )}
      {schema && tab === 0 && (
        <ModelsTab
          refresh={refresh}
          onBrowse={() => setTab(1)}
          onOpen={(model, content, version) => edit(content, t("{model}, phiên bản {version}", { model, version }))}
          onChanged={(message) => {
            setNote(message);
            setRefresh((value) => value + 1);
          }}
        />
      )}
      {schema && tab === 1 && (
        <TemplatesTab templates={templates} onUse={(template) => edit(template.content, t("Mẫu {name}", { name: template.name }))} />
      )}
      {schema && tab === 2 && (
        <EditorTab
          text={text}
          source={source}
          schema={schema}
          onText={setText}
          onApplied={(message) => {
            baselineRef.current = textRef.current;
            setNote(message);
            setRefresh((value) => value + 1);
            setTab(0);
          }}
        />
      )}
      {schema && tab === 3 && <OutboxTab />}
      {schema && tab === 4 && <UsersTab />}
      {schema && tab === 5 && <AppsTab />}
      {schema && tab === 6 && <DataCatalogTab />}
      {schema && tab === 7 && <ProjectsTab />}
      <Snackbar open={note !== null} autoHideDuration={6000} onClose={() => setNote(null)} message={note ?? ""} />
    </Box>
  );
}
