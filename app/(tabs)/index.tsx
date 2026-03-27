import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { MiniBarChart } from "@/components/ui/mini-bar-chart";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { Fonts } from "@/constants/theme";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";
import { loadDashboardView, type DashboardViewModel } from "@/lib/student-data";

function QuickLink({
  title,
  detail,
  icon,
  onPress,
}: {
  title: string;
  detail: string;
  icon: keyof typeof MaterialIcons.glyphMap;
  onPress: () => void;
}) {
  const colors = useColors();

  return (
    <Pressable className="flex-1" onPress={onPress}>
      <ElegantCard className="gap-3 p-4" variant="filled">
        <View
          className="h-11 w-11 items-center justify-center rounded-full"
          style={{ backgroundColor: colors.surface }}
        >
          <MaterialIcons color={colors.primary} name={icon} size={20} />
        </View>
        <View className="gap-1">
          <Text className="text-sm font-semibold text-foreground">{title}</Text>
          <Text className="text-xs leading-5 text-muted">{detail}</Text>
        </View>
      </ElegantCard>
    </Pressable>
  );
}

export default function HomeScreen() {
  const colors = useColors();
  const router = useRouter();
  const { user } = useAuth();
  const [dashboard, setDashboard] = useState<DashboardViewModel | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      const data = await loadDashboardView(user);
      setDashboard(data);
      setError(data.warning);
    } catch (loadError) {
      console.error("Dashboard load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a preparare la dashboard.");
      setDashboard(null);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [user]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState />
      </ScreenContainer>
    );
  }

  if (!dashboard) {
    return (
      <ScreenContainer className="flex-1 bg-background px-6">
        <LoadingState title="Dashboard non disponibile" detail={error ?? "Riprova tra poco."} />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 112 }}
        refreshControl={<RefreshControl onRefresh={() => { setIsRefreshing(true); void loadData(); }} refreshing={isRefreshing} />}
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-7 px-6 py-6">
          <ScreenHeader
            action={
              <Pressable
                className="h-12 w-12 items-center justify-center rounded-full border border-border bg-surface"
                onPress={() => router.push("/(tabs)/profile")}
              >
                <MaterialIcons color={colors.foreground} name="person-outline" size={22} />
              </Pressable>
            }
            eyebrow="Classeviva live"
            subtitle={dashboard.subheadline}
            title={dashboard.headline}
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Sincronizzazione parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <ElegantCard className="gap-6 p-6" gradient="primary" variant="gradient">
            <View className="flex-row items-start justify-between gap-4">
              <View className="flex-1 gap-2">
                <Text className="text-xs font-semibold uppercase tracking-[2px] text-background/75">Media aggiornata</Text>
                <View className="flex-row items-end gap-3">
                  <Text
                    className="text-6xl leading-[58px] text-background"
                    style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                  >
                    {dashboard.averageLabel}
                  </Text>
                  <Text className="pb-2 text-base font-semibold text-background/70">/ 10</Text>
                </View>
                <Text className="text-sm leading-6 text-background/80">
                  Panorama rapido del tuo andamento, delle urgenze e delle novita scolastiche.
                </Text>
              </View>
            </View>

            {dashboard.gradeTrend.length > 0 ? <MiniBarChart points={dashboard.gradeTrend} /> : null}
          </ElegantCard>

          <View className="flex-row flex-wrap gap-3">
            {dashboard.stats.map((stat) => (
              <ElegantCard key={stat.id} className="min-w-[160px] flex-1 gap-2 p-4" tone={stat.tone} variant="filled">
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{stat.label}</Text>
                <Text className="text-3xl font-semibold text-foreground">{stat.value}</Text>
                <Text className="text-xs leading-5 text-muted">{stat.detail}</Text>
              </ElegantCard>
            ))}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Accesso rapido" title="Muoviti nel registro" />
            <View className="flex-row gap-3">
              <QuickLink
                detail="Filtri, medie e andamento per materia."
                icon="leaderboard"
                onPress={() => router.push("/(tabs)/grades")}
                title="Voti"
              />
              <QuickLink
                detail="Calendario, scadenze e lezioni del giorno."
                icon="calendar-month"
                onPress={() => router.push("/(tabs)/calendar")}
                title="Agenda"
              />
            </View>
            <View className="flex-row gap-3">
              <QuickLink
                detail="Storico assenze, ritardi e giustificazioni."
                icon="fact-check"
                onPress={() => router.push("/(tabs)/absences")}
                title="Assenze"
              />
              <QuickLink
                detail="Comunicazioni, note, materiali e pagelle."
                icon="widgets"
                onPress={() => router.push("/(tabs)/more")}
                title="Altro"
              />
            </View>
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Subito dopo" title="Scadenze e appuntamenti" />
            {dashboard.upcomingItems.length > 0 ? (
              <View className="gap-3">
                {dashboard.upcomingItems.map((item) => (
                  <ElegantCard key={item.id} className="gap-3 p-4" tone={item.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-3">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                        <Text className="text-sm text-muted">{item.subtitle}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{item.shortDateLabel}</Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{item.detail}</Text>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{item.timeLabel}</Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState detail="Quando ci sono compiti, verifiche o eventi imminenti li trovi qui." title="Nessuna urgenza imminente" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Novita" title="Messaggi e richiami" />
            {dashboard.unreadCommunications.length > 0 || dashboard.highlightedNotes.length > 0 ? (
              <View className="gap-3">
                {dashboard.unreadCommunications.map((item) => (
                  <ElegantCard key={item.id} className="gap-2 p-4" tone={item.tone} variant="filled">
                    <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                    <Text className="text-sm leading-6 text-muted">{item.preview}</Text>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                      {item.dateLabel} · {item.metadataLabel}
                    </Text>
                  </ElegantCard>
                ))}
                {dashboard.highlightedNotes.map((item) => (
                  <ElegantCard key={`${item.categoryCode}-${item.id}`} className="gap-2 p-4" tone={item.tone} variant="filled">
                    <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                    <Text className="text-sm leading-6 text-muted">{item.preview}</Text>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                      {item.badgeLabel} · {item.dateLabel}
                    </Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState detail="Nessuna comunicazione urgente o nota recente da evidenziare." title="Niente in evidenza" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
