import { useRouter } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { MetricTile } from "@/components/ui/metric-tile";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { loadAbsencesView, type AbsenceRecordViewModel } from "@/lib/student-data";

type FilterKey = "all" | AbsenceRecordViewModel["type"];

const FILTER_LABELS: Record<FilterKey, string> = {
  all: "Tutte",
  assenza: "Assenze",
  ritardo: "Ritardi",
  uscita: "Uscite",
};

export default function AbsencesScreen() {
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
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
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare assenze e giustificazioni.");
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

  const totalAbsences = useMemo(() => absences.filter((item) => item.type === "assenza").length, [absences]);
  const totalLates = useMemo(() => absences.filter((item) => item.type === "ritardo").length, [absences]);
  const totalExits = useMemo(() => absences.filter((item) => item.type === "uscita").length, [absences]);
  const pendingCount = useMemo(() => absences.filter((item) => !item.justified).length, [absences]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Sto preparando cronologia, ritardi, uscite e stato delle giustificazioni."
          title="Carico le assenze"
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
              void loadData();
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
              eyebrow="Presenze"
              onBack={() => router.replace("/(tabs)/more")}
              subtitle="Assenze, ritardi e uscite restano separati, con attenzione prioritaria solo a cio che va giustificato."
              title="Assenze"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <RegisterListRow
                detail={error}
                meta="La cronologia gia disponibile resta comunque consultabile."
                title="Aggiornamento parziale"
                tone="warning"
              />
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <RegisterListRow
              detail={
                pendingCount > 0
                  ? "Verifica le giustificazioni ancora aperte: vengono messe in evidenza senza trattare tutto come emergenza."
                  : "Situazione allineata: non risultano eventi ancora da giustificare."
              }
              meta="Stato attuale"
              title={pendingCount > 0 ? `${pendingCount} eventi da chiudere` : "Nessuna giustificazione in sospeso"}
              tone={pendingCount > 0 ? "warning" : "success"}
              trailing={<StatusBadge label={pendingCount > 0 ? "Da seguire" : "Allineato"} tone={pendingCount > 0 ? "warning" : "success"} />}
            />
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="flex-row flex-wrap gap-3">
              <MetricTile detail="Assenze registrate." label="Assenze" value={String(totalAbsences)} />
              <MetricTile detail="Ritardi registrati." label="Ritardi" tone="warning" value={String(totalLates)} />
              <MetricTile detail="Uscite anticipate registrate." label="Uscite" value={String(totalExits)} />
              <MetricTile
                detail="Eventi ancora da giustificare."
                label="Da giustificare"
                tone={pendingCount > 0 ? "warning" : "success"}
                value={String(pendingCount)}
              />
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca per tipo, stato o dettaglio"
              value={searchQuery}
            />
          </AnimatedListItem>

          <AnimatedListItem index={5}>
            <View className="gap-3">
              <SectionTitle eyebrow="Filtro" title="Riduci la cronologia" />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-2">
                  {(Object.keys(FILTER_LABELS) as FilterKey[]).map((key) => (
                    <M3Chip key={key} label={FILTER_LABELS[key]} onPress={() => setFilter(key)} selected={filter === key} />
                  ))}
                </View>
              </ScrollView>
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Cronologia" title="Tutti gli eventi registrati" detail="Ogni riga distingue tipologia, data e stato della giustificazione." />
            {filteredAbsences.length > 0 ? (
              <View className="gap-3">
                {filteredAbsences.map((absence, index) => (
                  <AnimatedListItem key={absence.id} index={6 + index}>
                    <RegisterListRow
                      detail={absence.detail}
                      meta={absence.typeLabel}
                      subtitle={absence.dateLabel}
                      title={absence.typeLabel}
                      tone={absence.tone}
                      trailing={
                        <StatusBadge
                          label={absence.statusLabel}
                          tone={!absence.justified ? (absence.type === "assenza" ? "error" : "warning") : "neutral"}
                        />
                      }
                    />
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Prova a cambiare filtro o ricerca per rivedere altri eventi." title="Nessun evento trovato" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
