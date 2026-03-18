import {
  ScrollView,
  Text,
  View,
  ActivityIndicator,
  RefreshControl,
  TouchableOpacity,
} from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { generateMockLessons, generateMockHomeworks } from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";

interface CalendarEvent {
  id: string;
  title: string;
  description: string;
  date: string;
  type: "lezione" | "compito" | "verifica" | "evento";
  subject: string;
}

export default function CalendarScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [events, setEvents] = useState<CalendarEvent[]>([]);
  const [selectedDate, setSelectedDate] = useState<string | null>(null);

  const loadEvents = async () => {
    try {
      if (isDemoMode) {
        const lessons = generateMockLessons();
        const homeworks = generateMockHomeworks();
        
        const mockEvents = [
          ...lessons.map((l: any) => ({
            id: l.id,
            title: l.topic || "Lezione",
            description: l.topic,
            date: l.date,
            type: "lezione" as const,
            subject: l.subject,
          })),
          ...homeworks.map((h: any) => ({
            id: h.id,
            title: h.description,
            description: h.description,
            date: h.dueDate,
            type: "compito" as const,
            subject: h.subject,
          })),
        ];
        setEvents(mockEvents);
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadEvents();
  }, [isDemoMode]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadEvents();
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  const getEventColor = (type: string) => {
    const colors: Record<string, string> = {
      lezione: "bg-primary/10 border-primary/30",
      compito: "bg-warning/10 border-warning/30",
      verifica: "bg-error/10 border-error/30",
      evento: "bg-success/10 border-success/30",
    };
    return colors[type] || "bg-surface";
  };

  const getEventIcon = (type: string) => {
    const icons: Record<string, string> = {
      lezione: "📚",
      compito: "📝",
      verifica: "✏️",
      evento: "🎉",
    };
    return icons[type] || "📌";
  };

  const getEventTypeLabel = (type: string) => {
    const labels: Record<string, string> = {
      lezione: "Lezione",
      compito: "Compito",
      verifica: "Verifica",
      evento: "Evento",
    };
    return labels[type] || type;
  };

  const eventsByDate = events.reduce(
    (acc, event) => {
      const date = new Date(event.date).toLocaleDateString("it-IT");
      if (!acc[date]) {
        acc[date] = [];
      }
      acc[date].push(event);
      return acc;
    },
    {} as Record<string, CalendarEvent[]>
  );

  const sortedDates = Object.keys(eventsByDate).sort(
    (a, b) => new Date(a).getTime() - new Date(b).getTime()
  );

  const filteredDates = selectedDate
    ? sortedDates.filter((d) => d === selectedDate)
    : sortedDates;

  const upcomingEvents = events
    .filter((e) => new Date(e.date) >= new Date())
    .sort((a, b) => new Date(a.date).getTime() - new Date(b.date).getTime())
    .slice(0, 3);

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        refreshControl={
          <RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />
        }
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-2">
            <Text className="text-4xl font-bold text-foreground">Agenda</Text>
            <Text className="text-sm text-muted">
              {events.length} eventi registrati
            </Text>
          </View>

          {/* Upcoming Events */}
          {upcomingEvents.length > 0 && (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                Prossimi Eventi
              </Text>
              <View className="gap-2">
                {upcomingEvents.map((event) => (
                  <ElegantCard
                    key={event.id}
                    variant="filled"
                    className={`p-4 gap-2 border ${getEventColor(event.type)}`}
                  >
                    <View className="flex-row items-center gap-2">
                      <Text className="text-2xl">
                        {getEventIcon(event.type)}
                      </Text>
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-bold text-foreground">
                          {event.title}
                        </Text>
                        <Text className="text-xs text-muted">
                          {event.subject}
                        </Text>
                      </View>
                      <Text className="text-xs font-bold text-primary">
                        {new Date(event.date).toLocaleDateString("it-IT")}
                      </Text>
                    </View>
                    {event.description && (
                      <Text className="text-xs text-muted">
                        {event.description}
                      </Text>
                    )}
                  </ElegantCard>
                ))}
              </View>
            </View>
          )}

          {/* Date Filter */}
          {sortedDates.length > 0 && (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                Filtra per Data
              </Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{ gap: 8 }}
              >
                <TouchableOpacity
                  onPress={() => setSelectedDate(null)}
                  activeOpacity={0.7}
                >
                  <ElegantCard
                    variant={selectedDate === null ? "gradient" : "outlined"}
                    className={`px-4 py-2 ${
                      selectedDate === null ? "" : "border-primary"
                    }`}
                  >
                    <Text
                      className={`text-sm font-bold ${
                        selectedDate === null ? "text-background" : "text-primary"
                      }`}
                    >
                      Tutte
                    </Text>
                  </ElegantCard>
                </TouchableOpacity>

                {sortedDates.slice(0, 5).map((date) => (
                  <TouchableOpacity
                    key={date}
                    onPress={() => setSelectedDate(date)}
                    activeOpacity={0.7}
                  >
                    <ElegantCard
                      variant={selectedDate === date ? "gradient" : "outlined"}
                      className={`px-4 py-2 ${
                        selectedDate === date ? "" : "border-primary"
                      }`}
                    >
                      <Text
                        className={`text-sm font-bold ${
                          selectedDate === date ? "text-background" : "text-primary"
                        }`}
                      >
                        {new Date(date).toLocaleDateString("it-IT", {
                          day: "2-digit",
                          month: "short",
                        })}
                      </Text>
                    </ElegantCard>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          )}

          {/* Events by Date */}
          {filteredDates.length > 0 ? (
            <View className="gap-4">
              {filteredDates.map((date) => (
                <View key={date} className="gap-3">
                  <View className="flex-row items-center gap-2">
                    <View className="flex-1 h-px bg-border" />
                    <Text className="text-sm font-bold text-foreground">
                      {new Date(date).toLocaleDateString("it-IT", {
                        weekday: "long",
                        day: "numeric",
                        month: "long",
                      })}
                    </Text>
                    <View className="flex-1 h-px bg-border" />
                  </View>

                  <View className="gap-2">
                    {eventsByDate[date].map((event: any) => (
                      <ElegantCard
                        key={event.id}
                        variant="filled"
                        className={`p-4 gap-2 border ${getEventColor(
                          event.type
                        )}`}
                      >
                        <View className="flex-row items-start gap-3">
                          <Text className="text-2xl">
                            {getEventIcon(event.type)}
                          </Text>
                          <View className="flex-1 gap-1">
                            <Text className="text-sm font-bold text-foreground">
                              {event.title}
                            </Text>
                            <Text className="text-xs text-muted">
                              {event.subject} •{" "}
                              {getEventTypeLabel(event.type)}
                            </Text>
                            {event.description && (
                              <Text className="text-xs text-muted mt-1">
                                {event.description}
                              </Text>
                            )}
                          </View>
                        </View>
                      </ElegantCard>
                    ))}
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <ElegantCard variant="filled" className="p-6 items-center">
              <Text className="text-sm text-muted">
                Nessun evento disponibile
              </Text>
            </ElegantCard>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
