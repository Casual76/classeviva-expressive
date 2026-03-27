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
import { classeviva } from "@/lib/classeviva-client";
import {
  doesGradeMatchPeriod,
  loadGradesView,
  summarizeGrades,
  type GradeRowViewModel,
} from "@/lib/student-data";

type GradePeriodFilter = {
  code: string;
  label: string;
  description: string;
};

export default function GradesScreen() {
  const colors = useColors();
  const [grades, setGrades] = useState<GradeRowViewModel[]>([]);
  const [periods, setPeriods] = useState<GradePeriodFilter[]>([]);
  const [selectedPeriod, setSelectedPeriod] = useState<string | null>(null);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const [gradesData, periodsData] = await Promise.all([
        loadGradesView(),
        classeviva.getPeriods().catch(() => []),
      ]);
      setGrades(gradesData);
      setPeriods(
        periodsData
          .sort((left, right) => left.order - right.order)
          .map((period) => ({
            code: period.code,
            label: period.label,
            description: period.description,
          })),
      );
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

  const selectedPeriodOption = useMemo(
    () => periods.find((period) => period.code === selectedPeriod) ?? null,
    [periods, selectedPeriod],
  );
  const filteredGrades = useMemo(() => {
    return grades.filter((grade) => {
      if (selectedPeriodOption && !doesGradeMatchPeriod(grade, selectedPeriodOption)) {
        return false;
      }
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
  }, [grades, selectedPeriodOption, selectedSubject, deferredQuery]);

  const summaries = useMemo(() => summarizeGrades(filteredGrades), [filteredGrades]);
  const distributionTotal = useMemo(() => {
    return Object.values(summaries.gradeDistribution).reduce((sum, count) => sum + count, 0);
  }, [summaries.gradeDistribution]);
  const subjects = useMemo(
    () =>
      Array.from(new Set(grades.map((grade) => grade.subject).filter(Boolean))).sort((left, right) =>
        left.localeCompare(right, "it-IT"),
      ),
    [grades],
  );
  const hasDistribution = distributionTotal > 0;
  const hasPeriodFilters = periods.length > 1;
  const hasSubjectFilters = subjects.length > 0;
  const distributionIndex = 3;
  const searchIndex = distributionIndex + (hasDistribution ? 1 : 0);
  const periodFilterIndex = searchIndex + 1;
  const subjectFilterIndex = periodFilterIndex + (hasPeriodFilters ? 1 : 0);
  const subjectSummaryBaseIndex = subjectFilterIndex + (hasSubjectFilters ? 1 : 0);
  const historyBaseIndex = subjectSummaryBaseIndex + summaries.subjectSummaries.length;

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Recupero medie, andamento e storico per materia."
          title="Sto caricando i voti"
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
              void loadData();
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              subtitle="Medie per materia, andamento recente e lettura più chiara delle prove registrate."
              title="Voti"
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
            <ElegantCard className="gap-4 p-5" variant="elevated" radius="lg">
              <View className="flex-row gap-3">
                <ElegantCard className="flex-1 gap-1.5 p-4" tone="primary" variant="filled" radius="md">
                  <Text
                    className="text-[11px] font-medium uppercase tracking-[1.5px]"
                    style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                  >
                    Media vista
                  </Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>
                    {summaries.averageLabel}
                  </Text>
                </ElegantCard>
                <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                  <Text
                    className="text-[11px] font-medium uppercase tracking-[1.5px]"
                    style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                  >
                    Valutazioni
                  </Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>
                    {filteredGrades.length}
                  </Text>
                </ElegantCard>
              </View>
              {summaries.trend.length > 0 ? <MiniBarChart points={summaries.trend} /> : null}
            </ElegantCard>
          </AnimatedListItem>

          {hasDistribution ? (
            <AnimatedListItem index={distributionIndex}>
              <ElegantCard className="gap-3 p-4" variant="filled" radius="lg">
                <Text
                  className="text-[11px] font-medium uppercase tracking-[1.5px]"
                  style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                >
                  Distribuzione voti
                </Text>
                <View className="gap-2">
                  {[
                    { label: "Insufficiente", count: summaries.gradeDistribution.insufficient, tone: "error" as const },
                    { label: "Sufficiente", count: summaries.gradeDistribution.sufficient, tone: "warning" as const },
                    { label: "Discreto", count: summaries.gradeDistribution.good, tone: "primary" as const },
                    { label: "Buono", count: summaries.gradeDistribution.veryGood, tone: "success" as const },
                    { label: "Ottimo", count: summaries.gradeDistribution.excellent, tone: "success" as const },
                  ]
                    .filter((row) => row.count > 0)
                    .map((row) => {
                      const pct = distributionTotal > 0 ? row.count / distributionTotal : 0;
                      const barColor =
                        row.tone === "error"
                          ? colors.error
                          : row.tone === "warning"
                            ? colors.warning
                            : row.tone === "success"
                              ? colors.success
                              : colors.primary;

                      return (
                        <View key={row.label} className="flex-row items-center gap-3">
                          <Text className="text-xs w-[80px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {row.label}
                          </Text>
                          <View
                            className="flex-1 h-2 rounded-full overflow-hidden"
                            style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                          >
                            <View
                              style={{
                                width: `${Math.round(pct * 100)}%`,
                                height: "100%",
                                backgroundColor: barColor,
                                borderRadius: 4,
                              }}
                            />
                          </View>
                          <Text className="text-xs w-[20px] text-right font-medium" style={{ color: colors.foreground }}>
                            {row.count}
                          </Text>
                        </View>
                      );
                    })}
                </View>
              </ElegantCard>
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={searchIndex}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca materia, docente o note"
              value={searchQuery}
            />
          </AnimatedListItem>

          {hasPeriodFilters ? (
            <AnimatedListItem index={periodFilterIndex}>
              <View className="gap-3">
                <SectionTitle eyebrow="Periodo" title="Filtra per quadrimestre" />
                <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-2">
                    <M3Chip
                      label="Tutto l'anno"
                      onPress={() => setSelectedPeriod(null)}
                      selected={selectedPeriod === null}
                    />
                    {periods.map((period) => (
                      <M3Chip
                        key={period.code}
                        label={period.label}
                        onPress={() => setSelectedPeriod(period.code)}
                        selected={selectedPeriod === period.code}
                      />
                    ))}
                  </View>
                </ScrollView>
              </View>
            </AnimatedListItem>
          ) : null}

          {hasSubjectFilters ? (
            <AnimatedListItem index={subjectFilterIndex}>
              <View className="gap-3">
                <SectionTitle eyebrow="Materie" title="Filtra la vista" />
                <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-2">
                    <M3Chip label="Tutte" onPress={() => setSelectedSubject(null)} selected={selectedSubject === null} />
                    {subjects.map((subject) => (
                      <M3Chip
                        key={subject}
                        label={subject}
                        onPress={() => setSelectedSubject(subject)}
                        selected={selectedSubject === subject}
                      />
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
                {summaries.subjectSummaries.map((item, index) => (
                  <AnimatedListItem key={item.subject} index={subjectSummaryBaseIndex + index}>
                    <ElegantCard className="gap-2 p-4" tone={item.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-4">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                            {item.subject}
                          </Text>
                          <Text className="text-xs leading-4" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {item.teacherLabel}
                          </Text>
                        </View>
                        <Text className="text-2xl font-light" style={{ color: colors.foreground }}>
                          {item.averageLabel}
                        </Text>
                      </View>
                      <Text
                        className="text-[11px] font-medium uppercase tracking-[1.5px]"
                        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                      >
                        {item.typeBreakdown}
                      </Text>
                      {item.recentValues.length > 1 ? (
                        <View className="mt-1 flex-row gap-1.5">
                          {item.recentValues.map((value, valueIndex) => (
                            <View
                              key={`${item.subject}-${valueIndex}`}
                              style={{
                                width: 8,
                                height: 8,
                                borderRadius: 4,
                                backgroundColor:
                                  value >= 8 ? colors.success : value >= 6 ? colors.primary : colors.error,
                                opacity: 0.7 + (valueIndex / item.recentValues.length) * 0.3,
                              }}
                            />
                          ))}
                        </View>
                      ) : null}
                      <Text
                        className="text-[11px] font-medium uppercase tracking-[1.5px]"
                        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                      >
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
                {filteredGrades.map((grade, index) => (
                  <AnimatedListItem key={grade.id} index={historyBaseIndex + index}>
                    <ElegantCard className="gap-3 p-4" tone={grade.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-4">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                            {grade.subject}
                          </Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {grade.typeLabel}
                          </Text>
                        </View>
                        <Text className="text-4xl font-light" style={{ color: colors.foreground }}>
                          {grade.valueLabel}
                        </Text>
                      </View>
                      <View
                        className="gap-2 pt-3"
                        style={{ borderTopWidth: 1, borderTopColor: colors.outlineVariant ?? colors.border }}
                      >
                        <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                          {grade.detail}
                        </Text>
                        <Text className="text-xs" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                          {grade.teacherLabel}
                        </Text>
                        <Text
                          className="text-[11px] font-medium uppercase tracking-[1.5px]"
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
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
