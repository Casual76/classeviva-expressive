import { Text, View } from "react-native";

import { AnimatedLoader } from "@/components/ui/animated-loader";

export function LoadingState({
  title = "Sto caricando i dati",
  detail = "Sincronizzo il registro in tempo reale.",
}: {
  title?: string;
  detail?: string;
}) {
  return (
    <View className="flex-1 items-center justify-center gap-5 px-8">
      <AnimatedLoader />
      <View className="items-center gap-2">
        <Text className="text-lg font-semibold text-foreground">{title}</Text>
        <Text className="text-center text-sm leading-6 text-muted">{detail}</Text>
      </View>
    </View>
  );
}
