// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import { Link, useLocation, useSearchParams } from "react-router-dom";
import { Alert, Box, Button, MenuItem, Stack, TextField, Typography } from "@mui/material";
import { resourceName, type ModelMeta } from "../admin/types";
import PageHeader from "../shell/PageHeader";
import AssistantSearch from "./AssistantSearch";
import AssistantRecord from "./AssistantRecord";

export default function AssistantPage({ models }: { models: ModelMeta[] }) {
  const { search } = useLocation();
  return <AssistantWorkspace key={search} models={models} />;
}

function AssistantWorkspace({ models }: { models: ModelMeta[] }) {
  const [params] = useSearchParams();
  const options = models.flatMap((model) => model.entities.map((entity) => ({ model, entity })));
  const requested = options.find(({ model, entity }) => model.name === params.get("model") && entity.name === params.get("entity"));
  const [selection, setSelection] = useState(() => {
    const first = requested ?? options.find(({ model }) => model.name === params.get("model")) ?? options[0];
    return first ? resourceName(first.model.name, first.entity.name) : "";
  });
  const context = options.find(({ model, entity }) => resourceName(model.name, entity.name) === selection);
  const id = Number(params.get("recordId"));
  const [deepLinkOpen, setDeepLinkOpen] = useState(Boolean(requested && Number.isSafeInteger(id) && id > 0));

  return (
    <Box
      sx={{ p: { xs: 1, sm: 3 }, width: "100%", minWidth: 0, boxSizing: "border-box", contain: "inline-size", maxWidth: 1000, mx: "auto" }}
    >
      <PageHeader title="Trợ lý hồ sơ" subtitle="Tìm nội dung liên quan, chọn hồ sơ để đọc diễn biến hoặc tham khảo trường hợp tương tự." />
      <Stack spacing={2}>
        {params.has("entity") && !requested && (
          <Alert severity="info">Loại hồ sơ trong liên kết không tồn tại hoặc bạn không có quyền xem. Hãy chọn lại bên dưới.</Alert>
        )}
        {options.length === 0 ? (
          <Alert severity="info">Bạn chưa có loại hồ sơ được phép tra cứu.</Alert>
        ) : (
          <>
            <TextField
              select
              fullWidth
              label="Bạn muốn tìm trong loại hồ sơ nào?"
              value={selection}
              onChange={(event) => {
                setDeepLinkOpen(false);
                setSelection(event.target.value);
              }}
            >
              {options.map(({ model, entity }) => (
                <MenuItem key={resourceName(model.name, entity.name)} value={resourceName(model.name, entity.name)}>
                  {entity.label}
                  {models.length > 1 ? ` · ${model.name.replace(/_/g, " ")}` : ""}
                </MenuItem>
              ))}
            </TextField>
            {context && (
              <>
                <Box>
                  <Button component={Link} to={`/${selection}`}>
                    Mở danh sách {context.entity.label.toLocaleLowerCase("vi-VN")}
                  </Button>
                </Box>
                <Typography variant="body2" color="text.secondary">
                  Bạn cũng có thể tìm ngay trong danh sách, hoặc dùng nút tóm tắt và tìm tương tự khi mở một hồ sơ.
                </Typography>
                <AssistantSearch key={selection} model={context.model} entity={context.entity} standalone />
                {deepLinkOpen && (
                  <AssistantRecord
                    key={`${selection}/${id}`}
                    model={context.model}
                    entity={context.entity}
                    recordId={id}
                    initialOperation={params.get("operation") === "similar" ? "similar" : "summarize"}
                    autoRun={false}
                    onClose={() => setDeepLinkOpen(false)}
                  />
                )}
              </>
            )}
          </>
        )}
      </Stack>
    </Box>
  );
}
