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
            <View className="items-center gap-4 pt-8">
              <View className="w-20 h-20 rounded-3xl bg-gradient-to-br from-primary to-primary/80 items-center justify-center shadow-lg">
                <Text className="text-5xl">📚</Text>
              </View>
              <View className="items-center gap-2">
                <Text className="text-4xl font-bold text-foreground">
                  Classeviva
                </Text>
                <Text className="text-base text-muted">Expressive</Text>
              </View>
            </View>

            {/* Login Form */}
            <View className="gap-6">
              {error && (
                <View className="p-4 rounded-xl bg-error/10 border border-error">
                  <Text className="text-sm font-semibold text-error">
                    {error}
                  </Text>
                </View>
              )}

              {/* Username Field */}
              <View className="gap-2">
                <Text className="text-sm font-semibold text-foreground">
                  Username
                </Text>
                <View className="rounded-xl bg-surface border border-border px-4 py-3 flex-row items-center">
                  <TextInput
                    placeholder="Inserisci username"
                    placeholderTextColor={colors.muted}
                    value={username}
                    onChangeText={setUsername}
                    editable={!isLoading}
                    className="flex-1 text-base text-foreground"
                  />
                </View>
              </View>

              {/* Password Field */}
              <View className="gap-2">
                <Text className="text-sm font-semibold text-foreground">
                  Password
                </Text>
                <View className="rounded-xl bg-surface border border-border px-4 py-3 flex-row items-center gap-2">
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
                </View>
              </View>

              {/* Login Button */}
              <TouchableOpacity
                onPress={handleLogin}
                disabled={isLoading}
                className="py-4 rounded-xl bg-primary items-center justify-center active:opacity-80"
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
          </View>

          {/* Demo Mode Section */}
          <View className="gap-4">
            {/* Divider */}
            <View className="flex-row items-center gap-3">
              <View className="flex-1 h-px bg-border" />
              <Text className="text-xs font-semibold text-muted">OPPURE</Text>
              <View className="flex-1 h-px bg-border" />
            </View>

            {/* Demo Button */}
            <TouchableOpacity
              onPress={handleDemoLogin}
              disabled={isLoading}
              className="py-4 rounded-xl border-2 border-primary items-center justify-center active:opacity-80"
            >
              <Text className="text-base font-semibold text-primary">
                Accedi in Modalità Demo
              </Text>
            </TouchableOpacity>

            {/* Demo Info */}
            <View className="p-4 rounded-xl bg-primary/5 border border-primary/20 gap-2">
              <Text className="text-xs font-semibold text-primary">
                💡 Modalità Demo
              </Text>
              <Text className="text-xs text-muted leading-relaxed">
                Accedi senza credenziali per esplorare tutte le funzionalità
                dell'app con dati di esempio realistici.
              </Text>
            </View>

            {/* Footer */}
            <Text className="text-xs text-muted text-center">
              Classeviva Expressive v1.0.0
            </Text>
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
