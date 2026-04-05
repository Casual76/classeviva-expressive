import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Pressable, Text, View } from "react-native";

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
    <View className={cn("gap-3", className)}>
      <View className="flex-row items-start justify-between gap-4">
        <View className="flex-1 gap-2">
          {onBack ? (
            <Pressable
              accessibilityLabel={backLabel}
              className="flex-row items-center gap-2 self-start rounded-full px-3 py-2"
              style={{
                backgroundColor: colors.surface ?? colors.background,
                borderWidth: 1,
                borderColor: colors.outlineVariant ?? colors.border,
              }}
              onPress={onBack}
            >
              <MaterialIcons color={colors.onSurfaceVariant ?? colors.foreground} name="arrow-back" size={18} />
              <Text
                className="text-sm font-medium"
                style={{ color: colors.onSurfaceVariant ?? colors.foreground }}
              >
                {backLabel}
              </Text>
            </Pressable>
          ) : null}

          {eyebrow ? (
            <Text
              className="text-xs font-medium uppercase tracking-[1.5px]"
              style={{ color: colors.onSurfaceVariant ?? colors.muted }}
            >
              {eyebrow}
            </Text>
          ) : null}

          <Text
            className="text-[30px] leading-[36px] font-medium tracking-[-0.2px]"
            style={{ color: colors.foreground }}
          >
            {title}
          </Text>

          {subtitle ? (
            <Text
              className="max-w-[360px] text-sm leading-6 font-normal"
              style={{ color: colors.onSurfaceVariant ?? colors.muted }}
            >
              {subtitle}
            </Text>
          ) : null}
        </View>

        {action}
      </View>
    </View>
  );
}
