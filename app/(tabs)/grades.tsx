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
import { loadGradesView, type GradeRowViewModel } from "@/lib/student-data";

export default function GradesScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [grades, setGrades] = useState<GradeRowViewModel[]>([]);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mode = isDemoMode ? "demo" : "real";

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadGradesView(mode);
      setGrades(data);
    } catch (loadError) {
      console.error("Grades load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare i voti.");
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

  const subjects = useMemo(() => {
    return Array.from(new Set(grades.map((grade) => grade.subject).filter(Boolean))).sort();
  }, [grades]);

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
        grade.detail.toLowerCase().includes(query)
      );
    });
  }, [grades, selectedSubject, deferredQuery]);

  const filteredAverage = useMemo(() => {
    const numeric = filteredGrades
      .map((grade) => grade.numericValue)
      .filter((value): value is number => value !== null);

    if (numeric.length === 0) {
      return "--";
    }

    const average = numeric.reduce((sum, value) => sum + value, 0) / numeric.length;
    return average.toFixed(1);
  }, [filteredGrades]);

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
            eyebrow="Valutazioni"
            subtitle="Ricerca, filtro per materia e lettura piu compatta dei voti."
            title="Voti"
          />

          <View className="flex-row gap-3">
            <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Media vista</Text>
              <Text className="text-3xl font-semibold text-foreground">{filteredAverage}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Risultati</Text>
              <Text className="text-3xl font-semibold text-foreground">{filteredGrades.length}</Text>
            </ElegantCard>
          </View>

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca materia, tipo prova o note"
            value={searchQuery}
          />

          {subjects.length > 0 ? (
            <View className="gap-3">
              <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Materie</Text>
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-3">
                  <Pressable onPress={() => setSelectedSubject(null)}>
                    <ElegantCard
                      className="px-4 py-3"
                      tone={selectedSubject === null ? "primary" : "neutral"}
                      variant={selectedSubject === null ? "filled" : "outlined"}
                    >
                      <Text className="text-sm font-semibold text-foreground">Tutte</Text>
                    </ElegantCard>
                  </Pressable>

                  {subjects.map((subject) => (
                    <Pressable key={subject} onPress={() => setSelectedSubject(subject)}>
                      <ElegantCard
                        className="px-4 py-3"
                        tone={selectedSubject === subject ? "primary" : "neutral"}
                        variant={selectedSubject === subject ? "filled" : "outlined"}
                      >
                        <Text className="text-sm font-semibold text-foreground">{subject}</Text>
                      </ElegantCard>
                    </Pressable>
                  ))}
                </View>
              </ScrollView>
            </View>
          ) : null}

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Elenco</Text>
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
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {grade.dateLabel}
                      </Text>
                    </View>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <ElegantCard className="gap-2 p-5" variant="filled">
                <Text className="text-sm font-semibold text-foreground">Nessun voto trovato</Text>
                <Text className="text-sm leading-6 text-muted">
                  Cambia filtro o ricerca per rivedere altre valutazioni.
                </Text>
              </ElegantCard>
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
