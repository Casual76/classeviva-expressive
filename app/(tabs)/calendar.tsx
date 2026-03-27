import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { CalendarGrid } from "@/components/ui/calendar-grid";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useColors } from "@/hooks/use-colors";
import {
  buildCalendarMonth,
  groupAgendaByDate,
  loadAgendaView,
  toLocalIsoDate,
  type AgendaItemViewModel,
} from "@/lib/student-data";

type CategoryFilter = "all" | AgendaItemViewModel["category"];

const CATEGORY_LABELS: Record<CategoryFilter, string> = {
  all: "Tutto",
  assessment: "Verifiche",
  homework: "Compiti",
  lesson: "Lezioni",
  event: "Eventi",
};

const CATEGORY_ORDER: CategoryFilter[] = ["all", "assessment", "homework", "lesson", "event"];

function monthRange(date: Date) {
  const start = new Date(date.getFullYear(), date.getMonth(), 1);
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0);
  return {
    start: toLocalIsoDate(start),
    end: toLocalIsoDate(end),
  };
}

function isDateWithinRange(value: string, start: string, end: string) {
  return value >= start && value <= end;
}

export default function CalendarScreen() {
  const colors = useColors();
  const [items, setItems] = useState<AgendaItemViewModel[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [category, setCategory] = useState<CategoryFilter>("all");
  const [currentMonth, setCurrentMonth] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => toLocalIsoDate(new Date()));
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async (monthDate: Date) => {
    try {
      const range = monthRange(monthDate);
      setError(null);
      const data = await loadAgendaView(range.start, range.end);
      setItems(data);
      setSelectedDate((currentSelected) =>
        isDateWithinRange(currentSelected, range.start, range.end) ? currentSelected : range.start,
      );
    } catch (loadError) {
      console.error("Agenda load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare l'agenda.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData(currentMonth);
  }, [currentMonth, loadData]);

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (category !== "all" && item.category !== category) {
        return false;
      }
      if (!deferredQuery.trim()) {
        return true;
      }
      const query = deferredQuery.toLowerCase();
      return (
        item.title.toLowerCase().includes(query) ||
        item.subtitle.toLowerCase().includes(query) ||
        item.detail.toLowerCase().includes(query)
      );
    });
  }, [items, category, deferredQuery]);

  const calendarDays = useMemo(() => buildCalendarMonth(currentMonth, filteredItems), [currentMonth, filteredItems]);
  const selectedDayItems = useMemo(
    () => filteredItems.filter((item) => item.date === selectedDate),
    [filteredItems, selectedDate],
  );
  const sections = useMemo(() => groupAgendaByDate(filteredItems), [filteredItems]);
  const monthLabel = useMemo(
    () =>
      new Intl.DateTimeFormat("it-IT", {
        month: "long",
        year: "numeric",
      }).format(currentMonth),
    [currentMonth],
  );
  const selectedDateLabel = useMemo(() => {
    const [year, month, day] = selectedDate.split("-").map(Number);
    const date = new Date(year, (month ?? 1) - 1, day ?? 1);
    return date.toLocaleDateString("it-IT", { weekday: "long", day: "numeric", month: "long" });
  }, [selectedDate]);
  const monthSectionBaseIndex = 4 + selectedDayItems.length;

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Recupero calendario, lezioni e scadenze del mese."
          title="Sto preparando l'agenda"
          variant="skeleton"
        />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 100 }}
        refreshControl={
          <RefreshControl
            onRefresh={() => {
              setIsRefreshing(true);
              void loadData(currentMonth);
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              subtitle="Calendario mensile, vista giorno e lettura unica di lezioni, compiti e verifiche."
              title="Agenda"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md">
                <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                  Aggiornamento parziale
                </Text>
                <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                  {error}
                </Text>
              </ElegantCard>
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca una lezione o una scadenza"
              value={searchQuery}
            />
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="gap-3">
              <View className="flex-row items-center justify-between">
                <SectionTitle eyebrow="Mese" title={monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1)} />
                <View className="flex-row gap-2">
                  <Pressable
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                    onPress={() => setCurrentMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                  >
                    <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="chevron-left" size={20} />
                  </Pressable>
                  <Pressable
                    className="h-10 w-10 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                    onPress={() => setCurrentMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                  >
                    <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="chevron-right" size={20} />
                  </Pressable>
                </View>
              </View>

              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-2">
                  {CATEGORY_ORDER.map((key) => (
                    <M3Chip key={key} label={CATEGORY_LABELS[key]} onPress={() => setCategory(key)} selected={category === key} />
                  ))}
                </View>
              </ScrollView>

              <CalendarGrid days={calendarDays} onSelectDate={setSelectedDate} selectedDate={selectedDate} />
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Giorno selezionato" title={selectedDateLabel} />
            {selectedDayItems.length > 0 ? (
              <View className="gap-3">
                {selectedDayItems.map((item, index) => (
                  <AnimatedListItem key={item.id} index={4 + index}>
                    <ElegantCard className="gap-3 p-4" tone={item.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-3">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                            {item.title}
                          </Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {item.subtitle}
                          </Text>
                        </View>
                        <Text
                          className="text-[11px] font-medium uppercase tracking-[1.5px]"
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
                          {CATEGORY_LABELS[item.category]}
                        </Text>
                      </View>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {item.detail}
                      </Text>
                      <Text
                        className="text-[11px] font-medium uppercase tracking-[1.5px]"
                        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                      >
                        {item.timeLabel}
                      </Text>
                    </ElegantCard>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Non ci sono lezioni o scadenze da mostrare per questa data." title="Giornata libera" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Vista elenco" title="Tutto il mese" />
            {sections.length > 0 ? (
              <View className="gap-4">
                {sections.map((section, index) => (
                  <AnimatedListItem key={section.id} index={monthSectionBaseIndex + index}>
                    <View className="gap-3">
                      <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                        {section.label}
                      </Text>
                      <View className="gap-3">
                        {section.items.map((item) => (
                          <ElegantCard key={item.id} className="gap-3 p-4" tone={item.tone} variant="filled" radius="md">
                            <View className="flex-row items-start justify-between gap-3">
                              <View className="flex-1 gap-0.5">
                                <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                                  {item.title}
                                </Text>
                                <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                                  {item.subtitle}
                                </Text>
                              </View>
                              <Text
                                className="text-[11px] font-medium uppercase tracking-[1.5px]"
                                style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                              >
                                {item.timeLabel}
                              </Text>
                            </View>
                            <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                              {item.detail}
                            </Text>
                          </ElegantCard>
                        ))}
                      </View>
                    </View>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Filtri e ricerca non restituiscono risultati in questo momento." title="Nessuna voce da mostrare" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
