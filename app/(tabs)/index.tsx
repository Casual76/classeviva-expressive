import {
  ScrollView,
  Text,
  View,
  TouchableOpacity,
  RefreshControl,
  ActivityIndicator,
} from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { classeviva } from "@/lib/classeviva-client";
import {
  generateMockGrades,
  generateMockHomeworks,
  generateMockAbsences,
  generateMockLessons,
} from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ElegantButton } from "@/components/ui/elegant-button";
import { AnimatedCard } from "@/components/ui/animated-card";

interface DashboardStats {
  averageGrade: number | string;
  absences: number;
  lates: number;
  upcomingHomeworks: number;
  recentGrades?: any[];
  nextLessons?: any[];
}

export default function HomeScreen() {
  const colors = useColors();
  const { user, isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [stats, setStats] = useState<DashboardStats>({
    averageGrade: 0,
    absences: 0,
    lates: 0,
    upcomingHomeworks: 0,
  });
  const [error, setError] = useState<string | null>(null);

  const loadData = async () => {
    try {
      setError(null);

      if (isDemoMode) {
        // Carica dati mock per demo mode
        const grades = generateMockGrades();
        const homeworks = generateMockHomeworks();
        const absences = generateMockAbsences();
        const lessons = generateMockLessons();

        const numericGrades = grades
          .map((g) => {
            const num = parseFloat(String(g.grade));
            return isNaN(num) ? 0 : num;
          })
          .filter((g) => g > 0);

        const average =
          numericGrades.length > 0
            ? (numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length).toFixed(1)
            : "0";

        const absenceCount = absences.filter((a) => a.type === "assenza").length;
        const lateCount = absences.filter((a) => a.type === "ritardo").length;
        const upcomingHW = homeworks.filter(
          (h) => new Date(h.dueDate) >= new Date()
        ).length;

        setStats({
          averageGrade: average,
          absences: absenceCount,
          lates: lateCount,
          upcomingHomeworks: upcomingHW,
          recentGrades: grades.slice(-3),
          nextLessons: lessons.slice(0, 2),
        });
      } else {
        // Carica dati reali da Classeviva
        const [grades, absences, homeworks, lessons] = await Promise.all([
          classeviva.getGrades().catch(() => []),
          classeviva.getAbsences().catch(() => []),
          classeviva.getHomeworks().catch(() => []),
          classeviva.getLessons().catch(() => []),
        ]);

        // Calcola statistiche dai dati reali
        const numericGrades = (grades as any[])
          .map((g) => {
            const num = typeof g.grade === "number" ? g.grade : parseFloat(String(g.grade));
            return isNaN(num) ? 0 : num;
          })
          .filter((g) => g > 0);

        const averageGrade =
          numericGrades.length > 0
            ? (numericGrades.reduce((a, b) => a + b, 0) / numericGrades.length).toFixed(2)
            : "N/A";

        const absenceCount = (absences as any[]).filter(
          (a) => a.type === "assenza"
        ).length;
        const lateCount = (absences as any[]).filter(
          (a) => a.type === "ritardo"
        ).length;
        const upcomingHW = (homeworks as any[]).filter(
          (h) => new Date(h.dueDate) >= new Date()
        ).length;

        setStats({
          averageGrade: parseFloat(averageGrade as string) || 0,
          absences: absenceCount,
          lates: lateCount,
          upcomingHomeworks: upcomingHW,
          recentGrades: (grades as any[]).slice(-3),
          nextLessons: (lessons as any[]).slice(0, 2),
        });
      }
    } catch (err: any) {
      console.error("Errore nel caricamento dei dati:", err);
      setError(err.message || "Errore nel caricamento dei dati");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [isDemoMode, user]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await new Promise((resolve) => setTimeout(resolve, 500));
    await loadData();
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
        refreshControl={
          <RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />
        }
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-2">
            <Text className="text-4xl font-bold text-foreground">
              Ciao, {user?.name}! 👋
            </Text>
            <Text className="text-sm text-muted">
              Benvenuto su Classeviva Expressive
            </Text>
          </View>

          {/* Error Message */}
          {error && (
            <ElegantCard
              variant="filled"
              className="p-4 bg-error/10 border-error/30"
            >
              <Text className="text-sm text-error">{error}</Text>
            </ElegantCard>
          )}

          {/* Average Grade Card */}
          <AnimatedCard
            variant="gradient"
            gradient="primary"
            className="p-6 gap-3"
            delay={100}
          >
            <Text className="text-sm font-bold text-background/80">
              Media Voti
            </Text>
            <View className="flex-row items-baseline gap-2">
              <Text className="text-5xl font-bold text-background">
                {stats.averageGrade}
              </Text>
              <Text className="text-lg text-background/80">/10</Text>
            </View>
            <Text className="text-xs text-background/70">
              {stats.recentGrades && stats.recentGrades.length > 0
                ? "Basato su " + stats.recentGrades.length + " voti recenti"
                : "Nessun voto disponibile"}
            </Text>
          </AnimatedCard>

          {/* Summary Stats */}
          <View className="gap-3">
            <Text className="text-sm font-bold text-foreground">
              Riepilogo
            </Text>
            <View className="flex-row gap-3">
              <ElegantCard
                variant="filled"
                className="flex-1 p-4 gap-2 bg-error/10 border-error/30"
              >
                <Text className="text-xs font-bold text-muted">Assenze</Text>
                <Text className="text-3xl font-bold text-error">
                  {stats.absences}
                </Text>
              </ElegantCard>
              <ElegantCard
                variant="filled"
                className="flex-1 p-4 gap-2 bg-warning/10 border-warning/30"
              >
                <Text className="text-xs font-bold text-muted">Ritardi</Text>
                <Text className="text-3xl font-bold text-warning">
                  {stats.lates}
                </Text>
              </ElegantCard>
            </View>
            <ElegantCard
              variant="filled"
              className="p-4 gap-2 bg-success/10 border-success/30"
            >
              <Text className="text-xs font-bold text-muted">
                Compiti in Scadenza
              </Text>
              <Text className="text-3xl font-bold text-success">
                {stats.upcomingHomeworks}
              </Text>
            </ElegantCard>
          </View>

          {/* Recent Grades */}
          {stats.recentGrades && stats.recentGrades.length > 0 && (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                Ultimi Voti
              </Text>
              <View className="gap-2">
                {stats.recentGrades.map((grade: any) => (
                  <ElegantCard
                    key={grade.id}
                    variant="filled"
                    className="p-4 gap-2"
                  >
                    <View className="flex-row items-center justify-between">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-bold text-foreground">
                          {grade.subject}
                        </Text>
                        <Text className="text-xs text-muted">
                          {new Date(grade.date).toLocaleDateString("it-IT")}
                        </Text>
                      </View>
                      <View
                        className={`px-3 py-1 rounded-full ${
                          typeof grade.grade === "number" && grade.grade >= 7
                            ? "bg-success/20"
                            : "bg-warning/20"
                        }`}
                      >
                        <Text
                          className={`text-sm font-bold ${
                            typeof grade.grade === "number" && grade.grade >= 7
                              ? "text-success"
                              : "text-warning"
                          }`}
                        >
                          {grade.grade}
                        </Text>
                      </View>
                    </View>
                  </ElegantCard>
                ))}
              </View>
            </View>
          )}

          {/* Quick Access */}
          <View className="gap-3">
            <Text className="text-sm font-bold text-foreground">
              Accesso Rapido
            </Text>
            <View className="gap-2">
              <ElegantButton variant="secondary" size="lg" fullWidth>
                📊 Visualizza Tutti i Voti
              </ElegantButton>
              <ElegantButton variant="secondary" size="lg" fullWidth>
                📅 Vedi Agenda Completa
              </ElegantButton>
              <ElegantButton variant="secondary" size="lg" fullWidth>
                📬 Comunicazioni
              </ElegantButton>
            </View>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
