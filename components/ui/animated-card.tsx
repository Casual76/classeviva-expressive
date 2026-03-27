/**
 * Componente Card Animato con Reanimated — M3 Expressive
 * Spring-based entrance con fade-in, scale e translateY
 */

import React, { useEffect } from "react";
import Animated, {
  useAnimatedStyle,
  withSpring,
  withDelay,
  useSharedValue,
  withTiming,
  Easing,
} from "react-native-reanimated";
import { ElegantCard } from "./elegant-card";

interface AnimatedCardProps {
  delay?: number;
  children?: React.ReactNode;
  variant?: "filled" | "outlined" | "gradient" | "elevated";
  gradient?: "primary" | "success" | "warning" | "error";
  className?: string;
  [key: string]: any;
}

export function AnimatedCard({
  delay = 0,
  children,
  ...props
}: AnimatedCardProps) {
  const opacity = useSharedValue(0);
  const translateY = useSharedValue(12);

  useEffect(() => {
    opacity.value = withDelay(
      delay,
      withTiming(1, { duration: 350, easing: Easing.out(Easing.cubic) }),
    );
    translateY.value = withDelay(
      delay,
      withSpring(0, { damping: 18, stiffness: 180, mass: 0.8 }),
    );
  }, [delay, opacity, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }],
  }));

  const { delay: _, children: __, ...cardProps } = props;

  return (
    <Animated.View style={animatedStyle}>
      <ElegantCard {...cardProps}>{children}</ElegantCard>
    </Animated.View>
  );
}
