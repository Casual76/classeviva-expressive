import { ScrollView, Text, View, TouchableOpacity, Alert } from "react-native";
import { useAuth } from "@/lib/auth-context";
import { useRouter } from "expo-router";
import { ScreenContainer } from "@/components/screen-container";
import { useColors } from "@/hooks/use-colors";

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
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">Profilo</Text>
            <Text className="text-sm text-muted">Informazioni personali</Text>
          </View>

          {/* Profile Card */}
          <View className="rounded-2xl bg-gradient-to-br from-primary to-primary/80 p-6 gap-4">
            <View className="items-center gap-2">
              <View className="w-16 h-16 rounded-full bg-background/20 items-center justify-center">
                <Text className="text-4xl">👤</Text>
              </View>
              <View className="items-center gap-1">
                <Text className="text-2xl font-bold text-background">
                  {user?.name} {user?.surname}
                </Text>
                <Text className="text-sm text-background/80">
                  {user?.class} {user?.section}
                </Text>
              </View>
            </View>
          </View>

          {/* Personal Information */}
          <View className="gap-4">
            <Text className="text-lg font-semibold text-foreground">
              Informazioni Personali
            </Text>

            <View className="rounded-xl bg-surface border border-border p-4 gap-3">
              <View className="gap-1">
                <Text className="text-xs font-semibold text-muted">Nome</Text>
                <Text className="text-base text-foreground">
                  {user?.name || "Non disponibile"}
                </Text>
              </View>
              <View className="h-px bg-border" />
              <View className="gap-1">
                <Text className="text-xs font-semibold text-muted">Cognome</Text>
                <Text className="text-base text-foreground">
                  {user?.surname || "Non disponibile"}
                </Text>
              </View>
              <View className="h-px bg-border" />
              <View className="gap-1">
                <Text className="text-xs font-semibold text-muted">Email</Text>
                <Text className="text-base text-foreground">
                  {user?.email || "Non disponibile"}
                </Text>
              </View>
            </View>
          </View>

          {/* School Information */}
          <View className="gap-4">
            <Text className="text-lg font-semibold text-foreground">
              Informazioni Scolastiche
            </Text>

            <View className="rounded-xl bg-surface border border-border p-4 gap-3">
              <View className="gap-1">
                <Text className="text-xs font-semibold text-muted">Istituto</Text>
                <Text className="text-base text-foreground">
                  {user?.school || "Non disponibile"}
                </Text>
              </View>
              <View className="h-px bg-border" />
              <View className="gap-1">
                <Text className="text-xs font-semibold text-muted">Classe</Text>
                <Text className="text-base text-foreground">
                  {user?.class} {user?.section}
                </Text>
              </View>
              <View className="h-px bg-border" />
              <View className="gap-1">
                <Text className="text-xs font-semibold text-muted">
                  Anno Scolastico
                </Text>
                <Text className="text-base text-foreground">
                  {user?.schoolYear || "Non disponibile"}
                </Text>
              </View>
            </View>
          </View>

          {/* Settings */}
          <View className="gap-4">
            <Text className="text-lg font-semibold text-foreground">
              Impostazioni
            </Text>

            <View className="rounded-xl bg-surface border border-border overflow-hidden">
              <TouchableOpacity className="p-4 flex-row items-center justify-between active:opacity-70">
                <View className="gap-1">
                  <Text className="text-base font-semibold text-foreground">
                    Tema
                  </Text>
                  <Text className="text-xs text-muted">
                    Automatico
                  </Text>
                </View>
                <Text className="text-lg">🌓</Text>
              </TouchableOpacity>
              <View className="h-px bg-border" />
              <TouchableOpacity className="p-4 flex-row items-center justify-between active:opacity-70">
                <View className="gap-1">
                  <Text className="text-base font-semibold text-foreground">
                    Notifiche
                  </Text>
                  <Text className="text-xs text-muted">
                    Abilitate
                  </Text>
                </View>
                <Text className="text-lg">🔔</Text>
              </TouchableOpacity>
            </View>
          </View>

          {/* Logout Button */}
          <TouchableOpacity
            onPress={handleLogout}
            className="py-3 rounded-full bg-error items-center justify-center active:opacity-80"
          >
            <Text className="text-base font-semibold text-background">
              Esci
            </Text>
          </TouchableOpacity>

          {/* App Info */}
          <View className="p-4 rounded-lg bg-surface border border-border gap-2 items-center">
            <Text className="text-xs text-muted">Classeviva Expressive</Text>
            <Text className="text-xs text-muted">Versione 1.0.0</Text>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
