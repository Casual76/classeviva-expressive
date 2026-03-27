import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { MiniBarChart } from "@/components/ui/mini-bar-chart";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useColors } from "@/hooks/use-colors";
import {
  loadGradesView,
  summarizeGrades,
  type GradeRowViewModel,
} from "@/lib/student-data";

export default function GradesScreen() {
  const colors = useColors();
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
        contentContainerStyle={{ paddingBottom: 100 }}
        refreshControl={
          <RefreshControl
            onRefresh={() => { setIsRefreshing(true); void loadData(); }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              eyebrow="Valutazioni"
              subtitle="Medie per materia, andamento recente e lettura più compatta delle prove."
              title="Voti"
            />
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
            <ElegantCard className="gap-4 p-5" variant="elevated" radius="lg">
              <View className="flex-row gap-3">
                <ElegantCard className="flex-1 gap-1.5 p-4" tone="primary" variant="filled" radius="md">
                  <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Media vista</Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{summaries.averageLabel}</Text>
                </ElegantCard>
                <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                  <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Valutazioni</Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{filteredGrades.length}</Text>
                </ElegantCard>
              </View>
              {summaries.trend.length > 0 ? <MiniBarChart points={summaries.trend} /> : null}
            </ElegantCard>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca materia, docente o note"
              value={searchQuery}
            />
          </AnimatedListItem>

          {subjects.length > 0 ? (
            <AnimatedListItem index={4}>
              <View className="gap-3">
                <SectionTitle eyebrow="Materie" title="Filtra la vista" />
                <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-2">
                    <M3Chip label="Tutte" selected={selectedSubject === null} onPress={() => setSelectedSubject(null)} />
                    {subjects.map((subject) => (
                      <M3Chip key={subject} label={subject} selected={selectedSubject === subject} onPress={() => setSelectedSubject(subject)} />
                    ))}
                  </View>
                </ScrollView>
              </View>
            </AnimatedListItem>
          ) : null}

          {summaries.subjectSummaries.length > 0 ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Quadro per materia" title="Dove stai andando meglio" />
              <View className="gap-3">
                {summaries.subjectSummaries.map((item, i) => (
                  <AnimatedListItem key={item.subject} index={5 + i}>
                    <ElegantCard className="gap-2 p-4" tone={item.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-4">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{item.subject}</Text>
                          <Text className="text-xs leading-4" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.teacherLabel}</Text>
                        </View>
                        <Text className="text-2xl font-light" style={{ color: colors.foreground }}>{item.averageLabel}</Text>
                      </View>
                      <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {item.count} valutazioni considerate
                      </Text>
                    </ElegantCard>
                  </AnimatedListItem>
                ))}
              </View>
            </View>
          ) : null}

          <View className="gap-3">
            <SectionTitle eyebrow="Storico" title="Elenco completo" />
            {filteredGrades.length > 0 ? (
              <View className="gap-3">
                {filteredGrades.map((grade, i) => (
                  <AnimatedListItem key={grade.id} index={8 + i}>
                    <ElegantCard className="gap-3 p-4" tone={grade.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-4">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{grade.subject}</Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{grade.typeLabel}</Text>
                        </View>
                        <Text className="text-4xl font-light" style={{ color: colors.foreground }}>{grade.valueLabel}</Text>
                      </View>
                      <View
                        className="gap-2 pt-3"
                        style={{ borderTopWidth: 1, borderTopColor: colors.outlineVariant ?? colors.border }}
                      >
                        <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{grade.detail}</Text>
                        <Text className="text-xs" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{grade.teacherLabel}</Text>
                        <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                          {grade.dateLabel} · {grade.periodLabel}
                        </Text>
                      </View>
                    </ElegantCard>
                  </AnimatedListItem>
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
