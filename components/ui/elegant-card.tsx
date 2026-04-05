import { View, ViewProps, type ViewStyle } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { cn, withAlpha } from "@/lib/utils";

interface ElegantCardProps extends ViewProps {
  variant?: "elevated" | "filled" | "outlined" | "gradient";
  gradient?: "primary" | "success" | "warning" | "error";
  tone?: "neutral" | "primary" | "success" | "warning" | "error";
  radius?: "sm" | "md" | "lg" | "xl";
}

export function ElegantCard({
  variant = "elevated",
  gradient = "primary",
  tone = "neutral",
  radius = "md",
  className,
  style,
  children,
  ...props
}: ElegantCardProps) {
  const colors = useColors();

  const radiusMap = {
    sm: "rounded-lg",
    md: "rounded-[22px]",
    lg: "rounded-[28px]",
    xl: "rounded-[34px]",
  } as const;

  const tones = {
    neutral: {
      backgroundColor: colors.surface ?? colors.background,
      accentBackground: colors.surfaceContainer ?? colors.surface,
      borderColor: colors.outlineVariant ?? colors.border,
    },
    primary: {
      backgroundColor: colors.primaryContainer ?? colors.surface,
      accentBackground: colors.primaryContainer ?? withAlpha(colors.primary, 0.12),
      borderColor: withAlpha(colors.primary, 0.18),
    },
    success: {
      backgroundColor: colors.successContainer ?? colors.surface,
      accentBackground: colors.successContainer ?? withAlpha(colors.success, 0.12),
      borderColor: withAlpha(colors.success, 0.18),
    },
    warning: {
      backgroundColor: colors.warningContainer ?? colors.surface,
      accentBackground: colors.warningContainer ?? withAlpha(colors.warning, 0.12),
      borderColor: withAlpha(colors.warning, 0.18),
    },
    error: {
      backgroundColor: colors.errorContainer ?? colors.surface,
      accentBackground: colors.errorContainer ?? withAlpha(colors.error, 0.12),
      borderColor: withAlpha(colors.error, 0.18),
    },
  } as const;

  const gradientTone =
    gradient === "primary"
      ? tones.primary
      : gradient === "success"
        ? tones.success
        : gradient === "warning"
          ? tones.warning
          : tones.error;
  const selectedTone = tone === "neutral" ? tones.neutral : tones[tone];

  const variantStyle: Record<NonNullable<ElegantCardProps["variant"]>, ViewStyle> = {
    elevated: {
      backgroundColor: colors.surface ?? colors.background,
      borderColor: colors.outlineVariant ?? colors.border,
      borderWidth: 1,
      shadowColor: colors.foreground,
      shadowOpacity: 0.06,
      shadowRadius: 16,
      shadowOffset: { width: 0, height: 6 },
      elevation: 2,
    },
    filled: {
      backgroundColor: selectedTone.accentBackground,
      borderColor: selectedTone.borderColor,
      borderWidth: 1,
    },
    outlined: {
      backgroundColor: colors.surface ?? colors.background,
      borderColor: colors.outlineVariant ?? colors.border,
      borderWidth: 1,
    },
    gradient: {
      backgroundColor: colors.primaryContainer ?? gradientTone.accentBackground,
      borderColor: gradientTone.borderColor,
      borderWidth: 1,
      shadowColor: colors.primary,
      shadowOpacity: 0.08,
      shadowRadius: 20,
      shadowOffset: { width: 0, height: 6 },
      elevation: 3,
    },
  };

  return (
    <View className={cn("overflow-hidden", radiusMap[radius], className)} style={[variantStyle[variant], style]} {...props}>
      {children}
    </View>
  );
}
