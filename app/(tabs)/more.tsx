import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { Pressable, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { ElegantCard } from "@/components/ui/elegant-card";
import { RegisterListRow } from "@/components/ui/register-list-row";
import { ScreenHeader } from "@/components/ui/screen-header";
import { SectionTitle } from "@/components/ui/section-title";
import { StatusBadge } from "@/components/ui/status-badge";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

type ModuleHref =
  | "/(tabs)/absences"
  | "/(tabs)/notes"
  | "/(tabs)/materials"
  | "/(tabs)/schoolbooks"
  | "/(tabs)/report-cards"
  | "/(tabs)/profile"
  | "/(tabs)/schedule";

type ModuleGroup = {
  title: string;
  detail: string;
  items: {
    title: string;
    detail: string;
    badge: string;
    tone?: "neutral" | "primary" | "success" | "warning" | "error";
    icon: keyof typeof MaterialIcons.glyphMap;
    href: ModuleHref;
  }[];
};

export default function MoreScreen() {
  const colors = useColors();
  const router = useRouter();
  const { user } = useAuth();

  const groups: ModuleGroup[] = [
    {
      title: "In evidenza",
      detail: "Le sezioni secondarie che devono restare piu vicine alla navigazione principale.",
      items: [
        { title: "Note", detail: "Annotazioni, richiami e note disciplinari con filtri chiari.", badge: "Prioritario", tone: "warning", icon: "edit-note", href: "/(tabs)/notes" },
        { title: "Orario", detail: "Lezioni del giorno e settimana con fallback sulle ore.", badge: "Lezioni", icon: "view-week", href: "/(tabs)/schedule" },
        { title: "Profilo e impostazioni", detail: "Anagrafica, sessione, notifiche e test canali.", badge: "Profilo", icon: "person-outline", href: "/(tabs)/profile" },
      ],
    },
    {
      title: "Didattica",
      detail: "Materiali e strumenti utili fuori dal flusso rapido quotidiano.",
      items: [
        { title: "Materiale didattico", detail: "Cartelle, file, PDF, link e contenuti condivisi.", badge: "Materiali", icon: "folder-copy", href: "/(tabs)/materials" },
        { title: "Libri", detail: "Elenco testi per corso e stato di acquisto.", badge: "Libri", icon: "menu-book", href: "/(tabs)/schoolbooks" },
      ],
    },
    {
      title: "Registro",
      detail: "Storico presenze e documenti pubblicati dal portale.",
      items: [
        { title: "Assenze", detail: "Assenze, ritardi e uscite sempre separati nei conteggi.", badge: "Presenze", icon: "fact-check", href: "/(tabs)/absences" },
        { title: "Pagelle e documenti", detail: "Documenti pubblicati, con apertura esterna se richiesta.", badge: "Documenti", icon: "description", href: "/(tabs)/report-cards" },
      ],
    },
  ];

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={{ paddingBottom: 120 }} showsVerticalScrollIndicator={false}>
        <View className="gap-6 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              eyebrow="Hub secondario"
              subtitle="Le sezioni fuori dalla barra principale restano ordinate per compito, con Note e Orario piu in vista."
              title="Altro"
            />
          </AnimatedListItem>

          <AnimatedListItem index={1}>
            <ElegantCard className="gap-3 p-5" radius="lg" variant="gradient">
              <StatusBadge icon="school" label="Sessione attiva" tone="primary" />
              <View className="gap-1.5">
                <Text className="text-[24px] leading-[30px] font-medium" style={{ color: colors.foreground }}>
                  {[user?.name, user?.surname].filter(Boolean).join(" ") || "Studente"}
                </Text>
                <Text className="text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                  {[user?.school, [user?.class, user?.section].filter(Boolean).join(" "), user?.schoolYear]
                    .filter(Boolean)
                    .join(" / ") || "Profilo sincronizzato dal portale"}
                </Text>
              </View>
            </ElegantCard>
          </AnimatedListItem>

          {groups.map((group, groupIndex) => (
            <View key={group.title} className="gap-3">
              <SectionTitle eyebrow={group.title} title={group.title} detail={group.detail} />
              <View className="gap-3">
                {group.items.map((item, itemIndex) => (
                  <AnimatedListItem key={item.title} index={2 + groupIndex * 4 + itemIndex}>
                    <Pressable onPress={() => router.push(item.href)}>
                      <RegisterListRow
                        detail={item.detail}
                        leading={
                          <View
                            className="h-10 w-10 items-center justify-center rounded-full"
                            style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surface }}
                          >
                            <MaterialIcons color={colors.primary} name={item.icon} size={20} />
                          </View>
                        }
                        title={item.title}
                        tone={item.tone ?? "neutral"}
                        trailing={<StatusBadge label={item.badge} tone={item.tone ?? "neutral"} />}
                      />
                    </Pressable>
                  </AnimatedListItem>
                ))}
              </View>
            </View>
          ))}
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
