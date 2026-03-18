import React, { useState } from "react";
import {
  View,
  Text,
  TextInput,
  TouchableOpacity,
  ScrollView,
  ActivityIndicator,
  Alert,
} from "react-native";
import { useAuth } from "@/lib/auth-context";
import { useRouter } from "expo-router";
import { ScreenContainer } from "@/components/screen-container";
import { useColors } from "@/hooks/use-colors";

export default function LoginScreen() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login, error } = useAuth();
  const router = useRouter();
  const colors = useColors();

  const handleLogin = async () => {
    if (!username.trim() || !password.trim()) {
      Alert.alert("Errore", "Inserisci username e password");
      return;
    }

    setIsLoading(true);
    try {
      await login(username, password);
      router.replace("/(tabs)");
    } catch (err: any) {
      Alert.alert(
        "Errore di login",
        err.message || "Credenziali non valide. Riprova."
      );
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <ScreenContainer
      edges={["top", "left", "right", "bottom"]}
      className="bg-background"
    >
      <ScrollView
        contentContainerStyle={{ flexGrow: 1 }}
        className="flex-1"
        showsVerticalScrollIndicator={false}
      >
        <View className="flex-1 justify-center px-6 py-8 gap-8">
          {/* Header */}
          <View className="items-center gap-2">
            <Text className="text-5xl font-bold text-primary">📚</Text>
            <Text className="text-3xl font-bold text-foreground">
              Classeviva
            </Text>
            <Text className="text-base text-muted">
              Accedi al tuo registro elettronico
            </Text>
          </View>

          {/* Form */}
          <View className="gap-4">
            {/* Username Input */}
            <View className="gap-2">
              <Text className="text-sm font-semibold text-foreground">
                Username
              </Text>
              <TextInput
                placeholder="Inserisci il tuo username"
                value={username}
                onChangeText={setUsername}
                editable={!isLoading}
                placeholderTextColor={colors.muted}
                className="px-4 py-3 rounded-lg border border-border bg-surface text-foreground"
                style={{
                  color: colors.foreground,
                  borderColor: colors.border,
                }}
              />
            </View>

            {/* Password Input */}
            <View className="gap-2">
              <Text className="text-sm font-semibold text-foreground">
                Password
              </Text>
              <TextInput
                placeholder="Inserisci la tua password"
                value={password}
                onChangeText={setPassword}
                secureTextEntry
                editable={!isLoading}
                placeholderTextColor={colors.muted}
                className="px-4 py-3 rounded-lg border border-border bg-surface text-foreground"
                style={{
                  color: colors.foreground,
                  borderColor: colors.border,
                }}
              />
            </View>

            {/* Error Message */}
            {error && (
              <View className="p-3 rounded-lg bg-error/10 border border-error">
                <Text className="text-sm text-error">{error}</Text>
              </View>
            )}

            {/* Login Button */}
            <TouchableOpacity
              onPress={handleLogin}
              disabled={isLoading}
              activeOpacity={0.8}
              className="mt-4 py-3 rounded-full bg-primary items-center justify-center"
              style={{
                opacity: isLoading ? 0.7 : 1,
              }}
            >
              {isLoading ? (
                <ActivityIndicator color={colors.background} size="small" />
              ) : (
                <Text className="text-base font-semibold text-background">
                  Accedi
                </Text>
              )}
            </TouchableOpacity>
          </View>

          {/* Help Section */}
          <View className="items-center gap-2">
            <Text className="text-xs text-muted">
              Hai dimenticato le credenziali?
            </Text>
            <TouchableOpacity>
              <Text className="text-xs font-semibold text-primary">
                Recupera le tue credenziali
              </Text>
            </TouchableOpacity>
          </View>

          {/* Info */}
          <View className="p-4 rounded-lg bg-surface border border-border">
            <Text className="text-xs text-muted leading-relaxed">
              Questa app utilizza le tue credenziali Classeviva per accedere ai
              tuoi dati scolastici. Le credenziali vengono salvate in modo
              sicuro sul tuo dispositivo.
            </Text>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
