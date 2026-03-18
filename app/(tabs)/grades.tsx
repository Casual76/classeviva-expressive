import { ScrollView, Text, View, ActivityIndicator, RefreshControl } from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { classeviva, Grade } from "@/lib/classeviva-client";
import { useColors } from "@/hooks/use-colors";

export default function GradesScreen() {
  const colors = useColors();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadGrades = async () => {
    try {
      setError(null);
      const data = await classeviva.getGrades();
      setGrades(data.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
    } catch (err: any) {
      setError(err.message || "Errore nel caricamento dei voti");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadGrades();
  }, []);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadGrades();
  };

  const groupedBySubject = grades.reduce(
    (acc, grade) => {
      const subject = grade.subject || "Senza materia";
      if (!acc[subject]) {
        acc[subject] = [];
      }
      acc[subject].push(grade);
      return acc;
    },
    {} as Record<string, Grade[]>
  );

  const calculateSubjectAverage = (subjectGrades: Grade[]) => {
    const numericGrades = subjectGrades
      .map((g) => {
        const num = parseFloat(String(g.grade));
        return isNaN(num) ? 0 : num;
      })
      .filter((g) => g > 0);
    if (numericGrades.length === 0) return 0;
    return (numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length).toFixed(2);
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">Voti</Text>
            <Text className="text-sm text-muted">
              {grades.length} voti registrati
            </Text>
          </View>

          {error && (
            <View className="p-4 rounded-lg bg-error/10 border border-error">
              <Text className="text-sm text-error">{error}</Text>
            </View>
          )}

          {grades.length > 0 ? (
            <View className="gap-6">
              {Object.entries(groupedBySubject).map(([subject, subjectGrades]) => (
                <View key={subject} className="gap-3">
                  {/* Subject Header */}
                  <View className="flex-row items-center justify-between">
                    <View className="flex-1 gap-1">
                      <Text className="text-base font-semibold text-foreground">
                        {subject}
                      </Text>
                      <Text className="text-xs text-muted">
                        Media: {calculateSubjectAverage(subjectGrades)}
                      </Text>
                    </View>
                  </View>

                  {/* Grades List */}
                  <View className="gap-2">
                    {subjectGrades.map((grade) => (
                      <View
                        key={grade.id}
                        className="rounded-xl bg-surface border border-border p-4 flex-row items-center justify-between"
                      >
                        <View className="flex-1 gap-1">
                          <View className="flex-row items-center gap-2">
                            <Text className="text-sm font-semibold text-foreground">
                              {grade.type}
                            </Text>
                            {grade.weight && (
                              <Text className="text-xs text-muted">
                                (peso: {grade.weight})
                              </Text>
                            )}
                          </View>
                          {grade.description && (
                            <Text className="text-xs text-muted" numberOfLines={1}>
                              {grade.description}
                            </Text>
                          )}
                          <Text className="text-xs text-muted">
                            {new Date(grade.date).toLocaleDateString("it-IT")}
                          </Text>
                        </View>
                        <View className="items-center gap-1">
                          <Text className="text-2xl font-bold text-primary">
                            {grade.grade}
                          </Text>
                        </View>
                      </View>
                    ))}
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <View className="rounded-xl bg-surface border border-border p-6 items-center">
              <Text className="text-sm text-muted">Nessun voto disponibile</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
