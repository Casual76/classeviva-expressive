import { useRouter } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import {
  loadSchoolbooksView,
  type SchoolbookCourseViewModel,
} from "@/lib/student-data";

export default function SchoolbooksScreen() {
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [courses, setCourses] = useState<SchoolbookCourseViewModel[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadSchoolbooksView();
      setCourses(data);
    } catch (loadError) {
      console.error("Schoolbooks load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare i libri.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const filteredCourses = useMemo(() => {
    const query = deferredQuery.trim().toLowerCase();
    return courses
      .map((course) => ({
        ...course,
        books: course.books.filter((book) => {
          if (!query) {
            return true;
          }

          const haystack = `${book.title} ${book.subtitle} ${book.subject} ${book.authorLabel} ${book.statusLabel}`;
          return haystack.toLowerCase().includes(query);
        }),
      }))
      .filter((course) => course.books.length > 0);
  }, [courses, deferredQuery]);

  const totalBooks = useMemo(
    () => courses.reduce((sum, course) => sum + course.books.length, 0),
    [courses],
  );
  const toBuyCount = useMemo(
    () =>
      courses.reduce(
        (sum, course) => sum + course.books.filter((book) => book.statusLabel === "Da acquistare").length,
        0,
      ),
    [courses],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando i libri"
          detail="Recupero testi, stato di acquisto e organizzazione per corso."
        />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 112 }}
        refreshControl={
          <RefreshControl
            onRefresh={() => {
              setIsRefreshing(true);
              void loadData();
            }}
            refreshing={isRefreshing}
          />
        }
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            backLabel="Altro"
            eyebrow="Libri"
            onBack={() => router.replace("/(tabs)/more")}
            subtitle="Testi ordinati per corso, con stato acquisto e informazioni essenziali per ogni volume."
            title="Libri"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <View className="flex-row gap-3">
            <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Corsi</Text>
              <Text className="text-3xl font-semibold text-foreground">{courses.length}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Libri</Text>
              <Text className="text-3xl font-semibold text-foreground">{totalBooks}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Da comprare</Text>
              <Text className="text-3xl font-semibold text-foreground">{toBuyCount}</Text>
            </ElegantCard>
          </View>

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca titolo, materia o autore"
            value={searchQuery}
          />

          <View className="gap-3">
            <SectionTitle eyebrow="Catalogo" title="Tutti i libri di adozione" />
            {filteredCourses.length > 0 ? (
              <View className="gap-4">
                {filteredCourses.map((course) => (
                  <View key={course.id} className="gap-3">
                    <ElegantCard className="gap-2 p-4" variant="outlined">
                      <Text className="text-sm font-semibold text-foreground">{course.title}</Text>
                      <Text className="text-sm text-muted">{course.books.length} libri</Text>
                    </ElegantCard>

                    <View className="gap-3">
                      {course.books.map((book) => (
                        <ElegantCard key={book.id} className="gap-3 p-4" tone={book.tone} variant="filled">
                          <View className="gap-1">
                            <Text className="text-sm font-semibold text-foreground">{book.title}</Text>
                            <Text className="text-sm text-muted">{book.subtitle}</Text>
                          </View>
                          <View className="flex-row items-start justify-between gap-4">
                            <View className="flex-1 gap-1">
                              <Text className="text-xs text-muted">{book.subject}</Text>
                              <Text className="text-xs text-muted">{book.authorLabel}</Text>
                            </View>
                            <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                              {book.statusLabel}
                            </Text>
                          </View>
                          <Text className="text-sm font-semibold text-foreground">{book.priceLabel}</Text>
                        </ElegantCard>
                      ))}
                    </View>
                  </View>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Modifica la ricerca per rivedere altri libri."
                title="Nessun libro trovato"
              />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
