import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";
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
import { Fonts } from "@/constants/theme";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";
import { loadDashboardView, type DashboardViewModel } from "@/lib/student-data";

function ActionCard({
  title,
  subtitle,
  icon,
  onPress,
}: {
  title: string;
  subtitle: string;
  icon: keyof typeof MaterialIcons.glyphMap;
  onPress: () => void;
}) {
  const colors = useColors();

  return (
    <Pressable className="flex-1" onPress={onPress}>
      <ElegantCard className="gap-3 p-4" variant="filled">
        <View
          className="h-11 w-11 items-center justify-center rounded-full"
          style={{ backgroundColor: colors.surfaceAlt ?? colors.background }}
        >
          <MaterialIcons color={colors.primary} name={icon} size={20} />
        </View>
        <View className="gap-1">
          <Text className="text-sm font-semibold text-foreground">{title}</Text>
          <Text className="text-xs leading-5 text-muted">{subtitle}</Text>
        </View>
      </ElegantCard>
    </Pressable>
  );
}

export default function HomeScreen() {
  const colors = useColors();
  const router = useRouter();
  const { user, isDemoMode } = useAuth();
  const [dashboard, setDashboard] = useState<DashboardViewModel | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mode = isDemoMode ? "demo" : "real";

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadDashboardView(mode, user);
      setDashboard(data);
    } catch (loadError) {
      console.error("Dashboard load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare la dashboard.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [mode, user]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadData();
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color={colors.primary} size="large" />
      </ScreenContainer>
    );
  }

  if (!dashboard) {
    return (
      <ScreenContainer className="flex-1 justify-center bg-background px-6">
        <ElegantCard className="gap-4 p-6" tone="error" variant="filled">
          <Text className="text-lg font-semibold text-foreground">Dashboard non disponibile</Text>
          <Text className="text-sm leading-6 text-muted">{error ?? "Riprova tra poco."}</Text>
        </ElegantCard>
      </ScreenContainer>
    );
  }

  const secondaryArea = [
    {
      title: "Comunicazioni",
      description: "Seconda iterazione: lista completa e lettura allegati.",
    },
    {
      title: "Orario e pagelle",
      description: "Restano fuori dalla navigazione primaria finche non sono robusti.",
    },
  ];

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 112 }}
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            action={
              <Pressable
                className="h-12 w-12 items-center justify-center rounded-full"
                onPress={() => router.push("/profile")}
                style={{ backgroundColor: colors.surface }}
              >
                <MaterialIcons color={colors.foreground} name="person-outline" size={22} />
              </Pressable>
            }
            eyebrow={isDemoMode ? "Demo mode" : "Classeviva live"}
            subtitle={dashboard.subheadline}
            title={dashboard.headline}
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <ElegantCard className="gap-4 p-6" gradient="primary" variant="gradient">
            <View className="gap-2">
              <Text className="text-xs font-semibold uppercase tracking-[2px] text-background/75">
                Media corrente
              </Text>
              <View className="flex-row items-end gap-3">
                <Text
                  className="text-6xl leading-[60px] text-background"
                  style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                >
                  {dashboard.averageLabel}
                </Text>
                <Text className="pb-2 text-base font-semibold text-background/75">/ 10</Text>
              </View>
            </View>
            <Text className="text-sm leading-6 text-background/80">
              Vista sintetica delle valutazioni piu recenti, pensata per aprire l&apos;app e capire subito dove sei.
            </Text>
          </ElegantCard>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Riepilogo rapido</Text>
            <View className="gap-3">
              {dashboard.stats.map((stat) => (
                <ElegantCard key={stat.id} className="gap-3 p-4" tone={stat.tone} variant="filled">
                  <View className="flex-row items-start justify-between gap-3">
                    <View className="flex-1 gap-1">
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {stat.label}
                      </Text>
                      <Text className="text-sm leading-6 text-muted">{stat.detail}</Text>
                    </View>
                    <Text className="text-3xl font-semibold text-foreground">{stat.value}</Text>
                  </View>
                </ElegantCard>
              ))}
            </View>
          </View>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Muoviti dall&apos;hub</Text>
            <View className="flex-row gap-3">
              <ActionCard
                icon="warning-amber"
                onPress={() => router.push("/absences")}
                subtitle="Storico completo e stato giustificazioni."
                title="Assenze"
              />
              <ActionCard
                icon="person-outline"
                onPress={() => router.push("/profile")}
                subtitle="Dati studente, modalita attiva e logout."
                title="Profilo"
              />
            </View>
            <View className="flex-row gap-3">
              <ActionCard
                icon="bar-chart"
                onPress={() => router.push("/(tabs)/grades")}
                subtitle="Filtri e andamento valutazioni."
                title="Voti"
              />
              <ActionCard
                icon="calendar-today"
                onPress={() => router.push("/(tabs)/calendar")}
                subtitle="Lezioni, scadenze e vista per giorno."
                title="Agenda"
              />
            </View>
          </View>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Prossimo in agenda</Text>
            <View className="gap-3">
              {dashboard.upcomingItems.length > 0 ? (
                dashboard.upcomingItems.map((item) => (
                  <ElegantCard key={item.id} className="gap-3 p-4" tone={item.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-4">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                        <Text className="text-sm text-muted">{item.subtitle}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {item.shortDateLabel}
                      </Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                  </ElegantCard>
                ))
              ) : (
                <ElegantCard className="gap-2 p-4" variant="filled">
                  <Text className="text-sm font-semibold text-foreground">Nessuna voce imminente</Text>
                  <Text className="text-sm leading-6 text-muted">
                    La nuova agenda comparira qui appena abbiamo elementi da mettere in evidenza.
                  </Text>
                </ElegantCard>
              )}
            </View>
          </View>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Seconda area</Text>
            <View className="gap-3">
              {secondaryArea.map((item) => (
                <ElegantCard key={item.title} className="gap-2 p-4" variant="outlined">
                  <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                  <Text className="text-sm leading-6 text-muted">{item.description}</Text>
                </ElegantCard>
              ))}
            </View>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
