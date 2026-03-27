import { useRouter } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import {
  ActivityIndicator,
  Pressable,
  RefreshControl,
  ScrollView,
  Text,
  View,
} from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
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

type FilterKey = "all" | "unread";

const FILTER_LABELS: Record<FilterKey, string> = {
  all: "Tutte",
  unread: "Da leggere",
};

export default function CommunicationsScreen() {
  const colors = useColors();
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [communications, setCommunications] = useState<CommunicationRowViewModel[]>([]);
  const [selectedCommunication, setSelectedCommunication] = useState<CommunicationRowViewModel | null>(null);
  const [detail, setDetail] = useState<NoticeboardItemDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [filter, setFilter] = useState<FilterKey>("all");
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadCommunicationsView();
      setCommunications(data);
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
    setSelectedCommunication(item);
    setDetail(null);
    setDetailError(null);
    setIsDetailLoading(true);

    try {
      const nextDetail = await loadCommunicationDetailView(item);
      setDetail(nextDetail);
      setCommunications((current) =>
        current.map((entry) =>
          entry.id === item.id ? { ...entry, statusLabel: "Letta", tone: "neutral" } : entry,
        ),
      );
    } catch (loadError) {
      console.error("Communication detail failed", loadError);
      setDetailError(
        loadError instanceof Error
          ? loadError.message
          : "Non riesco a caricare il dettaglio della comunicazione.",
      );
    } finally {
      setIsDetailLoading(false);
    }
  }, []);

  const unreadCount = useMemo(
    () => communications.filter((item) => item.statusLabel === "Da leggere").length,
    [communications],
  );
  const withAttachmentsCount = useMemo(
    () => communications.filter((item) => item.metadataLabel.toLowerCase().includes("alleg")).length,
    [communications],
  );

  const filteredCommunications = useMemo(() => {
    return communications.filter((item) => {
      if (filter === "unread" && item.statusLabel !== "Da leggere") {
        return false;
      }

      if (!deferredQuery.trim()) {
        return true;
      }

      const query = deferredQuery.toLowerCase();
      return (
        item.title.toLowerCase().includes(query) ||
        item.preview.toLowerCase().includes(query) ||
        item.sender.toLowerCase().includes(query) ||
        item.metadataLabel.toLowerCase().includes(query)
      );
    });
  }, [communications, deferredQuery, filter]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando le comunicazioni"
          detail="Recupero bacheca, stato di lettura e dettagli delle circolari."
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
            eyebrow="Bacheca"
            onBack={() => router.replace("/(tabs)/more")}
            subtitle="Lista completa, filtro per non lette e dettaglio reale di ogni comunicazione."
            title="Comunicazioni"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <View className="flex-row gap-3">
            <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Da leggere</Text>
              <Text className="text-3xl font-semibold text-foreground">{unreadCount}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Con allegati</Text>
              <Text className="text-3xl font-semibold text-foreground">{withAttachmentsCount}</Text>
            </ElegantCard>
          </View>

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca titolo, mittente o contenuto"
            value={searchQuery}
          />

          <View className="gap-3">
            <SectionTitle eyebrow="Filtro" title="Riduci la bacheca" />
            <ScrollView horizontal showsHorizontalScrollIndicator={false}>
              <View className="flex-row gap-3">
                {(Object.keys(FILTER_LABELS) as FilterKey[]).map((key) => (
                  <Pressable key={key} onPress={() => setFilter(key)}>
                    <ElegantCard
                      className="px-4 py-3"
                      tone={filter === key ? "primary" : "neutral"}
                      variant={filter === key ? "filled" : "outlined"}
                    >
                      <Text className="text-sm font-semibold text-foreground">
                        {FILTER_LABELS[key]}
                      </Text>
                    </ElegantCard>
                  </Pressable>
                ))}
              </View>
            </ScrollView>
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Dettaglio" title="Comunicazione selezionata" />
            {selectedCommunication ? (
              <ElegantCard className="gap-4 p-5" tone="primary" variant="filled">
                <View className="gap-1">
                  <Text className="text-base font-semibold text-foreground">
                    {selectedCommunication.title}
                  </Text>
                  <Text className="text-sm text-muted">
                    {selectedCommunication.sender} - {selectedCommunication.dateLabel}
                  </Text>
                </View>

                {isDetailLoading ? (
                  <View className="flex-row items-center gap-3">
                    <ActivityIndicator color={colors.primary} size="small" />
                    <Text className="text-sm text-muted">Sto recuperando il contenuto completo.</Text>
                  </View>
                ) : null}

                {detailError ? (
                  <Text className="text-sm leading-6 text-muted">{detailError}</Text>
                ) : null}

                {detail ? (
                  <View className="gap-4">
                    <Text className="text-sm leading-7 text-muted">{detail.content}</Text>

                    {detail.replyText ? (
                      <ElegantCard className="gap-2 p-4" variant="outlined">
                        <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                          Risposta
                        </Text>
                        <Text className="text-sm leading-6 text-muted">{detail.replyText}</Text>
                      </ElegantCard>
                    ) : null}

                    {detail.attachments.length > 0 ? (
                      <View className="gap-2">
                        <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                          Allegati registrati
                        </Text>
                        {detail.attachments.map((attachment) => (
                          <ElegantCard key={attachment} className="p-4" variant="outlined">
                            <Text className="text-sm text-foreground">{attachment}</Text>
                          </ElegantCard>
                        ))}
                        <Text className="text-xs leading-5 text-muted">
                          Gli allegati risultano presenti nel portale ma non espongono un link diretto via API.
                        </Text>
                      </View>
                    ) : null}
                  </View>
                ) : null}
              </ElegantCard>
            ) : (
              <EmptyState
                detail="Seleziona una riga dalla lista per leggere il testo completo."
                title="Nessuna comunicazione selezionata"
              />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Elenco" title="Tutte le comunicazioni" />
            {filteredCommunications.length > 0 ? (
              <View className="gap-3">
                {filteredCommunications.map((item) => {
                  const isSelected = selectedCommunication?.id === item.id;

                  return (
                    <Pressable key={item.id} onPress={() => void handleSelect(item)}>
                      <ElegantCard
                        className="gap-3 p-4"
                        tone={isSelected ? "primary" : item.tone}
                        variant={isSelected ? "elevated" : "filled"}
                      >
                        <View className="flex-row items-start justify-between gap-3">
                          <View className="flex-1 gap-1">
                            <Text className="text-sm font-semibold text-foreground">{item.title}</Text>
                            <Text className="text-xs text-muted">{item.sender}</Text>
                          </View>
                          <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                            {item.statusLabel}
                          </Text>
                        </View>
                        <Text className="text-sm leading-6 text-muted">{item.preview}</Text>
                        <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                          {item.dateLabel} - {item.metadataLabel}
                        </Text>
                      </ElegantCard>
                    </Pressable>
                  );
                })}
              </View>
            ) : (
              <EmptyState
                detail="Modifica filtro o ricerca per rivedere altre comunicazioni."
                title="Nessuna comunicazione trovata"
              />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
