// SPDX-License-Identifier: Apache-2.0
import ContentCopyOutlinedIcon from "@mui/icons-material/ContentCopyOutlined";
import { Box, IconButton, Tooltip } from "@mui/material";
import { useNotify, useRecordContext } from "react-admin";
import { t } from "../shell/t";

// The readable code of a record, with a button that copies it.
export default function CodeField({ source }: { source: string }) {
  const record = useRecordContext();
  const notify = useNotify();
  const code = record?.[source];
  if (typeof code !== "string" || code === "") return <>—</>;
  const copy = () => {
    navigator.clipboard.writeText(code).then(
      () => notify(t("Đã sao chép mã"), { type: "info" }),
      () => notify(t("Không sao chép được"), { type: "warning" }),
    );
  };
  return (
    <Box component="span" sx={{ display: "inline-flex", alignItems: "center" }}>
      {code}
      <Tooltip title={t("Sao chép mã")}>
        <IconButton size="small" aria-label={t("Sao chép mã")} onClick={copy} sx={{ ml: 0.5 }}>
          <ContentCopyOutlinedIcon fontSize="inherit" />
        </IconButton>
      </Tooltip>
    </Box>
  );
}
