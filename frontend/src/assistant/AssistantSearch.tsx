// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import {
  Accordion,
  AccordionDetails,
  AccordionSummary,
  Alert,
  Box,
  Button,
  Checkbox,
  CircularProgress,
  FormControlLabel,
  Stack,
  TextField,
  Typography,
} from "@mui/material";
import ExpandMoreIcon from "@mui/icons-material/ExpandMore";
import { useListContext } from "react-admin";
import type { EntityMeta, ModelMeta } from "../admin/types";
import { capabilities, type Capabilities } from "./api";
import AssistantResult, { type RecordOperation } from "./AssistantResult";
import AssistantRecord from "./AssistantRecord";
import useAssistantQuery from "./useAssistantQuery";

export default function AssistantSearch({
  model,
  entity,
  filters = {},
  standalone = false,
}: {
  model: ModelMeta;
  entity: EntityMeta;
  filters?: Record<string, string>;
  standalone?: boolean;
}) {
  const [config, setConfig] = useState<Capabilities | null>(null);
  const [unavailable, setUnavailable] = useState(false);
  const [query, setQuery] = useState("");
  const [interpret, setInterpret] = useState(false);
  const [selected, setSelected] = useState<{ id: number; operation: RecordOperation } | null>(null);
  const task = useAssistantQuery(JSON.stringify([model.name, entity.name, filters, query, interpret]));
  useEffect(() => {
    let active = true;
    capabilities().then(
      (value) => {
        if (active) setConfig(value);
      },
      () => {
        if (active) setUnavailable(true);
      },
    );
    return () => {
      active = false;
    };
  }, []);
  if (!config?.enabled)
    return standalone && (config || unavailable) ? (
      <Alert severity="info">
        {unavailable ? "Không đọc được trạng thái trợ lý." : "Trợ lý chưa được bật."} Bạn vẫn có thể dùng danh sách và bộ lọc hồ sơ.
      </Alert>
    ) : null;

  return (
    <Box sx={{ my: 2, px: standalone ? 0 : 2, minWidth: 0, width: "100%", boxSizing: "border-box", contain: "inline-size" }}>
      <Box
        component="form"
        onSubmit={(event) => {
          event.preventDefault();
          void task.run({ operation: "find", model: model.name, entity: entity.name, query, filters, interpretQuery: interpret });
        }}
      >
        <Stack spacing={1.5}>
          <Stack direction={{ xs: "column", sm: "row" }} spacing={1}>
            <TextField
              fullWidth
              label="Tìm hồ sơ theo nội dung"
              placeholder="Mô tả nội dung bạn cần tìm…"
              value={query}
              onChange={(event) => setQuery(event.target.value)}
              inputProps={{ maxLength: 1000 }}
            />
            <Button type="submit" variant="outlined" disabled={task.busy} sx={{ flexShrink: 0 }}>
              Tìm theo nội dung
            </Button>
            {task.busy && <Button onClick={task.cancel}>Hủy chờ</Button>}
          </Stack>
          <Typography variant="caption" color="text.secondary">
            Tìm trong {entity.label.toLocaleLowerCase("vi-VN")} bạn được xem
            {Object.keys(filters).length ? ", giữ bộ lọc danh sách hiện tại" : ""}. Lọc trạng thái và xem số lượng bằng các bộ lọc thông
            thường của danh sách.
          </Typography>
          {config.generationEnabled && (
            <Accordion disableGutters>
              <AccordionSummary expandIcon={<ExpandMoreIcon />}>
                <Typography variant="body2">Tùy chọn nâng cao</Typography>
              </AccordionSummary>
              <AccordionDetails>
                <FormControlLabel
                  control={<Checkbox checked={interpret} onChange={(event) => setInterpret(event.target.checked)} />}
                  label="Nhờ AI diễn giải câu hỏi thành điều kiện lọc"
                />
              </AccordionDetails>
            </Accordion>
          )}
          {task.busy && (
            <Box role="status" sx={{ display: "flex", alignItems: "center", gap: 1 }}>
              <CircularProgress size={20} />
              Đang tìm nội dung liên quan…
            </Box>
          )}
          {task.error && <Alert severity="error">{task.error} Bạn vẫn có thể dùng danh sách bên dưới.</Alert>}
          {task.result && (
            <>
              <Box sx={{ display: "flex", justifyContent: "space-between", flexWrap: "wrap", gap: 1 }}>
                <Typography variant="subtitle1" fontWeight={700}>
                  Kết quả tìm theo nội dung
                </Typography>
                <Button size="small" onClick={task.cancel}>
                  Đóng kết quả
                </Button>
              </Box>
              <AssistantResult
                result={task.result}
                model={model}
                entity={entity}
                onSelect={(id, operation) => setSelected({ id, operation })}
              />
            </>
          )}
        </Stack>
      </Box>
      {task.result && !standalone && (
        <Typography variant="subtitle2" sx={{ mt: 3 }}>
          Danh sách theo bộ lọc
        </Typography>
      )}
      {selected && (
        <AssistantRecord
          key={`${selected.id}/${selected.operation}`}
          model={model}
          entity={entity}
          recordId={selected.id}
          initialOperation={selected.operation}
          onClose={() => setSelected(null)}
        />
      )}
    </Box>
  );
}

export function AssistantListSearch({ model, entity }: { model: ModelMeta; entity: EntityMeta }) {
  const { filterValues } = useListContext();
  const filters: Record<string, string> = {};
  Object.entries(filterValues ?? {}).forEach(([field, value]) => {
    if (value === null || value === undefined || value === "") return;
    filters[Array.isArray(value) ? `${field}.in` : field.includes(".") ? field : `${field}.eq`] = Array.isArray(value)
      ? value.join(",")
      : String(value);
  });
  return <AssistantSearch key={`${model.name}/${entity.name}`} model={model} entity={entity} filters={filters} />;
}
