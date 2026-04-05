import { useLocalSearchParams } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  Alert,
  Pressable,
  RefreshControl,
  ScrollView,
  Text,
  TextInput,
  View,
} from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { EmptyState } from "@/components/ui/empty-state";
import { GradePill } from "@/components/ui/grade-pill";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { MetricTile } from "@/components/ui/metric-tile";
import { MiniBarChart } from "@/components/ui/mini-bar-chart";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { writeSubjectGoal, type SubjectGoal } from "@/lib/app-preferences";
import { useAuth } from "@/lib/auth-context";
import { useColors } from "@/hooks/use-colors";
import type { Period } from "@/lib/classeviva-client";
import {
  doesGradeMatchPeriod,
  loadGradeSelectionSummary,
  markGradeRowAsSeen,
  summarizeGrades,
  type GradeRowViewModel,
  type GradeSubjectSummaryViewModel,
} from "@/lib/student-data";

type ViewMode = "recent" | "subjects" | "trend";

function getPerformanceSentence(item: GradeSubjectSummaryViewModel): string {
  if (item.targetAverage !== null) {
    return item.goalMessage;
  }

  if (item.averageNumeric === null) {
    return "Nessuna media disponibile per ora.";
  }
  if (item.averageNumeric > 8) {
    return "Andamento molto solido e costante.";
  }
  if (item.averageNumeric > 6) {
    return "Andamento positivo e sopra la soglia.";
  }
  if (item.averageNumeric === 6) {
    return "Materia in equilibrio sulla sufficienza.";
  }
  if (item.averageNumeric >= 5) {
    return "Area da consolidare con le prossime prove.";
  }

  return "Serve recupero sulle prossime valutazioni.";
}

function getGoalTone(summary: GradeSubjectSummaryViewModel): "neutral" | "primary" | "success" | "warning" | "error" {
  switch (summary.goalStatus) {
    case "encouraging":
      return "success";
    case "minimum":
      return "primary";
    case "required":
      return "warning";
    case "unreachable":
      return "error";
    default:
      return "neutral";
  }
}

