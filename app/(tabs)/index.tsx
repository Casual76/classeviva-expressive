import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useState } from "react";
import { Pressable, RefreshControl, ScrollView, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";
import {
  loadDashboardView,
  markGradeRowAsSeen,
  type DashboardViewModel,
  type GradeRowViewModel,
} from "@/lib/student-data";

export default function TodayScreen() {
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
      setError(loadError instanceof Error ? loadError.message : "Non riesco a preparare la vista di oggi.");
      setDashboard(null);
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [user]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openGrade = useCallback(
    async (grade: GradeRowViewModel) => {
      if (user?.id) {
        await markGradeRowAsSeen(user.id, grade);
      }

      router.push({
        pathname: "/(tabs)/grades",
        params: { focus: grade.id },
      });
    },
    [router, user?.id],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Sto preparando lezioni, nuovi voti e comunicazioni da leggere."
          title="Carico la Home"
          variant="skeleton"
        />
      </ScreenContainer>
    );
  }

  if (!dashboard) {
    return (
      <ScreenContainer className="flex-1 bg-background px-5">
        <LoadingState detail={error ?? "Riprova tra poco."} title="Home non disponibile" />
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
              action={
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full"
                  style={{
                    backgroundColor: colors.surface ?? colors.background,
                    borderWidth: 1,
                    borderColor: colors.outlineVariant ?? colors.border,
                  }}
                  onPress={() => router.push("/(tabs)/profile")}
                >
                  <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="person-outline" size={22} />
                </Pressable>
              }
              eyebrow="Home"
              subtitle={dashboard.subheadline}
              title="Oggi"
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

          <View className="gap-3">
            <SectionTitle eyebrow="Lezioni" title="Lezioni odierne" detail="Solo il programma della giornata corrente." />
            {dashboard.todayLessons.length > 0 ? (
              <View className="gap-3">
                {dashboard.todayLessons.map((lesson, index) => (
                  <AnimatedListItem key={lesson.id} index={2 + index}>
                    <RegisterListRow
                      detail={lesson.detail}
                      meta={lesson.timeLabel}
                      subtitle={lesson.title !== lesson.subtitle ? lesson.title : undefined}
                      title={lesson.subtitle}
                      tone="neutral"
                      trailing={<StatusBadge label="Lezione" tone="neutral" />}
                    />
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Per oggi non risultano lezioni registrate nel portale." title="Nessuna lezione disponibile" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Nuovi voti" title="Non ancora visualizzati" detail="Restano qui finche non apri il dettaglio del voto." />
            {dashboard.unseenGrades.length > 0 ? (
              <View className="gap-3">
                {dashboard.unseenGrades.map((grade, index) => (
                  <AnimatedListItem key={grade.id} index={8 + index}>
                    <Pressable onPress={() => void openGrade(grade)}>
                      <RegisterListRow
                        detail={grade.detail}
                        meta={`${grade.dateLabel} / ${grade.teacherLabel}`}
                        subtitle={`${grade.typeLabel} / ${grade.periodLabel}`}
                        title={grade.subject}
                        tone={grade.tone}
                        trailing={<StatusBadge label={grade.valueLabel} tone={grade.tone} />}
                      />
                    </Pressable>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Hai gia aperto tutti i voti sincronizzati sul dispositivo." title="Nessun nuovo voto" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Bacheca" title="Comunicazioni non lette" detail="Le voci piu recenti da aprire o confermare." />
            {dashboard.unreadCommunications.length > 0 ? (
              <View className="gap-3">
                {dashboard.unreadCommunications.map((item, index) => (
                  <AnimatedListItem key={item.id} index={16 + index}>
                    <Pressable onPress={() => router.push("/(tabs)/communications")}>
                      <RegisterListRow
                        detail={item.preview}
                        meta={`${item.dateLabel} / ${item.sender}`}
                        subtitle={item.metadataLabel}
                        title={item.title}
                        tone={item.tone}
                        trailing={<StatusBadge label="Da leggere" tone="primary" />}
                      />
                    </Pressable>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="La bacheca non ha comunicazioni nuove da segnalare." title="Nessuna comunicazione non letta" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
