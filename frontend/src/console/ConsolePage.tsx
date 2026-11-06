// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Alert, Box, Skeleton, Snackbar, Tab, Tabs } from "@mui/material";
import AutoAwesomeMosaicOutlinedIcon from "@mui/icons-material/AutoAwesomeMosaicOutlined";
import EditNoteOutlinedIcon from "@mui/icons-material/EditNoteOutlined";
import LayersOutlinedIcon from "@mui/icons-material/LayersOutlined";
import PeopleOutlineIcon from "@mui/icons-material/PeopleOutline";
import SyncAltIcon from "@mui/icons-material/SyncAlt";
import { Title } from "react-admin";
import PageHeader from "../shell/PageHeader";
import { getSchema, listTemplates, type Template } from "./api";
import EditorTab from "./EditorTab";
import ModelsTab from "./ModelsTab";
import { useModelSchema } from "./monacoSetup";
import OutboxTab from "./OutboxTab";
import TemplatesTab from "./TemplatesTab";
import UsersTab from "./UsersTab";

const BLANK = `# SPDX-License-Identifier: Apache-2.0
format: 0
name: my_model
roles:
  - name: USER
entities:
  item:
    fields:
      title: {type: text, label: Tiêu đề, required: true}
    access: {USER: all}
`;

// The control panel of the implementer: pick a template, write the model, see the plan, apply.
export default function ConsolePage() {
  const [tab, setTab] = useState(0);
  const [schema, setSchema] = useState<Record<string, unknown> | null>(null);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [text, setText] = useState(BLANK);
  const [source, setSource] = useState("Tệp mới");
  const [refresh, setRefresh] = useState(0);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([getSchema(), listTemplates()]).then(
      ([loadedSchema, loadedTemplates]) => {
        useModelSchema(loadedSchema);
        setSchema(loadedSchema);
        setTemplates(loadedTemplates);
      },
      (e: unknown) => setError(e instanceof Error ? e.message : "Không đọc được dữ liệu của bảng điều khiển"),
    );
  }, []);

  const edit = (content: string, label: string) => {
    setText(content);
    setSource(label);
    setTab(2);
  };

  if (error) return <Alert severity="error">{error}</Alert>;

  // The layout grows to the narrowest width of its content, so the page stays as wide as the screen.
  return (
    <Box sx={{ contain: "inline-size" }}>
      <Title title="Bảng điều khiển" />
      <PageHeader
        title="Bảng điều khiển"
        subtitle="Dựng, thay đổi và theo dõi các mô hình nghiệp vụ. Sửa tệp mô hình là đổi nghiệp vụ, không đổi mã."
      />
      <Tabs
        value={tab}
        onChange={(_, next) => setTab(next)}
        variant="scrollable"
        scrollButtons="auto"
        sx={{ mb: 2.5, borderBottom: 1, borderColor: "divider" }}
      >
        <Tab icon={<LayersOutlinedIcon fontSize="small" />} iconPosition="start" label="Mô hình đang chạy" />
        <Tab icon={<AutoAwesomeMosaicOutlinedIcon fontSize="small" />} iconPosition="start" label="Thư viện mẫu" />
        <Tab icon={<EditNoteOutlinedIcon fontSize="small" />} iconPosition="start" label="Soạn và áp dụng" />
        <Tab icon={<SyncAltIcon fontSize="small" />} iconPosition="start" label="Hàng đợi sự kiện" />
        <Tab icon={<PeopleOutlineIcon fontSize="small" />} iconPosition="start" label="Người dùng" />
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
          onOpen={(model, content, version) => edit(content, `${model}, phiên bản ${version}`)}
          onChanged={(message) => {
            setNote(message);
            setRefresh((value) => value + 1);
          }}
        />
      )}
      {schema && tab === 1 && (
        <TemplatesTab templates={templates} onUse={(template) => edit(template.content, `Mẫu ${template.name}`)} />
      )}
      {schema && tab === 2 && (
        <EditorTab
          text={text}
          source={source}
          schema={schema}
          onText={setText}
          onApplied={(message) => {
            setNote(message);
            setRefresh((value) => value + 1);
            setTab(0);
          }}
        />
      )}
      {schema && tab === 3 && <OutboxTab />}
      {schema && tab === 4 && <UsersTab />}
      <Snackbar open={note !== null} autoHideDuration={6000} onClose={() => setNote(null)} message={note ?? ""} />
    </Box>
  );
}
