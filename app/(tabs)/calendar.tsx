import { ScrollView, Text, View, ActivityIndicator, RefreshControl } from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { classeviva, Lesson, Homework } from "@/lib/classeviva-client";
import { useColors } from "@/hooks/use-colors";

interface CalendarEvent {
  id: string;
  date: string;
  type: "lesson" | "homework";
  subject: string;
  description: string;
  dueDate?: string;
}

export default function CalendarScreen() {
  const colors = useColors();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [error, setError] = useState<string | null>(null);
  const [selectedDate, setSelectedDate] = useState<string>(
    new Date().toISOString().split("T")[0]
  );

  const loadCalendarData = async () => {
    try {
      setError(null);
      const [lessons, homeworks] = await Promise.all([
        classeviva.getLessons(),
        classeviva.getHomeworks(),
      ]);

      const allEvents: CalendarEvent[] = [
        ...lessons.map((lesson) => ({
          id: lesson.id,
          date: lesson.date,
          type: "lesson" as const,
          subject: lesson.subject,
          description: lesson.topic || "Lezione",
          dueDate: undefined,
        })),
        ...homeworks.map((hw) => ({
          id: hw.id,
          date: hw.dueDate,
          type: "homework" as const,
          subject: hw.subject,
          description: hw.description,
          dueDate: hw.dueDate,
        })),
      ];

      setEvents(allEvents.sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime()));
    } catch (err: any) {
      setError(err.message || "Errore nel caricamento dell'agenda");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadCalendarData();
  }, []);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadCalendarData();
  };

  const groupedByDate = events.reduce(
    (acc, event) => {
      const date = event.date;
      if (!acc[date]) {
        acc[date] = [];
      }
      acc[date].push(event);
      return acc;
    },
    {} as Record<string, CalendarEvent[]>
  );

  const sortedDates = Object.keys(groupedByDate).sort();

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">Agenda</Text>
            <Text className="text-sm text-muted">
              {events.length} eventi programmati
            </Text>
          </View>

          {error && (
            <View className="p-4 rounded-lg bg-error/10 border border-error">
              <Text className="text-sm text-error">{error}</Text>
            </View>
          )}

          {events.length > 0 ? (
            <View className="gap-6">
              {sortedDates.map((date) => {
                const dateEvents = groupedByDate[date];
                const dateObj = new Date(date);
                const formattedDate = dateObj.toLocaleDateString("it-IT", {
                  weekday: "long",
                  year: "numeric",
                  month: "long",
                  day: "numeric",
                });

                return (
                  <View key={date} className="gap-3">
                    {/* Date Header */}
                    <Text className="text-base font-semibold text-foreground capitalize">
                      {formattedDate}
                    </Text>

                    {/* Events */}
                    <View className="gap-2">
                      {dateEvents.map((event) => (
                        <View
                          key={event.id}
                          className={`rounded-xl border p-4 gap-2 ${
                            event.type === "homework"
                              ? "bg-warning/10 border-warning"
                              : "bg-surface border-border"
                          }`}
                        >
                          <View className="flex-row items-start justify-between gap-2">
                            <View className="flex-1 gap-1">
                              <View className="flex-row items-center gap-2">
                                <Text className="text-sm font-semibold text-foreground">
                                  {event.subject}
                                </Text>
                                <View
                                  className={`px-2 py-1 rounded-full ${
                                    event.type === "homework"
                                      ? "bg-warning/20"
                                      : "bg-primary/20"
                                  }`}
                                >
                                  <Text
                                    className={`text-xs font-semibold ${
                                      event.type === "homework"
                                        ? "text-warning"
                                        : "text-primary"
                                    }`}
                                  >
                                    {event.type === "homework" ? "Compito" : "Lezione"}
                                  </Text>
                                </View>
                              </View>
                              <Text className="text-sm text-muted">
                                {event.description}
                              </Text>
                            </View>
                          </View>
                        </View>
                      ))}
                    </View>
                  </View>
                );
              })}
            </View>
          ) : (
            <View className="rounded-xl bg-surface border border-border p-6 items-center">
              <Text className="text-sm text-muted">Nessun evento programmato</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
