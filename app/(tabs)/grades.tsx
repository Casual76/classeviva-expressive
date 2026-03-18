import {
  ScrollView,
  Text,
  View,
  ActivityIndicator,
  RefreshControl,
  TouchableOpacity,
} from "react-native";
import { useEffect, useState } from "react";
import { ScreenContainer } from "@/components/screen-container";
import { useAuth } from "@/lib/auth-context";
import { generateMockGrades } from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";
import { ElegantCard } from "@/components/ui/elegant-card";
// Grade type defined locally
interface Grade {
  id: string;
  subject: string;
  grade: number | string;
  teacher: string;
  date: string;
  type: "scritto" | "orale" | "pratico" | "compito";
}

export default function GradesScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [grades, setGrades] = useState<Grade[]>([]);
  const [selectedSubject, setSelectedSubject] = useState<string | null>(null);

  const loadGrades = async () => {
    try {
      if (isDemoMode) {
        const mockGrades = generateMockGrades();
        setGrades(mockGrades as any);
      }
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadGrades();
  }, [isDemoMode]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadGrades();
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  const gradesBySubject = grades.reduce(
    (acc, grade) => {
      if (!acc[grade.subject]) {
        acc[grade.subject] = [];
      }
      acc[grade.subject].push(grade);
      return acc;
    },
    {} as Record<string, Grade[]>
  );

  const subjects = Object.keys(gradesBySubject);
  const filteredGrades = selectedSubject
    ? gradesBySubject[selectedSubject]
    : grades;

  const getSubjectAverage = (subject: string) => {
    const subjectGrades = gradesBySubject[subject]
      .map((g: any) => {
        const num = parseFloat(String(g.grade));
        return isNaN(num) ? 0 : num;
      })
      .filter((g: number) => g > 0);

    if (subjectGrades.length === 0) return 0;
    return (subjectGrades.reduce((a: number, b: number) => a + b, 0) / subjectGrades.length).toFixed(1);
  };

  const getGradeColor = (grade: number | string) => {
    const num = parseFloat(String(grade));
    if (num >= 8) return "text-success";
    if (num >= 7) return "text-primary";
    if (num >= 6) return "text-warning";
    return "text-error";
  };

  const getGradeBackground = (grade: number | string) => {
    const num = parseFloat(String(grade));
    if (num >= 8) return "bg-success/10";
    if (num >= 7) return "bg-primary/10";
    if (num >= 6) return "bg-warning/10";
    return "bg-error/10";
  };

  const getTypeLabel = (type: string) => {
    const labels: Record<string, string> = {
      scritto: "📝 Scritto",
      orale: "🗣️ Orale",
      pratico: "🔬 Pratico",
      compito: "📋 Compito",
    };
    return labels[type] || type;
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
            <Text className="text-4xl font-bold text-foreground">Voti</Text>
            <Text className="text-sm text-muted">
              {grades.length} valutazioni registrate
            </Text>
          </View>

          {/* Subject Filter */}
          {subjects.length > 0 && (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                Filtra per Materia
              </Text>
              <ScrollView
                horizontal
                showsHorizontalScrollIndicator={false}
                contentContainerStyle={{ gap: 8 }}
              >
                <TouchableOpacity
                  onPress={() => setSelectedSubject(null)}
                  activeOpacity={0.7}
                >
                  <ElegantCard
                    variant={selectedSubject === null ? "gradient" : "outlined"}
                    className={`px-4 py-2 ${
                      selectedSubject === null ? "" : "border-primary"
                    }`}
                  >
                    <Text
                      className={`text-sm font-bold ${
                        selectedSubject === null
                          ? "text-background"
                          : "text-primary"
                      }`}
                    >
                      Tutte
                    </Text>
                  </ElegantCard>
                </TouchableOpacity>

                {subjects.map((subject) => (
                  <TouchableOpacity
                    key={subject}
                    onPress={() => setSelectedSubject(subject)}
                    activeOpacity={0.7}
                  >
                    <ElegantCard
                      variant={
                        selectedSubject === subject ? "gradient" : "outlined"
                      }
                      className={`px-4 py-2 ${
                        selectedSubject === subject ? "" : "border-primary"
                      }`}
                    >
                      <Text
                        className={`text-sm font-bold ${
                          selectedSubject === subject
                            ? "text-background"
                            : "text-primary"
                        }`}
                      >
                        {subject.substring(0, 10)}
                      </Text>
                    </ElegantCard>
                  </TouchableOpacity>
                ))}
              </ScrollView>
            </View>
          )}

          {/* Subject Averages */}
          {!selectedSubject && subjects.length > 0 && (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                Medie per Materia
              </Text>
              <View className="gap-2">
                {subjects.map((subject) => (
                  <ElegantCard
                    key={subject}
                    variant="filled"
                    className="p-4 flex-row items-center justify-between"
                  >
                    <Text className="text-sm font-semibold text-foreground flex-1">
                      {subject}
                    </Text>
                    <View className="items-center gap-1">
                      <Text
                        className={`text-2xl font-bold ${getGradeColor(
                          getSubjectAverage(subject)
                        )}`}
                      >
                        {getSubjectAverage(subject)}
                      </Text>
                      <Text className="text-xs text-muted">
                        {gradesBySubject[subject].length} voti
                      </Text>
                    </View>
                  </ElegantCard>
                ))}
              </View>
            </View>
          )}

          {/* Grades List */}
          {filteredGrades.length > 0 ? (
            <View className="gap-3">
              <Text className="text-sm font-bold text-foreground">
                {selectedSubject ? `Voti - ${selectedSubject}` : "Ultimi Voti"}
              </Text>
              <View className="gap-2">
                {filteredGrades.map((grade: any) => (
                  <ElegantCard
                    key={grade.id}
                    variant="filled"
                    className={`p-4 gap-3 ${getGradeBackground(grade.grade)}`}
                  >
                    {/* Top Row */}
                    <View className="flex-row items-center justify-between">
                      <View className="flex-1 gap-1">
                        <Text className="text-sm font-bold text-foreground">
                          {grade.subject}
                        </Text>
                        <Text className="text-xs text-muted">
                          {grade.teacher}
                        </Text>
                      </View>
                      <View className="items-center gap-1">
                        <Text
                          className={`text-4xl font-bold ${getGradeColor(
                            grade.grade
                          )}`}
                        >
                          {grade.grade}
                        </Text>
                      </View>
                    </View>

                    {/* Bottom Row */}
                    <View className="flex-row items-center justify-between pt-2 border-t border-border/30">
                      <Text className="text-xs text-muted">
                        {getTypeLabel(grade.type)}
                      </Text>
                      <Text className="text-xs text-muted">
                        {new Date(grade.date).toLocaleDateString("it-IT")}
                      </Text>
                    </View>
                  </ElegantCard>
                ))}
              </View>
            </View>
          ) : (
            <ElegantCard variant="filled" className="p-6 items-center">
              <Text className="text-sm text-muted">
                Nessun voto disponibile
              </Text>
            </ElegantCard>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
