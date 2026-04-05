import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Alert, Platform, Pressable, RefreshControl, ScrollView, Switch, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import { type NotificationPreferences } from "@/lib/app-preferences";
import { useAuth } from "@/lib/auth-context";
import { notificationsService, type PermissionSnapshot } from "@/lib/notifications-service";
import { loadProfileSnapshot, type ProfileSnapshotViewModel } from "@/lib/student-data";

function InfoBlock({ label, value }: { label: string; value: string }) {
  const colors = useColors();
  return (
    <ElegantCard className="gap-1.5 p-4" radius="md" variant="filled">
      <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
        {label}
      </Text>
      <Text className="text-base font-medium" style={{ color: colors.foreground }}>
        {value}
      </Text>
    </ElegantCard>
  );
}

function NotificationToggleRow({
  label,
  detail,
  value,
  disabled,
  onValueChange,
}: {
  label: string;
  detail: string;
  value: boolean;
  disabled?: boolean;
  onValueChange: (value: boolean) => void;
}) {
  const colors = useColors();

  return (
    <ElegantCard className="flex-row items-center gap-4 p-4" radius="md" variant="filled">
      <View className="flex-1 gap-1">
        <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
          {label}
        </Text>
        <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
          {detail}
        </Text>
      </View>
      <Switch disabled={disabled} onValueChange={onValueChange} value={value} />
    </ElegantCard>
  );
}

function getPermissionBadge(permission: PermissionSnapshot | null) {
  if (!permission) {
    return { label: "In caricamento", tone: "neutral" as const };
  }

  if (permission.status === "granted") {
    return { label: "Permessi concessi", tone: "success" as const };
  }

  if (permission.status === "unsupported") {
    return { label: "Solo build dev", tone: "warning" as const };
  }

  if (permission.status === "denied") {
    return { label: "Permessi negati", tone: "error" as const };
  }

  return { label: "Da autorizzare", tone: "warning" as const };
}

