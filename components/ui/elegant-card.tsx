import { View, ViewProps, type ViewStyle } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { cn, withAlpha } from "@/lib/utils";

interface ElegantCardProps extends ViewProps {
  variant?: "elevated" | "filled" | "outlined" | "gradient";
  gradient?: "primary" | "success" | "warning" | "error";
  tone?: "neutral" | "primary" | "success" | "warning" | "error";
}

export function ElegantCard({
  variant = "elevated",
  gradient = "primary",
  tone = "neutral",
  className,
  style,
  children,
  ...props
}: ElegantCardProps) {
  const colors = useColors();
  const surfaceAlt = colors.surfaceAlt ?? colors.surface;

  const tones = {
    neutral: {
      backgroundColor: colors.surface,
      accentBackground: surfaceAlt,
      borderColor: colors.border,
    },
    primary: {
      backgroundColor: colors.primary,
      accentBackground: withAlpha(colors.primary, 0.1),
      borderColor: withAlpha(colors.primary, 0.26),
    },
    success: {
      backgroundColor: colors.success,
      accentBackground: withAlpha(colors.success, 0.12),
      borderColor: withAlpha(colors.success, 0.28),
    },
    warning: {
      backgroundColor: colors.warning,
      accentBackground: withAlpha(colors.warning, 0.12),
      borderColor: withAlpha(colors.warning, 0.28),
    },
    error: {
      backgroundColor: colors.error,
      accentBackground: withAlpha(colors.error, 0.12),
      borderColor: withAlpha(colors.error, 0.28),
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
      backgroundColor: colors.surface,
      borderColor: withAlpha(colors.border, 0.78),
      borderWidth: 1,
      shadowColor: colors.secondary ?? colors.foreground,
      shadowOpacity: 0.12,
      shadowRadius: 22,
      shadowOffset: { width: 0, height: 14 },
      elevation: 6,
    },
    filled: {
      backgroundColor: selectedTone.accentBackground,
      borderColor: tone === "neutral" ? colors.border : selectedTone.borderColor,
      borderWidth: 1,
    },
    outlined: {
      backgroundColor: withAlpha(colors.surface, 0.45),
      borderColor: selectedTone.borderColor,
      borderWidth: 1.5,
    },
    gradient: {
      backgroundColor: gradientTone.backgroundColor,
      borderColor: gradientTone.borderColor,
      borderWidth: 1,
      shadowColor: gradientTone.borderColor,
      shadowOpacity: 0.2,
      shadowRadius: 24,
      shadowOffset: { width: 0, height: 16 },
      elevation: 7,
    },
  };

  return (
    <View className={cn("overflow-hidden rounded-[32px]", className)} style={[variantStyle[variant], style]} {...props}>
      {children}
    </View>
  );
}
