import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import {
  loadAbsencesView,
  type AbsenceRecordViewModel,
} from "@/lib/student-data";

type FilterKey = "all" | AbsenceRecordViewModel["type"];

const FILTER_LABELS: Record<FilterKey, string> = {
  all: "Tutte",
  assenza: "Assenze",
  ritardo: "Ritardi",
  uscita: "Uscite",
};

export default function AbsencesScreen() {
  const [absences, setAbsences] = useState<AbsenceRecordViewModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [filter, setFilter] = useState<FilterKey>("all");
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadAbsencesView();
      setAbsences(data);
    } catch (loadError) {
      console.error("Absences load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare le assenze.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const filteredAbsences = useMemo(() => {
    return absences.filter((item) => {
      if (filter !== "all" && item.type !== filter) {
        return false;
      }

      if (!deferredQuery.trim()) {
        return true;
      }

      const query = deferredQuery.toLowerCase();
      return (
        item.typeLabel.toLowerCase().includes(query) ||
        item.detail.toLowerCase().includes(query) ||
        item.statusLabel.toLowerCase().includes(query) ||
        item.dateLabel.toLowerCase().includes(query)
      );
    });
  }, [absences, deferredQuery, filter]);

  const stats = useMemo(() => {
    const totalAbsences = absences.filter((item) => item.type === "assenza").length;
    const totalLates = absences.filter((item) => item.type === "ritardo").length;
    const totalExits = absences.filter((item) => item.type === "uscita").length;
    const pending = absences.filter((item) => !item.justified).length;

    return [
      { id: "absences", label: "Assenze", value: String(totalAbsences), tone: "neutral" as const },
      { id: "lates", label: "Ritardi", value: String(totalLates), tone: "warning" as const },
      { id: "exits", label: "Uscite", value: String(totalExits), tone: "primary" as const },
      {
        id: "pending",
        label: "Da giustificare",
        value: String(pending),
        tone: pending > 0 ? ("error" as const) : ("success" as const),
      },
    ];
  }, [absences]);

  const pendingCount = useMemo(() => absences.filter((item) => !item.justified).length, [absences]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando le assenze"
          detail="Recupero cronologia, ritardi e stato delle giustificazioni."
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
              void loadData();
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            eyebrow="Presenze"
            subtitle="Storico completo, stato delle giustificazioni e vista piu leggibile degli eventi."
            title="Assenze"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <ElegantCard className="gap-4 p-5" tone={pendingCount > 0 ? "warning" : "success"} variant="filled">
            <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
              Situazione attuale
            </Text>
            <Text className="text-2xl font-semibold text-foreground">
              {pendingCount > 0
                ? `${pendingCount} eventi da giustificare`
                : "Nessuna giustificazione in sospeso"}
            </Text>
            <Text className="text-sm leading-6 text-muted">
              Tieni sotto controllo ritardi, uscite e assenze non ancora chiuse.
            </Text>
          </ElegantCard>

          <View className="flex-row flex-wrap gap-3">
            {stats.map((stat) => (
              <ElegantCard
                key={stat.id}
                className="min-w-[160px] flex-1 gap-2 p-4"
                tone={stat.tone}
                variant="filled"
              >
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                  {stat.label}
                </Text>
                <Text className="text-3xl font-semibold text-foreground">{stat.value}</Text>
              </ElegantCard>
            ))}
          </View>

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca per tipo, stato o dettaglio"
            value={searchQuery}
          />

          <View className="gap-3">
            <SectionTitle eyebrow="Filtro" title="Riduci la cronologia" />
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View className="flex-row gap-3">
                {(Object.keys(FILTER_LABELS) as FilterKey[]).map((key) => (
                  <Pressable key={key} onPress={() => setFilter(key)}>
                    <ElegantCard
                      className="px-4 py-3"
                      tone={filter === key ? "primary" : "neutral"}
                      variant={filter === key ? "filled" : "outlined"}
                    >
                      <Text className="text-sm font-semibold text-foreground">
                        {FILTER_LABELS[key]}
                      </Text>
                    </ElegantCard>
                  </Pressable>
                ))}
              </View>
            </ScrollView>
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Cronologia" title="Tutti gli eventi registrati" />
            {filteredAbsences.length > 0 ? (
              <View className="gap-3">
                {filteredAbsences.map((absence) => (
                  <ElegantCard key={absence.id} className="gap-4 p-4" tone={absence.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-4">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">
                          {absence.typeLabel}
                        </Text>
                        <Text className="text-sm text-muted">{absence.dateLabel}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {absence.statusLabel}
                      </Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{absence.detail}</Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Prova a cambiare filtro o ricerca per vedere altri eventi."
                title="Nessuna assenza trovata"
              />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
