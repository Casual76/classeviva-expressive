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
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useColors } from "@/hooks/use-colors";
import {
  buildCalendarMonth,
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

function sortAgendaItems(left: AgendaItemViewModel, right: AgendaItemViewModel) {
  const slotDiff = (left.slot ?? Number.MAX_SAFE_INTEGER) - (right.slot ?? Number.MAX_SAFE_INTEGER);
  if (slotDiff !== 0) {
    return slotDiff;
  }

  return left.timeLabel.localeCompare(right.timeLabel, "it-IT");
}

export default function AgendaScreen() {
  const colors = useColors();
  const todayIso = useMemo(() => toLocalIsoDate(new Date()), []);
  const [items, setItems] = useState<AgendaItemViewModel[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [category, setCategory] = useState<CategoryFilter>("all");
  const [currentMonth, setCurrentMonth] = useState(() => new Date());
  const [selectedDate, setSelectedDate] = useState(() => todayIso);
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
        isDateWithinRange(currentSelected, range.start, range.end) ? currentSelected : todayIso,
      );
    } catch (loadError) {
      console.error("Agenda load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare l'agenda.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [todayIso]);

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
    () => filteredItems.filter((item) => item.date === selectedDate).sort(sortAgendaItems),
    [filteredItems, selectedDate],
  );

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
          detail="Sto preparando il calendario e la lista del giorno selezionato."
          title="Carico l'agenda"
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
              void loadData(currentMonth);
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              eyebrow="Pianificazione"
              subtitle="Calendario compatto e lista limitata al giorno scelto, senza rumore mensile."
              title="Agenda"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <RegisterListRow
                detail={error}
                meta="Le voci gia sincronizzate restano visibili."
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
                    Mese corrente
                  </Text>
                  <Text className="text-[24px] leading-[30px] font-medium" style={{ color: colors.foreground }}>
                    {monthLabel.charAt(0).toUpperCase() + monthLabel.slice(1)}
                  </Text>
                </View>
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

              <View className="flex-row flex-wrap gap-2">
                <M3Chip
                  label="Vai a oggi"
                  onPress={() => {
                    setCurrentMonth(new Date());
                    setSelectedDate(todayIso);
                  }}
                  selected={selectedDate === todayIso}
                />
                <StatusBadge label={`${selectedDayItems.length} voci nel giorno`} tone={selectedDayItems.length > 0 ? "primary" : "neutral"} />
              </View>
            </ElegantCard>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca compito, lezione o evento"
              value={searchQuery}
            />
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <View className="gap-3">
              <SectionTitle eyebrow="Filtro" title="Riduci la vista" />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-2">
                  {CATEGORY_ORDER.map((key) => (
                    <M3Chip key={key} label={CATEGORY_LABELS[key]} onPress={() => setCategory(key)} selected={category === key} />
                  ))}
                </View>
              </ScrollView>
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={5}>
            <View className="gap-3">
              <SectionTitle
                eyebrow="Calendario"
                title="Seleziona un giorno"
                detail="I puntini, le card e i badge usano sempre la stessa palette per tipo di evento."
              />
              <CalendarGrid days={calendarDays} onSelectDate={setSelectedDate} selectedDate={selectedDate} />
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle
              eyebrow="Giorno selezionato"
              title={selectedDateLabel}
              detail="La lista mostra solo gli elementi del giorno scelto, ordinati per fascia oraria."
            />
            {selectedDayItems.length > 0 ? (
              <View className="gap-3">
                {selectedDayItems.map((item, index) => (
                  <AnimatedListItem key={item.id} index={6 + index}>
                    <RegisterListRow
                      detail={item.detail}
                      meta={item.timeLabel}
                      subtitle={item.subtitle}
                      title={item.title}
                      tone={item.tone}
                      trailing={<StatusBadge label={CATEGORY_LABELS[item.category]} tone={item.tone} />}
                    />
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Nessuna lezione o scadenza per il giorno selezionato." title="Giornata libera" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
