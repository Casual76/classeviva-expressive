import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  Text,
  View,
} from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";
import {
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

export default function CalendarScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [items, setItems] = useState<AgendaItemViewModel[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [category, setCategory] = useState<CategoryFilter>("all");
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mode = isDemoMode ? "demo" : "real";

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadAgendaView(mode);
      setItems(data);
    } catch (loadError) {
      console.error("Agenda load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare l'agenda.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [mode]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadData();
  };

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

  const sections = useMemo(() => groupAgendaByDate(filteredItems), [filteredItems]);
  const upcomingItems = useMemo(() => filteredItems.slice(0, 3), [filteredItems]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color={colors.primary} size="large" />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 112 }}
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            eyebrow="Agenda"
            subtitle="Lezioni, compiti e verifiche raccolti in una sola vista per data."
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

          <ScrollView horizontal showsHorizontalScrollIndicator={false}>
            <View className="flex-row gap-3">
              {(Object.keys(CATEGORY_LABELS) as CategoryFilter[]).map((key) => (
                <Pressable key={key} onPress={() => setCategory(key)}>
                  <ElegantCard
                    className="px-4 py-3"
                    tone={category === key ? "primary" : "neutral"}
                    variant={category === key ? "filled" : "outlined"}
                  >
                    <Text className="text-sm font-semibold text-foreground">{CATEGORY_LABELS[key]}</Text>
                  </ElegantCard>
                </Pressable>
              ))}
            </View>
          </ScrollView>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Subito dopo</Text>
            {upcomingItems.length > 0 ? (
              <View className="gap-3">
                {upcomingItems.map((item) => (
                  <ElegantCard key={`upcoming-${item.id}`} className="gap-3 p-4" tone={item.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-4">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                        <Text className="text-sm text-muted">{item.subtitle}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {item.shortDateLabel}
                      </Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                      {item.timeLabel}
                    </Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <ElegantCard className="gap-2 p-4" variant="filled">
                <Text className="text-sm font-semibold text-foreground">Agenda vuota</Text>
                <Text className="text-sm leading-6 text-muted">
                  Quando ci sono lezioni o scadenze imminenti le vedrai qui.
                </Text>
              </ElegantCard>
            )}
          </View>

          <View className="gap-4">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Per data</Text>
            {sections.length > 0 ? (
              sections.map((section) => (
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
                          <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                            {CATEGORY_LABELS[item.category]}
                          </Text>
                        </View>
                        <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                        <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                          {item.timeLabel}
                        </Text>
                      </ElegantCard>
                    ))}
                  </View>
                </View>
              ))
            ) : (
              <ElegantCard className="gap-2 p-5" variant="filled">
                <Text className="text-sm font-semibold text-foreground">Nessuna voce da mostrare</Text>
                <Text className="text-sm leading-6 text-muted">
                  Filtri e ricerca non restituiscono risultati in questo momento.
                </Text>
              </ElegantCard>
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
