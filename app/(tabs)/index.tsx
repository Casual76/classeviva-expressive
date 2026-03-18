import { ScrollView, Text, View, TouchableOpacity, RefreshControl, ActivityIndicator } from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { classeviva, Grade, Homework, Absence } from "@/lib/classeviva-client";
import { useColors } from "@/hooks/use-colors";

export default function HomeScreen() {
  const { user } = useAuth();
  const colors = useColors();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [homeworks, setHomeworks] = useState<Homework[]>([]);
  const [absences, setAbsences] = useState<Absence[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    try {
      setError(null);
      const [gradesData, homeworksData, absencesData] = await Promise.all([
        classeviva.getGrades(),
        classeviva.getHomeworks(),
        classeviva.getAbsences(),
      ]);
      setGrades(gradesData);
      setHomeworks(homeworksData);
      setAbsences(absencesData);
    } catch (err: any) {
      setError(err.message || "Errore nel caricamento dei dati");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, []);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadData();
  };

  const calculateAverageGrade = () => {
    if (grades.length === 0) return 0;
    const numericGrades = grades
      .map((g) => {
        const num = parseFloat(String(g.grade));
        return isNaN(num) ? 0 : num;
      })
      .filter((g) => g > 0);
    if (numericGrades.length === 0) return 0;
    return (numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length).toFixed(2);
  };

  const upcomingHomeworks = homeworks
    .sort((a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime())
    .slice(0, 3);

  const totalAbsences = absences.filter((a) => a.type === "assenza").length;
  const totalLates = absences.filter((a) => a.type === "ritardo").length;

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
          {/* Greeting */}
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">
              Ciao, {user?.name || "Studente"}! 👋
            </Text>
            <Text className="text-sm text-muted">
              {user?.class} {user?.section}
            </Text>
          </View>

          {error && (
            <View className="p-4 rounded-lg bg-error/10 border border-error">
              <Text className="text-sm text-error">{error}</Text>
            </View>
          )}

          {/* Media Voti Card */}
          <View className="rounded-2xl bg-gradient-to-br from-primary to-primary/80 p-6 gap-3">
            <Text className="text-sm font-semibold text-background/80">Media Voti</Text>
            <Text className="text-4xl font-bold text-background">{calculateAverageGrade()}</Text>
            <Text className="text-xs text-background/70">
              Basato su {grades.length} voti
            </Text>
          </View>

          {/* Assenze Card */}
          <View className="flex-row gap-4">
            <View className="flex-1 rounded-2xl bg-surface border border-border p-4 gap-2">
              <Text className="text-xs font-semibold text-muted">Assenze</Text>
              <Text className="text-3xl font-bold text-foreground">{totalAbsences}</Text>
            </View>
            <View className="flex-1 rounded-2xl bg-surface border border-border p-4 gap-2">
              <Text className="text-xs font-semibold text-muted">Ritardi</Text>
              <Text className="text-3xl font-bold text-foreground">{totalLates}</Text>
            </View>
          </View>

          {/* Prossimi Compiti */}
          <View className="gap-3">
            <View className="flex-row items-center justify-between">
              <Text className="text-lg font-semibold text-foreground">Prossimi Compiti</Text>
              {upcomingHomeworks.length > 0 && (
                <Text className="text-xs text-muted">{upcomingHomeworks.length}</Text>
              )}
            </View>

            {upcomingHomeworks.length > 0 ? (
              <View className="gap-2">
                {upcomingHomeworks.map((hw) => (
                  <View
                    key={hw.id}
                    className="rounded-xl bg-surface border border-border p-4 gap-2"
                  >
                    <View className="flex-row items-start justify-between">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">
                          {hw.subject}
                        </Text>
                        <Text className="text-xs text-muted" numberOfLines={2}>
                          {hw.description}
                        </Text>
                      </View>
                    </View>
                    <Text className="text-xs text-primary font-medium">
                      Scadenza: {new Date(hw.dueDate).toLocaleDateString("it-IT")}
                    </Text>
                  </View>
                ))}
              </View>
            ) : (
              <View className="rounded-xl bg-surface border border-border p-6 items-center">
                <Text className="text-sm text-muted">Nessun compito in scadenza</Text>
              </View>
            )}
          </View>

          {/* Ultimi Voti */}
          <View className="gap-3">
            <Text className="text-lg font-semibold text-foreground">Ultimi Voti</Text>
            {grades.length > 0 ? (
              <View className="gap-2">
                {grades.slice(0, 3).map((grade) => (
                  <View
                    key={grade.id}
                    className="rounded-xl bg-surface border border-border p-4 flex-row items-center justify-between"
                  >
                    <View className="flex-1 gap-1">
                      <Text className="text-sm font-semibold text-foreground">
                        {grade.subject}
                      </Text>
                      <Text className="text-xs text-muted">{grade.type}</Text>
                    </View>
                    <View className="items-center gap-1">
                      <Text className="text-2xl font-bold text-primary">
                        {grade.grade}
                      </Text>
                      <Text className="text-xs text-muted">
                        {new Date(grade.date).toLocaleDateString("it-IT")}
                      </Text>
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
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
