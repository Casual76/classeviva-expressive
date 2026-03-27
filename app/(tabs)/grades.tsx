import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { MiniBarChart } from "@/components/ui/mini-bar-chart";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import {
  loadGradesView,
  summarizeGrades,
  type GradeRowViewModel,
} from "@/lib/student-data";

export default function GradesScreen() {
  const [grades, setGrades] = useState<GradeRowViewModel[]>([]);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadGradesView();
      setGrades(data);
    } catch (loadError) {
      console.error("Grades load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare i voti.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const filteredGrades = useMemo(() => {
    return grades.filter((grade) => {
      if (selectedSubject && grade.subject !== selectedSubject) {
        return false;
      }

      if (!deferredQuery.trim()) {
        return true;
      }

      const query = deferredQuery.toLowerCase();
      return (
        grade.subject.toLowerCase().includes(query) ||
        grade.typeLabel.toLowerCase().includes(query) ||
        grade.detail.toLowerCase().includes(query) ||
        grade.teacherLabel.toLowerCase().includes(query)
      );
    });
  }, [grades, selectedSubject, deferredQuery]);

  const summaries = useMemo(() => summarizeGrades(filteredGrades), [filteredGrades]);
  const subjects = useMemo(
    () => Array.from(new Set(grades.map((grade) => grade.subject).filter(Boolean))).sort((a, b) => a.localeCompare(b, "it-IT")),
    [grades],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState title="Sto caricando i voti" detail="Recupero medie, andamento e storico per materia." />
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
            eyebrow="Valutazioni"
            subtitle="Medie per materia, andamento recente e lettura piu compatta delle prove."
            title="Voti"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <ElegantCard className="gap-5 p-5" variant="elevated">
            <View className="flex-row gap-3">
              <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Media vista</Text>
                <Text className="text-3xl font-semibold text-foreground">{summaries.averageLabel}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Valutazioni</Text>
                <Text className="text-3xl font-semibold text-foreground">{filteredGrades.length}</Text>
              </ElegantCard>
            </View>

            {summaries.trend.length > 0 ? <MiniBarChart points={summaries.trend} /> : null}
          </ElegantCard>

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca materia, docente o note"
            value={searchQuery}
          />

          {subjects.length > 0 ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Materie" title="Filtra la vista" />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-3">
                  <Pressable onPress={() => setSelectedSubject(null)}>
                    <ElegantCard className="px-4 py-3" tone={selectedSubject === null ? "primary" : "neutral"} variant={selectedSubject === null ? "filled" : "outlined"}>
                      <Text className="text-sm font-semibold text-foreground">Tutte</Text>
                    </ElegantCard>
                  </Pressable>
                  {subjects.map((subject) => (
                    <Pressable key={subject} onPress={() => setSelectedSubject(subject)}>
                      <ElegantCard className="px-4 py-3" tone={selectedSubject === subject ? "primary" : "neutral"} variant={selectedSubject === subject ? "filled" : "outlined"}>
                        <Text className="text-sm font-semibold text-foreground">{subject}</Text>
                      </ElegantCard>
                    </Pressable>
                  ))}
                </View>
              </ScrollView>
            </View>
          ) : null}

          {summaries.subjectSummaries.length > 0 ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Quadro per materia" title="Dove stai andando meglio" />
              <View className="gap-3">
                {summaries.subjectSummaries.map((item) => (
                  <ElegantCard key={item.subject} className="gap-2 p-4" tone={item.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-4">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{item.subject}</Text>
                        <Text className="text-xs leading-5 text-muted">{item.teacherLabel}</Text>
                      </View>
                      <Text className="text-2xl font-semibold text-foreground">{item.averageLabel}</Text>
                    </View>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                      {item.count} valutazioni considerate
                    </Text>
                  </ElegantCard>
                ))}
              </View>
            </View>
          ) : null}

          <View className="gap-3">
            <SectionTitle eyebrow="Storico" title="Elenco completo" />
            {filteredGrades.length > 0 ? (
              <View className="gap-3">
                {filteredGrades.map((grade) => (
                  <ElegantCard key={grade.id} className="gap-4 p-4" tone={grade.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-4">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{grade.subject}</Text>
                        <Text className="text-sm text-muted">{grade.typeLabel}</Text>
                      </View>
                      <Text className="text-4xl font-semibold text-foreground">{grade.valueLabel}</Text>
                    </View>
                    <View className="gap-2 border-t border-border pt-3">
                      <Text className="text-sm leading-6 text-muted">{grade.detail}</Text>
                      <Text className="text-xs text-muted">{grade.teacherLabel}</Text>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {grade.dateLabel} · {grade.periodLabel}
                      </Text>
                    </View>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState detail="Cambia filtro o ricerca per rivedere altre valutazioni." title="Nessun voto trovato" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
