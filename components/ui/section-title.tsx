import { Text, View } from "react-native";
import { useColors } from "@/hooks/use-colors";

export function SectionTitle({
  eyebrow,
  title,
  detail,
}: {
  eyebrow?: string;
  title: string;
  detail?: string;
}) {
  const colors = useColors();

  return (
    <View className="gap-1">
      {eyebrow ? (
        <Text
          className="text-[11px] font-medium uppercase tracking-[1.5px]"
          style={{ color: colors.onSurfaceVariant ?? colors.muted }}
        >
          {eyebrow}
        </Text>
      ) : null}
      <View className="gap-0.5">
        <Text className="text-base font-medium" style={{ color: colors.foreground }}>{title}</Text>
        {detail ? (
          <Text className="text-sm leading-5" style={{ color: colors.onSurfaceVariant ?? colors.muted }}>
            {detail}
          </Text>
        ) : null}
      </View>
    </View>
  );
}
