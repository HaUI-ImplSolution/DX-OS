// SPDX-License-Identifier: Apache-2.0
import { useEffect, useState } from "react";
import PrintOutlinedIcon from "@mui/icons-material/PrintOutlined";
import { Menu, MenuItem } from "@mui/material";
import { Button, useNotify, useRecordContext } from "react-admin";
import { listDocuments, printDocument } from "./api";
import type { EntityMeta, PrintableDocument } from "./types";
import { t } from "../shell/t";

// Saves the PDF of the record made from one of the print templates of its entity.
export default function PrintButton({ model, entity }: { model: string; entity: EntityMeta }) {
  const record = useRecordContext();
  const notify = useNotify();
  const [documents, setDocuments] = useState<PrintableDocument[]>([]);
  const [anchor, setAnchor] = useState<HTMLElement | null>(null);
  const [busy, setBusy] = useState(false);
  const hasTemplates = (entity.documents?.length ?? 0) > 0;

  // The state of the record decides which templates it may be printed with, so they are read again after a change.
  useEffect(() => {
    if (!hasTemplates || !record) return;
    listDocuments(model, entity.name, record.id).then(setDocuments, () => setDocuments([]));
  }, [model, entity.name, hasTemplates, record?.id, record?.status, record?.version]);

  const available = documents.filter((document) => document.available);
  if (available.length === 0) return null;

  const print = async (document: PrintableDocument) => {
    setAnchor(null);
    setBusy(true);
    try {
      const { blob, name } = await printDocument(model, entity.name, record!.id, document.name);
      const url = URL.createObjectURL(blob);
      const link = window.document.createElement("a");
      link.href = url;
      link.download = name;
      link.click();
      URL.revokeObjectURL(url);
    } catch (error) {
      notify(error instanceof Error ? error.message : t("Không in được"), { type: "error" });
    } finally {
      setBusy(false);
    }
  };

  if (available.length === 1) {
    return (
      <Button label={t("In: {name}", { name: available[0].label })} onClick={() => print(available[0])} disabled={busy}>
        <PrintOutlinedIcon />
      </Button>
    );
  }
  return (
    <>
      <Button label={t("In")} onClick={(event) => setAnchor(event.currentTarget)} disabled={busy}>
        <PrintOutlinedIcon />
      </Button>
      <Menu anchorEl={anchor} open={anchor !== null} onClose={() => setAnchor(null)}>
        {available.map((document) => (
          <MenuItem key={document.name} onClick={() => print(document)}>
            {document.label}
          </MenuItem>
        ))}
      </Menu>
    </>
  );
}
