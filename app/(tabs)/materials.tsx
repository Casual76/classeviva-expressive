import { useRouter } from "expo-router";
import { useCallback, useDeferredValue, useEffect, useMemo, useState } from "react";
import { ActivityIndicator, Pressable, RefreshControl, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { ElegantButton } from "@/components/ui/elegant-button";
import { EmptyState } from "@/components/ui/empty-state";
import { ElegantCard } from "@/components/ui/elegant-card";
import { LoadingState } from "@/components/ui/loading-state";
import { MetricTile } from "@/components/ui/metric-tile";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SearchBar } from "@/components/ui/search-bar";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useReturnToMoreOnHardwareBack } from "@/hooks/use-return-to-more-on-hardware-back";
import { useColors } from "@/hooks/use-colors";
import type { DidacticAsset, DidacticContent, DidacticFolder } from "@/lib/classeviva-client";
import { openExternalContent } from "@/lib/open-content";
import { loadMaterialAssetView, loadMaterialsView } from "@/lib/student-data";

function getContentTypeLabel(content: DidacticContent): string {
  const value = content.objectType.toLowerCase();
  if (value.includes("link")) {
    return "Link";
  }
  if (value.includes("video")) {
    return "Video";
  }
  if (value.includes("pdf") || value.includes("file")) {
    return "File";
  }

  return content.objectType || "Contenuto";
}

function getOpenTarget(asset: DidacticAsset | null, content: DidacticContent | null): string | null {
  return asset?.dataUrl ?? asset?.sourceUrl ?? content?.sourceUrl ?? null;
}

function getOpenLabel(asset: DidacticAsset | null, content: DidacticContent | null) {
  const typeLabel = content ? getContentTypeLabel(content) : "contenuto";

  if (asset?.dataUrl) {
    return typeLabel === "Link" ? "Apri collegamento" : "Apri contenuto";
  }

  if (asset?.sourceUrl || content?.sourceUrl) {
    return typeLabel === "Link" ? "Apri esternamente" : "Apri nel browser";
  }

  return "Apri contenuto";
}

