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
import {
  generateMockGrades,
  generateMockHomeworks,
  generateMockAbsences,
} from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ElegantButton } from "@/components/ui/elegant-button";

export default function HomeScreen() {
  const colors = useColors();
  const { user, isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [stats, setStats] = useState({
    averageGrade: 0,
    absences: 0,
    lates: 0,
    upcomingHomeworks: 0,
  });

  const loadData = async () => {
    try {
      if (isDemoMode) {
        const grades = generateMockGrades();
        const homeworks = generateMockHomeworks();
        const absences = generateMockAbsences();

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

        const now = new Date();
        const upcomingCount = homeworks.filter(
          (h) => new Date(h.dueDate) > now
        ).length;

        const absenceCount = absences.filter((a) => a.type === "assenza").length;
        const lateCount = absences.filter((a) => a.type === "ritardo").length;

        setStats({
          averageGrade: parseFloat(average),
          absences: absenceCount,
          lates: lateCount,
          upcomingHomeworks: upcomingCount,
        });
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadData();
  }, [isDemoMode]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
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
        <View className="px-6 py-6 gap-8">
          {/* Welcome Header */}
          <View className="gap-3">
            <Text className="text-5xl font-bold text-foreground">
              Ciao, {user?.name}! 👋
            </Text>
            <Text className="text-base text-muted">
              Benvenuto su Classeviva Expressive
            </Text>
            {isDemoMode && (
              <View className="px-4 py-2 rounded-full bg-primary/15 w-fit">
                <Text className="text-xs font-bold text-primary">
                  🎯 MODALITÀ DEMO
                </Text>
              </View>
            )}
          </View>

          {/* Main Stats Card */}
          <ElegantCard variant="gradient" gradient="primary" className="p-6 gap-4">
            <View className="gap-1">
              <Text className="text-sm font-semibold text-background/80">
                Media Voti
              </Text>
              <View className="flex-row items-baseline gap-2">
                <Text className="text-6xl font-bold text-background">
                  {stats.averageGrade.toFixed(1)}
                </Text>
                <Text className="text-lg text-background/80">/10</Text>
              </View>
            </View>
            <View className="h-px bg-background/20" />
            <Text className="text-xs text-background/70">
              Basato su {stats.averageGrade > 0 ? "ultimi voti" : "nessun voto"}
            </Text>
          </ElegantCard>

          {/* Quick Stats */}
          <View className="gap-3">
            <Text className="text-sm font-bold text-foreground">
              Riepilogo
            </Text>
            <View className="gap-3">
              {/* Absences and Lates */}
              <View className="flex-row gap-3">
                <ElegantCard variant="filled" className="flex-1 p-4 gap-2">
                  <Text className="text-xs font-semibold text-muted">
                    Assenze
                  </Text>
                  <Text className="text-4xl font-bold text-error">
                    {stats.absences}
                  </Text>
                </ElegantCard>
                <ElegantCard variant="filled" className="flex-1 p-4 gap-2">
                  <Text className="text-xs font-semibold text-muted">
                    Ritardi
                  </Text>
                  <Text className="text-4xl font-bold text-warning">
                    {stats.lates}
                  </Text>
                </ElegantCard>
              </View>

              {/* Upcoming Homeworks */}
              <ElegantCard
                variant={
                  stats.upcomingHomeworks > 0 ? "filled" : "filled"
                }
                className={`p-4 gap-2 ${
                  stats.upcomingHomeworks === 0 ? "bg-success/10" : ""
                }`}
              >
                <Text className="text-xs font-semibold text-muted">
                  Compiti in Scadenza
                </Text>
                <View className="flex-row items-center justify-between">
                  <Text
                    className={`text-4xl font-bold ${
                      stats.upcomingHomeworks === 0
                        ? "text-success"
                        : "text-primary"
                    }`}
                  >
                    {stats.upcomingHomeworks}
                  </Text>
                  {stats.upcomingHomeworks === 0 && (
                    <Text className="text-2xl">✓</Text>
                  )}
                </View>
              </ElegantCard>
            </View>
          </View>

          {/* Quick Actions */}
          <View className="gap-4">
            <Text className="text-sm font-bold text-foreground">
              Accesso Rapido
            </Text>
            <View className="gap-3">
              <TouchableOpacity
                activeOpacity={0.7}
                className="active:opacity-70"
              >
                <ElegantCard variant="outlined" className="p-4 flex-row items-center justify-between">
                  <View className="flex-row items-center gap-3">
                    <Text className="text-3xl">📊</Text>
                    <View className="gap-1">
                      <Text className="text-sm font-bold text-foreground">
                        Voti
                      </Text>
                      <Text className="text-xs text-muted">
                        Ultime valutazioni
                      </Text>
                    </View>
                  </View>
                  <Text className="text-lg text-primary">→</Text>
                </ElegantCard>
              </TouchableOpacity>

              <TouchableOpacity
                activeOpacity={0.7}
                className="active:opacity-70"
              >
                <ElegantCard variant="outlined" className="p-4 flex-row items-center justify-between">
                  <View className="flex-row items-center gap-3">
                    <Text className="text-3xl">📅</Text>
                    <View className="gap-1">
                      <Text className="text-sm font-bold text-foreground">
                        Agenda
                      </Text>
                      <Text className="text-xs text-muted">
                        Lezioni e compiti
                      </Text>
                    </View>
                  </View>
                  <Text className="text-lg text-primary">→</Text>
                </ElegantCard>
              </TouchableOpacity>

              <TouchableOpacity
                activeOpacity={0.7}
                className="active:opacity-70"
              >
                <ElegantCard variant="outlined" className="p-4 flex-row items-center justify-between">
                  <View className="flex-row items-center gap-3">
                    <Text className="text-3xl">🔔</Text>
                    <View className="gap-1">
                      <Text className="text-sm font-bold text-foreground">
                        Comunicazioni
                      </Text>
                      <Text className="text-xs text-muted">
                        Ultime notizie
                      </Text>
                    </View>
                  </View>
                  <Text className="text-lg text-primary">→</Text>
                </ElegantCard>
              </TouchableOpacity>
            </View>
          </View>

          {/* Info Card */}
          <ElegantCard variant="filled" className="p-4 gap-2 bg-primary/5 border border-primary/20">
            <Text className="text-xs font-bold text-primary">
              💡 Suggerimento
            </Text>
            <Text className="text-xs text-muted leading-relaxed">
              Mantieni aggiornati i tuoi compiti e controlla regolarmente le
              comunicazioni della scuola.
            </Text>
          </ElegantCard>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
