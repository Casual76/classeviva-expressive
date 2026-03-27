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
import { ElegantButton } from "@/components/ui/elegant-button";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import type {
  DidacticAsset,
  DidacticContent,
  DidacticFolder,
} from "@/lib/classeviva-client";
import { openExternalContent } from "@/lib/open-content";
import {
  loadMaterialAssetView,
  loadMaterialsView,
} from "@/lib/student-data";

export default function MaterialsScreen() {
  const router = useRouter();
  useReturnToMoreOnHardwareBack();
  const [folders, setFolders] = useState<DidacticFolder[]>([]);
  const [selectedContent, setSelectedContent] = useState<DidacticContent | null>(null);
  const [asset, setAsset] = useState<DidacticAsset | null>(null);
  const [assetError, setAssetError] = useState<string | null>(null);
  const [isAssetLoading, setIsAssetLoading] = useState(false);
  const [searchQuery, setSearchQuery] = useState("");
  const deferredQuery = useDeferredValue(searchQuery);
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const loadData = useCallback(async () => {
    try {
      setError(null);
      const data = await loadMaterialsView();
      setFolders(data);
    } catch (loadError) {
      console.error("Materials load failed", loadError);
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare il materiale.");
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  }, []);

  useEffect(() => {
    void loadData();
  }, [loadData]);

  const handleSelect = useCallback(async (content: DidacticContent) => {
    setSelectedContent(content);
    setAsset(null);
    setAssetError(null);
    setIsAssetLoading(true);

    try {
      const nextAsset = await loadMaterialAssetView(content);
      setAsset(nextAsset);
    } catch (loadError) {
      console.error("Material asset failed", loadError);
      setAssetError(loadError instanceof Error ? loadError.message : "Non riesco ad aprire il materiale.");
    } finally {
      setIsAssetLoading(false);
    }
  }, []);

  const filteredFolders = useMemo(() => {
    return folders
      .map((folder) => {
        const query = deferredQuery.trim().toLowerCase();
        const matchesFolder = !query
          ? true
          : `${folder.title} ${folder.teacherName}`.toLowerCase().includes(query);

        const contents = folder.contents.filter((content) => {
          if (matchesFolder || !query) {
            return true;
          }

          const haystack = `${content.title} ${content.teacherName} ${content.folderName} ${content.objectType}`;
          return haystack.toLowerCase().includes(query);
        });

        return {
          ...folder,
          contents: matchesFolder ? folder.contents : contents,
        };
      })
      .filter((folder) => folder.contents.length > 0);
  }, [deferredQuery, folders]);

  const materialCount = useMemo(
    () => folders.reduce((sum, folder) => sum + folder.contents.length, 0),
    [folders],
  );
  const fileCount = useMemo(
    () =>
      folders.reduce(
        (sum, folder) => sum + folder.contents.filter((content) => content.objectType === "file").length,
        0,
      ),
    [folders],
  );

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          title="Sto caricando i materiali"
          detail="Recupero cartelle, contenuti condivisi e disponibilita dei documenti."
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
            eyebrow="Didattica"
            onBack={() => router.replace("/(tabs)/more")}
            subtitle="Cartelle reali dal portale, contenuti condivisi e apertura controllata dei documenti."
            title="Materiali"
          />

          {error ? (
            <ElegantCard className="gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-sm font-semibold text-foreground">Aggiornamento parziale</Text>
              <Text className="text-sm leading-6 text-muted">{error}</Text>
            </ElegantCard>
          ) : null}

          <View className="flex-row gap-3">
            <ElegantCard className="flex-1 gap-2 p-4" tone="primary" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Cartelle</Text>
              <Text className="text-3xl font-semibold text-foreground">{folders.length}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">Risorse</Text>
              <Text className="text-3xl font-semibold text-foreground">{materialCount}</Text>
            </ElegantCard>
            <ElegantCard className="flex-1 gap-2 p-4" tone="warning" variant="filled">
              <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">File</Text>
              <Text className="text-3xl font-semibold text-foreground">{fileCount}</Text>
            </ElegantCard>
          </View>

          <SearchBar
            onChangeText={setSearchQuery}
            onClear={() => setSearchQuery("")}
            placeholder="Cerca titolo, cartella o docente"
            value={searchQuery}
          />

          <View className="gap-3">
            <SectionTitle eyebrow="Anteprima" title="Contenuto selezionato" />
            {selectedContent ? (
              <ElegantCard className="gap-4 p-5" tone="primary" variant="filled">
                <View className="gap-1">
                  <Text className="text-base font-semibold text-foreground">{selectedContent.title}</Text>
                  <Text className="text-sm text-muted">
                    {selectedContent.folderName} - {selectedContent.teacherName}
                  </Text>
                </View>

                {isAssetLoading ? (
                  <View className="flex-row items-center gap-3">
                    <ActivityIndicator size="small" />
                    <Text className="text-sm text-muted">Sto preparando il contenuto da aprire.</Text>
                  </View>
                ) : null}

                {assetError ? <Text className="text-sm leading-6 text-muted">{assetError}</Text> : null}

                {asset ? (
                  <View className="gap-4">
                    <ElegantCard className="gap-2 p-4" variant="outlined">
                      <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                        Stato
                      </Text>
                      <Text className="text-sm font-semibold text-foreground">
                        {asset.capabilityState.label}
                      </Text>
                      {asset.capabilityState.detail ? (
                        <Text className="text-sm leading-6 text-muted">{asset.capabilityState.detail}</Text>
                      ) : null}
                    </ElegantCard>

                    {asset.textPreview ? (
                      <ElegantCard className="gap-2 p-4" variant="outlined">
                        <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                          Anteprima testo
                        </Text>
                        <Text className="text-sm leading-6 text-muted">{asset.textPreview}</Text>
                      </ElegantCard>
                    ) : null}

                    {asset.dataUrl ? (
                      <ElegantButton
                        fullWidth
                        onPress={() => void openExternalContent(asset.dataUrl ?? "")}
                        variant="primary"
                      >
                        Apri documento
                      </ElegantButton>
                    ) : null}

                    <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                      {asset.mimeType ?? "Mime type non disponibile"}
                    </Text>
                  </View>
                ) : null}
              </ElegantCard>
            ) : (
              <EmptyState
                detail="Seleziona un materiale dall'elenco per preparare l'apertura o la preview."
                title="Nessun contenuto selezionato"
              />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Cartelle" title="Tutto il materiale didattico" />
            {filteredFolders.length > 0 ? (
              <View className="gap-4">
                {filteredFolders.map((folder) => (
                  <View key={folder.id} className="gap-3">
                    <ElegantCard className="gap-2 p-4" variant="outlined">
                      <Text className="text-sm font-semibold text-foreground">{folder.title}</Text>
                      <Text className="text-sm text-muted">{folder.teacherName}</Text>
                    </ElegantCard>

                    <View className="gap-3">
                      {folder.contents.map((content) => {
                        const isSelected = selectedContent?.id === content.id;

                        return (
                          <Pressable key={content.id} onPress={() => void handleSelect(content)}>
                            <ElegantCard
                              className="gap-3 p-4"
                              tone={isSelected ? "primary" : "neutral"}
                              variant={isSelected ? "elevated" : "filled"}
                            >
                              <View className="flex-row items-start justify-between gap-3">
                                <View className="flex-1 gap-1">
                                  <Text className="text-sm font-semibold text-foreground">
                                    {content.title}
                                  </Text>
                                  <Text className="text-xs text-muted">{content.teacherName}</Text>
                                </View>
                                <Text className="text-xs font-semibold uppercase tracking-[1.5px] text-muted">
                                  {content.objectType}
                                </Text>
                              </View>
                              <Text className="text-sm leading-6 text-muted">
                                {content.capabilityState.detail ?? "Contenuto disponibile"}
                              </Text>
                            </ElegantCard>
                          </Pressable>
                        );
                      })}
                    </View>
                  </View>
                ))}
              </View>
            ) : (
              <EmptyState
                detail="Non ci sono materiali che corrispondono ai filtri attuali."
                title="Nessun materiale trovato"
              />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
