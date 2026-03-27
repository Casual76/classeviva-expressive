import { useRouter } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import { loadSchoolbooksView, type SchoolbookCourseViewModel } from "@/lib/student-data";

export default function SchoolbooksScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [courses, setCourses] = useState<SchoolbookCourseViewModel[]>([]);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try { setError(null); const data = await loadSchoolbooksView(); setCourses(data); }
    catch (loadError) { console.error("Schoolbooks load failed", loadError); setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare i libri."); }
    finally { setIsLoading(false); setIsRefreshing(false); }
  }, []);

  useEffect(() => { void loadData(); }, [loadData]);

  const filteredCourses = useMemo(() => {
    const query = deferredQuery.trim().toLowerCase();
    return courses.map((course) => ({
      ...course,
      books: course.books.filter((book) => {
        if (!query) return true;
        return `${book.title} ${book.subtitle} ${book.subject} ${book.authorLabel} ${book.statusLabel}`.toLowerCase().includes(query);
      }),
    })).filter((course) => course.books.length > 0);
  }, [courses, deferredQuery]);

  const totalBooks = useMemo(() => courses.reduce((sum, c) => sum + c.books.length, 0), [courses]);
  const toBuyCount = useMemo(() => courses.reduce((s, c) => s + c.books.filter((b) => b.statusLabel === "Da acquistare").length, 0), [courses]);

  if (isLoading) {
    return (<ScreenContainer className="flex-1 bg-background"><LoadingState title="Sto caricando i libri" detail="Recupero testi, stato di acquisto e organizzazione per corso." /></ScreenContainer>);
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={{ paddingBottom: 100 }} refreshControl={<RefreshControl onRefresh={() => { setIsRefreshing(true); void loadData(); }} refreshing={isRefreshing} />} showsVerticalScrollIndicator={false}>
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader backLabel="Altro" eyebrow="Libri" onBack={() => router.replace("/(tabs)/more")} subtitle="Testi ordinati per corso, con stato acquisto e informazioni essenziali per ogni volume." title="Libri" />
          </AnimatedListItem>

          {error ? (<ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md"><Text className="text-sm font-medium" style={{ color: colors.foreground }}>Aggiornamento parziale</Text><Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{error}</Text></ElegantCard>) : null}

          <AnimatedListItem index={1}>
            <View className="flex-row gap-3">
              <ElegantCard className="flex-1 gap-1.5 p-4" tone="primary" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Corsi</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{courses.length}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Libri</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{totalBooks}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-1.5 p-4" tone="warning" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Da comprare</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{toBuyCount}</Text>
              </ElegantCard>
            </View>
          </AnimatedListItem>

          <SearchBar onChangeText={setSearchQuery} onClear={() => setSearchQuery("")} placeholder="Cerca titolo, materia o autore" value={searchQuery} />

          <View className="gap-3">
            <SectionTitle eyebrow="Catalogo" title="Tutti i libri di adozione" />
            {filteredCourses.length > 0 ? (
              <View className="gap-4">
                {filteredCourses.map((course, ci) => (
                  <AnimatedListItem key={course.id} index={2 + ci}>
                    <View className="gap-3">
                      <ElegantCard className="gap-1.5 p-4" variant="outlined" radius="md">
                        <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{course.title}</Text>
                        <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{course.books.length} libri</Text>
                      </ElegantCard>
                      <View className="gap-3">
                        {course.books.map((book) => (
                          <ElegantCard key={book.id} className="gap-3 p-4" tone={book.tone} variant="filled" radius="md">
                            <View className="gap-0.5">
                              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{book.title}</Text>
                              <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{book.subtitle}</Text>
                            </View>
                            <View className="flex-row items-start justify-between gap-4">
                              <View className="flex-1 gap-0.5">
                                <Text className="text-xs" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{book.subject}</Text>
                                <Text className="text-xs" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{book.authorLabel}</Text>
                              </View>
                              <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{book.statusLabel}</Text>
                            </View>
                            <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{book.priceLabel}</Text>
                          </ElegantCard>
                        ))}
                      </View>
                    </View>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Modifica la ricerca per rivedere altri libri." title="Nessun libro trovato" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