export default function MaterialsScreen() {
  const colors = useColors();
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
      setError(loadError instanceof Error ? loadError.message : "Non riesco a caricare il materiale didattico.");
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
      setAssetError(loadError instanceof Error ? loadError.message : "Non riesco ad aprire questo contenuto.");
    } finally {
      setIsAssetLoading(false);
    }
  }, []);

  const filteredFolders = useMemo(() => {
    return folders
      .map((folder) => {
        const query = deferredQuery.trim().toLowerCase();
        const matchesFolder = !query ? true : `${folder.title} ${folder.teacherName}`.toLowerCase().includes(query);
        const contents = folder.contents.filter((content) => {
          if (matchesFolder || !query) {
            return true;
          }

          return `${content.title} ${content.teacherName} ${content.folderName} ${content.objectType}`.toLowerCase().includes(query);
        });

        return { ...folder, contents: matchesFolder ? folder.contents : contents };
      })
      .filter((folder) => folder.contents.length > 0);
  }, [deferredQuery, folders]);

  const materialCount = useMemo(() => folders.reduce((sum, folder) => sum + folder.contents.length, 0), [folders]);
  const fileCount = useMemo(
    () => folders.reduce((sum, folder) => sum + folder.contents.filter((content) => getContentTypeLabel(content) === "File").length, 0),
    [folders],
  );
  const externalCount = useMemo(
    () =>
      folders.reduce(
        (sum, folder) =>
          sum + folder.contents.filter((content) => content.capabilityState.status === "external_only").length,
        0,
      ),
    [folders],
  );
  const openTarget = getOpenTarget(asset, selectedContent);

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 bg-background">
        <LoadingState
          detail="Sto preparando cartelle, contenuti condivisi e possibilita di apertura."
          title="Carico il materiale didattico"
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
              backLabel="Altro"
              eyebrow="Didattica"
              onBack={() => router.replace("/(tabs)/more")}
              subtitle="Cartelle del portale, file, PDF, link esterni e anteprime testuali quando disponibili."
              title="Materiale didattico"
            />
          </AnimatedListItem>

          {error ? (
            <AnimatedListItem index={1}>
              <RegisterListRow
                detail={error}
                meta="Se alcuni contenuti non arrivano, il resto del materiale resta consultabile."
                title="Aggiornamento parziale"
                tone="warning"
              />
            </AnimatedListItem>
          ) : null}

          <AnimatedListItem index={2}>
            <View className="flex-row flex-wrap gap-3">
              <MetricTile detail="Cartelle recuperate dal portale." label="Cartelle" value={String(folders.length)} />
              <MetricTile detail="Contenuti totali disponibili." label="Risorse" value={String(materialCount)} />
              <MetricTile detail="File allegati o documenti." label="File" tone="primary" value={String(fileCount)} />
              <MetricTile detail="Contenuti che richiedono apertura esterna." label="Link esterni" tone="warning" value={String(externalCount)} />
            </View>
          </AnimatedListItem>

          <AnimatedListItem index={3}>
            <SearchBar
              onChangeText={setSearchQuery}
              onClear={() => setSearchQuery("")}
              placeholder="Cerca titolo, cartella o docente"
              value={searchQuery}
            />
          </AnimatedListItem>

          <View className="gap-3">
            <SectionTitle eyebrow="Anteprima" title="Contenuto selezionato" detail="Quando il portale consente solo apertura esterna, qui trovi comunque il collegamento corretto." />
            {selectedContent ? (
              <ElegantCard className="gap-4 p-5" radius="lg" variant="elevated">
                <View className="gap-1.5">
                  <View className="flex-row items-center gap-2">
                    <StatusBadge label={getContentTypeLabel(selectedContent)} tone="primary" />
                    <StatusBadge label={selectedContent.teacherName || "Docente"} tone="neutral" />
                  </View>
                  <Text className="text-lg font-medium" style={{ color: colors.foreground }}>
                    {selectedContent.title}
                  </Text>
                  <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                    {selectedContent.folderName}
                  </Text>
                </View>

                {isAssetLoading ? (
                  <View className="flex-row items-center gap-3">
                    <ActivityIndicator color={colors.primary} size="small" />
                    <Text className="text-sm" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                      Sto preparando l'apertura del contenuto.
                    </Text>
                  </View>
                ) : null}

                {assetError ? (
                  <RegisterListRow detail={assetError} meta="Stato apertura" title="Impossibile aprire il contenuto" tone="warning" />
                ) : null}

                {asset ? (
                  <View className="gap-3">
                    <RegisterListRow
                      detail={asset.capabilityState.detail}
                      meta={asset.mimeType || "Mime type non disponibile"}
                      title={asset.capabilityState.label}
                      tone={asset.capabilityState.status === "external_only" ? "primary" : "neutral"}
                      trailing={
                        <StatusBadge
                          label={asset.capabilityState.status === "available" ? "Disponibile" : asset.capabilityState.status === "external_only" ? "Esterna" : "Non disponibile"}
                          tone={asset.capabilityState.status === "available" ? "success" : asset.capabilityState.status === "external_only" ? "primary" : "warning"}
                        />
                      }
                    />

                    {asset.textPreview ? (
                      <ElegantCard className="gap-2 p-4" radius="md" variant="filled">
                        <Text
                          className="text-[11px] font-medium uppercase tracking-[1.2px]"
                          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
                        >
                          Anteprima testo
                        </Text>
                        <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                          {asset.textPreview}
                        </Text>
                      </ElegantCard>
                    ) : null}

                    {openTarget ? (
                      <ElegantButton fullWidth onPress={() => void openExternalContent(openTarget)} variant="primary">
                        {getOpenLabel(asset, selectedContent)}
                      </ElegantButton>
                    ) : null}
                  </View>
                ) : null}
              </ElegantCard>
            ) : (
              <EmptyState detail="Seleziona un contenuto dalla lista per vedere stato, anteprima o collegamento di apertura." title="Nessun contenuto selezionato" />
            )}
          </View>

          <View className="gap-3">
            <SectionTitle eyebrow="Cartelle" title="Tutto il materiale disponibile" detail="Le cartelle restano leggibili anche quando i file richiedono browser o download esterno." />
            {filteredFolders.length > 0 ? (
              <View className="gap-5">
                {filteredFolders.map((folder, folderIndex) => (
                  <AnimatedListItem key={folder.id} index={4 + folderIndex}>
                    <View className="gap-3">
                      <ElegantCard className="gap-1.5 p-4" radius="md" variant="elevated">
                        <Text className="text-base font-medium" style={{ color: colors.foreground }}>
                          {folder.title}
                        </Text>
                        <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                          {folder.teacherName} / {folder.contents.length} contenuti
                        </Text>
                      </ElegantCard>

                      <View className="gap-3">
                        {folder.contents.map((content) => {
                          const isSelected = selectedContent?.id === content.id;
                          const typeLabel = getContentTypeLabel(content);
                          const badgeTone = content.capabilityState.status === "external_only" ? "primary" : "neutral";

                          return (
                            <Pressable key={content.id} onPress={() => void handleSelect(content)}>
                              <RegisterListRow
                                detail={content.capabilityState.detail || "Contenuto disponibile dal portale."}
                                meta={content.folderName}
                                subtitle={content.teacherName}
                                title={content.title}
                                tone={isSelected ? "primary" : badgeTone}
                                trailing={<StatusBadge label={typeLabel} tone={isSelected ? "primary" : badgeTone} />}
                              />
                            </Pressable>
                          );
                        })}
                      </View>
                    </View>
                  </AnimatedListItem>
                ))}
              </View>
            ) : (
              <EmptyState detail="Nessun contenuto corrisponde ai filtri attuali o il portale non ha restituito materiali." title="Materiale non disponibile" />
            )}
          </View>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
