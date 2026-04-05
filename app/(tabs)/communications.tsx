import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { EmptyState } from "@/components/ui/empty-state";
import { LoadingState } from "@/components/ui/loading-state";
import { M3Chip } from "@/components/ui/m3-chip";
import { MetricTile } from "@/components/ui/metric-tile";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useColors } from "@/hooks/use-colors";
import type { NoticeboardItemDetail } from "@/lib/classeviva-client";
import { openExternalContent } from "@/lib/open-content";
import {
  acknowledgeCommunicationView,
  joinCommunicationView,
  loadCommunicationDetailView,
  loadCommunicationsView,
  type CommunicationRowViewModel,
} from "@/lib/student-data";

type ReadFilter = "all" | "read" | "unread";

export default function CommunicationsScreen() {
  const colors = useColors();
  const [items, setItems] = useState<CommunicationRowViewModel[]>([]);
  const [selectedItem, setSelectedItem] = useState<CommunicationRowViewModel | null>(null);
  const [detail, setDetail] = useState<NoticeboardItemDetail | null>(null);
  const [detailError, setDetailError] = useState<string | null>(null);
  const [isDetailLoading, setIsDetailLoading] = useState(false);
  const [isActionLoading, setIsActionLoading] = useState<"ack" | "join" | null>(null);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [filter, setFilter] = useState<ReadFilter>("all");
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
      setItems((current) =>
        current.map((entry) =>
          entry.id === item.id
            ? {
                ...entry,
                statusLabel: "Letta",
                tone: "neutral",
              }
            : entry,
        ),
      );
    } catch (loadError) {
      console.error("Communication detail failed", loadError);
      setDetailError(loadError instanceof Error ? loadError.message : "Non riesco a caricare il contenuto completo.");
    } finally {
      setIsDetailLoading(false);
    }
  }, []);

  const handleAction = useCallback(
    async (action: "ack" | "join") => {
      if (!selectedItem) {
        return;
      }

      setIsActionLoading(action);
      setDetailError(null);

      try {
        const nextDetail =
          action === "ack"
            ? await acknowledgeCommunicationView(selectedItem)
            : await joinCommunicationView(selectedItem);
        setDetail(nextDetail);
        await loadData();
      } catch (actionError) {
        console.error("Communication action failed", actionError);
        setDetailError(
          actionError instanceof Error ? actionError.message : "Non sono riuscito a completare l'azione richiesta.",
        );
      } finally {
        setIsActionLoading(null);
      }
    },
    [loadData, selectedItem],
  );

  const filteredItems = useMemo(() => {
    return items.filter((item) => {
      if (filter === "read" && item.statusLabel !== "Letta") {
        return false;
      }
      if (filter === "unread" && item.statusLabel === "Letta") {
        return false;
      }
      if (!deferredQuery.trim()) {
        return true;
      }

      const query = deferredQuery.toLowerCase();
      return (
        item.title.toLowerCase().includes(query) ||
        item.preview.toLowerCase().includes(query) ||
        item.metadataLabel.toLowerCase().includes(query)
      );
    });
  }, [items, filter, deferredQuery]);

  const unreadCount = useMemo(() => items.filter((item) => item.statusLabel !== "Letta").length, [items]);
  const actionableCount = useMemo(
    () => items.filter((item) => item.needsAck || item.needsJoin || item.needsReply).length,
    [items],
  );
  const withAttachmentsCount = useMemo(() => items.filter((item) => item.hasAttachments).length, [items]);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Sto preparando bacheca, dettaglio e azioni operative."
          title="Carico le comunicazioni"
        />
      </ScreenContainer>
    );
  }

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView
        contentContainerStyle={{ paddingBottom: 120 }}
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
        <View className="gap-6 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              eyebrow="Bacheca"
              subtitle="Apri il testo completo, conferma, aderisci e scarica gli allegati disponibili."
              title="Comunicazioni"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <RegisterListRow
                detail={error}
                meta="Sincronizzazione"
                title="Aggiornamento parziale"
                tone="warning"
              />
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <View className="flex-row flex-wrap gap-3">
              <MetricTile detail="Messaggi non ancora letti." label="Non lette" tone={unreadCount > 0 ? "warning" : "success"} value={String(unreadCount)} />
              <MetricTile detail="Comunicazioni con un'azione richiesta." label="Da confermare" tone={actionableCount > 0 ? "primary" : "neutral"} value={String(actionableCount)} />
              <MetricTile detail="Voci con allegati o file." label="Allegati" tone="primary" value={String(withAttachmentsCount)} />
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca titolo, mittente o contenuto"
              value={searchQuery}
            />
          </AnimatedListItem>

          <AnimatedListItem index={4}>
            <View className="gap-3">
              <SectionTitle eyebrow="Filtro" title="Stato di lettura" />
              <View className="flex-row gap-2">
                <M3Chip label="Tutte" onPress={() => setFilter("all")} selected={filter === "all"} />
                <M3Chip label="Non lette" onPress={() => setFilter("unread")} selected={filter === "unread"} />
                <M3Chip label="Lette" onPress={() => setFilter("read")} selected={filter === "read"} />
              </View>
            </View>
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Dettaglio" title="Comunicazione selezionata" detail="Il contenuto completo resta visibile qui, insieme alle azioni del portale." />
            {selectedItem ? (
              <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
                <View className="gap-2">
                  <View className="flex-row flex-wrap gap-2">
                    <StatusBadge label={selectedItem.statusLabel === "Letta" ? "Letta" : "Da leggere"} tone={selectedItem.statusLabel === "Letta" ? "neutral" : "primary"} />
                    <StatusBadge label={selectedItem.metadataLabel} tone="neutral" />
                    {selectedItem.hasAttachments ? <StatusBadge label="Con allegati" tone="primary" /> : null}
                  </View>
                  <Text className="text-lg font-medium" style={{ color: colors.foreground }}>
                    {selectedItem.title}
                  </Text>
                  <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    {selectedItem.sender} / {selectedItem.dateLabel}
                  </Text>
                </View>

                {isDetailLoading ? (
                  <View className="flex-row items-center gap-3">
                    <ActivityIndicator color={colors.primary} size="small" />
                    <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                      Sto recuperando il contenuto completo.
                    </Text>
                  </View>
                ) : null}

                {detailError ? (
                  <RegisterListRow detail={detailError} meta="Dettaglio" title="Operazione non completata" tone="warning" />
                ) : null}

                {detail ? (
                  <View className="gap-4">
                    <Text className="text-sm leading-7" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                      {detail.content}
                    </Text>

                    {detail.replyText ? (
                      <RegisterListRow
                        detail={detail.replyText}
                        meta="Risposta richiesta"
                        title="Indicazioni del portale"
                        tone="primary"
                      />
                    ) : null}

                    {detail.actions.some((action) => action.type === "ack" || action.type === "join") ? (
                      <View className="flex-row flex-wrap gap-3">
                        {detail.actions.some((action) => action.type === "ack") ? (
                          <ElegantButton disabled={isActionLoading !== null} onPress={() => void handleAction("ack")} variant="primary">
                            {isActionLoading === "ack" ? "Confermo..." : "Conferma"}
                          </ElegantButton>
                        ) : null}
                        {detail.actions.some((action) => action.type === "join") ? (
                          <ElegantButton disabled={isActionLoading !== null} onPress={() => void handleAction("join")} variant="secondary">
                            {isActionLoading === "join" ? "Aderisco..." : "Aderisci"}
                          </ElegantButton>
                        ) : null}
                      </View>
                    ) : null}

                    {detail.attachments.length > 0 ? (
                      <View className="gap-3">
                        <SectionTitle eyebrow="Allegati" title="File disponibili" />
                        <View className="gap-3">
                          {detail.attachments.map((attachment) => (
                            <Pressable
                              key={attachment.id}
                              disabled={!attachment.url}
                              onPress={() => {
                                if (attachment.url) {
                                  void openExternalContent(attachment.url);
                                }
                              }}
                            >
                              <RegisterListRow
                                detail={attachment.capabilityState.detail}
                                meta={attachment.mimeType || attachment.fileName}
                                title={attachment.title}
                                tone={attachment.url ? "primary" : "warning"}
                                trailing={
                                  <StatusBadge
                                    label={attachment.url ? "Apri / scarica" : "Link mancante"}
                                    tone={attachment.url ? "primary" : "warning"}
                                  />
                                }
                              />
                            </Pressable>
                          ))}
                        </View>
                      </View>
                    ) : null}
                  </View>
                ) : null}
              </ElegantCard>
            ) : (
              <EmptyState detail="Seleziona una comunicazione dalla lista per aprire il testo completo e le eventuali azioni." title="Nessuna comunicazione selezionata" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Elenco" title="Tutte le comunicazioni" detail="La lista mette in evidenza conferme, adesioni e allegati da aprire." />
            {filteredItems.length > 0 ? (
              <View className="gap-3">
                {filteredItems.map((item, index) => {
                  const isSelected = selectedItem?.id === item.id;
                  return (
                    <AnimatedListItem key={item.id} index={5 + index}>
                      <Pressable onPress={() => void handleSelect(item)}>
                        <RegisterListRow
                          detail={item.preview}
                          meta={`${item.dateLabel} / ${item.sender}`}
                          subtitle={item.metadataLabel}
                          title={item.title}
                          tone={isSelected ? "primary" : item.tone}
                          trailing={
                            <StatusBadge
                              label={item.statusLabel === "Letta" ? "Letta" : "Nuova"}
                              tone={item.statusLabel === "Letta" ? "neutral" : "primary"}
                            />
                          }
                        />
                      </Pressable>
                    </AnimatedListItem>
                  );
                })}
              </View>
            ) : (
              <EmptyState detail="Modifica filtri o ricerca per trovare altre comunicazioni." title="Nessuna comunicazione trovata" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
