import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { Pressable, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ScreenHeader } from "@/components/ui/screen-header";
import { Fonts } from "@/constants/theme";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

function ModuleCard({
  title,
  detail,
  icon,
  onPress,
}: {
  title: string;
  detail: string;
  icon: keyof typeof MaterialIcons.glyphMap;
  onPress: () => void;
}) {
  const colors = useColors();

  return (
    <Pressable onPress={onPress}>
      <ElegantCard className="gap-4 p-5" variant="elevated">
        <View className="flex-row items-start justify-between gap-4">
          <View className="flex-1 gap-2">
            <Text className="text-base font-semibold text-foreground">{title}</Text>
            <Text className="text-sm leading-6 text-muted">{detail}</Text>
          </View>
          <View
            className="h-11 w-11 items-center justify-center rounded-full"
            style={{ backgroundColor: colors.surfaceAlt ?? colors.surface }}
          >
            <MaterialIcons color={colors.primary} name={icon} size={20} />
          </View>
        </View>
      </ElegantCard>
    </Pressable>
  );
}

export default function MoreScreen() {
  const router = useRouter();
  const { user } = useAuth();

  const modules: {
    title: string;
    detail: string;
    icon: keyof typeof MaterialIcons.glyphMap;
    href:
      | "/(tabs)/communications"
      | "/(tabs)/notes"
      | "/(tabs)/materials"
      | "/(tabs)/schoolbooks"
      | "/(tabs)/report-cards"
      | "/(tabs)/schedule"
      | "/(tabs)/profile";
  }[] = [
    {
      title: "Comunicazioni",
      detail: "Circolari, bacheca, allegati e contenuti completi in lettura.",
      icon: "campaign",
      href: "/(tabs)/communications" as const,
    },
    {
      title: "Note",
      detail: "Richiami, note docente e dettaglio completo per ogni evento.",
      icon: "edit-note",
      href: "/(tabs)/notes" as const,
    },
    {
      title: "Materiale didattico",
      detail: "Cartelle, contenuti condivisi e apertura controllata di file e PDF.",
      icon: "folder-copy",
      href: "/(tabs)/materials" as const,
    },
    {
      title: "Libri",
      detail: "Elenco testi per corso, stato di acquisto e riepilogo materia.",
      icon: "menu-book",
      href: "/(tabs)/schoolbooks" as const,
    },
    {
      title: "Pagelle",
      detail: "Documenti pubblicati dal portale con apertura esterna quando disponibile.",
      icon: "description",
      href: "/(tabs)/report-cards" as const,
    },
    {
      title: "Orario",
      detail: "Vista settimanale delle lezioni con navigazione rapida tra le settimane.",
      icon: "view-week",
      href: "/(tabs)/schedule" as const,
    },
    {
      title: "Profilo",
      detail: "Anagrafica studente, materie, periodi e gestione della sessione.",
      icon: "person-outline",
      href: "/(tabs)/profile" as const,
    },
  ];

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={{ paddingBottom: 112 }} showsVerticalScrollIndicator={false}>
        <View className="gap-6 px-6 py-6">
          <ScreenHeader
            eyebrow="Hub secondario"
            subtitle="Tutte le sezioni meno frequenti restano vicine, ma senza appesantire la navigazione primaria."
            title="Altro"
          />

          <ElegantCard className="gap-4 p-6" gradient="primary" variant="gradient">
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-background/75">
              Sessione attiva
            </Text>
            <Text
              className="text-3xl leading-[34px] text-background"
              style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
            >
              {[user?.name, user?.surname].filter(Boolean).join(" ") || "Studente"}
            </Text>
            <Text className="text-sm leading-6 text-background/80">
              {[user?.school, [user?.class, user?.section].filter(Boolean).join(" "), user?.schoolYear]
                .filter(Boolean)
                .join(" - ") || "Profilo Classeviva sincronizzato"}
            </Text>
          </ElegantCard>

          <View className="gap-3">
            {modules.map((module) => (
              <ModuleCard
                key={module.title}
                detail={module.detail}
                icon={module.icon}
                onPress={() => router.push(module.href)}
                title={module.title}
              />
            ))}
          </View>

          <ElegantCard className="gap-3 p-5" variant="filled">
            <Text className="text-sm font-semibold text-foreground">Architettura piu pulita</Text>
            <Text className="text-sm leading-6 text-muted">
              Home, Voti, Agenda e Assenze restano sempre in tab. Il resto vive qui con schermate dedicate complete.
            </Text>
          </ElegantCard>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
