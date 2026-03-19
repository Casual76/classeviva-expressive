import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useState } from "react";
import {
  ActivityIndicator,
  Platform,
  Pressable,
  ScrollView,
  Text,
  TextInput,
  View,
} from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { Fonts } from "@/constants/theme";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

const IS_WEB = Platform.OS === "web";

export default function LoginScreen() {
  const colors = useColors();
  const router = useRouter();
  const { login, loginDemo, isLoading, error, clearError } = useAuth();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = async () => {
    if (!username.trim() || !password.trim()) {
      return;
    }

    try {
      await login(username.trim(), password);
      router.replace("/(tabs)");
    } catch {
      // Error surfaced by auth context.
    }
  };

  const handleDemoLogin = async () => {
    try {
      await loginDemo();
      router.replace("/(tabs)");
    } catch {
      // Error surfaced by auth context.
    }
  };

  const featureRows = [
    {
      title: "Dashboard piu leggibile",
      body: "Media, scadenze e assenze in una home davvero utile.",
      icon: "dashboard",
    },
    {
      title: "Navigazione ripulita",
      body: "Tre tab essenziali e sezioni secondarie dal centro dell'app.",
      icon: "layers",
    },
    {
      title: "Fallback demo affidabile",
      body: "Sul web puoi provare l'esperienza senza credenziali reali.",
      icon: "play-circle-outline",
    },
  ] as const;

  return (
    <ScreenContainer className="flex-1 bg-background" edges={["top", "left", "right", "bottom"]}>
      <ScrollView
        contentContainerStyle={{ flexGrow: 1 }}
        showsVerticalScrollIndicator={false}
      >
        <View className="flex-1 px-6 pb-8 pt-4">
          <View className="gap-5">
            <ElegantCard className="p-6" tone="neutral" variant="filled">
              <View className="gap-6">
                <View className="flex-row items-start justify-between gap-4">
                  <View className="flex-1 gap-3">
                    <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">
                      Classeviva Expressive
                    </Text>
                    <Text
                      className="text-[40px] leading-[44px] text-foreground"
                      style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                    >
                      Registro scolastico, ripensato per mobile.
                    </Text>
                    <Text className="text-base leading-7 text-muted">
                      Accesso reale da app mobile, demo curata e stabile sul web.
                    </Text>
                  </View>

                  <View
                    className="h-14 w-14 items-center justify-center rounded-full"
                    style={{ backgroundColor: colors.primary }}
                  >
                    <MaterialIcons color={colors.background} name="school" size={24} />
                  </View>
                </View>

                <View className="gap-3">
                  {featureRows.map((feature) => (
                    <View key={feature.title} className="flex-row items-start gap-3">
                      <View
                        className="mt-1 h-10 w-10 items-center justify-center rounded-full"
                        style={{ backgroundColor: colors.surfaceAlt ?? colors.surface }}
                      >
                        <MaterialIcons color={colors.primary} name={feature.icon} size={18} />
                      </View>
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-semibold text-foreground">{feature.title}</Text>
                        <Text className="text-sm leading-6 text-muted">{feature.body}</Text>
                      </View>
                    </View>
                  ))}
                </View>
              </View>
            </ElegantCard>

            <ElegantCard className="p-6" variant="elevated">
              <View className="gap-5">
                <View className="gap-2">
                  <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">
                    Accesso
                  </Text>
                  <Text
                    className="text-3xl leading-[36px] text-foreground"
                    style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                  >
                    {IS_WEB ? "Apri subito la demo" : "Accedi con le tue credenziali"}
                  </Text>
                  <Text className="text-sm leading-6 text-muted">
                    {IS_WEB
                      ? "Il login Classeviva reale resta mobile-first. Qui puoi usare una demo completa e coerente."
                      : "Usa le credenziali del registro elettronico per iniziare. La demo rimane disponibile come fallback."}
                  </Text>
                </View>

                {error ? (
                  <ElegantCard className="p-4" tone="error" variant="filled">
                    <Text className="text-sm font-semibold text-foreground">{error}</Text>
                  </ElegantCard>
                ) : null}

                {!IS_WEB ? (
                  <View className="gap-4">
                    <View className="gap-2">
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        Username
                      </Text>
                      <ElegantCard className="px-4 py-4" variant="filled">
                        <TextInput
                          autoCapitalize="none"
                          className="text-base text-foreground"
                          editable={!isLoading}
                          onChangeText={(value) => {
                            clearError();
                            setUsername(value);
                          }}
                          placeholder="Inserisci username"
                          placeholderTextColor={colors.muted}
                          value={username}
                        />
                      </ElegantCard>
                    </View>

                    <View className="gap-2">
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        Password
                      </Text>
                      <ElegantCard className="flex-row items-center gap-3 px-4 py-4" variant="filled">
                        <TextInput
                          className="flex-1 text-base text-foreground"
                          editable={!isLoading}
                          onChangeText={(value) => {
                            clearError();
                            setPassword(value);
                          }}
                          placeholder="Inserisci password"
                          placeholderTextColor={colors.muted}
                          secureTextEntry={!showPassword}
                          value={password}
                        />
                        <Pressable
                          className="h-10 w-10 items-center justify-center rounded-full"
                          onPress={() => setShowPassword((current) => !current)}
                          style={{ backgroundColor: colors.surfaceAlt ?? colors.background }}
                        >
                          <MaterialIcons
                            color={colors.foreground}
                            name={showPassword ? "visibility-off" : "visibility"}
                            size={18}
                          />
                        </Pressable>
                      </ElegantCard>
                    </View>

                    <ElegantButton
                      disabled={isLoading || !username.trim() || !password.trim()}
                      fullWidth
                      onPress={handleLogin}
                      size="lg"
                      variant="primary"
                    >
                      {isLoading ? (
                        <ActivityIndicator color={colors.background} size="small" />
                      ) : (
                        "Accedi"
                      )}
                    </ElegantButton>
                  </View>
                ) : null}

                <View className="gap-3">
                  <ElegantButton
                    disabled={isLoading}
                    fullWidth
                    onPress={handleDemoLogin}
                    size="lg"
                    variant={IS_WEB ? "primary" : "secondary"}
                  >
                    {isLoading ? (
                      <ActivityIndicator color={IS_WEB ? colors.background : colors.foreground} size="small" />
                    ) : (
                      "Entra in modalita demo"
                    )}
                  </ElegantButton>

                  <View className="flex-row items-start gap-3 rounded-[24px] border border-border px-4 py-4">
                    <MaterialIcons color={colors.primary} name="info-outline" size={18} />
                    <Text className="flex-1 text-sm leading-6 text-muted">
                      {IS_WEB
                        ? "La demo usa dati realistici e mantiene la stessa struttura delle schermate mobili."
                        : "Se il registro non risponde o sei offline, puoi comunque entrare in demo senza perdere il flusso."}
                    </Text>
                  </View>
                </View>
              </View>
            </ElegantCard>
          </View>

          <View className="mt-auto pt-6">
            <Text className="text-center text-xs uppercase tracking-[2px] text-muted">
              Mobile-first MVP · Marzo 2026
            </Text>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
