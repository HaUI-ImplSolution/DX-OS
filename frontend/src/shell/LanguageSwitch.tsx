// SPDX-License-Identifier: Apache-2.0
import { ToggleButton, ToggleButtonGroup } from "@mui/material";
import { getLocale, setLocale, type Locale } from "./locale";

const VIETNAMESE = "Tiếng Việt"; // i18n-keep: a language is named in itself

// Switches the interface between Vietnamese and English; the page reloads in the chosen language.
export default function LanguageSwitch() {
  return (
    <ToggleButtonGroup
      exclusive
      size="small"
      value={getLocale()}
      onChange={(_, next: Locale | null) => next && setLocale(next)}
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
  );
}
