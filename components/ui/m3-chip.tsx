/**
 * M3 Filter Chip — Material 3 Expressive
 * Tonal surface when selected, outlined when unselected
 */

import React from "react";
import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Pressable, Text, View } from "react-native";
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
}

export function M3Chip({
  label,
  selected = false,
  onPress,
  icon,
}: M3ChipProps) {
  const colors = useColors();
  const scale = useSharedValue(1);

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
      ]}
      className="flex-row items-center gap-1.5 rounded-lg px-4 py-2"
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
