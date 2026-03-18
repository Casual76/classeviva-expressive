import {
  ScrollView,
  Text,
  View,
  ActivityIndicator,
  RefreshControl,
} from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { generateMockAbsences } from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";

interface Absence {
  id: string;
  date: string;
  type: "assenza" | "ritardo" | "uscita";
  justified: boolean;
  justificationReason?: string;
}

export default function AbsencesScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [absences, setAbsences] = useState<Absence[]>([]);

  const loadAbsences = async () => {
    try {
      if (isDemoMode) {
        const mockAbsences = generateMockAbsences();
        setAbsences(mockAbsences as any);
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadAbsences();
  }, [isDemoMode]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadAbsences();
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  const totalAbsences = absences.filter((a: any) => a.type === "assenza").length;
  const totalLates = absences.filter((a: any) => a.type === "ritardo").length;
  const totalExits = absences.filter((a: any) => a.type === "uscita").length;
  const unjustified = absences.filter((a: any) => !a.justified).length;

  const getAbsenceIcon = (type: string) => {
    const icons: Record<string, string> = {
      assenza: "❌",
      ritardo: "⏰",
      uscita: "🚪",
    };
    return icons[type] || "📋";
  };

  const getAbsenceLabel = (type: string) => {
    const labels: Record<string, string> = {
      assenza: "Assenza",
      ritardo: "Ritardo",
      uscita: "Uscita",
    };
    return labels[type] || "Evento";
  };

  const getAbsenceColor = (type: string, justified: boolean) => {
    if (!justified) return "bg-error/10 border-error/30";
    switch (type) {
      case "assenza":
        return "bg-error/10 border-error/30";
      case "ritardo":
        return "bg-warning/10 border-warning/30";
      case "uscita":
        return "bg-primary/10 border-primary/30";
      default:
        return "bg-surface";
    }
  };

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        refreshControl={
          <RefreshControl refreshing={isRefreshing} onRefresh={handleRefresh} />
        }
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-2">
            <Text className="text-4xl font-bold text-foreground">Assenze</Text>
            <Text className="text-sm text-muted">
              {absences.length} eventi registrati
            </Text>
          </View>

          {/* Statistics Grid */}
          <View className="gap-3">
            <View className="flex-row gap-3">
              <ElegantCard
                variant="filled"
                className="flex-1 p-4 gap-2 bg-error/10 border-error/30"
              >
                <Text className="text-xs font-bold text-muted">Assenze</Text>
                <Text className="text-3xl font-bold text-error">
                  {totalAbsences}
                </Text>
              </ElegantCard>
              <ElegantCard
                variant="filled"
                className="flex-1 p-4 gap-2 bg-warning/10 border-warning/30"
              >
                <Text className="text-xs font-bold text-muted">Ritardi</Text>
                <Text className="text-3xl font-bold text-warning">
                  {totalLates}
                </Text>
              </ElegantCard>
            </View>

            <View className="flex-row gap-3">
              <ElegantCard
                variant="filled"
                className="flex-1 p-4 gap-2 bg-primary/10 border-primary/30"
              >
                <Text className="text-xs font-bold text-muted">Uscite</Text>
                <Text className="text-3xl font-bold text-primary">
                  {totalExits}
                </Text>
              </ElegantCard>
              <ElegantCard
                variant="filled"
                className="flex-1 p-4 gap-2 bg-error/10 border-error/30"
              >
                <Text className="text-xs font-bold text-muted">
                  Non Giustificate
                </Text>
                <Text className="text-3xl font-bold text-error">
                  {unjustified}
                </Text>
              </ElegantCard>
            </View>
          </View>

          {/* Absences List */}
          {absences.length > 0 ? (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                Cronologia
              </Text>
              <View className="gap-2">
                {absences.map((absence: any) => (
                  <ElegantCard
                    key={absence.id}
                    variant="filled"
                    className={`p-4 gap-3 border ${getAbsenceColor(
                      absence.type,
                      absence.justified
                    )}`}
                  >
                    {/* Top Row */}
                    <View className="flex-row items-center gap-3">
                      <Text className="text-3xl">
                        {getAbsenceIcon(absence.type)}
                      </Text>
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-bold text-foreground">
                          {getAbsenceLabel(absence.type)}
                        </Text>
                        <Text className="text-xs text-muted">
                          {new Date(absence.date).toLocaleDateString("it-IT", {
                            weekday: "long",
                            day: "numeric",
                            month: "long",
                          })}
                        </Text>
                      </View>
                      <View
                        className={`px-3 py-1 rounded-full ${
                          absence.justified
                            ? "bg-success/20"
                            : "bg-error/20"
                        }`}
                      >
                        <Text
                          className={`text-xs font-bold ${
                            absence.justified
                              ? "text-success"
                              : "text-error"
                          }`}
                        >
                          {absence.justified ? "✓" : "⚠"}
                        </Text>
                      </View>
                    </View>

                    {/* Justification Status */}
                    <View className="pt-2 border-t border-border/30">
                      <Text
                        className={`text-xs font-bold ${
                          absence.justified
                            ? "text-success"
                            : "text-error"
                        }`}
                      >
                        {absence.justified
                          ? "✓ Giustificata"
                          : "⚠ Non giustificata"}
                      </Text>
                      {absence.justificationReason && (
                        <Text className="text-xs text-muted mt-1">
                          {absence.justificationReason}
                        </Text>
                      )}
                    </View>
                  </ElegantCard>
                ))}
              </View>
            </View>
          ) : (
            <ElegantCard variant="filled" className="p-6 items-center">
              <Text className="text-sm text-muted">
                Nessuna assenza registrata
              </Text>
            </ElegantCard>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
