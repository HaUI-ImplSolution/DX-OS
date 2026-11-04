// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import { Button } from "@mui/material";
import { useRecordContext } from "react-admin";
import { capabilities } from "./api";
import type { EntityMeta, ModelMeta } from "../admin/types";
import AssistantRecord from "./AssistantRecord";
import type { RecordOperation } from "./AssistantResult";

export function useAssistantEnabled() {
  const [enabled, setEnabled] = useState(false);
  useEffect(() => {
    let active = true;
    capabilities().then(
      (value) => {
        if (active) setEnabled(value.enabled);
      },
      () => {},
    );
    return () => {
      active = false;
    };
  }, []);
  return enabled;
}

export default function AssistantEntry({ model, entity }: { model: ModelMeta; entity: EntityMeta }) {
  const record = useRecordContext();
  const enabled = useAssistantEnabled();
  const [operation, setOperation] = useState<RecordOperation | null>(null);
  if (!enabled || !record?.id) return null;
  return (
    <>
      <Button size="small" onClick={() => setOperation("summarize")}>
        Tóm tắt diễn biến
      </Button>
      <Button size="small" onClick={() => setOperation("similar")}>
        Tìm trường hợp tương tự
      </Button>
      {operation && (
        <AssistantRecord
          key={`${record.id}/${operation}`}
          model={model}
          entity={entity}
          recordId={Number(record.id)}
          initialOperation={operation}
          onClose={() => setOperation(null)}
        />
      )}
    </>
  );
}
