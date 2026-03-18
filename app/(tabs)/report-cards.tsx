import { ScrollView, Text, View, ActivityIndicator } from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { generateMockReportCards } from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";

interface ReportCard {
  id: string;
  period: string;
  date: string;
  grades: Array<{
    subject: string;
    grade: number;
    teacher: string;
  }>;
  overallGrade?: string;
  credits?: number;
}

export default function ReportCardsScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [reportCards, setReportCards] = useState<ReportCard[]>([]);

  useEffect(() => {
    const loadReportCards = async () => {
      try {
        if (isDemoMode) {
          setReportCards(generateMockReportCards());
        }
        // TODO: Implementare caricamento da API reale
      } finally {
        setIsLoading(false);
      }
    };

    loadReportCards();
  }, [isDemoMode]);

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
        showsVerticalScrollIndicator={false}
        contentContainerStyle={{ paddingBottom: 20 }}
      >
        <View className="px-6 py-6 gap-6">
          {/* Header */}
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">Pagelle</Text>
            <Text className="text-sm text-muted">
              {reportCards.length} pagelle disponibili
            </Text>
          </View>

          {/* Report Cards */}
          {reportCards.length > 0 ? (
            <View className="gap-4">
              {reportCards.map((reportCard) => (
                <View
                  key={reportCard.id}
                  className="rounded-2xl bg-surface border border-border overflow-hidden"
                >
                  {/* Header */}
                  <View className="bg-gradient-to-r from-primary to-primary/80 px-6 py-4 gap-2">
                    <Text className="text-lg font-bold text-background">
                      {reportCard.period}
                    </Text>
                    <Text className="text-sm text-background/80">
                      {new Date(reportCard.date).toLocaleDateString("it-IT", {
                        year: "numeric",
                        month: "long",
                        day: "numeric",
                      })}
                    </Text>
                  </View>

                  {/* Grades */}
                  <View className="p-6 gap-4">
                    {reportCard.grades.map((grade, index) => (
                      <View key={index}>
                        <View className="flex-row items-center justify-between gap-2 mb-2">
                          <View className="flex-1">
                            <Text className="text-sm font-semibold text-foreground">
                              {grade.subject}
                            </Text>
                            <Text className="text-xs text-muted">
                              {grade.teacher}
                            </Text>
                          </View>
                          <View className="items-center gap-1">
                            <Text className="text-2xl font-bold text-primary">
                              {grade.grade}
                            </Text>
                          </View>
                        </View>
                        {index < reportCard.grades.length - 1 && (
                          <View className="h-px bg-border" />
                        )}
                      </View>
                    ))}

                    {/* Overall Grade and Credits */}
                    {(reportCard.overallGrade || reportCard.credits) && (
                      <>
                        <View className="h-px bg-border" />
                        <View className="flex-row items-center justify-between gap-4 pt-2">
                          {reportCard.overallGrade && (
                            <View className="flex-1 gap-1">
                              <Text className="text-xs font-semibold text-muted">
                                Giudizio Globale
                              </Text>
                              <Text className="text-base font-bold text-foreground">
                                {reportCard.overallGrade}
                              </Text>
                            </View>
                          )}
                          {reportCard.credits && (
                            <View className="flex-1 gap-1">
                              <Text className="text-xs font-semibold text-muted">
                                Crediti
                              </Text>
                              <Text className="text-base font-bold text-primary">
                                {reportCard.credits}
                              </Text>
                            </View>
                          )}
                        </View>
                      </>
                    )}
                  </View>
                </View>
              ))}
            </View>
          ) : (
            <View className="rounded-xl bg-surface border border-border p-6 items-center">
              <Text className="text-sm text-muted">Nessuna pagella disponibile</Text>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
