export const themeColors = {
  primary: { light: "#235F57", dark: "#9ED3C9" },
  onPrimary: { light: "#FFFFFF", dark: "#073731" },
  primaryContainer: { light: "#D3ECE5", dark: "#1A4A43" },
  onPrimaryContainer: { light: "#103A35", dark: "#D3ECE5" },

  secondary: { light: "#53626A", dark: "#BBC9D2" },
  onSecondary: { light: "#FFFFFF", dark: "#24323A" },
  secondaryContainer: { light: "#D7E5EE", dark: "#39474F" },
  onSecondaryContainer: { light: "#25323A", dark: "#D7E5EE" },

  tertiary: { light: "#7B6406", dark: "#E9CF72" },
  tertiaryContainer: { light: "#F6E6A7", dark: "#5E4B00" },

  background: { light: "#F4F1EC", dark: "#111411" },
  surface: { light: "#FBF8F3", dark: "#171A17" },
  surfaceContainer: { light: "#F0EBE4", dark: "#202420" },
  surfaceContainerHigh: { light: "#E7E0D8", dark: "#2A2E2A" },
  surfaceContainerHighest: { light: "#DCD3CA", dark: "#353934" },
  surfaceAlt: { light: "#EFE7DD", dark: "#232723" },

  foreground: { light: "#1A1D1A", dark: "#E2E6E1" },
  onSurfaceVariant: { light: "#5C615C", dark: "#BAC0BA" },
  muted: { light: "#696F69", dark: "#A7ADA7" },

  border: { light: "#C8C1B7", dark: "#454B46" },
  outline: { light: "#837B73", dark: "#8D948E" },
  outlineVariant: { light: "#D5CDC2", dark: "#373C37" },

  success: { light: "#2D6A3E", dark: "#98D1A5" },
  successContainer: { light: "#D8EEDC", dark: "#1C4E2B" },
  warning: { light: "#996100", dark: "#F3C869" },
  warningContainer: { light: "#F8E4BA", dark: "#704800" },
  error: { light: "#B33A2E", dark: "#FFB4AA" },
  errorContainer: { light: "#F6D8D3", dark: "#8B261D" },
  onError: { light: "#FFFFFF", dark: "#5B150F" },
} as const;

export default { themeColors };
