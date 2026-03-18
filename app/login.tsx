import {
  ScrollView,
  Text,
  View,
  TextInput,
  TouchableOpacity,
  ActivityIndicator,
  Alert,
} from "react-native";
import { useAuth } from "@/lib/auth-context";
import { useRouter } from "expo-router";
import { useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ElegantButton } from "@/components/ui/elegant-button";

export default function LoginScreen() {
  const { login, loginDemo, isLoading, error } = useAuth();
  const router = useRouter();
  const colors = useColors();

  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);

  const handleLogin = async () => {
    if (!username.trim() || !password.trim()) {
      Alert.alert("Errore", "Inserisci username e password");
      return;
    }

    try {
      await login(username, password);
      router.replace("/(tabs)");
    } catch (err) {
      // L'errore è già gestito dal context
    }
  };

  const handleDemoLogin = async () => {
    try {
      await loginDemo();
      router.replace("/(tabs)");
    } catch (err) {
      Alert.alert("Errore", "Impossibile accedere in modalità demo");
    }
  };

  return (
    <ScreenContainer
      className="flex-1 bg-background"
      edges={["top", "left", "right", "bottom"]}
    >
      <ScrollView
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ flexGrow: 1 }}
      >
        <View className="flex-1 px-6 py-8 justify-between">
          {/* Header */}
          <View className="gap-8">
            {/* Logo Section */}
            <View className="items-center gap-6 pt-12">
              <ElegantCard
                variant="gradient"
                gradient="primary"
                className="w-24 h-24 items-center justify-center"
              >
                <Text className="text-6xl">📚</Text>
              </ElegantCard>
              <View className="items-center gap-2">
                <Text className="text-5xl font-bold text-foreground">
                  Classeviva
                </Text>
                <Text className="text-lg font-semibold text-primary">
                  Expressive
                </Text>
                <Text className="text-xs text-muted mt-2">
                  Accedi al tuo registro elettronico
                </Text>
              </View>
            </View>

            {/* Login Form */}
            <View className="gap-6">
              {error && (
                <ElegantCard variant="filled" className="p-4 gap-2 bg-error/10 border border-error">
                  <Text className="text-sm font-bold text-error">
                    ⚠️ {error}
                  </Text>
                </ElegantCard>
              )}

              {/* Username Field */}
              <View className="gap-2">
                <Text className="text-sm font-bold text-foreground">
                  Username
                </Text>
                <ElegantCard variant="filled" className="px-4 py-3 flex-row items-center">
                  <TextInput
                    placeholder="Inserisci username"
                    placeholderTextColor={colors.muted}
                    value={username}
                    onChangeText={setUsername}
                    editable={!isLoading}
                    className="flex-1 text-base text-foreground"
                  />
                </ElegantCard>
              </View>

              {/* Password Field */}
              <View className="gap-2">
                <Text className="text-sm font-bold text-foreground">
                  Password
                </Text>
                <ElegantCard variant="filled" className="px-4 py-3 flex-row items-center gap-2">
                  <TextInput
                    placeholder="Inserisci password"
                    placeholderTextColor={colors.muted}
                    value={password}
                    onChangeText={setPassword}
                    secureTextEntry={!showPassword}
                    editable={!isLoading}
                    className="flex-1 text-base text-foreground"
                  />
                  <TouchableOpacity
                    onPress={() => setShowPassword(!showPassword)}
                    disabled={isLoading}
                  >
                    <Text className="text-xl">
                      {showPassword ? "👁️" : "👁️‍🗨️"}
                    </Text>
                  </TouchableOpacity>
                </ElegantCard>
              </View>

              {/* Login Button */}
              <ElegantButton
                variant="primary"
                size="lg"
                fullWidth
                onPress={handleLogin}
                disabled={isLoading}
              >
                {isLoading ? (
                  <ActivityIndicator color={colors.background} size="small" />
                ) : (
                  "Accedi"
                )}
              </ElegantButton>
            </View>
          </View>

          {/* Demo Mode Section */}
          <View className="gap-5">
            {/* Divider */}
            <View className="flex-row items-center gap-3">
              <View className="flex-1 h-px bg-border" />
              <Text className="text-xs font-bold text-muted">OPPURE</Text>
              <View className="flex-1 h-px bg-border" />
            </View>

            {/* Demo Button */}
            <ElegantButton
              variant="outline"
              size="lg"
              fullWidth
              onPress={handleDemoLogin}
              disabled={isLoading}
            >
              Accedi in Modalità Demo
            </ElegantButton>

            {/* Demo Info */}
            <ElegantCard variant="filled" className="p-5 gap-3 bg-primary/8 border border-primary/25">
              <View className="flex-row items-center gap-2">
                <Text className="text-lg">🎯</Text>
                <Text className="text-xs font-bold text-primary">
                  Modalità Demo Attiva
                </Text>
              </View>
              <Text className="text-xs text-muted leading-relaxed">
                Esplora tutte le funzionalità dell'app con dati di esempio
                realistici senza inserire credenziali.
              </Text>
            </ElegantCard>

            {/* Footer */}
            <Text className="text-xs text-muted text-center">
              Classeviva Expressive v2.0.0
            </Text>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
