export const colors = {
  bg: "#f4f6f3",
  surface: "#ffffff",
  border: "#dfe4dc",
  text: "#1f2a1c",
  textMuted: "#667066",
  primary: "#2e7d32",
  primaryDark: "#1b5e20",
  warning: "#b26a00",
  danger: "#b3261e",
  neutral: "#667066",
} as const;

export const chipTones = {
  neutral: { background: "#eceeea", text: "#667066" },
  warning: { background: "#fdf0dc", text: "#b26a00" },
  success: { background: "#e3f1e4", text: "#1b5e20" },
  danger: { background: "#fbe6e4", text: "#b3261e" },
} as const;

export const spacing = { xs: 4, sm: 8, md: 12, lg: 16, xl: 24 } as const;

export const radius = { card: 12, button: 12, chip: 999 } as const;

export const fontSize = { caption: 14, body: 16, cardTitle: 18, title: 22 } as const;

export const sizes = { touch: 48, primaryButton: 56, fab: 64 } as const;
