import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import {
  groupAgendaByDate,
  loadAgendaView,
  type AgendaItemViewModel,
} from "@/lib/student-data";

function startOfWeek(date: Date) {
  const current = new Date(date);
  const day = (current.getDay() + 6) % 7;
  current.setHours(0, 0, 0, 0);
  current.setDate(current.getDate() - day);
  return current;
}

function toIso(date: Date) {
  return date.toISOString().slice(0, 10);
}

function buildWeekDays(weekStart: Date) {
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart);
    date.setDate(weekStart.getDate() + index);

    return {
      isoDate: toIso(date),
      label: new Intl.DateTimeFormat("it-IT", {
        weekday: "short",
        day: "numeric",
      }).format(date),
    };
  });
}

export default function ScheduleScreen() {
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [currentWeekStart, setCurrentWeekStart] = useState(() => startOfWeek(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => toIso(new Date()));
  const [lessons, setLessons] = useState<AgendaItemViewModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async (weekStart: Date) => {
    try {
      setError(null);
      const weekEnd = new Date(weekStart);
      weekEnd.setDate(weekStart.getDate() + 6);

      const data = await loadAgendaView(toIso(weekStart), toIso(weekEnd));
      const lessonOnly = data.filter((item) => item.category === "lesson");
      setLessons(lessonOnly);

      const nextSelected = lessonOnly.find((item) => item.date === selectedDate)?.date ?? toIso(weekStart);
      setSelectedDate(nextSelected);
    } catch (loadError) {
      console.error("Schedule load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare l'orario.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [selectedDate]);

  useEffect(() => {
    void loadData(currentWeekStart);
  }, [currentWeekStart, loadData]);

  const weekDays = useMemo(() => buildWeekDays(currentWeekStart), [currentWeekStart]);
  const selectedDayItems = useMemo(
    () => lessons.filter((item) => item.date === selectedDate),
    [lessons, selectedDate],
  );
  const sections = useMemo(() => groupAgendaByDate(lessons), [lessons]);
  const weekLabel = useMemo(() => {
    const weekEnd = new Date(currentWeekStart);
    weekEnd.setDate(currentWeekStart.getDate() + 6);

    return `${currentWeekStart.toLocaleDateString("it-IT", { day: "numeric", month: "short" })} - ${weekEnd.toLocaleDateString("it-IT", { day: "numeric", month: "short" })}`;
  }, [currentWeekStart]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando l'orario"
          detail="Recupero le lezioni della settimana corrente."
        />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 112 }}
        refreshControl={
          <RefreshControl
            onRefresh={() => {
              setIsRefreshing(true);
              void loadData(currentWeekStart);
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            backLabel="Altro"
            eyebrow="Lezioni"
            onBack={() => router.replace("/(tabs)/more")}
            subtitle="Vista settimanale concentrata solo sulle lezioni, con dettaglio rapido per giorno."
            title="Orario"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <ElegantCard className="gap-4 p-5" variant="elevated">
            <View className="flex-row items-center justify-between">
              <Text className="text-base font-semibold text-foreground">{weekLabel}</Text>
              <View className="flex-row gap-2">
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full border border-border bg-surface"
                  onPress={() =>
                    setCurrentWeekStart((prev) => {
                      const next = new Date(prev);
                      next.setDate(prev.getDate() - 7);
                      return next;
                    })
                  }
                >
                  <MaterialIcons name="west" size={18} />
                </Pressable>
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full border border-border bg-surface"
                  onPress={() =>
                    setCurrentWeekStart((prev) => {
                      const next = new Date(prev);
                      next.setDate(prev.getDate() + 7);
                      return next;
                    })
                  }
                >
                  <MaterialIcons name="east" size={18} />
                </Pressable>
              </View>
            </View>

            <View className="flex-row gap-3">
              <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Lezioni</Text>
                <Text className="text-3xl font-semibold text-foreground">{lessons.length}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Giorni attivi</Text>
                <Text className="text-3xl font-semibold text-foreground">{sections.length}</Text>
              </ElegantCard>
            </View>

            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View className="flex-row gap-3">
                {weekDays.map((day) => {
                  const isSelected = selectedDate === day.isoDate;
                  const count = lessons.filter((item) => item.date === day.isoDate).length;

                  return (
                    <Pressable key={day.isoDate} onPress={() => setSelectedDate(day.isoDate)}>
                      <ElegantCard
                        className="min-w-[92px] gap-2 px-4 py-3"
                        tone={isSelected ? "primary" : "neutral"}
                        variant={isSelected ? "filled" : "outlined"}
                      >
                        <Text className="text-sm font-semibold text-foreground">{day.label}</Text>
                        <Text className="text-xs text-muted">{count} lezioni</Text>
                      </ElegantCard>
                    </Pressable>
                  );
                })}
              </View>
            </ScrollView>
          </ElegantCard>

          <View className="gap-3">
            <SectionTitle eyebrow="Giorno selezionato" title="Lezioni del giorno" />
            {selectedDayItems.length > 0 ? (
              <View className="gap-3">
                {selectedDayItems.map((item) => (
                  <ElegantCard key={item.id} className="gap-3 p-4" variant="filled">
                    <View className="flex-row items-start justify-between gap-3">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                        <Text className="text-sm text-muted">{item.subtitle}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {item.timeLabel}
                      </Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Per questa giornata non risultano lezioni nel calendario corrente."
                title="Nessuna lezione nel giorno scelto"
              />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Settimana" title="Panoramica completa" />
            {sections.length > 0 ? (
              <View className="gap-4">
                {sections.map((section) => (
                  <View key={section.id} className="gap-3">
                    <Text className="text-sm font-semibold text-foreground">{section.label}</Text>
                    <View className="gap-3">
                      {section.items.map((item) => (
                        <ElegantCard key={item.id} className="gap-3 p-4" variant="filled">
                          <View className="flex-row items-start justify-between gap-3">
                            <View className="flex-1 gap-1">
                              <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                              <Text className="text-sm text-muted">{item.subtitle}</Text>
                            </View>
                            <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                              {item.timeLabel}
                            </Text>
                          </View>
                          <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                        </ElegantCard>
                      ))}
                    </View>
                  </View>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Non risultano lezioni per la settimana selezionata."
                title="Orario vuoto"
              />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
