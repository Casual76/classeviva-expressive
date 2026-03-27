import { Text, View } from "react-native";

export function SectionTitle({
  eyebrow,
  title,
  detail,
}: {
  eyebrow?: string;
  title: string;
  detail?: string;
}) {
  return (
    <View className="gap-1.5">
      {eyebrow ? (
        <Text className="text-xs font-semibold uppercase tracking-[2px] text-muted">{eyebrow}</Text>
      ) : null}
      <View className="gap-1">
        <Text className="text-lg font-semibold text-foreground">{title}</Text>
        {detail ? <Text className="text-sm leading-6 text-muted">{detail}</Text> : null}
      </View>
    </View>
  );
}
