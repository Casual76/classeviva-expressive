import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { MiniBarChart } from "@/components/ui/mini-bar-chart";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
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
      <ElegantCard className="gap-3 p-4" variant="filled" radius="md">
        <View
          className="h-10 w-10 items-center justify-center rounded-full"
          style={{ backgroundColor: colors.primaryContainer ?? colors.surface }}
        >
          <MaterialIcons color={colors.onPrimaryContainer ?? colors.primary} name={icon} size={20} />
        </View>
        <View className="gap-0.5">
          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
            {title}
          </Text>
          <Text className="text-xs leading-4" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
            {detail}
          </Text>
        </View>
      </ElegantCard>
    </Pressable>
  );
}

function getTrendSubtitle(dashboard: DashboardViewModel) {
  if (dashboard.averageNumeric === null || dashboard.gradeTrend.length < 2) {
    return null;
  }

  const last = dashboard.gradeTrend[dashboard.gradeTrend.length - 1]?.value ?? 0;
  const previous = dashboard.gradeTrend[dashboard.gradeTrend.length - 2]?.value ?? 0;
  const diff = Math.abs(last - previous).toFixed(1);

  if (last > previous) {
    return `↑ +${diff} rispetto all'ultima valutazione`;
  }
  if (last < previous) {
    return `↓ ${diff} rispetto all'ultima valutazione`;
  }

  return "Ultimo andamento stabile";
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

  const mutedOnPrimary = colors.onPrimaryContainer ? `${colors.onPrimaryContainer}99` : colors.muted;
  const softOnPrimary = colors.onPrimaryContainer ? `${colors.onPrimaryContainer}CC` : colors.muted;
  const trendSubtitle = useMemo(() => (dashboard ? getTrendSubtitle(dashboard) : null), [dashboard]);
  const remainingUpcomingItems = useMemo(
    () => dashboard?.upcomingItems.filter((item) => item.category !== "assessment") ?? [],
    [dashboard],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState variant="skeleton" />
      </ScreenContainer>
    );
  }

  if (!dashboard) {
    return (
      <ScreenContainer className="flex-1 bg-background px-5">
        <LoadingState detail={error ?? "Riprova tra poco."} title="Dashboard non disponibile" />
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
        <View className="gap-6 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              action={
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full"
                  style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                  onPress={() => router.push("/(tabs)/profile")}
                >
                  <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="person-outline" size={22} />
                </Pressable>
              }
              subtitle={dashboard.subheadline}
              title={dashboard.headline}
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md">
                <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                  Sincronizzazione parziale
                </Text>
                <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                  {error}
                </Text>
              </ElegantCard>
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <ElegantCard className="gap-5 p-5" variant="gradient" radius="xl">
              <View className="flex-row items-start justify-between gap-4">
                <View className="flex-1 gap-2">
                  <Text
                    className="text-[11px] font-medium uppercase tracking-[1.5px]"
                    style={{ color: colors.onPrimaryContainer ? `${colors.onPrimaryContainer}BB` : colors.muted }}
                  >
                    Media aggiornata
                  </Text>
                  <View className="flex-row items-end gap-3">
                    <Text
                      className="text-[68px] leading-[68px] font-light"
                      style={{ color: colors.onPrimaryContainer ?? colors.foreground }}
                    >
                      {dashboard.averageLabel}
                    </Text>
                    <Text className="pb-2 text-base font-medium" style={{ color: mutedOnPrimary }}>
                      / 10
                    </Text>
                  </View>
                  {trendSubtitle ? (
                    <Text className="text-sm font-medium" style={{ color: mutedOnPrimary }}>
                      {trendSubtitle}
                    </Text>
                  ) : null}
                  <Text className="text-sm leading-5" style={{ color: softOnPrimary }}>
                    Panorama rapido del tuo andamento, delle urgenze e delle novità scolastiche.
                  </Text>
                </View>
              </View>

              {dashboard.gradeTrend.length > 0 ? <MiniBarChart points={dashboard.gradeTrend} /> : null}
            </ElegantCard>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="flex-row flex-wrap gap-3">
              {dashboard.stats.map((stat) => (
                <ElegantCard
                  key={stat.id}
                  className="min-w-[150px] flex-1 gap-1.5 p-4"
                  tone={stat.tone}
                  variant="filled"
                  radius="md"
                >
                  <Text
                    className="text-[11px] font-medium uppercase tracking-[1.5px]"
                    style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                  >
                    {stat.label}
                  </Text>
                  <Text className="text-3xl font-light" style={{ color: colors.foreground }}>
                    {stat.value}
                  </Text>
                  <Text className="text-xs leading-4" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    {stat.detail}
                  </Text>
                </ElegantCard>
              ))}
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <View className="gap-3">
              <SectionTitle eyebrow="Oggi" title="Lezioni del giorno" />
              {dashboard.todayLessons.length > 0 ? (
                <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                  <View className="flex-row gap-3">
                    {dashboard.todayLessons.map((lesson) => (
                      <ElegantCard
                        key={lesson.id}
                        className="gap-1 p-4"
                        radius="md"
                        style={{ minWidth: 140 }}
                        variant="filled"
                      >
                        <Text
                          className="text-[11px] font-medium uppercase tracking-[1.5px]"
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
                          {lesson.timeLabel}
                        </Text>
                        <Text className="text-sm font-medium" numberOfLines={2} style={{ color: colors.foreground }}>
                          {lesson.subtitle}
                        </Text>
                        <Text
                          className="text-xs"
                          numberOfLines={1}
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
                          {lesson.detail}
                        </Text>
                      </ElegantCard>
                    ))}
                  </View>
                </ScrollView>
              ) : (
                <ElegantCard className="p-4" radius="md" variant="filled">
                  <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    Nessuna lezione registrata per oggi.
                  </Text>
                </ElegantCard>
              )}
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={5}>
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
                  detail="Lezioni della settimana e griglia del giorno."
                  icon="view-week"
                  onPress={() => router.push("/(tabs)/schedule")}
                  title="Orario"
                />
                <QuickLink
                  detail="Comunicazioni, note, materiali e pagelle."
                  icon="widgets"
                  onPress={() => router.push("/(tabs)/more")}
                  title="Altro"
                />
              </View>
            </View>
          </AnimatedListItem>

          {dashboard.upcomingAssessments.length > 0 ? (
            <AnimatedListItem index={6}>
              <View className="gap-3">
                <SectionTitle eyebrow="In arrivo" title="Prossime verifiche" />
                <View className="gap-3">
                  {dashboard.upcomingAssessments.map((item) => (
                    <ElegantCard key={item.id} className="gap-2 p-4" tone="warning" variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-3">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                            {item.title}
                          </Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {item.subtitle}
                          </Text>
                        </View>
                        <Text
                          className="text-[11px] font-medium uppercase tracking-[1.5px]"
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
                          {item.shortDateLabel}
                        </Text>
                      </View>
                    </ElegantCard>
                  ))}
                </View>
              </View>
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={7}>
            <View className="gap-3">
              <SectionTitle eyebrow="Subito dopo" title="Scadenze e appuntamenti" />
              {remainingUpcomingItems.length > 0 ? (
                <View className="gap-3">
                  {remainingUpcomingItems.map((item) => (
                    <ElegantCard key={item.id} className="gap-3 p-4" tone={item.tone} variant="filled" radius="md">
                      <View className="flex-row items-start justify-between gap-3">
                        <View className="flex-1 gap-0.5">
                          <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                            {item.title}
                          </Text>
                          <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                            {item.subtitle}
                          </Text>
                        </View>
                        <Text
                          className="text-[11px] font-medium uppercase tracking-[1.5px]"
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
                          {item.shortDateLabel}
                        </Text>
                      </View>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {item.detail}
                      </Text>
                      <Text
                        className="text-[11px] font-medium uppercase tracking-[1.5px]"
                        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                      >
                        {item.timeLabel}
                      </Text>
                    </ElegantCard>
                  ))}
                </View>
              ) : (
                <EmptyState
                  detail="Quando ci sono compiti o appuntamenti imminenti li trovi qui."
                  title="Nessuna urgenza imminente"
                />
              )}
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={8}>
            <View className="gap-3">
              <SectionTitle eyebrow="Novità" title="Messaggi e richiami" />
              {dashboard.unreadCommunications.length > 0 || dashboard.highlightedNotes.length > 0 ? (
                <View className="gap-3">
                  {dashboard.unreadCommunications.map((item) => (
                    <ElegantCard key={item.id} className="gap-2 p-4" tone={item.tone} variant="filled" radius="md">
                      <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                        {item.title}
                      </Text>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {item.preview}
                      </Text>
                      <Text
                        className="text-[11px] font-medium uppercase tracking-[1.5px]"
                        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                      >
                        {item.dateLabel} · {item.metadataLabel}
                      </Text>
                    </ElegantCard>
                  ))}
                  {dashboard.highlightedNotes.map((item) => (
                    <ElegantCard key={`${item.categoryCode}-${item.id}`} className="gap-2 p-4" tone={item.tone} variant="filled" radius="md">
                      <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                        {item.title}
                      </Text>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {item.preview}
                      </Text>
                      <Text
                        className="text-[11px] font-medium uppercase tracking-[1.5px]"
                        style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                      >
                        {item.badgeLabel} · {item.dateLabel}
                      </Text>
                    </ElegantCard>
                  ))}
                </View>
              ) : (
                <EmptyState detail="Nessuna comunicazione urgente o nota recente da evidenziare." title="Niente in evidenza" />
              )}
            </View>
          </AnimatedListItem>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
