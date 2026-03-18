/**
 * Componente Card Animato con Reanimated
 * Fornisce animazioni di fade-in e scale quando la card appare
 */

import React, { useEffect } from "react";
import { View, type ViewProps } from "react-native";
import Animated, {
  useSharedValue,
  useAnimatedStyle,
  withTiming,
  Easing,
} from "react-native-reanimated";
import { ElegantCard } from "./elegant-card";

interface AnimatedCardProps {
  delay?: number;
  duration?: number;
  children?: React.ReactNode;
  variant?: "filled" | "outlined" | "gradient" | "elevated";
  gradient?: "primary" | "success" | "warning" | "error";
  className?: string;
  [key: string]: any;
}

export function AnimatedCard({
  delay = 0,
  duration = 400,
  children,
  ...props
}: AnimatedCardProps) {
  const opacity = useSharedValue(0);
  const scale = useSharedValue(0.95);

  useEffect(() => {
    const timer = setTimeout(() => {
      opacity.value = withTiming(1, {
        duration,
        easing: Easing.out(Easing.cubic),
      });

      scale.value = withTiming(1, {
        duration,
        easing: Easing.out(Easing.cubic),
      });
    }, delay);

    return () => clearTimeout(timer);
  }, []);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ scale: scale.value }],
  }));

  const { delay: _, duration: __, children: ___, ...cardProps } = props;

  return (
    <Animated.View style={animatedStyle}>
      <ElegantCard {...cardProps}>{children}</ElegantCard>
    </Animated.View>
  );
}
