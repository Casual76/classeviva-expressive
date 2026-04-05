import { Text, View } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { withAlpha } from "@/lib/utils";

type GradeTone = "neutral" | "primary" | "success" | "warning" | "error";

interface GradePillProps {
  value: string;
  tone?: GradeTone;
  compact?: boolean;
}

export function GradePill({ value, tone = "neutral", compact = false }: GradePillProps) {
  const colors = useColors();

  const accent =
    tone === "error"
      ? colors.error
      : tone === "warning"
        ? colors.warning
        : tone === "success"
          ? colors.success
          : tone === "primary"
            ? colors.primary
            : colors.onSurfaceVariant ?? colors.muted;

  return (
    <View
      className={compact ? "rounded-full px-3 py-1.5" : "rounded-[18px] px-4 py-2"}
      style={{
        backgroundColor: withAlpha(accent, tone === "neutral" ? 0.12 : 0.16),
        borderWidth: 1,
        borderColor: withAlpha(accent, 0.18),
      }}
    >
      <Text
        className={compact ? "text-sm font-semibold" : "text-lg font-semibold"}
        style={{ color: accent }}
      >
        {value}
      </Text>
    </View>
  );
}
