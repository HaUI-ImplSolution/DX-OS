// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { useState } from "react";
import { Button, Dialog, DialogActions, DialogContent, DialogContentText, DialogTitle, ToggleButton, ToggleButtonGroup } from "@mui/material";
import { getLocale, hasUnsavedWork, setLocale, type Locale } from "./locale";
import { t } from "./t";

const VIETNAMESE = "Tiếng Việt"; // i18n-keep: a language is named in itself

// Switches the interface between Vietnamese and English; the page reloads in the chosen language,
// after a confirmation when a screen holds work that is not saved.
export default function LanguageSwitch() {
  const [pending, setPending] = useState<Locale | null>(null);

  const choose = (next: Locale) => (hasUnsavedWork() ? setPending(next) : setLocale(next));

  return (
    <>
      <ToggleButtonGroup
        exclusive
        size="small"
        value={getLocale()}
        onChange={(_, next: Locale | null) => next && choose(next)}
        aria-label="Language"
        sx={{ mr: 1, "& .MuiToggleButton-root": { px: 1, py: 0.25, fontSize: 12, fontWeight: 700, lineHeight: 1.6 } }}
      >
        <ToggleButton value="vi" aria-label={VIETNAMESE}>
          VI
        </ToggleButton>
        <ToggleButton value="en" aria-label="English">
          EN
        </ToggleButton>
      </ToggleButtonGroup>
      <Dialog open={pending !== null} onClose={() => setPending(null)}>
        <DialogTitle>{t("Đổi ngôn ngữ?")}</DialogTitle>
        <DialogContent>
          <DialogContentText>{t("Trang sẽ tải lại và bản đang soạn chưa áp dụng sẽ mất.")}</DialogContentText>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPending(null)}>{t("Ở lại")}</Button>
          <Button color="warning" onClick={() => pending && setLocale(pending)}>
            {t("Đổi và tải lại")}
          </Button>
        </DialogActions>
      </Dialog>
    </>
  );
}
