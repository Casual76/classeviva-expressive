import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { CalendarGrid } from "@/components/ui/calendar-grid";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import {
  buildCalendarMonth,
  groupAgendaByDate,
  loadAgendaView,
  type AgendaItemViewModel,
} from "@/lib/student-data";

type CategoryFilter = "all" | AgendaItemViewModel["category"];

const CATEGORY_LABELS: Record<CategoryFilter, string> = {
  all: "Tutto",
  lesson: "Lezioni",
  homework: "Compiti",
  assessment: "Verifiche",
  event: "Eventi",
};

function monthRange(date: Date) {
  const start = new Date(date.getFullYear(), date.getMonth(), 1);
  const end = new Date(date.getFullYear(), date.getMonth() + 1, 0);
  return {
    start: start.toISOString().slice(0, 10),
    end: end.toISOString().slice(0, 10),
  };
}

export default function CalendarScreen() {
  const [items, setItems] = useState<AgendaItemViewModel[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [category, setCategory] = useState<CategoryFilter>("all");
  const [currentMonth, setCurrentMonth] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async (monthDate: Date) => {
    try {
      const range = monthRange(monthDate);
      setError(null);
      const data = await loadAgendaView(range.start, range.end);
      setItems(data);
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

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState title="Sto preparando l'agenda" detail="Recupero calendario, lezioni e scadenze del mese." />
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
              void loadData(currentMonth);
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            eyebrow="Agenda"
            subtitle="Calendario mensile, vista giorno e lettura unica di lezioni, compiti e verifiche."
            title="Agenda"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca una lezione o una scadenza"
            value={searchQuery}
          />

          <View className="gap-3">
            <View className="flex-row items-center justify-between">
              <SectionTitle eyebrow="Mese" title={monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1)} />
              <View className="flex-row gap-2">
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full border border-border bg-surface"
                  onPress={() => setCurrentMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() - 1, 1))}
                >
                  <MaterialIcons name="west" size={18} />
                </Pressable>
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full border border-border bg-surface"
                  onPress={() => setCurrentMonth((prev) => new Date(prev.getFullYear(), prev.getMonth() + 1, 1))}
                >
                  <MaterialIcons name="east" size={18} />
                </Pressable>
              </View>
            </View>

            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View className="flex-row gap-3">
                {(Object.keys(CATEGORY_LABELS) as CategoryFilter[]).map((key) => (
                  <Pressable key={key} onPress={() => setCategory(key)}>
                    <ElegantCard className="px-4 py-3" tone={category === key ? "primary" : "neutral"} variant={category === key ? "filled" : "outlined"}>
                      <Text className="text-sm font-semibold text-foreground">{CATEGORY_LABELS[key]}</Text>
                    </ElegantCard>
                  </Pressable>
                ))}
              </View>
            </ScrollView>

            <CalendarGrid days={calendarDays} onSelectDate={setSelectedDate} selectedDate={selectedDate} />
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Giorno selezionato" title={new Date(selectedDate).toLocaleDateString("it-IT", { weekday: "long", day: "numeric", month: "long" })} />
            {selectedDayItems.length > 0 ? (
              <View className="gap-3">
                {selectedDayItems.map((item) => (
                  <ElegantCard key={item.id} className="gap-3 p-4" tone={item.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-3">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                        <Text className="text-sm text-muted">{item.subtitle}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{CATEGORY_LABELS[item.category]}</Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{item.timeLabel}</Text>
                  </ElegantCard>
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
                {sections.map((section) => (
                  <View key={section.id} className="gap-3">
                    <Text className="text-sm font-semibold text-foreground">{section.label}</Text>
                    <View className="gap-3">
                      {section.items.map((item) => (
                        <ElegantCard key={item.id} className="gap-3 p-4" tone={item.tone} variant="filled">
                          <View className="flex-row items-start justify-between gap-3">
                            <View className="flex-1 gap-1">
                              <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                              <Text className="text-sm text-muted">{item.subtitle}</Text>
                            </View>
                            <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{item.timeLabel}</Text>
                          </View>
                          <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                        </ElegantCard>
                      ))}
                    </View>
                  </View>
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
