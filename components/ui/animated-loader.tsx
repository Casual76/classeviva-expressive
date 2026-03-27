/**
 * Componente Loader Animato con Reanimated — M3 Expressive
 * Spinner circolare con pulsazione e rotazione fluida
 */

import React, { useEffect } from "react";
import { View } from "react-native";
import Animated, {
  Easing,
  useAnimatedStyle,
  withRepeat,
  withSequence,
  withTiming,
  useSharedValue,
} from "react-native-reanimated";
import { useColors } from "@/hooks/use-colors";

export function AnimatedLoader() {
  const colors = useColors();
  const rotation = useSharedValue(0);
  const scale = useSharedValue(1);

  useEffect(() => {
    rotation.value = withRepeat(
      withTiming(360, {
        duration: 1200,
        easing: Easing.bezier(0.4, 0, 0.2, 1), // M3 emphasized easing
      }),
      -1,
    );

    scale.value = withRepeat(
      withSequence(
        withTiming(1.08, { duration: 600, easing: Easing.inOut(Easing.ease) }),
        withTiming(0.95, { duration: 600, easing: Easing.inOut(Easing.ease) }),
      ),
      -1,
    );
  }, [rotation, scale]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${rotation.value}deg` }, { scale: scale.value }],
  }));

  return (
    <View className="flex-1 items-center justify-center">
      <Animated.View
        style={[
          animatedStyle,
          {
            width: 48,
            height: 48,
            borderRadius: 24,
            borderWidth: 4,
            borderColor: colors.primaryContainer ?? colors.primary,
            borderTopColor: colors.primary,
            borderRightColor: colors.primaryContainer ?? colors.primary,
          },
        ]}
      />
    </View>
  );
}
