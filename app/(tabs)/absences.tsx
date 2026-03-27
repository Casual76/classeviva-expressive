import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useColors } from "@/hooks/use-colors";
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
  const colors = useColors();
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
      if (filter !== "all" && item.type !== filter) return false;
      if (!deferredQuery.trim()) return true;
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
      { id: "pending", label: "Da giustificare", value: String(pending), tone: pending > 0 ? ("error" as const) : ("success" as const) },
    ];
  }, [absences]);

  const pendingCount = useMemo(() => absences.filter((item) => !item.justified).length, [absences]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState title="Sto caricando le assenze" detail="Recupero cronologia, ritardi e stato delle giustificazioni." />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 100 }}
        refreshControl={<RefreshControl onRefresh={() => { setIsRefreshing(true); void loadData(); }} refreshing={isRefreshing} />}
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader eyebrow="Presenze" subtitle="Storico completo, stato delle giustificazioni e vista più leggibile degli eventi." title="Assenze" />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md">
                <Text className="text-sm font-medium" style={{ color: colors.foreground }}>Aggiornamento parziale</Text>
                <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{error}</Text>
              </ElegantCard>
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <ElegantCard className="gap-3 p-5" tone={pendingCount > 0 ? "warning" : "success"} variant="filled" radius="lg">
              <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                Situazione attuale
              </Text>
              <Text className="text-xl font-normal" style={{ color: colors.foreground }}>
                {pendingCount > 0 ? `${pendingCount} eventi da giustificare` : "Nessuna giustificazione in sospeso"}
              </Text>
              <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                Tieni sotto controllo ritardi, uscite e assenze non ancora chiuse.
              </Text>
            </ElegantCard>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="flex-row flex-wrap gap-3">
              {stats.map((stat) => (
                <ElegantCard key={stat.id} className="min-w-[150px] flex-1 gap-1.5 p-4" tone={stat.tone} variant="filled" radius="md">
                  <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{stat.label}</Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{stat.value}</Text>
                </ElegantCard>
              ))}
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <SearchBar onChangeText={setSearchQuery} onClear={() => setSearchQuery("")} placeholder="Cerca per tipo, stato o dettaglio" value={searchQuery} />
          </AnimatedListItem>

          <AnimatedListItem index={5}>
            <View className="gap-3">
              <SectionTitle eyebrow="Filtro" title="Riduci la cronologia" />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-2">
                  {(Object.keys(FILTER_LABELS) as FilterKey[]).map((key) => (
                    <M3Chip key={key} label={FILTER_LABELS[key]} selected={filter === key} onPress={() => setFilter(key)} />
                  ))}
                </View>
              </ScrollView>
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Cronologia" title="Tutti gli eventi registrati" />
            {filteredAbsences.length > 0 ? (
              <View className="gap-3">
                {filteredAbsences.map((absence, i) => (
                  <AnimatedListItem key={absence.id} index={6 + i}>
                    <ElegantCard className="gap-3 p-4" tone={absence.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-4">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{absence.typeLabel}</Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{absence.dateLabel}</Text>
                        </View>
                        <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{absence.statusLabel}</Text>
                      </View>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{absence.detail}</Text>
                    </ElegantCard>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Prova a cambiare filtro o ricerca per vedere altri eventi." title="Nessuna assenza trovata" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
