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
import { generateMockCommunications } from "@/lib/mock-data";
import { useColors } from "@/hooks/use-colors";

interface Communication {
  id: string;
  title: string;
  content: string;
  sender: string;
  date: string;
  read: boolean;
  attachments?: string[];
}

export default function CommunicationsScreen() {
  const colors = useColors();
  const { isDemoMode } = useAuth();
  const [isLoading, setIsLoading] = useState(true);
  const [isRefreshing, setIsRefreshing] = useState(false);
  const [communications, setCommunications] = useState<Communication[]>([]);
  const [expandedId, setExpandedId] = useState<string | null>(null);

  const loadCommunications = async () => {
    try {
      if (isDemoMode) {
        setCommunications(generateMockCommunications());
      }
      // TODO: Implementare caricamento da API reale
    } finally {
      setIsLoading(false);
      setIsRefreshing(false);
    }
  };

  useEffect(() => {
    loadCommunications();
  }, [isDemoMode]);

  const handleRefresh = async () => {
    setIsRefreshing(true);
    await loadCommunications();
  };

  if (isLoading) {
    return (
      <ScreenContainer className="flex-1 items-center justify-center">
        <ActivityIndicator size="large" color={colors.primary} />
      </ScreenContainer>
    );
  }

  const unreadCount = communications.filter((c) => !c.read).length;

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
          <View className="gap-1">
            <Text className="text-3xl font-bold text-foreground">
              Comunicazioni
            </Text>
            <Text className="text-sm text-muted">
              {unreadCount > 0
                ? `${unreadCount} non lette`
                : "Tutte le comunicazioni lette"}
            </Text>
          </View>

          {/* Communications List */}
          {communications.length > 0 ? (
            <View className="gap-3">
              {communications.map((comm) => (
                <TouchableOpacity
                  key={comm.id}
                  onPress={() =>
                    setExpandedId(expandedId === comm.id ? null : comm.id)
                  }
                  className={`rounded-xl border p-4 gap-2 ${
                    comm.read
                      ? "bg-surface border-border"
                      : "bg-primary/5 border-primary/30"
                  }`}
                >
                  {/* Header */}
                  <View className="flex-row items-start justify-between gap-2">
                    <View className="flex-1 gap-1">
                      <View className="flex-row items-center gap-2">
                        <Text className="text-sm font-bold text-foreground flex-1">
                          {comm.title}
                        </Text>
                        {!comm.read && (
                          <View className="w-3 h-3 rounded-full bg-primary" />
                        )}
                      </View>
                      <Text className="text-xs text-muted">{comm.sender}</Text>
                    </View>
                  </View>

                  {/* Date */}
                  <Text className="text-xs text-muted">
                    {new Date(comm.date).toLocaleDateString("it-IT")}
                  </Text>

                  {/* Expanded Content */}
                  {expandedId === comm.id && (
                    <View className="mt-3 pt-3 border-t border-border gap-2">
                      <Text className="text-sm text-foreground leading-relaxed">
                        {comm.content}
                      </Text>
                      {comm.attachments && comm.attachments.length > 0 && (
                        <View className="gap-2 mt-2">
                          <Text className="text-xs font-semibold text-muted">
                            Allegati:
                          </Text>
                          {comm.attachments.map((att, idx) => (
                            <TouchableOpacity
                              key={idx}
                              className="flex-row items-center gap-2 p-2 rounded-lg bg-background"
                            >
                              <Text className="text-lg">📎</Text>
                              <Text className="text-xs text-primary font-semibold flex-1">
                                {att}
                              </Text>
                            </TouchableOpacity>
                          ))}
                        </View>
                      )}
                    </View>
                  )}
                </TouchableOpacity>
              ))}
            </View>
          ) : (
            <View className="rounded-xl bg-surface border border-border p-6 items-center">
              <Text className="text-sm text-muted">
                Nessuna comunicazione disponibile
              </Text>
            </View>
          )}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
