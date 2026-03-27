export const themeColors = {
  // M3 Expressive — Primary (Violet)
  primary: { light: "#6750A4", dark: "#D0BCFF" },
  onPrimary: { light: "#FFFFFF", dark: "#381E72" },
  primaryContainer: { light: "#EADDFF", dark: "#4F378B" },
  onPrimaryContainer: { light: "#21005D", dark: "#EADDFF" },

  // M3 Expressive — Secondary (Muted Violet)
  secondary: { light: "#625B71", dark: "#CCC2DC" },
  onSecondary: { light: "#FFFFFF", dark: "#332D41" },
  secondaryContainer: { light: "#E8DEF8", dark: "#4A4458" },
  onSecondaryContainer: { light: "#1D192B", dark: "#E8DEF8" },

  // M3 Expressive — Tertiary (Rose)
  tertiary: { light: "#7D5260", dark: "#EFB8C8" },
  tertiaryContainer: { light: "#FFD8E4", dark: "#633B48" },

  // Surfaces
  background: { light: "#FEF7FF", dark: "#141218" },
  surface: { light: "#FEF7FF", dark: "#141218" },
  surfaceContainer: { light: "#F3EDF7", dark: "#211F26" },
  surfaceContainerHigh: { light: "#ECE6F0", dark: "#2B2930" },
  surfaceContainerHighest: { light: "#E6E0E9", dark: "#36343B" },
  surfaceAlt: { light: "#F3EDF7", dark: "#211F26" },

  // On Surfaces
  foreground: { light: "#1D1B20", dark: "#E6E0E9" },
  onSurfaceVariant: { light: "#49454F", dark: "#CAC4D0" },
  muted: { light: "#49454F", dark: "#CAC4D0" },

  // Outlines
  border: { light: "#CAC4D0", dark: "#49454F" },
  outline: { light: "#79747E", dark: "#938F99" },
  outlineVariant: { light: "#CAC4D0", dark: "#49454F" },

  // Semantic
  success: { light: "#386A20", dark: "#9CD67D" },
  successContainer: { light: "#BBEDA0", dark: "#205107" },
  warning: { light: "#8B5000", dark: "#FFB870" },
  warningContainer: { light: "#FFDCB8", dark: "#6C3B00" },
  error: { light: "#B3261E", dark: "#F2B8B5" },
  errorContainer: { light: "#F9DEDC", dark: "#8C1D18" },
  onError: { light: "#FFFFFF", dark: "#601410" },
} as const;

export default { themeColors };
