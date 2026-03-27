/**
 * M3 Filter Chip — Material 3 Expressive
 * Tonal surface when selected, outlined when unselected
 */

import React from "react";
import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Pressable, Text } from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from "react-native-reanimated";

import { useColors } from "@/hooks/use-colors";

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

interface M3ChipProps {
  label: string;
  selected?: boolean;
  onPress?: () => void;
  icon?: keyof typeof MaterialIcons.glyphMap;
  size?: "sm" | "md" | "lg";
}

export function M3Chip({
  label,
  selected = false,
  onPress,
  icon,
  size = "md",
}: M3ChipProps) {
  const colors = useColors();
  const scale = useSharedValue(1);
  const paddingStyle =
    size === "lg"
      ? { paddingHorizontal: 20, paddingVertical: 10 }
      : size === "sm"
        ? { paddingHorizontal: 10, paddingVertical: 4 }
        : { paddingHorizontal: 16, paddingVertical: 8 };

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  return (
    <AnimatedPressable
      style={[
        animatedStyle,
        {
          backgroundColor: selected
            ? (colors.secondaryContainer ?? colors.primary)
            : "transparent",
          borderWidth: selected ? 0 : 1,
          borderColor: colors.outline ?? colors.border,
        },
        paddingStyle,
      ]}
      className="flex-row items-center gap-1.5 rounded-lg"
      onPress={onPress}
      onPressIn={() => {
        scale.value = withSpring(0.95, { damping: 15, stiffness: 400 });
      }}
      onPressOut={() => {
        scale.value = withSpring(1, { damping: 15, stiffness: 400 });
      }}
    >
      {selected && (
        <MaterialIcons
          name="check"
          size={16}
          color={colors.onSecondaryContainer ?? colors.foreground}
        />
      )}
      {!selected && icon && (
        <MaterialIcons
          name={icon}
          size={16}
          color={colors.onSurfaceVariant ?? colors.muted}
        />
      )}
      <Text
        className="text-sm font-medium"
        style={{
          color: selected
            ? (colors.onSecondaryContainer ?? colors.foreground)
            : (colors.onSurfaceVariant ?? colors.foreground),
        }}
      >
        {label}
      </Text>
    </AnimatedPressable>
  );
}
