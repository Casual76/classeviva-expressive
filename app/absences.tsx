import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  RefreshControl,
  ScrollView,
  Text,
  View,
} from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ScreenHeader } from "@/components/ui/screen-header";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";
import { loadAbsencesView, type AbsenceRecordViewModel } from "@/lib/student-data";

export default function AbsencesScreen() {
  const colors = useColors();
  const router = useRouter();
  const { isDemoMode } = useAuth();
  const [absences, setAbsences] = useState<AbsenceRecordViewModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const mode = isDemoMode ? "demo" : "real";

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadAbsencesView(mode);
      setAbsences(data);
    } catch (loadError) {
      console.error("Absences load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare le assenze.");
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

  const stats = useMemo(() => {
    const totalAbsences = absences.filter((item) => item.type === "assenza").length;
    const totalLates = absences.filter((item) => item.type === "ritardo").length;
    const totalExits = absences.filter((item) => item.type === "uscita").length;
    const pending = absences.filter((item) => !item.justified).length;

    return [
      { id: "absences", label: "Assenze", value: String(totalAbsences), tone: "neutral" as const },
      { id: "lates", label: "Ritardi", value: String(totalLates), tone: "warning" as const },
      { id: "exits", label: "Uscite", value: String(totalExits), tone: "primary" as const },
      { id: "pending", label: "Da giustificare", value: String(pending), tone: "error" as const },
    ];
  }, [absences]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center bg-background">
        <ActivityIndicator color={colors.primary} size="large" />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background" edges={["top", "left", "right", "bottom"]}>
      <ScrollView
        contentContainerStyle={{ paddingBottom: 40 }}
        refreshControl={<RefreshControl onRefresh={handleRefresh} refreshing={isRefreshing} />}
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            backLabel="Home"
            eyebrow="Assenze"
            onBack={() => router.back()}
            subtitle="Storico completo, stato delle giustificazioni e lettura piu pulita delle assenze."
            title="Assenze"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <View className="flex-row flex-wrap gap-3">
            {stats.map((stat) => (
              <ElegantCard
                key={stat.id}
                className="min-w-[160px] flex-1 gap-2 p-4"
                tone={stat.tone}
                variant="filled"
              >
                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                  {stat.label}
                </Text>
                <Text className="text-3xl font-semibold text-foreground">{stat.value}</Text>
              </ElegantCard>
            ))}
          </View>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Cronologia</Text>
            {absences.length > 0 ? (
              <View className="gap-3">
                {absences.map((absence) => (
                  <ElegantCard key={absence.id} className="gap-4 p-4" tone={absence.tone} variant="filled">
                    <View className="flex-row items-start justify-between gap-4">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{absence.typeLabel}</Text>
                        <Text className="text-sm text-muted">{absence.dateLabel}</Text>
                      </View>
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        {absence.statusLabel}
                      </Text>
                    </View>
                    <Text className="text-sm leading-6 text-muted">{absence.detail}</Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <ElegantCard className="gap-2 p-5" variant="filled">
                <Text className="text-sm font-semibold text-foreground">Nessuna assenza registrata</Text>
                <Text className="text-sm leading-6 text-muted">
                  Quando ci sono eventi di presenza li troverai qui in ordine cronologico.
                </Text>
              </ElegantCard>
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
