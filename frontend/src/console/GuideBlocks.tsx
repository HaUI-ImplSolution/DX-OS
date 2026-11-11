// SPDX-License-Identifier: Apache-2.0
import { Box, Typography } from "@mui/material";
import type { Guide } from "./formGuide";
import { t } from "../shell/t";

function Block({ title, items }: { title: string; items: string[] }) {
  if (items.length === 0) return null;
  return (
    <Box sx={{ mt: 1 }}>
      <Typography variant="caption" sx={{ fontWeight: 700, textTransform: "uppercase", letterSpacing: 0.3 }}>
        {title}
      </Typography>
      <Box component="ul" sx={{ m: 0, pl: 2.5, fontSize: 13 }}>
        {items.map((item) => (
          <li key={item}>{item}</li>
        ))}
      </Box>
    </Box>
  );
}

// What a part of the model file accepts, what can be extended in it and what can be created.
export default function GuideBlocks({ guide }: { guide: Guide }) {
  return (
    <>
      <Block title={t("Hệ thống chấp nhận")} items={guide.accepts} />
      <Block title={t("Mở rộng được")} items={guide.extends} />
      <Block title={t("Tự tạo được")} items={guide.creates} />
    </>
  );
}
