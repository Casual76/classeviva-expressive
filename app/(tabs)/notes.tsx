import { useRouter } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import type { NoteDetail } from "@/lib/classeviva-client";
import {
  loadNoteDetailView,
  loadNotesView,
  type NoteRowViewModel,
} from "@/lib/student-data";

export default function NotesScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [notes, setNotes] = useState<NoteRowViewModel[]>([]);
  const [selectedNote, setSelectedNote] = useState<NoteRowViewModel | null>(null);
  const [detail, setDetail] = useState<NoteDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [selectedCategory, setSelectedCategory] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadNotesView();
      setNotes(data);
    } catch (loadError) {
      console.error("Notes load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare le note.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSelect = useCallback(async (item: NoteRowViewModel) => {
    setSelectedNote(item);
    setDetail(null);
    setDetailError(null);
    setIsDetailLoading(true);
    try {
      const nextDetail = await loadNoteDetailView(item);
      setDetail(nextDetail);
    } catch (loadError) {
      console.error("Note detail failed", loadError);
      setDetailError(loadError instanceof Error ? loadError.message : "Non riesco a caricare il dettaglio.");
    } finally {
      setIsDetailLoading(false);
    }
  }, []);

  const categories = useMemo(
    () => Array.from(new Set(notes.map((item) => item.badgeLabel).filter(Boolean))).sort((a, b) => a.localeCompare(b, "it-IT")),
    [notes],
  );

  const filteredNotes = useMemo(() => {
    return notes.filter((item) => {
      if (selectedCategory && item.badgeLabel !== selectedCategory) return false;
      if (!deferredQuery.trim()) return true;
      const query = deferredQuery.toLowerCase();
      return (
        item.title.toLowerCase().includes(query) ||
        item.preview.toLowerCase().includes(query) ||
        item.author.toLowerCase().includes(query) ||
        item.badgeLabel.toLowerCase().includes(query)
      );
    });
  }, [deferredQuery, notes, selectedCategory]);

  const attentionCount = useMemo(
    () => notes.filter((item) => item.tone === "warning" || item.tone === "error").length,
    [notes],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState title="Sto caricando le note" detail="Recupero richiami, note docente e dettagli associati." />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 100 }}
        refreshControl={<RefreshControl onRefresh={() => { setIsRefreshing(true); void loadData(); }} refreshing={isRefreshing} />}
        showsVerticalScrollIndicator={false}
      >
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              backLabel="Altro"
              eyebrow="Disciplina"
              onBack={() => router.replace("/(tabs)/more")}
              subtitle="Note reali dal portale, con filtro per categoria e lettura completa del contenuto."
              title="Note"
            />
          </AnimatedListItem>

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled" radius="md">
              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>Aggiornamento parziale</Text>
              <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{error}</Text>
            </ElegantCard>
          ) : null}

          <AnimatedListItem index={1}>
            <View className="flex-row gap-3">
              <ElegantCard className="flex-1 gap-1.5 p-4" tone="warning" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Da attenzionare</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{attentionCount}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Totale note</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{notes.length}</Text>
              </ElegantCard>
            </View>
          </AnimatedListItem>

          <SearchBar onChangeText={setSearchQuery} onClear={() => setSearchQuery("")} placeholder="Cerca testo, autore o categoria" value={searchQuery} />

          {categories.length > 0 ? (
            <View className="gap-3">
              <SectionTitle eyebrow="Categorie" title="Filtra la vista" />
              <ScrollView horizontal showsHorizontalScrollIndicator={false}>
                <View className="flex-row gap-2">
                  <M3Chip label="Tutte" selected={selectedCategory === null} onPress={() => setSelectedCategory(null)} />
                  {categories.map((category) => (
                    <M3Chip key={category} label={category} selected={selectedCategory === category} onPress={() => setSelectedCategory(category)} />
                  ))}
                </View>
              </ScrollView>
            </View>
          ) : null}

          <View className="gap-3">
            <SectionTitle eyebrow="Dettaglio" title="Nota selezionata" />
            {selectedNote ? (
              <ElegantCard className="gap-4 p-5" tone={selectedNote.tone} variant="filled" radius="lg">
                <View className="gap-0.5">
                  <Text className="text-base font-medium" style={{ color: colors.foreground }}>{selectedNote.title}</Text>
                  <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    {selectedNote.badgeLabel} — {selectedNote.author} — {selectedNote.dateLabel}
                  </Text>
                </View>
                {isDetailLoading ? (
                  <View className="flex-row items-center gap-3">
                    <ActivityIndicator color={colors.primary} size="small" />
                    <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Sto recuperando il dettaglio completo.</Text>
                  </View>
                ) : null}
                {detailError ? <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{detailError}</Text> : null}
                {detail ? <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{detail.content}</Text> : null}
              </ElegantCard>
            ) : (
              <EmptyState detail="Seleziona una nota dalla lista per leggere il testo completo." title="Nessuna nota selezionata" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Elenco" title="Tutte le note" />
            {filteredNotes.length > 0 ? (
              <View className="gap-3">
                {filteredNotes.map((item, i) => {
                  const isSelected = selectedNote?.id === item.id && selectedNote?.categoryCode === item.categoryCode;
                  return (
                    <AnimatedListItem key={`${item.categoryCode}-${item.id}`} index={3 + i}>
                      <Pressable onPress={() => void handleSelect(item)}>
                        <ElegantCard className="gap-3 p-4" tone={isSelected ? "primary" : item.tone} variant={isSelected ? "elevated" : "filled"} radius="md">
                          <View className="flex-row items-start justify-between gap-3">
                            <View className="flex-1 gap-0.5">
                              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{item.title}</Text>
                              <Text className="text-xs" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.author}</Text>
                            </View>
                            <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.badgeLabel}</Text>
                          </View>
                          <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.preview}</Text>
                          <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.dateLabel}</Text>
                        </ElegantCard>
                      </Pressable>
                    </AnimatedListItem>
                  );
                })}
              </View>
            ) : (
              <EmptyState detail="Modifica filtro o ricerca per vedere altre note." title="Nessuna nota trovata" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
