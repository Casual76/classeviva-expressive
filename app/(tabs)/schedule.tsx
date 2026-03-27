import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
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

function toIso(date: Date) { return date.toISOString().slice(0, 10); }

function buildWeekDays(weekStart: Date) {
  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart);
    date.setDate(weekStart.getDate() + index);
    return {
      isoDate: toIso(date),
      label: new Intl.DateTimeFormat("it-IT", { weekday: "short", day: "numeric" }).format(date),
    };
  });
}

export default function ScheduleScreen() {
  const colors = useColors();
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
      const weekEnd = new Date(weekStart); weekEnd.setDate(weekStart.getDate() + 6);
      const data = await loadAgendaView(toIso(weekStart), toIso(weekEnd));
      const lessonOnly = data.filter((item) => item.category === "lesson");
      setLessons(lessonOnly);
      const nextSelected = lessonOnly.find((item) => item.date === selectedDate)?.date ?? toIso(weekStart);
      setSelectedDate(nextSelected);
    } catch (loadError) {
      console.error("Schedule load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare l'orario.");
    } finally { setIsLoading(false); setIsRefreshing(false); }
  }, [selectedDate]);

  useEffect(() => { void loadData(currentWeekStart); }, [currentWeekStart, loadData]);

  const weekDays = useMemo(() => buildWeekDays(currentWeekStart), [currentWeekStart]);
  const selectedDayItems = useMemo(() => lessons.filter((item) => item.date === selectedDate), [lessons, selectedDate]);
  const sections = useMemo(() => groupAgendaByDate(lessons), [lessons]);
  const weekLabel = useMemo(() => {
    const weekEnd = new Date(currentWeekStart); weekEnd.setDate(currentWeekStart.getDate() + 6);
    return `${currentWeekStart.toLocaleDateString("it-IT", { day: "numeric", month: "short" })} — ${weekEnd.toLocaleDateString("it-IT", { day: "numeric", month: "short" })}`;
  }, [currentWeekStart]);

  if (isLoading) {
    return (<ScreenContainer className="flex-1 bg-background"><LoadingState title="Sto caricando l'orario" detail="Recupero le lezioni della settimana corrente." /></ScreenContainer>);
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={{ paddingBottom: 100 }} refreshControl={<RefreshControl onRefresh={() => { setIsRefreshing(true); void loadData(currentWeekStart); }} refreshing={isRefreshing} />} showsVerticalScrollIndicator={false}>
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader backLabel="Altro" eyebrow="Lezioni" onBack={() => router.replace("/(tabs)/more")} subtitle="Vista settimanale concentrata solo sulle lezioni, con dettaglio rapido per giorno." title="Orario" />
          </AnimatedListItem>

          {error ? (<ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md"><Text className="text-sm font-medium" style={{ color: colors.foreground }}>Aggiornamento parziale</Text><Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{error}</Text></ElegantCard>) : null}

          <AnimatedListItem index={1}>
            <ElegantCard className="gap-4 p-5" variant="elevated" radius="lg">
              <View className="flex-row items-center justify-between">
                <Text className="text-base font-medium" style={{ color: colors.foreground }}>{weekLabel}</Text>
                <View className="flex-row gap-2">
                  <Pressable
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                    onPress={() => setCurrentWeekStart((prev) => { const next = new Date(prev); next.setDate(prev.getDate() - 7); return next; })}
                  >
                    <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="chevron-left" size={20} />
                  </Pressable>
                  <Pressable
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                    onPress={() => setCurrentWeekStart((prev) => { const next = new Date(prev); next.setDate(prev.getDate() + 7); return next; })}
                  >
                    <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="chevron-right" size={20} />
                  </Pressable>
                </View>
              </View>

              <View className="flex-row gap-3">
                <ElegantCard className="flex-1 gap-1.5 p-4" tone="primary" variant="filled" radius="md">
                  <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Lezioni</Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{lessons.length}</Text>
                </ElegantCard>
                <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                  <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Giorni attivi</Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{sections.length}</Text>
                </ElegantCard>
              </View>

              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-2">
                  {weekDays.map((day) => {
                    const isSelected = selectedDate === day.isoDate;
                    const count = lessons.filter((item) => item.date === day.isoDate).length;
                    return (
                      <M3Chip
                        key={day.isoDate}
                        label={`${day.label} (${count})`}
                        selected={isSelected}
                        onPress={() => setSelectedDate(day.isoDate)}
                      />
                    );
                  })}
                </View>
              </ScrollView>
            </ElegantCard>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Giorno selezionato" title="Lezioni del giorno" />
            {selectedDayItems.length > 0 ? (
              <View className="gap-3">
                {selectedDayItems.map((item, i) => (
                  <AnimatedListItem key={item.id} index={2 + i}>
                    <ElegantCard className="gap-3 p-4" variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-3">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{item.title}</Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.subtitle}</Text>
                        </View>
                        <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.timeLabel}</Text>
                      </View>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.detail}</Text>
                    </ElegantCard>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Per questa giornata non risultano lezioni nel calendario corrente." title="Nessuna lezione nel giorno scelto" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Settimana" title="Panoramica completa" />
            {sections.length > 0 ? (
              <View className="gap-4">
                {sections.map((section) => (
                  <View key={section.id} className="gap-3">
                    <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{section.label}</Text>
                    <View className="gap-3">
                      {section.items.map((item) => (
                        <ElegantCard key={item.id} className="gap-3 p-4" variant="filled" radius="md">
                          <View className="flex-row items-start justify-between gap-3">
                            <View className="flex-1 gap-0.5">
                              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{item.title}</Text>
                              <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.subtitle}</Text>
                            </View>
                            <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.timeLabel}</Text>
                          </View>
                          <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.detail}</Text>
                        </ElegantCard>
                      ))}
                    </View>
                  </View>
                ))}
              </View>
            ) : (
              <EmptyState detail="Non risultano lezioni per la settimana selezionata." title="Orario vuoto" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
