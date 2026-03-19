import { View, ViewProps, type ViewStyle } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { cn } from "@/lib/utils";

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
      accentBackground: surfaceAlt,
      borderColor: colors.primary,
    },
    success: {
      backgroundColor: colors.success,
      accentBackground: surfaceAlt,
      borderColor: colors.success,
    },
    warning: {
      backgroundColor: colors.warning,
      accentBackground: surfaceAlt,
      borderColor: colors.warning,
    },
    error: {
      backgroundColor: colors.error,
      accentBackground: surfaceAlt,
      borderColor: colors.error,
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
      borderColor: colors.border,
      borderWidth: 1,
      shadowColor: colors.secondary ?? colors.foreground,
      shadowOpacity: 0.1,
      shadowRadius: 18,
      shadowOffset: { width: 0, height: 10 },
      elevation: 5,
    },
    filled: {
      backgroundColor: selectedTone.accentBackground,
      borderColor: colors.border,
      borderWidth: 1,
    },
    outlined: {
      backgroundColor: "transparent",
      borderColor: selectedTone.borderColor,
      borderWidth: 1.5,
    },
    gradient: {
      backgroundColor: gradientTone.backgroundColor,
      borderColor: gradientTone.borderColor,
      borderWidth: 1,
      shadowColor: gradientTone.borderColor,
      shadowOpacity: 0.18,
      shadowRadius: 20,
      shadowOffset: { width: 0, height: 12 },
      elevation: 6,
    },
  };

  return (
    <View className={cn("overflow-hidden rounded-[28px]", className)} style={[variantStyle[variant], style]} {...props}>
      {children}
    </View>
  );
}
