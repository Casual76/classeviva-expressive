import { Text, View } from "react-native";

import { ElegantCard } from "@/components/ui/elegant-card";
import { useColors } from "@/hooks/use-colors";

type MetricTone = "neutral" | "primary" | "success" | "warning" | "error";

interface MetricTileProps {
  label: string;
  value: string;
  detail?: string;
  tone?: MetricTone;
}

export function MetricTile({ label, value, detail, tone = "neutral" }: MetricTileProps) {
  const colors = useColors();

  return (
    <ElegantCard className="min-w-[140px] flex-1 gap-2 p-4" tone={tone} variant="filled" radius="md">
      <Text
        className="text-[11px] font-medium uppercase tracking-[1.2px]"
        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
      >
        {label}
      </Text>
      <View className="gap-1">
        <Text className="text-[30px] leading-[34px] font-medium" style={{ color: colors.foreground }}>
          {value}
        </Text>
        {detail ? (
          <Text className="text-xs leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
            {detail}
          </Text>
        ) : null}
      </View>
    </ElegantCard>
  );
}
