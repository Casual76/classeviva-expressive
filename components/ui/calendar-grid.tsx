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
    <View className="gap-4 rounded-[30px] border border-border bg-surface p-4">
      <View className="flex-row justify-between">
        {WEEK_LABELS.map((label) => (
          <Text key={label} className="w-10 text-center text-xs font-semibold uppercase tracking-[1px] text-muted">
            {label}
          </Text>
        ))}
      </View>

      <View className="flex-row flex-wrap gap-y-3">
        {days.map((day) => {
          const isSelected = selectedDate === day.isoDate;
          const backgroundColor = isSelected
            ? colors.primary
            : day.isToday
              ? withAlpha(colors.primary, 0.12)
              : "transparent";
          const textColor = isSelected
            ? colors.background
            : day.isCurrentMonth
              ? colors.foreground
              : colors.muted;

          return (
            <Pressable
              key={day.id}
              className="w-[14.28%] items-center"
              onPress={() => onSelectDate?.(day.isoDate)}
            >
              <View
                className="h-12 w-10 items-center justify-center rounded-[18px]"
                style={{ backgroundColor }}
              >
                <Text style={{ color: textColor, fontWeight: day.isToday || isSelected ? "700" : "600" }}>
                  {day.dayLabel}
                </Text>
              </View>
              <View className="mt-1 flex-row gap-1">
                {day.tones.slice(0, 3).map((tone, index) => {
                  const color =
                    tone === "error"
                      ? colors.error
                      : tone === "warning"
                        ? colors.warning
                        : tone === "success"
                          ? colors.success
                          : tone === "primary"
                            ? colors.primary
                            : colors.border;

                  return (
                    <View
                      key={`${day.id}-${tone}-${index}`}
                      className="h-1.5 w-1.5 rounded-full"
                      style={{ backgroundColor: color, opacity: day.count > 0 ? 1 : 0 }}
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
