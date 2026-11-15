// SPDX-FileCopyrightText: 2026 HaUI-ImplSolution
// SPDX-License-Identifier: Apache-2.0
import { alpha, createTheme, type Theme } from "@mui/material/styles";
import { defaultTheme } from "react-admin";

// System fonts only: the packaged app loads nothing from other hosts.
const FONT = '"Inter", "Segoe UI", Roboto, "Helvetica Neue", Arial, sans-serif';

function build(mode: "light" | "dark"): Theme {
  const light = mode === "light";
  const primary = light ? "#3451d1" : "#8da2fb";
  const line = light ? "#e3e7f0" : "#262d45";
  const paper = light ? "#ffffff" : "#161b2e";
  const ground = light ? "#f4f6fb" : "#0e1220";
  const muted = light ? "#5b6478" : "#9aa3bb";

  return createTheme({
    ...defaultTheme,
    palette: {
      mode,
      primary: { main: primary },
      secondary: { main: light ? "#0f8b8d" : "#4fd1c5" },
      success: { main: light ? "#2b8a3e" : "#69db7c" },
      warning: { main: light ? "#d9730d" : "#ffa94d" },
      error: { main: light ? "#d1322f" : "#ff8787" },
      info: { main: light ? "#1971c2" : "#74c0fc" },
      background: { default: ground, paper },
      text: { primary: light ? "#1b2236" : "#e8ebf5", secondary: muted },
      divider: line,
    },
    shape: { borderRadius: 10 },
    typography: {
      fontFamily: FONT,
      h5: { fontWeight: 700, letterSpacing: -0.2 },
      h6: { fontWeight: 700 },
      subtitle2: { fontWeight: 600 },
      button: { textTransform: "none", fontWeight: 600 },
    },
    components: {
      ...defaultTheme.components,
      MuiButton: {
        defaultProps: { disableElevation: true },
        styleOverrides: { root: { borderRadius: 8 } },
      },
      MuiPaper: {
        styleOverrides: { root: { backgroundImage: "none" } },
      },
      MuiCard: {
        defaultProps: { variant: "outlined" },
        styleOverrides: { root: { borderColor: line, boxShadow: "none" } },
      },
      MuiChip: {
        styleOverrides: { root: { fontWeight: 600 } },
      },
      MuiTabs: {
        styleOverrides: { indicator: { height: 3, borderRadius: 3 } },
      },
      MuiTab: {
        styleOverrides: { root: { textTransform: "none", fontWeight: 600, minHeight: 44 } },
      },
      MuiTableCell: {
        styleOverrides: { root: { borderBottomColor: line } },
      },
      MuiOutlinedInput: {
        styleOverrides: { root: { borderRadius: 8 } },
      },
      RaLayout: {
        styleOverrides: {
          root: {
            "& .RaLayout-content": { padding: "20px 24px", backgroundColor: ground },
          },
        },
      },
      RaSidebar: {
        styleOverrides: {
          root: {
            "& .MuiDrawer-paper": { backgroundColor: paper, borderRight: `1px solid ${line}` },
          },
        },
      },
      RaMenuItemLink: {
        styleOverrides: {
          root: {
            margin: "2px 10px",
            borderRadius: 8,
            padding: "8px 12px",
            color: muted,
            "& .RaMenuItemLink-icon": { minWidth: 36, color: "inherit" },
            "&.RaMenuItemLink-active": {
              backgroundColor: alpha(primary, light ? 0.1 : 0.18),
              color: primary,
              fontWeight: 700,
            },
            "&:hover": { backgroundColor: alpha(primary, light ? 0.06 : 0.12) },
          },
        },
      },
      RaList: {
        styleOverrides: {
          root: {
            "& .RaList-content": { border: `1px solid ${line}`, borderRadius: 12, boxShadow: "none", overflow: "auto" },
            // The list keeps the width of the page and the table scrolls inside its frame, so a narrow screen is not widened.
            "& .RaList-main": { contain: "inline-size" },
          },
        },
      },
      RaDatagrid: {
        styleOverrides: {
          root: {
            "& .RaDatagrid-headerCell": {
              backgroundColor: light ? "#f8f9fc" : "#1b2136",
              color: muted,
              fontSize: 12,
              fontWeight: 700,
              letterSpacing: 0.4,
              textTransform: "uppercase",
              padding: "10px 16px",
            },
            "& .RaDatagrid-rowCell": { padding: "12px 16px" },
            "& .RaDatagrid-clickableRow:hover": { backgroundColor: alpha(primary, light ? 0.05 : 0.1) },
          },
        },
      },
      RaToolbar: {
        styleOverrides: {
          root: { backgroundColor: "transparent", borderTop: `1px solid ${line}`, padding: "12px 16px" },
        },
      },
      RaShow: {
        styleOverrides: {
          root: { "& .RaShow-card": { border: `1px solid ${line}`, borderRadius: 12, boxShadow: "none" } },
        },
      },
      RaEdit: {
        styleOverrides: {
          root: { "& .RaEdit-card": { border: `1px solid ${line}`, borderRadius: 12, boxShadow: "none" } },
        },
      },
      RaCreate: {
        styleOverrides: {
          root: { "& .RaCreate-card": { border: `1px solid ${line}`, borderRadius: 12, boxShadow: "none" } },
        },
      },
    },
  });
}

export const lightTheme = build("light");
export const darkTheme = build("dark");