export default function GradesScreen() {
  const colors = useColors();
  const { user } = useAuth();
  const { focus } = useLocalSearchParams<{ focus?: string }>();
  const [grades, setGrades] = useState<GradeRowViewModel[]>([]);
  const [periods, setPeriods] = useState<Period[]>([]);
  const [goals, setGoals] = useState<Record<string, SubjectGoal>>({});
  const [selectedPeriod, setSelectedPeriod] = useState<string | null>(null);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
  const [selectedGrade, setSelectedGrade] = useState<GradeRowViewModel | null>(null);
  const [editingGoalSubject, setEditingGoalSubject] = useState<string | null>(null);
  const [goalDraft, setGoalDraft] = useState("");
  const [mode, setMode] = useState<ViewMode>("recent");
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isSavingGoal, setIsSavingGoal] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    if (!user?.id) {
      setGrades([]);
      setPeriods([]);
      setGoals({});
      setIsLoading(false);
      setIsRefreshing(false);
      return;
    }

    try {
      setError(null);
      const summary = await loadGradeSelectionSummary(user.id);
      setGrades(summary.rows);
      setPeriods(summary.periods);
      setGoals(summary.goals);
      setSelectedPeriod((current) => current ?? summary.currentPeriodCode ?? null);
    } catch (loadError) {
      console.error("Grades load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare i voti.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [user?.id]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  useEffect(() => {
    if (!focus || typeof focus !== "string" || grades.length === 0) {
      return;
    }

    const target = grades.find((grade) => grade.id === focus);
    if (!target) {
      return;
    }

    setSelectedGrade(target);
    setSelectedSubject(target.subject);
    setMode("recent");
    if (user?.id) {
      void markGradeRowAsSeen(user.id, target);
    }
  }, [focus, grades, user?.id]);

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

  const summaries = useMemo(() => summarizeGrades(filteredGrades, goals), [filteredGrades, goals]);
  const subjects = useMemo(
    () =>
      Array.from(new Set(grades.map((grade) => grade.subject).filter(Boolean))).sort((left, right) =>
        left.localeCompare(right, "it-IT"),
      ),
    [grades],
  );
  const belowThresholdCount = useMemo(
    () => filteredGrades.filter((grade) => grade.numericValue !== null && grade.numericValue < 6).length,
    [filteredGrades],
  );

  const activeGoalSubject = editingGoalSubject ?? selectedSubject ?? selectedGrade?.subject ?? null;
  const activeGoalValue =
    activeGoalSubject && goals[activeGoalSubject]
      ? String(goals[activeGoalSubject]?.targetAverage ?? "").replace(".", ",")
      : "";

  useEffect(() => {
    if (!activeGoalSubject) {
      setGoalDraft("");
      return;
    }

    setGoalDraft(activeGoalValue);
  }, [activeGoalSubject, activeGoalValue]);

  const handleSelectGrade = useCallback(
    async (grade: GradeRowViewModel) => {
      setSelectedGrade(grade);
      setSelectedSubject(grade.subject);
      if (user?.id) {
        await markGradeRowAsSeen(user.id, grade);
      }
    },
    [user?.id],
  );

  const handleSaveGoal = useCallback(async () => {
    if (!user?.id || !activeGoalSubject) {
      return;
    }

    const parsed = Number(goalDraft.replace(",", "."));
    if (!Number.isFinite(parsed) || parsed <= 0 || parsed > 10) {
      Alert.alert("Media non valida", "Inserisci un numero compreso tra 1 e 10.");
      return;
    }

    try {
      setIsSavingGoal(true);
      const nextGoals = await writeSubjectGoal(user.id, activeGoalSubject, parsed);
      setGoals(nextGoals);
      setEditingGoalSubject(null);
    } catch (saveError) {
      console.error("Save subject goal failed", saveError);
      Alert.alert("Salvataggio non riuscito", "Non sono riuscito a salvare la media obiettivo.");
    } finally {
      setIsSavingGoal(false);
    }
  }, [activeGoalSubject, goalDraft, user?.id]);

  const handleRemoveGoal = useCallback(async () => {
    if (!user?.id || !activeGoalSubject) {
      return;
    }

    try {
      setIsSavingGoal(true);
      const nextGoals = await writeSubjectGoal(user.id, activeGoalSubject, null);
      setGoals(nextGoals);
      setGoalDraft("");
      setEditingGoalSubject(null);
    } catch (saveError) {
      console.error("Remove subject goal failed", saveError);
      Alert.alert("Operazione non riuscita", "Non sono riuscito a rimuovere la media obiettivo.");
    } finally {
      setIsSavingGoal(false);
    }
  }, [activeGoalSubject, user?.id]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Sto preparando ultimi voti, medie per materia, periodi e andamento."
          title="Carico i voti"
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
              eyebrow="Rendimento"
              subtitle="Periodo corrente di default, focus per materia e calcolo del voto necessario per il tuo obiettivo."
              title="Voti"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <RegisterListRow
                detail={error}
                meta="Sincronizzazione"
                title="Aggiornamento parziale"
                tone="warning"
              />
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <View className="flex-row flex-wrap gap-3">
              <MetricTile
                detail="Media della selezione attiva."
                label="Media"
                tone={summaries.averageNumeric !== null && summaries.averageNumeric >= 6 ? "success" : "warning"}
                value={summaries.averageLabel}
              />
              <MetricTile detail="Valutazioni filtrate." label="Voti" value={String(filteredGrades.length)} />
              <MetricTile
                detail="Valutazioni sotto il 6 nella selezione."
                label="Sotto soglia"
                tone={belowThresholdCount > 0 ? "warning" : "success"}
                value={String(belowThresholdCount)}
              />
              <MetricTile detail="Materie visibili." label="Materie" value={String(summaries.subjectSummaries.length)} />
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="gap-3">
              <SectionTitle eyebrow="Vista" title="Scegli la lettura" />
              <View className="flex-row gap-2">
                <M3Chip label="Ultimi voti" onPress={() => setMode("recent")} selected={mode === "recent"} />
                <M3Chip label="Per materia" onPress={() => setMode("subjects")} selected={mode === "subjects"} />
                <M3Chip label="Andamento" onPress={() => setMode("trend")} selected={mode === "trend"} />
              </View>
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca materia, docente o nota"
              value={searchQuery}
            />
          </AnimatedListItem>

          {periods.length > 0 ? (
            <AnimatedListItem index={5}>
              <View className="gap-3">
                <SectionTitle eyebrow="Periodo" title="Filtra l'anno scolastico" />
                <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-2">
                    <M3Chip label="Generale" onPress={() => setSelectedPeriod(null)} selected={selectedPeriod === null} />
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

          {subjects.length > 0 ? (
            <AnimatedListItem index={6}>
              <View className="gap-3">
                <SectionTitle eyebrow="Materia" title="Riduci la selezione" />
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

          {activeGoalSubject ? (
            <AnimatedListItem index={7}>
              <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
                <View className="gap-1">
                  <SectionTitle eyebrow="Obiettivo" title={activeGoalSubject} detail="Imposta la media che vuoi raggiungere per questa materia." />
                  <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    La media obiettivo resta salvata sul dispositivo e viene usata per il calcolo del prossimo voto utile.
                  </Text>
                </View>
                <TextInput
                  autoCapitalize="none"
                  autoCorrect={false}
                  keyboardType="decimal-pad"
                  onChangeText={setGoalDraft}
                  placeholder="Es. 7,5"
                  value={goalDraft}
                  className="rounded-2xl px-4 py-4 text-base"
                  style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface, color: colors.foreground }}
                />
                <View className="flex-row gap-3">
                  <ElegantButton disabled={isSavingGoal} onPress={() => void handleSaveGoal()} variant="primary">
                    Salva media
                  </ElegantButton>
                  <ElegantButton disabled={isSavingGoal} onPress={() => void handleRemoveGoal()} variant="outline">
                    Rimuovi
                  </ElegantButton>
                </View>
              </ElegantCard>
            </AnimatedListItem>
          ) : null}

          {selectedGrade ? (
            <AnimatedListItem index={8}>
              <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
                <View className="flex-row items-start justify-between gap-4">
                  <View className="flex-1 gap-1.5">
                    <StatusBadge label={selectedGrade.periodLabel} tone="neutral" />
                    <Text className="text-xl font-medium" style={{ color: colors.foreground }}>
                      {selectedGrade.subject}
                    </Text>
                    <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                      {selectedGrade.typeLabel} / {selectedGrade.teacherLabel} / {selectedGrade.dateLabel}
                    </Text>
                  </View>
                  <GradePill tone={selectedGrade.tone} value={selectedGrade.valueLabel} />
                </View>
                <RegisterListRow
                  detail={selectedGrade.detail}
                  meta={selectedGrade.weight > 1 ? `Peso ${selectedGrade.weight}` : "Peso standard"}
                  title="Dettaglio voto"
                  tone={selectedGrade.tone}
                />
              </ElegantCard>
            </AnimatedListItem>
          ) : null}

          {mode === "recent" ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Cronologia" title="Ultimi voti" detail="Apri una riga per segnare il voto come visualizzato e leggerne il dettaglio." />
              {filteredGrades.length > 0 ? (
                <View className="gap-3">
                  {filteredGrades.map((grade, index) => (
                    <AnimatedListItem key={grade.id} index={9 + index}>
                      <Pressable onPress={() => void handleSelectGrade(grade)}>
                        <RegisterListRow
                          detail={grade.detail}
                          meta={`${grade.dateLabel} / ${grade.teacherLabel}`}
                          subtitle={`${grade.typeLabel} / ${grade.periodLabel}`}
                          title={grade.subject}
                          tone={grade.tone}
                          trailing={<GradePill compact tone={grade.tone} value={grade.valueLabel} />}
                        />
                      </Pressable>
                    </AnimatedListItem>
                  ))}
                </View>
              ) : (
                <EmptyState detail="Cambia filtri o ricerca per rivedere altre valutazioni." title="Nessun voto nella selezione corrente" />
              )}
            </View>
          ) : null}

          {mode === "subjects" ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Per materia" title="Medie e obiettivi" detail="Media semplice, media pesata e stima del prossimo voto utile per raggiungere l'obiettivo." />
              {summaries.subjectSummaries.length > 0 ? (
                <View className="gap-3">
                  {summaries.subjectSummaries.map((item, index) => (
                    <AnimatedListItem key={item.subject} index={9 + index}>
                      <Pressable
                        onPress={() => {
                          setSelectedSubject(item.subject);
                          setEditingGoalSubject(item.subject);
                        }}
                      >
                        <ElegantCard className="gap-4 p-5" radius="lg" variant="filled" tone={item.tone}>
                          <View className="flex-row items-start justify-between gap-4">
                            <View className="flex-1 gap-1.5">
                              <Text className="text-base font-medium" style={{ color: colors.foreground }}>
                                {item.subject}
                              </Text>
                              <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                                {item.typeBreakdown} / {item.teacherLabel}
                              </Text>
                            </View>
                            <View className="items-end gap-2">
                              <GradePill compact tone={item.tone} value={item.averageLabel} />
                              <StatusBadge label={item.targetAverage !== null ? `Target ${item.targetAverageLabel}` : "Nessun target"} tone={getGoalTone(item)} />
                            </View>
                          </View>
                          <View className="flex-row flex-wrap gap-2">
                            <StatusBadge label={`Media pesata ${item.weightedAverageLabel}`} tone="neutral" />
                            <StatusBadge label={`${item.count} voti`} tone="neutral" />
                          </View>
                          <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {getPerformanceSentence(item)}
                          </Text>
                        </ElegantCard>
                      </Pressable>
                    </AnimatedListItem>
                  ))}
                </View>
              ) : (
                <EmptyState detail="Nessuna materia soddisfa i filtri attuali." title="Sintesi non disponibile" />
              )}
            </View>
          ) : null}

          {mode === "trend" ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Andamento" title="Trend del periodo selezionato" detail="Il grafico segue sempre i filtri correnti di periodo e materia." />
              {summaries.trend.length > 0 ? (
                <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
                  <MiniBarChart points={summaries.trend} />
                  <View className="flex-row flex-wrap gap-2">
                    <StatusBadge label={`Media ${summaries.averageLabel}`} tone={summaries.averageNumeric !== null && summaries.averageNumeric >= 6 ? "success" : "warning"} />
                    <StatusBadge label={`Materie ${summaries.subjectSummaries.length}`} tone="neutral" />
                  </View>
                </ElegantCard>
              ) : (
                <EmptyState detail="Servono voti numerici nella selezione corrente per costruire il grafico." title="Trend non disponibile" />
              )}
            </View>
          ) : null}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
