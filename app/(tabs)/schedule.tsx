import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { DayScheduleGrid } from "@/components/ui/day-schedule-grid";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import {
  compareTimeLabels,
  groupAgendaByDate,
  loadAgendaView,
  toLocalIsoDate,
  type AgendaItemViewModel,
} from "@/lib/student-data";

function startOfWeek(date: Date) {
  const current = new Date(date);
  const day = (current.getDay() + 6) % 7;
  current.setHours(0, 0, 0, 0);
  current.setDate(current.getDate() - day);
  return current;
}

function buildWeekDays(weekStart: Date) {
  const todayIso = toLocalIsoDate();

  return Array.from({ length: 7 }, (_, index) => {
    const date = new Date(weekStart);
    date.setDate(weekStart.getDate() + index);

    return {
      isoDate: toLocalIsoDate(date),
      shortLabel: new Intl.DateTimeFormat("it-IT", { weekday: "short" }).format(date),
      numericLabel: new Intl.DateTimeFormat("it-IT", { day: "numeric" }).format(date),
      isToday: toLocalIsoDate(date) === todayIso,
    };
  });
}

function shiftWeek(date: Date, amount: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + amount * 7);
  return next;
}

function sortLessons(left: AgendaItemViewModel, right: AgendaItemViewModel) {
  const slotDiff = (left.slot ?? Number.MAX_SAFE_INTEGER) - (right.slot ?? Number.MAX_SAFE_INTEGER);
  if (slotDiff !== 0) {
    return slotDiff;
  }

  return compareTimeLabels(left.timeLabel, right.timeLabel);
}