export default function ProfileScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const { user, logout } = useAuth();
  const [snapshot, setSnapshot] = useState<ProfileSnapshotViewModel | null>(null);
  const [preferences, setPreferences] = useState<NotificationPreferences | null>(null);
  const [permission, setPermission] = useState<PermissionSnapshot | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [isNotificationsBusy, setIsNotificationsBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const [profileData, notificationPreferences, permissionSnapshot] = await Promise.all([
        loadProfileSnapshot(user),
        notificationsService.readPreferences(),
        notificationsService.getPermissionSnapshot(),
      ]);
      setSnapshot(profileData);
      setPreferences(notificationPreferences);
      setPermission(permissionSnapshot);
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

  const handleUpdatePreferences = useCallback(
    async (next: Partial<NotificationPreferences>) => {
      try {
        setIsNotificationsBusy(true);
        const updated = await notificationsService.updatePreferences(next);
        setPreferences(updated);
      } catch (notificationError) {
        console.error("Notification preferences update failed", notificationError);
        Alert.alert("Aggiornamento non riuscito", "Non sono riuscito a salvare le preferenze notifiche.");
      } finally {
        setIsNotificationsBusy(false);
      }
    },
    [],
  );

  const handleRequestPermissions = useCallback(async () => {
    try {
      setIsNotificationsBusy(true);
      const nextPermission = await notificationsService.requestPermissions();
      setPermission(nextPermission);
      await notificationsService.initialize();
      Alert.alert(
        nextPermission.status === "granted" ? "Permessi attivi" : "Permessi non concessi",
        nextPermission.status === "granted"
          ? "Le notifiche locali sono pronte e i canali Android risultano configurati."
          : "Per ricevere notifiche reali devi concedere i permessi nella build di sviluppo o nelle impostazioni del sistema.",
      );
    } catch (permissionError) {
      console.error("Request notification permissions failed", permissionError);
      Alert.alert("Operazione non riuscita", "Non sono riuscito a richiedere i permessi notifiche.");
    } finally {
      setIsNotificationsBusy(false);
    }
  }, []);

  const handleSendTestNotification = useCallback(async () => {
    try {
      setIsNotificationsBusy(true);
      const sent = await notificationsService.sendTestNotification();
      const nextPermission = await notificationsService.getPermissionSnapshot();
      setPermission(nextPermission);

      Alert.alert(
        sent ? "Test inviato" : "Test non inviato",
        sent
          ? "Controlla il device: la notifica di test dovrebbe arrivare entro pochi secondi."
          : "Serve una development build con permessi notifiche concessi per eseguire il test.",
      );
    } catch (testError) {
      console.error("Send test notification failed", testError);
      Alert.alert("Test non riuscito", "Non sono riuscito a inviare la notifica di test.");
    } finally {
      setIsNotificationsBusy(false);
    }
  }, []);

  const profile = snapshot?.profile ?? user;
  const initials = useMemo(
    () => `${profile?.name?.[0] ?? ""}${profile?.surname?.[0] ?? ""}`.trim() || "CV",
    [profile],
  );
  const permissionBadge = getPermissionBadge(permission);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState title="Sto caricando il profilo" detail="Recupero anagrafica, materie, periodi e preferenze locali." />
      </ScreenContainer>
    );
  }

  if (!profile) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <EmptyState detail={error ?? "Riprova tra poco per recuperare il profilo."} title="Profilo non disponibile" />
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
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              action={
                <Pressable
                  className="h-11 w-11 items-center justify-center rounded-full"
                  style={{ backgroundColor: colors.errorContainer ?? colors.surface }}
                  onPress={handleLogout}
                >
                  <MaterialIcons color={colors.error} name="logout" size={20} />
                </Pressable>
              }
              backLabel="Altro"
              eyebrow="Profilo"
              onBack={() => router.replace("/(tabs)/more")}
              subtitle="Dati anagrafici, contesto scolastico e impostazioni locali della sessione attiva."
              title="Profilo"
            />
          </AnimatedListItem>

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md">
              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                Aggiornamento parziale
              </Text>
              <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                {error}
              </Text>
            </ElegantCard>
          ) : null}

          <AnimatedListItem index={1}>
            <ElegantCard className="gap-5 p-6" variant="gradient" radius="lg">
              <View className="flex-row items-center gap-4">
                <View className="h-14 w-14 items-center justify-center rounded-full" style={{ backgroundColor: colors.primary }}>
                  <Text className="text-xl font-medium" style={{ color: colors.onPrimary ?? "#FFFFFF" }}>
                    {initials}
                  </Text>
                </View>
                <View className="flex-1 gap-0.5">
                  <Text className="text-xl font-normal" style={{ color: colors.onPrimaryContainer ?? colors.foreground }}>
                    {[profile.name, profile.surname].filter(Boolean).join(" ") || "Studente"}
                  </Text>
                  <Text className="text-sm" style={{ color: colors.onPrimaryContainer ? `${colors.onPrimaryContainer}BB` : colors.muted }}>
                    Sessione reale Classeviva
                  </Text>
                </View>
              </View>

              <Text className="text-sm leading-5" style={{ color: colors.onPrimaryContainer ? `${colors.onPrimaryContainer}CC` : colors.muted }}>
                {[profile.school, [profile.class, profile.section].filter(Boolean).join(" "), profile.schoolYear]
                  .filter(Boolean)
                  .join(" - ") || "Profilo sincronizzato dal portale"}
              </Text>
            </ElegantCard>
          </AnimatedListItem>

          <AnimatedListItem index={2}>
            <View className="gap-3">
              <SectionTitle eyebrow="Account" title="Anagrafica essenziale" />
              <InfoBlock label="Nome" value={profile.name || "Non disponibile"} />
              <InfoBlock label="Cognome" value={profile.surname || "Non disponibile"} />
              <InfoBlock label="Email" value={profile.email || "Non disponibile"} />
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <View className="gap-3">
              <SectionTitle eyebrow="Scuola" title="Contesto scolastico" />
              <InfoBlock label="Istituto" value={profile.school || "Non disponibile"} />
              <InfoBlock label="Classe" value={[profile.class, profile.section].filter(Boolean).join(" ") || "Non disponibile"} />
              <InfoBlock label="Anno scolastico" value={profile.schoolYear || "Non disponibile"} />
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <View className="gap-3">
              <SectionTitle
                eyebrow="Notifiche"
                title="Impostazioni locali"
                detail="Toggle globali, canali Android dedicati e test rapido per verificare il device."
              />
              <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
                <View className="flex-row items-center justify-between gap-3">
                  <View className="gap-1">
                    <Text className="text-base font-medium" style={{ color: colors.foreground }}>
                      Stato permessi
                    </Text>
                    <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                      {permission?.status === "unsupported"
                        ? "In Expo Go le notifiche native restano disattivate: usa una development build."
                        : "Le preferenze restano locali al device e regolano i canali dell'app."}
                    </Text>
                  </View>
                  <StatusBadge label={permissionBadge.label} tone={permissionBadge.tone} />
                </View>

                {preferences ? (
                  <View className="gap-3">
                    <NotificationToggleRow
                      detail="Spegne o riattiva tutta la parte notifiche dell'app."
                      disabled={isNotificationsBusy}
                      label="Notifiche abilitate"
                      onValueChange={(value) => void handleUpdatePreferences({ enabled: value })}
                      value={preferences.enabled}
                    />
                    <NotificationToggleRow
                      detail="Promemoria dedicati ai compiti in scadenza."
                      disabled={isNotificationsBusy || !preferences.enabled}
                      label="Compiti"
                      onValueChange={(value) => void handleUpdatePreferences({ homework: value })}
                      value={preferences.homework}
                    />
                    <NotificationToggleRow
                      detail="Nuove comunicazioni e circolari dalla bacheca."
                      disabled={isNotificationsBusy || !preferences.enabled}
                      label="Comunicazioni"
                      onValueChange={(value) => void handleUpdatePreferences({ communications: value })}
                      value={preferences.communications}
                    />
                    <NotificationToggleRow
                      detail="Promemoria per assenze, ritardi e giustificazioni."
                      disabled={isNotificationsBusy || !preferences.enabled}
                      label="Assenze"
                      onValueChange={(value) => void handleUpdatePreferences({ absences: value })}
                      value={preferences.absences}
                    />
                    <NotificationToggleRow
                      detail="Canale dedicato alla notifica di prova."
                      disabled={isNotificationsBusy || !preferences.enabled}
                      label="Test"
                      onValueChange={(value) => void handleUpdatePreferences({ test: value })}
                      value={preferences.test}
                    />
                  </View>
                ) : null}

                <View className="gap-3">
                  <ElegantButton disabled={isNotificationsBusy} fullWidth onPress={() => void handleRequestPermissions()} variant="secondary">
                    Richiedi permessi notifiche
                  </ElegantButton>
                  <ElegantButton disabled={isNotificationsBusy || !preferences?.enabled} fullWidth onPress={() => void handleSendTestNotification()} variant="primary">
                    Test notifiche
                  </ElegantButton>
                </View>

                {Platform.OS === "android" ? (
                  <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    In Android vedrai i canali Compiti, Comunicazioni, Assenze e Test anche nelle impostazioni di sistema della development build.
                  </Text>
                ) : null}
              </ElegantCard>
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={5}>
            <View className="gap-3">
              <SectionTitle eyebrow="Materie" title="Piano didattico" />
              {snapshot?.subjects.length ? (
                <View className="gap-3">
                  {snapshot.subjects.map((subject) => (
                    <ElegantCard key={subject.id} className="gap-1.5 p-4" variant="filled" radius="md">
                      <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                        {subject.description}
                      </Text>
                      <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {subject.teachers.length > 0 ? subject.teachers.join(", ") : "Docenti non indicati"}
                      </Text>
                    </ElegantCard>
                  ))}
                </View>
              ) : (
                <EmptyState detail="Il portale non ha restituito un elenco materie per questo profilo." title="Materie non disponibili" />
              )}
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={6}>
            <View className="gap-3">
              <SectionTitle eyebrow="Periodi" title="Scansione dell'anno" />
              {snapshot?.periods.length ? (
                <View className="gap-3">
                  {snapshot.periods.map((period) => (
                    <ElegantCard key={period.code} className="gap-1.5 p-4" variant="filled" radius="md">
                      <Text className="text-sm font-medium" style={{ color: colors.foreground }}>
                        {period.label}
                      </Text>
                      <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {period.description}
                      </Text>
                      <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                        {period.startDate} - {period.endDate}
                      </Text>
                    </ElegantCard>
                  ))}
                </View>
              ) : (
                <EmptyState detail="Non risultano periodi configurati in questo momento." title="Periodi non disponibili" />
              )}
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={7}>
            <ElegantButton fullWidth onPress={handleLogout} size="lg" variant="outline">
              Esci
            </ElegantButton>
          </AnimatedListItem>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
