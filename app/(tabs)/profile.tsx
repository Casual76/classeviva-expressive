import {
  ScrollView,
  Text,
  View,
  TouchableOpacity,
  Alert,
} from "react-native";
import { useAuth } from "@/lib/auth-context";
import { useRouter } from "expo-router";
import { ScreenContainer } from "@/components/screen-container";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ElegantButton } from "@/components/ui/elegant-button";

export default function ProfileScreen() {
  const { user, logout } = useAuth();
  const router = useRouter();
  const colors = useColors();

  const handleLogout = () => {
    Alert.alert("Logout", "Sei sicuro di voler uscire?", [
      {
        text: "Annulla",
        onPress: () => {},
        style: "cancel",
      },
      {
        text: "Esci",
        onPress: async () => {
          await logout();
          router.replace("/login");
        },
        style: "destructive",
      },
    ]);
  };

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-2">
            <Text className="text-4xl font-bold text-foreground">Profilo</Text>
            <Text className="text-sm text-muted">
              Informazioni personali e scolastiche
            </Text>
          </View>

          {/* Profile Card */}
          <ElegantCard
            variant="gradient"
            gradient="primary"
            className="p-6 gap-4 items-center"
          >
            <View className="w-20 h-20 rounded-full bg-background/20 items-center justify-center">
              <Text className="text-5xl">👤</Text>
            </View>
            <View className="items-center gap-2">
              <Text className="text-2xl font-bold text-background text-center">
                {user?.name} {user?.surname}
              </Text>
              <Text className="text-sm text-background/80">
                {user?.class} {user?.section}
              </Text>
            </View>
          </ElegantCard>

          {/* Personal Information */}
          <View className="gap-4">
            <Text className="text-sm font-bold text-foreground">
              Informazioni Personali
            </Text>

            <View className="gap-2">
              <ElegantCard variant="filled" className="p-4 gap-3">
                <View className="gap-1">
                  <Text className="text-xs font-bold text-muted">Nome</Text>
                  <Text className="text-base font-semibold text-foreground">
                    {user?.name || "Non disponibile"}
                  </Text>
                </View>
              </ElegantCard>

              <ElegantCard variant="filled" className="p-4 gap-3">
                <View className="gap-1">
                  <Text className="text-xs font-bold text-muted">Cognome</Text>
                  <Text className="text-base font-semibold text-foreground">
                    {user?.surname || "Non disponibile"}
                  </Text>
                </View>
              </ElegantCard>

              <ElegantCard variant="filled" className="p-4 gap-3">
                <View className="gap-1">
                  <Text className="text-xs font-bold text-muted">Email</Text>
                  <Text className="text-base font-semibold text-foreground">
                    {user?.email || "Non disponibile"}
                  </Text>
                </View>
              </ElegantCard>
            </View>
          </View>

          {/* School Information */}
          <View className="gap-4">
            <Text className="text-sm font-bold text-foreground">
              Informazioni Scolastiche
            </Text>

            <View className="gap-2">
              <ElegantCard variant="filled" className="p-4 gap-3">
                <View className="gap-1">
                  <Text className="text-xs font-bold text-muted">Istituto</Text>
                  <Text className="text-base font-semibold text-foreground">
                    {user?.school || "Non disponibile"}
                  </Text>
                </View>
              </ElegantCard>

              <ElegantCard variant="filled" className="p-4 gap-3">
                <View className="gap-1">
                  <Text className="text-xs font-bold text-muted">Classe</Text>
                  <Text className="text-base font-semibold text-foreground">
                    {user?.class} {user?.section}
                  </Text>
                </View>
              </ElegantCard>

              <ElegantCard variant="filled" className="p-4 gap-3">
                <View className="gap-1">
                  <Text className="text-xs font-bold text-muted">
                    Anno Scolastico
                  </Text>
                  <Text className="text-base font-semibold text-foreground">
                    {user?.schoolYear || "Non disponibile"}
                  </Text>
                </View>
              </ElegantCard>
            </View>
          </View>

          {/* Settings */}
          <View className="gap-4">
            <Text className="text-sm font-bold text-foreground">
              Impostazioni
            </Text>

            <View className="gap-2">
              <ElegantCard
                variant="filled"
                className="p-4 flex-row items-center justify-between"
              >
                <View className="flex-1 gap-1">
                  <Text className="text-sm font-bold text-foreground">
                    Tema
                  </Text>
                  <Text className="text-xs text-muted">
                    Automatico (Chiaro/Scuro)
                  </Text>
                </View>
                <Text className="text-2xl">🌓</Text>
              </ElegantCard>

              <ElegantCard
                variant="filled"
                className="p-4 flex-row items-center justify-between"
              >
                <View className="flex-1 gap-1">
                  <Text className="text-sm font-bold text-foreground">
                    Notifiche
                  </Text>
                  <Text className="text-xs text-muted">Abilitate</Text>
                </View>
                <Text className="text-2xl">🔔</Text>
              </ElegantCard>

              <ElegantCard
                variant="filled"
                className="p-4 flex-row items-center justify-between"
              >
                <View className="flex-1 gap-1">
                  <Text className="text-sm font-bold text-foreground">
                    Sincronizzazione
                  </Text>
                  <Text className="text-xs text-muted">
                    Automatica ogni ora
                  </Text>
                </View>
                <Text className="text-2xl">🔄</Text>
              </ElegantCard>
            </View>
          </View>

          {/* Logout Button */}
          <ElegantButton
            variant="primary"
            size="lg"
            fullWidth
            onPress={handleLogout}
          >
            Esci
          </ElegantButton>

          {/* App Info */}
          <ElegantCard
            variant="filled"
            className="p-5 gap-2 items-center bg-primary/8 border-primary/25"
          >
            <Text className="text-sm font-bold text-foreground">
              Classeviva Expressive
            </Text>
            <Text className="text-xs text-muted">Versione 2.2.0</Text>
            <Text className="text-xs text-muted mt-2">
              © 2026 - Registro Elettronico Moderno
            </Text>
          </ElegantCard>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