export default function ScheduleScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [currentWeekStart, setCurrentWeekStart] = useState(() => startOfWeek(new Date()));
  const [selectedDate, setSelectedDate] = useState(() => toLocalIsoDate(new Date()));
  const [lessons, setLessons] = useState<AgendaItemViewModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(
    async (weekStart: Date) => {
      try {
        setError(null);
        const weekEnd = shiftWeek(weekStart, 1);
        weekEnd.setDate(weekEnd.getDate() - 1);
        const data = await loadAgendaView(toLocalIsoDate(weekStart), toLocalIsoDate(weekEnd));
        const lessonOnly = data.filter((item) => item.category === "lesson");
        const todayIso = toLocalIsoDate();
        const weekDates = buildWeekDays(weekStart).map((day) => day.isoDate);
        const nextSelectedDate =
          lessonOnly.find((item) => item.date === selectedDate)?.date ??
          lessonOnly.find((item) => item.date === todayIso)?.date ??
          weekDates.find((date) => date === todayIso) ??
          weekDates[0] ??
          toLocalIsoDate(weekStart);

        setLessons(lessonOnly);
        setSelectedDate(nextSelectedDate);
      } catch (loadError) {
        console.error("Schedule load failed", loadError);
        setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare l'orario.");
      } finally {
        setIsLoading(false);
        setIsRefreshing(false);
      }
    },
    [selectedDate],
  );

  useEffect(() => {
    void loadData(currentWeekStart);
  }, [currentWeekStart, loadData]);

  const weekDays = useMemo(() => buildWeekDays(currentWeekStart), [currentWeekStart]);
  const selectedDayItems = useMemo(
    () => lessons.filter((item) => item.date === selectedDate).sort(sortLessons),
    [lessons, selectedDate],
  );
  const sections = useMemo(() => groupAgendaByDate(lessons), [lessons]);
  const weekLabel = useMemo(() => {
    const weekEnd = shiftWeek(currentWeekStart, 1);
    weekEnd.setDate(weekEnd.getDate() - 1);
    return `${currentWeekStart.toLocaleDateString("it-IT", { day: "numeric", month: "short" })} - ${weekEnd.toLocaleDateString("it-IT", { day: "numeric", month: "short" })}`;
  }, [currentWeekStart]);
  const selectedDayLabel = useMemo(() => {
    const [year, month, day] = selectedDate.split("-").map(Number);
    const date = new Date(year, (month ?? 1) - 1, day ?? 1);
    return new Intl.DateTimeFormat("it-IT", {
      weekday: "long",
      day: "numeric",
      month: "long",
    }).format(date);
  }, [selectedDate]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Sto ricostruendo la settimana usando lezioni reali e fallback dall'agenda."
          title="Carico l'orario"
          variant="skeleton"
        />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 120 }}
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
        <View className="gap-6 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              backLabel="Altro"
              eyebrow="Lezioni"
              onBack={() => router.replace("/(tabs)/more")}
              subtitle="La griglia usa gli orari reali quando ci sono, altrimenti ricostruisce la sequenza delle ore."
              title="Orario"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <RegisterListRow
                detail={error}
                meta="Se alcune lezioni non arrivano complete, il resto della settimana resta consultabile."
                title="Aggiornamento parziale"
                tone="warning"
              />
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
              <View className="flex-row items-center justify-between gap-4">
                <View className="gap-1">
                  <Text
                    className="text-[11px] font-medium uppercase tracking-[1.2px]"
                    style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                  >
                    Settimana
                  </Text>
                  <Text className="text-[24px] leading-[30px] font-medium" style={{ color: colors.foreground }}>
                    {weekLabel}
                  </Text>
                </View>
                <View className="flex-row gap-2">
                  <Pressable
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                    onPress={() => setCurrentWeekStart((prev) => shiftWeek(prev, -1))}
                  >
                    <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="chevron-left" size={20} />
                  </Pressable>
                  <Pressable
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                    onPress={() => setCurrentWeekStart((prev) => shiftWeek(prev, 1))}
                  >
                    <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="chevron-right" size={20} />
                  </Pressable>
                </View>
              </View>

              <View className="flex-row flex-wrap gap-2">
                <M3Chip
                  label="Questa settimana"
                  onPress={() => {
                    setCurrentWeekStart(startOfWeek(new Date()));
                    setSelectedDate(toLocalIsoDate(new Date()));
                  }}
                  selected={toLocalIsoDate(currentWeekStart) === toLocalIsoDate(startOfWeek(new Date()))}
                />
                <StatusBadge label={`${selectedDayItems.length} lezioni nel giorno`} tone={selectedDayItems.length > 0 ? "primary" : "neutral"} />
              </View>
            </ElegantCard>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="gap-3">
              <SectionTitle eyebrow="Giorni" title="Seleziona il giorno" />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-3">
                  {weekDays.map((day) => {
                    const count = lessons.filter((item) => item.date === day.isoDate).length;
                    const isSelected = selectedDate === day.isoDate;

                    return (
                      <Pressable key={day.isoDate} onPress={() => setSelectedDate(day.isoDate)}>
                        <ElegantCard
                          className="min-w-[86px] items-center gap-1.5 px-4 py-3"
                          radius="md"
                          tone={isSelected ? "primary" : "neutral"}
                          variant={isSelected ? "filled" : "elevated"}
                        >
                          <Text
                            className="text-[11px] font-medium uppercase tracking-[1.2px]"
                            style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                          >
                            {day.shortLabel}
                          </Text>
                          <Text className="text-xl font-medium" style={{ color: colors.foreground }}>
                            {day.numericLabel}
                          </Text>
                          <StatusBadge label={day.isToday ? "Oggi" : `${count} lezioni`} tone={day.isToday ? "primary" : "neutral"} />
                        </ElegantCard>
                      </Pressable>
                    );
                  })}
                </View>
              </ScrollView>
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <View className="gap-3">
              <SectionTitle
                eyebrow="Giorno selezionato"
                title={selectedDayLabel}
                detail="Se il portale non espone un orario preciso, la griglia usa l'ordine delle ore per non lasciare buchi."
              />
              {selectedDayItems.length > 0 ? (
                <ElegantCard className="gap-4 p-4" radius="lg" variant="elevated">
                  <DayScheduleGrid lessons={selectedDayItems} />
                  <View className="gap-3">
                    {selectedDayItems.map((item) => (
                      <RegisterListRow
                        key={item.id}
                        detail={item.detail}
                        meta={item.timeLabel}
                        subtitle={item.title !== item.subtitle ? item.title : undefined}
                        title={item.subtitle}
                        tone="neutral"
                      />
                    ))}
                  </View>
                </ElegantCard>
              ) : (
                <EmptyState detail="Per il giorno selezionato non risultano lezioni nel portale." title="Nessuna lezione registrata" />
              )}
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Settimana" title="Panoramica compatta" detail="Una lettura di sicurezza quando vuoi controllare tutta la settimana in sequenza." />
            {sections.length > 0 ? (
              <View className="gap-5">
                {sections.map((section, index) => (
                  <AnimatedListItem key={section.id} index={10 + index}>
                    <View className="gap-3">
                      <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                        {section.label}
                      </Text>
                      <View className="gap-3">
                        {section.items.map((item) => (
                          <RegisterListRow
                            key={item.id}
                            detail={item.detail}
                            meta={item.timeLabel}
                            subtitle={item.title !== item.subtitle ? item.title : undefined}
                            title={item.subtitle}
                            tone="neutral"
                          />
                        ))}
                      </View>
                    </View>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Nessuna lezione trovata per la settimana selezionata." title="Orario vuoto" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
