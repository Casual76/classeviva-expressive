import type { ReactNode } from "react";
import { Text, View } from "react-native";

import { ElegantCard } from "@/components/ui/elegant-card";
import { useColors } from "@/hooks/use-colors";
import { withAlpha } from "@/lib/utils";

type RowTone = "neutral" | "primary" | "success" | "warning" | "error";

interface RegisterListRowProps {
  title: string;
  subtitle?: string;
  detail?: string;
  meta?: string;
  trailing?: ReactNode;
  leading?: ReactNode;
  tone?: RowTone;
}

export function RegisterListRow({
  title,
  subtitle,
  detail,
  meta,
  trailing,
  leading,
  tone = "neutral",
}: RegisterListRowProps) {
  const colors = useColors();

  const accentColor =
    tone === "error"
      ? colors.error
      : tone === "warning"
        ? colors.warning
        : tone === "success"
          ? colors.success
          : tone === "primary"
            ? colors.primary
            : colors.outline ?? colors.border;

  return (
    <ElegantCard
      className="gap-3 p-4"
      radius="md"
      style={{
        borderWidth: 1,
        borderColor: withAlpha(accentColor, tone === "neutral" ? 0.16 : 0.22),
      }}
      tone={tone}
      variant="filled"
    >
      <View className="flex-row items-start gap-3">
        {leading ? <View className="pt-0.5">{leading}</View> : null}
        <View className="flex-1 gap-1">
          <View className="flex-row items-start justify-between gap-3">
            <View className="flex-1 gap-0.5">
              <Text className="text-base font-medium" style={{ color: colors.foreground }}>
                {title}
              </Text>
              {subtitle ? (
                <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                  {subtitle}
                </Text>
              ) : null}
            </View>
            {trailing ? <View className="items-end">{trailing}</View> : null}
          </View>

          {detail ? (
            <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
              {detail}
            </Text>
          ) : null}

          {meta ? (
            <Text
              className="text-[11px] font-medium uppercase tracking-[1.2px]"
              style={{ color: colors.onSurfaceVariant ?? colors.muted }}
            >
              {meta}
            </Text>
          ) : null}
        </View>
      </View>
    </ElegantCard>
  );
}
