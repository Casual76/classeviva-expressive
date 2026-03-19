import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { Alert, Pressable, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Fonts } from "@/constants/theme";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

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
  const { user, sessionMode, logout } = useAuth();

  const handleLogout = () => {
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
  };

  const initials = `${user?.name?.[0] ?? ""}${user?.surname?.[0] ?? ""}`.trim() || "CV";

  return (
    <ScreenContainer className="flex-1 bg-background" edges={["top", "left", "right", "bottom"]}>
      <ScrollView contentContainerStyle={{ paddingBottom: 40 }} showsVerticalScrollIndicator={false}>
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            action={
              <Pressable
                className="h-12 w-12 items-center justify-center rounded-full"
                onPress={handleLogout}
                style={{ backgroundColor: colors.surface }}
              >
                <MaterialIcons color={colors.error} name="logout" size={20} />
              </Pressable>
            }
            backLabel="Home"
            eyebrow="Profilo"
            onBack={() => router.back()}
            subtitle="Dati base dello studente e stato della sessione attiva."
            title="Profilo"
          />

          <ElegantCard className="gap-5 p-6" gradient="primary" variant="gradient">
            <View className="flex-row items-center justify-between gap-4">
              <View className="flex-row items-center gap-4">
                <View className="h-16 w-16 items-center justify-center rounded-full bg-background/15">
                  <Text
                    className="text-2xl text-background"
                    style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                  >
                    {initials}
                  </Text>
                </View>
                <View className="gap-1">
                  <Text className="text-2xl font-semibold text-background">
                    {[user?.name, user?.surname].filter(Boolean).join(" ") || "Studente"}
                  </Text>
                  <Text className="text-sm text-background/75">
                    {sessionMode === "demo" ? "Modalita demo" : "Sessione reale Classeviva"}
                  </Text>
                </View>
              </View>
            </View>

            <Text className="text-sm leading-6 text-background/80">
              Un solo contratto auth gestisce ripristino sessione, demo fallback e logout.
            </Text>
          </ElegantCard>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Account</Text>
            <InfoBlock label="Nome" value={user?.name || "Non disponibile"} />
            <InfoBlock label="Cognome" value={user?.surname || "Non disponibile"} />
            <InfoBlock label="Email" value={user?.email || "Non disponibile"} />
          </View>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Scuola</Text>
            <InfoBlock label="Istituto" value={user?.school || "Non disponibile"} />
            <InfoBlock
              label="Classe"
              value={[user?.class, user?.section].filter(Boolean).join(" ") || "Non disponibile"}
            />
            <InfoBlock label="Anno scolastico" value={user?.schoolYear || "Non disponibile"} />
          </View>

          <View className="gap-3">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">MVP notes</Text>
            <ElegantCard className="gap-3 p-4" variant="outlined">
              <Text className="text-sm font-semibold text-foreground">Navigazione semplificata</Text>
              <Text className="text-sm leading-6 text-muted">
                Profilo e assenze escono dalla tab bar e diventano destinazioni secondarie raggiungibili dalla Home.
              </Text>
            </ElegantCard>
            <ElegantCard className="gap-3 p-4" variant="outlined">
              <Text className="text-sm font-semibold text-foreground">Web come fallback</Text>
              <Text className="text-sm leading-6 text-muted">
                Il browser resta utile per demo e presentazione, mentre il login reale e ottimizzato per mobile.
              </Text>
            </ElegantCard>
          </View>

          <ElegantButton fullWidth onPress={handleLogout} size="lg" variant="primary">
            Esci
          </ElegantButton>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
