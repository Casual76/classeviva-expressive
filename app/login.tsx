import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { useCallback, useRef, useState } from "react";
import {
  ActivityIndicator,
  KeyboardAvoidingView,
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

function FeatureRow({
  icon,
  title,
  detail,
}: {
  icon: keyof typeof MaterialIcons.glyphMap;
  title: string;
  detail: string;
}) {
  const colors = useColors();

  return (
    <View className="flex-row items-start gap-3">
      <View
        className="mt-1 h-10 w-10 items-center justify-center rounded-full"
        style={{ backgroundColor: colors.surfaceAlt ?? colors.surface }}
      >
        <MaterialIcons color={colors.primary} name={icon} size={18} />
      </View>
      <View className="flex-1 gap-1">
        <Text className="text-sm font-semibold text-foreground">{title}</Text>
        <Text className="text-sm leading-6 text-muted">{detail}</Text>
      </View>
    </View>
  );
}

export default function LoginScreen() {
  const colors = useColors();
  const router = useRouter();
  const { login, isLoading, error, clearError } = useAuth();

  const passwordInputRef = useRef<TextInput>(null);
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = useCallback(async () => {
    if (!username.trim() || !password.trim() || isLoading) {
      return;
    }

    try {
      await login(username.trim(), password);
      router.replace("/(tabs)");
    } catch {
      // Error surfaced by auth context.
    }
  }, [isLoading, login, password, router, username]);

  return (
    <ScreenContainer className="flex-1 bg-background" edges={["top", "left", "right", "bottom"]}>
      <KeyboardAvoidingView
        behavior={Platform.OS === "ios" ? "padding" : undefined}
        className="flex-1"
      >
        <ScrollView
          contentContainerStyle={{ flexGrow: 1, paddingBottom: 24 }}
          keyboardShouldPersistTaps="handled"
          showsVerticalScrollIndicator={false}
        >
          <View className="flex-1 gap-5 px-6 py-6">
            <ElegantCard className="gap-6 p-6" gradient="primary" variant="gradient">
              <View className="flex-row items-start justify-between gap-4">
                <View className="flex-1 gap-3">
                  <Text className="text-xs font-semibold uppercase tracking-[2px] text-background/75">
                    Classeviva live
                  </Text>
                  <Text
                    className="text-[38px] leading-[42px] text-background"
                    style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                  >
                    Registro scolastico, ripensato per essere davvero usabile.
                  </Text>
                  <Text className="text-sm leading-7 text-background/80">
                    Accesso reale, dati sincronizzati dal portale e navigazione piu chiara per l&apos;uso quotidiano.
                  </Text>
                </View>

                <View className="h-14 w-14 items-center justify-center rounded-full bg-background/15">
                  <MaterialIcons color={colors.background} name="school" size={24} />
                </View>
              </View>

              <View className="gap-3">
                <FeatureRow
                  detail="Solo sessione reale, con persistenza sicura e ripristino automatico."
                  icon="verified-user"
                  title="Accesso serio"
                />
                <FeatureRow
                  detail="Campi configurati per riconoscimento username/password e compilazione assistita."
                  icon="password"
                  title="Autofill e password manager"
                />
                <FeatureRow
                  detail="Home, voti, agenda, assenze e funzioni secondarie organizzate per priorita."
                  icon="dashboard"
                  title="Esperienza mobile-first"
                />
              </View>
            </ElegantCard>

            <ElegantCard className="gap-5 p-6" variant="elevated">
              <View className="gap-2">
                <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">Accesso</Text>
                <Text
                  className="text-3xl leading-[36px] text-foreground"
                  style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
                >
                  Entra con le tue credenziali Classeviva
                </Text>
                <Text className="text-sm leading-6 text-muted">
                  I campi qui sotto sono preparati per essere riconosciuti dal telefono come username e password.
                </Text>
              </View>

              {error ? (
                <ElegantCard className="p-4" tone="error" variant="filled">
                  <Text className="text-sm font-semibold text-foreground">{error}</Text>
                </ElegantCard>
              ) : null}

              <View className="gap-4">
                <View className="gap-2">
                  <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                    Username
                  </Text>
                  <ElegantCard className="px-4 py-4" variant="filled">
                    <TextInput
                      accessibilityLabel="Username Classeviva"
                      autoCapitalize="none"
                      autoComplete="username"
                      autoCorrect={false}
                      blurOnSubmit={false}
                      className="text-base text-foreground"
                      editable={!isLoading}
                      importantForAutofill="yes"
                      onChangeText={(value) => {
                        clearError();
                        setUsername(value);
                      }}
                      onSubmitEditing={() => passwordInputRef.current?.focus()}
                      placeholder="Inserisci username"
                      placeholderTextColor={colors.muted}
                      returnKeyType="next"
                      style={{ color: colors.foreground }}
                      textContentType="username"
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
                      ref={passwordInputRef}
                      accessibilityLabel="Password Classeviva"
                      autoCapitalize="none"
                      autoComplete="current-password"
                      autoCorrect={false}
                      className="flex-1 text-base text-foreground"
                      editable={!isLoading}
                      importantForAutofill="yes"
                      onChangeText={(value) => {
                        clearError();
                        setPassword(value);
                      }}
                      onSubmitEditing={() => void handleLogin()}
                      placeholder="Inserisci password"
                      placeholderTextColor={colors.muted}
                      returnKeyType="go"
                      secureTextEntry={!showPassword}
                      style={{ color: colors.foreground }}
                      textContentType="password"
                      value={password}
                    />
                    <Pressable
                      accessibilityLabel={showPassword ? "Nascondi password" : "Mostra password"}
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
                  onPress={() => void handleLogin()}
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

              <ElegantCard className="gap-2 p-4" variant="outlined">
                <Text className="text-sm font-semibold text-foreground">Sessione protetta</Text>
                <Text className="text-sm leading-6 text-muted">
                  Dopo il login la sessione viene salvata in modo sicuro e ripristinata automaticamente all&apos;apertura successiva.
                </Text>
              </ElegantCard>
            </ElegantCard>
          </View>
        </ScrollView>
      </KeyboardAvoidingView>
    </ScreenContainer>
  );
}
