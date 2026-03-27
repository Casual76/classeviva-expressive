import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { useRouter } from "expo-router";
import { Pressable, ScrollView, Text, View } from "react-native";

import { ScreenContainer } from "@/components/screen-container";
import { AnimatedListItem } from "@/components/ui/animated-list-item";
import { ElegantCard } from "@/components/ui/elegant-card";
import { ScreenHeader } from "@/components/ui/screen-header";
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
      <ElegantCard className="gap-4 p-5" variant="elevated" radius="md">
        <View className="flex-row items-start justify-between gap-4">
          <View className="flex-1 gap-1.5">
            <Text className="text-base font-medium" style={{ color: colors.foreground }}>{title}</Text>
            <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>{detail}</Text>
          </View>
          <View
            className="h-11 w-11 items-center justify-center rounded-full"
            style={{ backgroundColor: colors.primaryContainer ?? colors.surface }}
          >
            <MaterialIcons color={colors.onPrimaryContainer ?? colors.primary} name={icon} size={20} />
          </View>
        </View>
      </ElegantCard>
    </Pressable>
  );
}

export default function MoreScreen() {
  const colors = useColors();
  const router = useRouter();
  const { user } = useAuth();

  const modules: {
    title: string;
    detail: string;
    icon: keyof typeof MaterialIcons.glyphMap;
    href:
      | "/(tabs)/absences"
      | "/(tabs)/communications"
      | "/(tabs)/notes"
      | "/(tabs)/materials"
      | "/(tabs)/schoolbooks"
      | "/(tabs)/report-cards"
      | "/(tabs)/profile";
  }[] = [
    { title: "Assenze", detail: "Storico assenze, ritardi e stato delle giustificazioni.", icon: "fact-check", href: "/(tabs)/absences" },
    { title: "Comunicazioni", detail: "Circolari, bacheca, allegati e contenuti completi in lettura.", icon: "campaign", href: "/(tabs)/communications" },
    { title: "Note", detail: "Richiami, note docente e dettaglio completo per ogni evento.", icon: "edit-note", href: "/(tabs)/notes" },
    { title: "Materiale didattico", detail: "Cartelle, contenuti condivisi e apertura controllata di file e PDF.", icon: "folder-copy", href: "/(tabs)/materials" },
    { title: "Libri", detail: "Elenco testi per corso, stato di acquisto e riepilogo materia.", icon: "menu-book", href: "/(tabs)/schoolbooks" },
    { title: "Pagelle", detail: "Documenti pubblicati dal portale con apertura esterna quando disponibile.", icon: "description", href: "/(tabs)/report-cards" },
    { title: "Profilo", detail: "Anagrafica studente, materie, periodi e gestione della sessione.", icon: "person-outline", href: "/(tabs)/profile" },
  ];

  return (
    <ScreenContainer className="flex-1 bg-background">
      <ScrollView contentContainerStyle={{ paddingBottom: 100 }} showsVerticalScrollIndicator={false}>
        <View className="gap-5 px-5 py-6">
          <AnimatedListItem index={0}>
            <ScreenHeader
              eyebrow="Hub secondario"
              subtitle="Tutte le sezioni meno frequenti restano vicine, ma senza appesantire la navigazione primaria."
              title="Altro"
            />
          </AnimatedListItem>

          <AnimatedListItem index={1}>
            <ElegantCard className="gap-4 p-6" variant="gradient" radius="lg">
              <Text
                className="text-[11px] font-medium uppercase tracking-[1.5px]"
                style={{ color: colors.onPrimaryContainer ? `${colors.onPrimaryContainer}BB` : colors.muted }}
              >
                Sessione attiva
              </Text>
              <Text
                className="text-[28px] leading-[34px]"
                style={{ color: colors.onPrimaryContainer ?? colors.foreground }}
              >
                {[user?.name, user?.surname].filter(Boolean).join(" ") || "Studente"}
              </Text>
              <Text
                className="text-sm leading-5"
                style={{ color: colors.onPrimaryContainer ? `${colors.onPrimaryContainer}CC` : colors.muted }}
              >
                {[user?.school, [user?.class, user?.section].filter(Boolean).join(" "), user?.schoolYear]
                  .filter(Boolean)
                  .join(" — ") || "Profilo Classeviva sincronizzato"}
              </Text>
            </ElegantCard>
          </AnimatedListItem>

          <View className="gap-3">
            {modules.map((module, i) => (
              <AnimatedListItem key={module.title} index={2 + i}>
                <ModuleCard
                  detail={module.detail}
                  icon={module.icon}
                  onPress={() => router.push(module.href)}
                  title={module.title}
                />
              </AnimatedListItem>
            ))}
          </View>

          <AnimatedListItem index={9}>
            <ElegantCard className="gap-2 p-5" variant="filled" radius="md">
              <Text className="text-sm font-medium" style={{ color: colors.foreground }}>Architettura più pulita</Text>
              <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
                Home, Voti, Agenda e Orario restano sempre in tab. Il resto vive qui con schermate dedicate complete.
              </Text>
            </ElegantCard>
          </AnimatedListItem>
        </View>
      </ScrollView>
    </ScreenContainer>
  );
}
