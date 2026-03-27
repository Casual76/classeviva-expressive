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
import type { NoticeboardItemDetail } from "@/lib/classeviva-client";
import {
  loadCommunicationDetailView,
  loadCommunicationsView,
  type CommunicationRowViewModel,
} from "@/lib/student-data";

export default function CommunicationsScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [items, setItems] = useState<CommunicationRowViewModel[]>([]);
  const [selectedItem, setSelectedItem] = useState<CommunicationRowViewModel | null>(null);
  const [detail, setDetail] = useState<NoticeboardItemDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [filter, setFilter] = useState<"all" | "read" | "unread">("all");
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadCommunicationsView();
      setItems(data);
    } catch (loadError) {
      console.error("Communications load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare le comunicazioni.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSelect = useCallback(async (item: CommunicationRowViewModel) => {
    setSelectedItem(item);
    setDetail(null);
    setDetailError(null);
    setIsDetailLoading(true);
    try {
      const nextDetail = await loadCommunicationDetailView(item);
      setDetail(nextDetail);
    } catch (loadError) {
      console.error("Communication detail failed", loadError);
      setDetailError(loadError instanceof Error ? loadError.message : "Non riesco a caricare il contenuto.");
    } finally {
      setIsDetailLoading(false);
    }
  }, []);

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (filter === "read" && item.statusLabel !== "Letta") return false;
      if (filter === "unread" && item.statusLabel === "Letta") return false;
      if (!deferredQuery.trim()) return true;
      const query = deferredQuery.toLowerCase();
      return (
        item.title.toLowerCase().includes(query) ||
        item.preview.toLowerCase().includes(query) ||
        item.metadataLabel.toLowerCase().includes(query)
      );
    });
  }, [items, filter, deferredQuery]);

  const unreadCount = useMemo(() => items.filter((item) => item.statusLabel !== "Letta").length, [items]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState title="Sto caricando le comunicazioni" detail="Recupero circolari, contenuti e stato di lettura." />
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
              eyebrow="Bacheca"
              onBack={() => router.replace("/(tabs)/more")}
              subtitle="Circolari, allegati e contenuti completi con stato di lettura."
              title="Comunicazioni"
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
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Non lette</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{unreadCount}</Text>
              </ElegantCard>
              <ElegantCard className="flex-1 gap-1.5 p-4" variant="filled" radius="md">
                <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Totale</Text>
                <Text className="text-3xl font-light" style={{ color: colors.foreground }}>{items.length}</Text>
              </ElegantCard>
            </View>
          </AnimatedListItem>

          <SearchBar onChangeText={setSearchQuery} onClear={() => setSearchQuery("")} placeholder="Cerca titolo, contenuto o mittente" value={searchQuery} />

          <View className="gap-3">
            <SectionTitle eyebrow="Filtro" title="Stato lettura" />
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View className="flex-row gap-2">
                <M3Chip label="Tutte" selected={filter === "all"} onPress={() => setFilter("all")} />
                <M3Chip label="Non lette" selected={filter === "unread"} onPress={() => setFilter("unread")} />
                <M3Chip label="Lette" selected={filter === "read"} onPress={() => setFilter("read")} />
              </View>
            </ScrollView>
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Dettaglio" title="Comunicazione selezionata" />
            {selectedItem ? (
              <ElegantCard className="gap-4 p-5" tone="primary" variant="filled" radius="lg">
                <View className="gap-0.5">
                  <Text className="text-base font-medium" style={{ color: colors.foreground }}>{selectedItem.title}</Text>
                  <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    {selectedItem.metadataLabel} — {selectedItem.dateLabel}
                  </Text>
                </View>
                {isDetailLoading ? (
                  <View className="flex-row items-center gap-3">
                    <ActivityIndicator color={colors.primary} size="small" />
                    <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>Sto caricando il contenuto completo.</Text>
                  </View>
                ) : null}
                {detailError ? <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{detailError}</Text> : null}
                {detail ? <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{detail.content}</Text> : null}
              </ElegantCard>
            ) : (
              <EmptyState detail="Seleziona una comunicazione per leggere il contenuto completo." title="Nessuna comunicazione selezionata" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Elenco" title="Tutte le comunicazioni" />
            {filteredItems.length > 0 ? (
              <View className="gap-3">
                {filteredItems.map((item, i) => {
                  const isSelected = selectedItem?.id === item.id;
                  return (
                    <AnimatedListItem key={item.id} index={4 + i}>
                      <Pressable onPress={() => void handleSelect(item)}>
                        <ElegantCard className="gap-3 p-4" tone={isSelected ? "primary" : item.tone} variant={isSelected ? "elevated" : "filled"} radius="md">
                          <View className="flex-row items-start justify-between gap-3">
                            <View className="flex-1 gap-0.5">
                              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>{item.title}</Text>
                              <Text className="text-xs" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{item.metadataLabel}</Text>
                            </View>
                            <Text className="text-[11px] font-medium uppercase tracking-[1.5px]" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                              {item.statusLabel === "Letta" ? "Letta" : "Nuova"}
                            </Text>
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
              <EmptyState detail="Modifica filtro o ricerca per trovare altre comunicazioni." title="Nessuna comunicazione trovata" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
