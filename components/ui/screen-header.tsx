import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Pressable, Text, View } from "react-native";

import { Fonts } from "@/constants/theme";
import { useColors } from "@/hooks/use-colors";
import { cn } from "@/lib/utils";

interface ScreenHeaderProps {
  title: string;
  subtitle?: string;
  eyebrow?: string;
  backLabel?: string;
  onBack?: () => void;
  action?: React.ReactNode;
  className?: string;
}

export function ScreenHeader({
  title,
  subtitle,
  eyebrow,
  backLabel = "Indietro",
  onBack,
  action,
  className,
}: ScreenHeaderProps) {
  const colors = useColors();

  return (
    <View className={cn("gap-4", className)}>
      <View className="flex-row items-start justify-between gap-4">
        <View className="flex-1 gap-2">
          {onBack ? (
            <Pressable
              accessibilityLabel={backLabel}
              className="flex-row items-center gap-2 self-start rounded-full border border-border px-3 py-2"
              onPress={onBack}
            >
              <MaterialIcons color={colors.foreground} name="west" size={16} />
              <Text className="text-xs font-semibold text-foreground">{backLabel}</Text>
            </Pressable>
          ) : null}

          {eyebrow ? (
            <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">
              {eyebrow}
            </Text>
          ) : null}

          <Text
            className="text-4xl leading-[44px] text-foreground"
            style={{ fontFamily: Fonts.serif, fontWeight: "700" }}
          >
            {title}
          </Text>

          {subtitle ? (
            <Text className="max-w-[320px] text-sm leading-6 text-muted">{subtitle}</Text>
          ) : null}
        </View>

        {action}
      </View>
    </View>
  );
}
