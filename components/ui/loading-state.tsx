import { Text, View } from "react-native";

import { AnimatedLoader } from "@/components/ui/animated-loader";
import { useColors } from "@/hooks/use-colors";

interface LoadingStateProps {
  title?: string;
  detail?: string;
  variant?: "spinner" | "skeleton";
}

export function LoadingState({
  title = "Sto caricando i dati",
  detail = "Sincronizzo il registro in tempo reale.",
  variant = "spinner",
}: LoadingStateProps) {
  const colors = useColors();

  if (variant === "skeleton") {
    return (
      <View className="gap-5 px-5 py-6">
        <View
          style={{
            height: 88,
            borderRadius: 28,
            backgroundColor: colors.surfaceContainer ?? colors.surface,
            opacity: 0.8,
          }}
        />
        <View className="flex-row gap-3">
          {[0, 1].map((index) => (
            <View
              key={index}
              style={{
                flex: 1,
                height: 92,
                borderRadius: 22,
                backgroundColor: colors.surfaceContainerHigh ?? colors.surface,
                opacity: 0.7,
              }}
            />
          ))}
        </View>
        <View
          style={{
            height: 220,
            borderRadius: 24,
            backgroundColor: colors.surfaceContainer ?? colors.surface,
            opacity: 0.75,
          }}
        />
      </View>
    );
  }

  return (
    <View className="flex-1 items-center justify-center gap-5 px-8">
      <AnimatedLoader />
      <View className="items-center gap-2">
        <Text className="text-lg font-medium" style={{ color: colors.foreground }}>
          {title}
        </Text>
        <Text className="text-center text-sm leading-6" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
          {detail}
        </Text>
      </View>
    </View>
  );
}
