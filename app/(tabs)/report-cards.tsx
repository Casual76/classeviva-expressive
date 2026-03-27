import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import { openExternalContent } from "@/lib/open-content";
import { loadReportCardsView, type SchoolReportViewModel } from "@/lib/student-data";

export default function ReportCardsScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [reports, setReports] = useState<SchoolReportViewModel[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try { setError(null); const data = await loadReportCardsView(); setReports(data); }
    catch (loadError) { console.error("Report cards load failed", loadError); setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare le pagelle."); }
    finally { setIsLoading(false); setIsRefreshing(false); }
  }, []);

  useEffect(() => { void loadData(); }, [loadData]);

  const openableCount = useMemo(() => reports.filter((r) => r.capabilityState.status !== "unavailable").length, [reports]);

  if (isLoading) {
    return (<ScreenContainer className="flex-1 bg-background"><LoadingState title="Sto caricando le pagelle" detail="Recupero i documenti pubblicati dal portale Classeviva." /></ScreenContainer>);
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={{ paddingBottom: 100 }} refreshControl={<RefreshControl onRefresh={() => { setIsRefreshing(true); void loadData(); }} refreshing={isRefreshing} />} showsVerticalScrollIndicator={false}>
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader backLabel="Altro" eyebrow="Documenti" onBack={() => router.replace("/(tabs)/more")} subtitle="Pagelle e documenti ufficiali, con apertura esterna quando il portale fornisce il link." title="Pagelle" />
          </AnimatedListItem>

          {error ? (<ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md"><Text className="text-sm font-medium" style={{ color: colors.foreground }}>Aggiornamento parziale</Text><Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{error}</Text></ElegantCard>) : null}

          <AnimatedListItem index={1}>
            <View className="flex-row gap-3">
              <ElegantCard className="flex-1 gap-1.5 p-4" tone="primary" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Documenti</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{reports.length}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Apribili</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{openableCount}</Text>
              </ElegantCard>
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Elenco" title="Documenti disponibili" />
            {reports.length > 0 ? (
              <View className="gap-3">
                {reports.map((report, i) => (
                  <AnimatedListItem key={report.id} index={2 + i}>
                    <ElegantCard className="gap-4 p-5" tone={report.tone} variant="filled" radius="lg">
                      <View className="gap-0.5">
                        <Text className="text-base font-medium" style={{ color: colors.foreground }}>{report.title}</Text>
                        <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{report.capabilityState.label}</Text>
                      </View>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{report.detail}</Text>
                      {report.viewUrl ? (
                        <ElegantButton fullWidth onPress={() => void openExternalContent(report.viewUrl ?? "")} variant="primary">Apri documento</ElegantButton>
                      ) : (
                        <ElegantCard className="p-4" variant="outlined" radius="md">
                          <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Il portale non ha fornito un link diretto per questo documento.</Text>
                        </ElegantCard>
                      )}
                    </ElegantCard>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Quando la scuola pubblica una pagella o un documento ufficiale, lo troverai qui." title="Nessuna pagella disponibile" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
