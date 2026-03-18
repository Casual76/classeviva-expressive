import { ScrollView, Text, View, ActivityIndicator } from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { generateMockSchedule } from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";

interface ScheduleItem {
  day: string;
  hours: Array<{
    time: string;
    subject: string;
    teacher: string;
    room: string;
  }>;
}

export default function ScheduleScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [schedule, setSchedule] = useState<ScheduleItem[]>([]);

  useEffect(() => {
    const loadSchedule = async () => {
      try {
        if (isDemoMode) {
          setSchedule(generateMockSchedule());
        }
        // TODO: Implementare caricamento da API reale
      } finally {
        setIsLoading(false);
      }
    };

    loadSchedule();
  }, [isDemoMode]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  const getDayColor = (day: string) => {
    const colors_map: Record<string, string> = {
      Lunedì: "#FF6B6B",
      Martedì: "#4ECDC4",
      Mercoledì: "#45B7D1",
      Giovedì: "#FFA07A",
      Venerdì: "#98D8C8",
    };
    return colors_map[day] || colors.primary;
  };

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">Orario</Text>
            <Text className="text-sm text-muted">
              Orario settimanale delle lezioni
            </Text>
          </View>

          {/* Schedule */}
          {schedule.length > 0 ? (
            <View className="gap-6">
              {schedule.map((daySchedule) => (
                <View key={daySchedule.day} className="gap-3">
                  {/* Day Header */}
                  <View
                    className="rounded-xl px-4 py-3"
                    style={{ backgroundColor: getDayColor(daySchedule.day) }}
                  >
                    <Text className="text-base font-bold text-white">
                      {daySchedule.day}
                    </Text>
                  </View>

                  {/* Hours */}
                  <View className="gap-2">
                    {daySchedule.hours.map((hour, index) => (
                      <View
                        key={index}
                        className="rounded-xl bg-surface border border-border p-4 gap-2"
                      >
                        <View className="flex-row items-center justify-between gap-2">
                          <View className="flex-1 gap-1">
                            <Text className="text-sm font-semibold text-foreground">
                              {hour.subject}
                            </Text>
                            <Text className="text-xs text-muted">
                              {hour.teacher}
                            </Text>
                          </View>
                          <View className="items-end gap-1">
                            <Text className="text-xs font-semibold text-primary">
                              {hour.time}
                            </Text>
                            <Text className="text-xs text-muted">
                              {hour.room}
                            </Text>
                          </View>
                        </View>
                      </View>
                    ))}
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <View className="rounded-xl bg-surface border border-border p-6 items-center">
              <Text className="text-sm text-muted">
                Nessun orario disponibile
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
