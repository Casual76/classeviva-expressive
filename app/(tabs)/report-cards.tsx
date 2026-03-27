import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { openExternalContent } from "@/lib/open-content";
import {
  loadReportCardsView,
  type SchoolReportViewModel,
} from "@/lib/student-data";

export default function ReportCardsScreen() {
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [reports, setReports] = useState<SchoolReportViewModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadReportCardsView();
      setReports(data);
    } catch (loadError) {
      console.error("Report cards load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare le pagelle.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const openableCount = useMemo(
    () => reports.filter((report) => report.capabilityState.status !== "unavailable").length,
    [reports],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando le pagelle"
          detail="Recupero i documenti pubblicati dal portale Classeviva."
        />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 112 }}
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
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            backLabel="Altro"
            eyebrow="Documenti"
            onBack={() => router.replace("/(tabs)/more")}
            subtitle="Pagelle e documenti ufficiali, con apertura esterna quando il portale fornisce il link."
            title="Pagelle"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <View className="flex-row gap-3">
            <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Documenti</Text>
              <Text className="text-3xl font-semibold text-foreground">{reports.length}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Apribili</Text>
              <Text className="text-3xl font-semibold text-foreground">{openableCount}</Text>
            </ElegantCard>
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Elenco" title="Documenti disponibili" />
            {reports.length > 0 ? (
              <View className="gap-3">
                {reports.map((report) => (
                  <ElegantCard
                    key={report.id}
                    className="gap-4 p-5"
                    tone={report.tone}
                    variant="filled"
                  >
                    <View className="gap-1">
                      <Text className="text-base font-semibold text-foreground">{report.title}</Text>
                      <Text className="text-sm text-muted">{report.capabilityState.label}</Text>
                    </View>

                    <Text className="text-sm leading-6 text-muted">{report.detail}</Text>

                    {report.viewUrl ? (
                      <ElegantButton
                        fullWidth
                        onPress={() => void openExternalContent(report.viewUrl ?? "")}
                        variant="primary"
                      >
                        Apri documento
                      </ElegantButton>
                    ) : (
                      <ElegantCard className="p-4" variant="outlined">
                        <Text className="text-sm leading-6 text-muted">
                          Il portale non ha fornito un link diretto per questo documento.
                        </Text>
                      </ElegantCard>
                    )}
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Quando la scuola pubblica una pagella o un documento ufficiale, lo troverai qui."
                title="Nessuna pagella disponibile"
              />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
