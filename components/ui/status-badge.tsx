import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Text, View } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { withAlpha } from "@/lib/utils";

type BadgeTone = "neutral" | "primary" | "success" | "warning" | "error";

interface StatusBadgeProps {
  label: string;
  tone?: BadgeTone;
  icon?: keyof typeof MaterialIcons.glyphMap;
}

export function StatusBadge({ label, tone = "neutral", icon }: StatusBadgeProps) {
  const colors = useColors();

  const palette =
    tone === "error"
      ? { background: colors.errorContainer ?? withAlpha(colors.error, 0.14), foreground: colors.error }
      : tone === "warning"
        ? { background: colors.warningContainer ?? withAlpha(colors.warning, 0.14), foreground: colors.warning }
        : tone === "success"
          ? { background: colors.successContainer ?? withAlpha(colors.success, 0.14), foreground: colors.success }
          : tone === "primary"
            ? {
                background: colors.primaryContainer ?? withAlpha(colors.primary, 0.14),
                foreground: colors.primary,
              }
            : {
                background: colors.surfaceContainerHigh ?? colors.surface,
                foreground: colors.onSurfaceVariant ?? colors.muted,
              };

  return (
    <View
      className="flex-row items-center gap-1.5 self-start rounded-full px-3 py-1.5"
      style={{ backgroundColor: palette.background }}
    >
      {icon ? <MaterialIcons color={palette.foreground} name={icon} size={14} /> : null}
      <Text
        className="text-[11px] font-medium uppercase tracking-[1.2px]"
        style={{ color: palette.foreground }}
      >
        {label}
      </Text>
    </View>
  );
}
