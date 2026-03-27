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
        {[120, 80, 200].map((height, index) => (
          <View
            key={index}
            style={{
              height,
              borderRadius: 16,
              backgroundColor: colors.surfaceContainerHigh ?? colors.surface,
              opacity: 0.6,
            }}
          />
        ))}
      </View>
    );
  }

  return (
    <View className="flex-1 items-center justify-center gap-5 px-8">
      <AnimatedLoader />
      <View className="items-center gap-2">
        <Text className="text-lg font-medium" style={{ color: colors.foreground }}>{title}</Text>
        <Text className="text-center text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
          {detail}
        </Text>
      </View>
    </View>
  );
}
