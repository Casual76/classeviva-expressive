import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Text, View } from "react-native";

import { ElegantCard } from "@/components/ui/elegant-card";
import { useColors } from "@/hooks/use-colors";

export function EmptyState({
  icon = "inbox",
  title,
  detail,
}: {
  icon?: keyof typeof MaterialIcons.glyphMap;
  title: string;
  detail: string;
}) {
  const colors = useColors();

  return (
    <ElegantCard className="items-center gap-4 p-6" variant="filled">
      <View
        className="h-14 w-14 items-center justify-center rounded-full"
        style={{ backgroundColor: colors.surface }}
      >
        <MaterialIcons color={colors.primary} name={icon} size={24} />
      </View>
      <View className="items-center gap-2">
        <Text className="text-base font-semibold text-foreground">{title}</Text>
        <Text className="text-center text-sm leading-6 text-muted">{detail}</Text>
      </View>
    </ElegantCard>
  );
}
