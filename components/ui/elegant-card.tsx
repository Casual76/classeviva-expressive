import { View, ViewProps, type ViewStyle } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { cn, withAlpha } from "@/lib/utils";

interface ElegantCardProps extends ViewProps {
  variant?: "elevated" | "filled" | "outlined" | "gradient";
  gradient?: "primary" | "success" | "warning" | "error";
  tone?: "neutral" | "primary" | "success" | "warning" | "error";
  radius?: "sm" | "md" | "lg";
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
    sm: "rounded-xl",       // 12dp
    md: "rounded-2xl",      // 16dp
    lg: "rounded-[28px]",   // 28dp
  } as const;

  const tones = {
    neutral: {
      backgroundColor: colors.surfaceContainerHigh ?? colors.surface,
      accentBackground: colors.surfaceContainerHigh ?? colors.surface,
      borderColor: colors.outlineVariant ?? colors.border,
    },
    primary: {
      backgroundColor: colors.primary,
      accentBackground: colors.primaryContainer ?? withAlpha(colors.primary, 0.12),
      borderColor: withAlpha(colors.primary, 0.2),
    },
    success: {
      backgroundColor: colors.success,
      accentBackground: colors.successContainer ?? withAlpha(colors.success, 0.12),
      borderColor: withAlpha(colors.success, 0.2),
    },
    warning: {
      backgroundColor: colors.warning,
      accentBackground: colors.warningContainer ?? withAlpha(colors.warning, 0.12),
      borderColor: withAlpha(colors.warning, 0.2),
    },
    error: {
      backgroundColor: colors.error,
      accentBackground: colors.errorContainer ?? withAlpha(colors.error, 0.12),
      borderColor: withAlpha(colors.error, 0.2),
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
      backgroundColor: colors.surfaceContainer ?? colors.surface,
      borderColor: "transparent",
      borderWidth: 0,
      shadowColor: colors.foreground,
      shadowOpacity: 0.08,
      shadowRadius: 12,
      shadowOffset: { width: 0, height: 4 },
      elevation: 2,
    },
    filled: {
      backgroundColor: selectedTone.accentBackground,
      borderColor: "transparent",
      borderWidth: 0,
    },
    outlined: {
      backgroundColor: "transparent",
      borderColor: colors.outlineVariant ?? colors.border,
      borderWidth: 1,
    },
    gradient: {
      backgroundColor: colors.primaryContainer ?? gradientTone.accentBackground,
      borderColor: "transparent",
      borderWidth: 0,
      shadowColor: colors.primary,
      shadowOpacity: 0.12,
      shadowRadius: 16,
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
