import { ScrollView, Text, View, ActivityIndicator, RefreshControl } from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { classeviva, Absence } from "@/lib/classeviva-client";
import { useColors } from "@/hooks/use-colors";

export default function AbsencesScreen() {
  const colors = useColors();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [absences, setAbsences] = useState<Absence[]>([]);
  const [error, setError] = useState<string | null>(null);

  const loadAbsences = async () => {
    try {
      setError(null);
      const data = await classeviva.getAbsences();
      setAbsences(data.sort((a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()));
    } catch (err: any) {
      setError(err.message || "Errore nel caricamento delle assenze");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadAbsences();
  }, []);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadAbsences();
  };

  const totalAbsences = absences.filter((a) => a.type === "assenza").length;
  const totalLates = absences.filter((a) => a.type === "ritardo").length;
  const totalExits = absences.filter((a) => a.type === "uscita").length;
  const unjustified = absences.filter((a) => !a.justified).length;

  const getAbsenceIcon = (type: string) => {
    switch (type) {
      case "assenza":
        return "❌";
      case "ritardo":
        return "⏰";
      case "uscita":
        return "🚪";
      default:
        return "📋";
    }
  };

  const getAbsenceLabel = (type: string) => {
    switch (type) {
      case "assenza":
        return "Assenza";
      case "ritardo":
        return "Ritardo";
      case "uscita":
        return "Uscita";
      default:
        return "Evento";
    }
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        refreshControl={<RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />}
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">Assenze</Text>
            <Text className="text-sm text-muted">
              {absences.length} eventi registrati
            </Text>
          </View>

          {error && (
            <View className="p-4 rounded-lg bg-error/10 border border-error">
              <Text className="text-sm text-error">{error}</Text>
            </View>
          )}

          {/* Statistics */}
          <View className="gap-3">
            <View className="grid grid-cols-2 gap-3">
              <View className="rounded-xl bg-surface border border-border p-4 gap-2">
                <Text className="text-xs font-semibold text-muted">Assenze</Text>
                <Text className="text-3xl font-bold text-error">{totalAbsences}</Text>
              </View>
              <View className="rounded-xl bg-surface border border-border p-4 gap-2">
                <Text className="text-xs font-semibold text-muted">Ritardi</Text>
                <Text className="text-3xl font-bold text-warning">{totalLates}</Text>
              </View>
            </View>
            <View className="grid grid-cols-2 gap-3">
              <View className="rounded-xl bg-surface border border-border p-4 gap-2">
                <Text className="text-xs font-semibold text-muted">Uscite</Text>
                <Text className="text-3xl font-bold text-primary">{totalExits}</Text>
              </View>
              <View className="rounded-xl bg-surface border border-border p-4 gap-2">
                <Text className="text-xs font-semibold text-muted">Non Giustificate</Text>
                <Text className="text-3xl font-bold text-error">{unjustified}</Text>
              </View>
            </View>
          </View>

          {/* Absences List */}
          {absences.length > 0 ? (
            <View className="gap-3">
              <Text className="text-base font-semibold text-foreground">Cronologia</Text>
              <View className="gap-2">
                {absences.map((absence) => (
                  <View
                    key={absence.id}
                    className={`rounded-xl border p-4 gap-2 ${
                      absence.justified
                        ? "bg-surface border-border"
                        : "bg-error/10 border-error"
                    }`}
                  >
                    <View className="flex-row items-start justify-between gap-2">
                      <View className="flex-1 gap-2">
                        <View className="flex-row items-center gap-2">
                          <Text className="text-2xl">
                            {getAbsenceIcon(absence.type)}
                          </Text>
                          <View className="flex-1">
                            <Text className="text-sm font-semibold text-foreground">
                              {getAbsenceLabel(absence.type)}
                            </Text>
                            <Text className="text-xs text-muted">
                              {new Date(absence.date).toLocaleDateString("it-IT")}
                            </Text>
                          </View>
                        </View>

                        {/* Justification Status */}
                        <View
                          className={`px-3 py-1 rounded-full w-fit ${
                            absence.justified
                              ? "bg-success/20"
                              : "bg-error/20"
                          }`}
                        >
                          <Text
                            className={`text-xs font-semibold ${
                              absence.justified
                                ? "text-success"
                                : "text-error"
                            }`}
                          >
                            {absence.justified ? "✓ Giustificata" : "⚠ Non giustificata"}
                          </Text>
                        </View>

                        {absence.justificationReason && (
                          <Text className="text-xs text-muted">
                            Motivo: {absence.justificationReason}
                          </Text>
                        )}
                      </View>
                    </View>
                  </View>
                ))}
              </View>
            </View>
          ) : (
            <View className="rounded-xl bg-surface border border-border p-6 items-center">
              <Text className="text-sm text-muted">Nessuna assenza registrata</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
