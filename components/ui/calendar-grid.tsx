import { Pressable, Text, View } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { withAlpha } from "@/lib/utils";

type CalendarDay = {
  id: string;
  isoDate: string;
  dayLabel: string;
  isCurrentMonth: boolean;
  isToday: boolean;
  count: number;
  tones: ("neutral" | "primary" | "success" | "warning" | "error")[];
};

const WEEK_LABELS = ["Lun", "Mar", "Mer", "Gio", "Ven", "Sab", "Dom"];

export function CalendarGrid({
  days,
  selectedDate,
  onSelectDate,
}: {
  days: CalendarDay[];
  selectedDate?: string;
  onSelectDate?: (date: string) => void;
}) {
  const colors = useColors();

  return (
    <View
      className="gap-3 rounded-[28px] p-3"
      style={{
        backgroundColor: colors.surface ?? colors.background,
        borderWidth: 1,
        borderColor: colors.outlineVariant ?? colors.border,
      }}
    >
      <View className="flex-row justify-between px-1">
        {WEEK_LABELS.map((label) => (
          <Text
            key={label}
            className="w-9 text-center text-[11px] font-medium uppercase tracking-[1px]"
            style={{ color: colors.onSurfaceVariant ?? colors.muted }}
          >
            {label}
          </Text>
        ))}
      </View>

      <View className="flex-row flex-wrap gap-y-1.5">
        {days.map((day) => {
          const isSelected = selectedDate === day.isoDate;
          const backgroundColor = isSelected
            ? colors.primaryContainer ?? colors.surfaceContainerHigh
            : day.isToday
              ? colors.surfaceContainerHigh ?? withAlpha(colors.primary, 0.12)
              : colors.surface;
          const textColor = isSelected
            ? (colors.onPrimaryContainer ?? colors.primary)
            : day.isCurrentMonth
              ? colors.foreground
              : (colors.onSurfaceVariant ?? colors.muted);

          return (
            <Pressable
              key={day.id}
              className="w-[14.28%] items-center py-0.5"
              onPress={() => onSelectDate?.(day.isoDate)}
            >
              <View
                className="h-10 w-9 items-center justify-center rounded-full"
                style={{
                  backgroundColor,
                  borderWidth: 1,
                  borderColor: isSelected
                    ? withAlpha(colors.primary, 0.24)
                    : day.isToday
                      ? colors.outlineVariant ?? colors.border
                      : "transparent",
                }}
              >
                <Text style={{ color: textColor, fontWeight: day.isToday || isSelected ? "600" : "400", fontSize: 13 }}>
                  {day.dayLabel}
                </Text>
              </View>
              <View className="mt-1 flex-row gap-0.5">
                {day.tones.slice(0, 3).map((tone, index) => {
                  const dotColor =
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
                    <View
                      key={`${day.id}-${tone}-${index}`}
                      className="h-1 w-1 rounded-full"
                      style={{ backgroundColor: dotColor, opacity: day.count > 0 ? 1 : 0 }}
                    />
                  );
                })}
              </View>
            </Pressable>
          );
        })}
      </View>
    </View>
  );
}
