/**
 * Componente Loader Animato con Reanimated
 * Mostra un'animazione di caricamento elegante
 */

import React, { useEffect } from "react";
import { View } from "react-native";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withRepeat,
  withTiming,
  Easing,
  interpolate,
  Extrapolate,
} from "react-native-reanimated";
import { useColors } from "@/hooks/use-colors";

export function AnimatedLoader() {
  const colors = useColors();
  const rotation = useSharedValue(0);
  const opacity = useSharedValue(0.3);

  useEffect(() => {
    rotation.value = withRepeat(
      withTiming(360, {
        duration: 2000,
        easing: Easing.linear,
      }),
      -1
    );

    opacity.value = withRepeat(
      withTiming(1, {
        duration: 1500,
        easing: Easing.inOut(Easing.ease),
      }),
      -1,
      true
    );
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ rotate: `${rotation.value}deg` }],
    opacity: opacity.value,
  }));

  return (
    <View className="flex-1 items-center justify-center">
      <Animated.View
        style={[
          animatedStyle,
          {
            width: 50,
            height: 50,
            borderRadius: 25,
            borderWidth: 3,
            borderColor: colors.primary,
            borderTopColor: colors.primary,
            borderRightColor: "transparent",
          },
        ]}
      />
    </View>
  );
}
