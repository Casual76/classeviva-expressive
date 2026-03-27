import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { Fonts } from "@/constants/theme";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";
import {
  loadProfileSnapshot,
  type ProfileSnapshotViewModel,
} from "@/lib/student-data";

function InfoBlock({ label, value }: { label: string; value: string }) {
  return (
    <ElegantCard className="gap-2 p-4" variant="filled">
      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">{label}</Text>
      <Text className="text-base font-semibold text-foreground">{value}</Text>
    </ElegantCard>
  );
}

export default function ProfileScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const { user, logout } = useAuth();
  const [snapshot, setSnapshot] = useState<ProfileSnapshotViewModel | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadProfileSnapshot(user);
      setSnapshot(data);
    } catch (loadError) {
      console.error("Profile load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare il profilo.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, [user]);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleLogout = useCallback(() => {
    Alert.alert("Esci", "Vuoi davvero chiudere la sessione corrente?", [
      { text: "Annulla", style: "cancel" },
      {
        text: "Esci",
        style: "destructive",
        onPress: async () => {
          await logout();
          router.replace("/login");
        },
      },
    ]);
  }, [logout, router]);

  const profile = snapshot?.profile ?? user;
  const initials = useMemo(
    () => `${profile?.name?.[0] ?? ""}${profile?.surname?.[0] ?? ""}`.trim() || "CV",
    [profile],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando il profilo"
          detail="Recupero anagrafica, materie e periodi della sessione attiva."
        />
      </ScreenContainer>
    );
  }

  if (!profile) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <EmptyState
          detail={error ?? "Riprova tra poco per recuperare il profilo."}
          title="Profilo non disponibile"
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
            action={
              <Pressable
                className="h-12 w-12 items-center justify-center rounded-full border border-border bg-surface"
                onPress={handleLogout}
              >
                <MaterialIcons color={colors.error} name="logout" size={20} />
              </Pressable>
            }
            backLabel="Altro"
            eyebrow="Profilo"
            onBack={() => router.replace("/(tabs)/more")}
            subtitle="Dati anagrafici, assetto scolastico e panoramica della sessione attiva."
            title="Profilo"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <ElegantCard className="gap-5 p-6" gradient="primary" variant="gradient">
            <View className="flex-row items-center gap-4">
              <View className="h-16 w-16 items-center justify-center rounded-full bg-background/15">
                <Text
                  className="text-2xl text-background"
                  style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                >
                  {initials}
                </Text>
              </View>
              <View className="flex-1 gap-1">
                <Text className="text-2xl font-semibold text-background">
                  {[profile.name, profile.surname].filter(Boolean).join(" ") || "Studente"}
                </Text>
                <Text className="text-sm text-background/75">Sessione reale Classeviva</Text>
              </View>
            </View>

            <Text className="text-sm leading-6 text-background/80">
              {[profile.school, [profile.class, profile.section].filter(Boolean).join(" "), profile.schoolYear]
                .filter(Boolean)
                .join(" - ") || "Profilo sincronizzato dal portale"}
            </Text>
          </ElegantCard>

          <View className="gap-3">
            <SectionTitle eyebrow="Account" title="Anagrafica essenziale" />
            <InfoBlock label="Nome" value={profile.name || "Non disponibile"} />
            <InfoBlock label="Cognome" value={profile.surname || "Non disponibile"} />
            <InfoBlock label="Email" value={profile.email || "Non disponibile"} />
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Scuola" title="Contesto scolastico" />
            <InfoBlock label="Istituto" value={profile.school || "Non disponibile"} />
            <InfoBlock
              label="Classe"
              value={[profile.class, profile.section].filter(Boolean).join(" ") || "Non disponibile"}
            />
            <InfoBlock label="Anno scolastico" value={profile.schoolYear || "Non disponibile"} />
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Materie" title="Piano didattico" />
            {snapshot?.subjects.length ? (
              <View className="gap-3">
                {snapshot.subjects.map((subject) => (
                  <ElegantCard key={subject.id} className="gap-2 p-4" variant="filled">
                    <Text className="text-sm font-semibold text-foreground">{subject.description}</Text>
                    <Text className="text-sm leading-6 text-muted">
                      {subject.teachers.length > 0 ? subject.teachers.join(", ") : "Docenti non indicati"}
                    </Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Il portale non ha restituito un elenco materie per questo profilo."
                title="Materie non disponibili"
              />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Periodi" title="Scansione dell'anno" />
            {snapshot?.periods.length ? (
              <View className="gap-3">
                {snapshot.periods.map((period) => (
                  <ElegantCard key={period.code} className="gap-2 p-4" variant="filled">
                    <Text className="text-sm font-semibold text-foreground">{period.label}</Text>
                    <Text className="text-sm text-muted">{period.description}</Text>
                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                      {period.startDate} - {period.endDate}
                    </Text>
                  </ElegantCard>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Non risultano periodi configurati in questo momento."
                title="Periodi non disponibili"
              />
            )}
          </View>

          <ElegantButton fullWidth onPress={handleLogout} size="lg" variant="primary">
            Esci
          </ElegantButton>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
