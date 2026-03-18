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
        <View className="px-6 py-6 gap-6">
          {/* Welcome Header */}
          <View className="gap-2">
            <Text className="text-4xl font-bold text-foreground">
              Ciao, {user?.name}! 👋
            </Text>
            {isDemoMode && (
              <View className="px-3 py-1 rounded-full bg-primary/20 w-fit">
                <Text className="text-xs font-semibold text-primary">
                  📊 Modalità Demo
                </Text>
              </View>
            )}
          </View>

          {/* Stats Cards */}
          <View className="gap-3">
            {/* Average Grade */}
            <View className="rounded-2xl bg-gradient-to-br from-primary to-primary/80 p-6 gap-2">
              <Text className="text-sm font-semibold text-background/80">
                Media Voti
              </Text>
              <View className="flex-row items-baseline gap-2">
                <Text className="text-5xl font-bold text-background">
                  {stats.averageGrade.toFixed(1)}
                </Text>
                <Text className="text-sm text-background/80">/10</Text>
              </View>
            </View>

            {/* Attendance Stats */}
            <View className="grid grid-cols-2 gap-3">
              <View className="rounded-2xl bg-surface border border-border p-4 gap-2">
                <Text className="text-xs font-semibold text-muted">Assenze</Text>
                <Text className="text-4xl font-bold text-error">
                  {stats.absences}
                </Text>
              </View>
              <View className="rounded-2xl bg-surface border border-border p-4 gap-2">
                <Text className="text-xs font-semibold text-muted">Ritardi</Text>
                <Text className="text-4xl font-bold text-warning">
                  {stats.lates}
                </Text>
              </View>
            </View>
          </View>

          {/* Upcoming Homeworks Section */}
          <View className="gap-3">
            <View className="flex-row items-center justify-between">
              <Text className="text-lg font-bold text-foreground">
                Prossimi Compiti
              </Text>
              <TouchableOpacity className="active:opacity-70">
                <Text className="text-sm font-semibold text-primary">
                  Vedi tutti →
                </Text>
              </TouchableOpacity>
            </View>

            {stats.upcomingHomeworks > 0 ? (
              <View className="rounded-2xl bg-surface border border-border p-4 gap-3">
                <View className="flex-row items-center justify-between">
                  <View className="gap-1">
                    <Text className="text-sm font-semibold text-foreground">
                      Compiti in scadenza
                    </Text>
                    <Text className="text-xs text-muted">
                      Prossimi 7 giorni
                    </Text>
                  </View>
                  <View className="items-center gap-1">
                    <Text className="text-3xl font-bold text-primary">
                      {stats.upcomingHomeworks}
                    </Text>
                  </View>
                </View>
              </View>
            ) : (
              <View className="rounded-2xl bg-success/10 border border-success p-4 gap-2">
                <Text className="text-sm font-semibold text-success">
                  ✓ Nessun compito in scadenza
                </Text>
                <Text className="text-xs text-success/80">
                  Sei in pari con i compiti!
                </Text>
              </View>
            )}
          </View>

          {/* Quick Actions */}
          <View className="gap-3">
            <Text className="text-lg font-bold text-foreground">
              Accesso Rapido
            </Text>
            <View className="gap-2">
              <TouchableOpacity className="rounded-xl bg-surface border border-border p-4 flex-row items-center justify-between active:opacity-70">
                <View className="flex-row items-center gap-3">
                  <Text className="text-2xl">📝</Text>
                  <View className="gap-1">
                    <Text className="text-sm font-semibold text-foreground">
                      Voti Recenti
                    </Text>
                    <Text className="text-xs text-muted">
                      Ultimi voti registrati
                    </Text>
                  </View>
                </View>
                <Text className="text-lg">→</Text>
              </TouchableOpacity>

              <TouchableOpacity className="rounded-xl bg-surface border border-border p-4 flex-row items-center justify-between active:opacity-70">
                <View className="flex-row items-center gap-3">
                  <Text className="text-2xl">📅</Text>
                  <View className="gap-1">
                    <Text className="text-sm font-semibold text-foreground">
                      Agenda
                    </Text>
                    <Text className="text-xs text-muted">
                      Lezioni e compiti
                    </Text>
                  </View>
                </View>
                <Text className="text-lg">→</Text>
              </TouchableOpacity>

              <TouchableOpacity className="rounded-xl bg-surface border border-border p-4 flex-row items-center justify-between active:opacity-70">
                <View className="flex-row items-center gap-3">
                  <Text className="text-2xl">🔔</Text>
                  <View className="gap-1">
                    <Text className="text-sm font-semibold text-foreground">
                      Comunicazioni
                    </Text>
                    <Text className="text-xs text-muted">
                      Ultime notizie dalla scuola
                    </Text>
                  </View>
                </View>
                <Text className="text-lg">→</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Info Card */}
          <View className="rounded-2xl bg-primary/5 border border-primary/20 p-4 gap-2">
            <Text className="text-xs font-semibold text-primary">
              💡 Suggerimento
            </Text>
            <Text className="text-xs text-muted leading-relaxed">
              Controlla regolarmente le comunicazioni della scuola e i compiti
              in scadenza per rimanere sempre aggiornato.
            </Text>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
