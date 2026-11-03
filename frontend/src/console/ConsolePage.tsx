// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Alert, Box, Snackbar, Tab, Tabs, Typography } from "@mui/material";
import { Title } from "react-admin";
import { getSchema, listFamilies, listTemplates, type Family, type Template } from "./api";
import EditorTab from "./EditorTab";
import ModelsTab from "./ModelsTab";
import OutboxTab from "./OutboxTab";
import { useModelSchema } from "./monacoSetup";
import TemplatesTab from "./TemplatesTab";

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
  const [families, setFamilies] = useState<Family[]>([]);
  const [templates, setTemplates] = useState<Template[]>([]);
  const [text, setText] = useState(BLANK);
  const [source, setSource] = useState("Tệp mới");
  const [refresh, setRefresh] = useState(0);
  const [note, setNote] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    Promise.all([getSchema(), listFamilies(), listTemplates()]).then(
      ([loadedSchema, loadedFamilies, loadedTemplates]) => {
        useModelSchema(loadedSchema);
        setSchema(loadedSchema);
        setFamilies(loadedFamilies);
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
  if (!schema) return <Typography sx={{ p: 2 }}>Đang tải...</Typography>;

  return (
    <Box sx={{ p: 2 }}>
      <Title title="Bảng điều khiển" />
      <Typography variant="h5" sx={{ mb: 1 }}>
        Bảng điều khiển
      </Typography>
      <Tabs value={tab} onChange={(_, next) => setTab(next)} sx={{ mb: 2 }}>
        <Tab label="Mô hình đang chạy" />
        <Tab label="Thư viện mẫu" />
        <Tab label="Soạn và áp dụng" />
        <Tab label="Hàng đợi sự kiện" />
      </Tabs>
      {tab === 0 && (
        <ModelsTab
          refresh={refresh}
          onOpen={(model, content, version) => edit(content, `${model}, phiên bản ${version}`)}
          onChanged={(message) => {
            setNote(message);
            setRefresh((value) => value + 1);
          }}
        />
      )}
      {tab === 1 && <TemplatesTab templates={templates} families={families} onUse={(template) => edit(template.content, `Mẫu ${template.name}`)} />}
      {tab === 2 && (
        <EditorTab
          text={text}
          source={source}
          schema={schema}
          families={families}
          onText={setText}
          onApplied={(message) => {
            setNote(message);
            setRefresh((value) => value + 1);
            setTab(0);
          }}
        />
      )}
      {tab === 3 && <OutboxTab />}
      <Snackbar open={note !== null} autoHideDuration={6000} onClose={() => setNote(null)} message={note ?? ""} />
    </Box>
  );
}
