/**
 * AnimatedListItem — M3 Expressive staggered entrance
 * Wraps children with FadeInDown animation on mount
 */

import React, { useEffect } from "react";
import Animated, {
  LinearTransition,
  useAnimatedStyle,
  useSharedValue,
  withDelay,
  withSpring,
  withTiming,
  Easing,
} from "react-native-reanimated";

interface AnimatedListItemProps {
  index: number;
  children: React.ReactNode;
}

export function AnimatedListItem({ index, children }: AnimatedListItemProps) {
  const opacity = useSharedValue(0);
  const translateY = useSharedValue(16);
  const scale = useSharedValue(0.985);
  const delay = Math.min(index * 50, 400); // cap at 400ms

  useEffect(() => {
    opacity.value = withDelay(
      delay,
      withTiming(1, { duration: 300, easing: Easing.out(Easing.cubic) }),
    );
    translateY.value = withDelay(
      delay,
      withSpring(0, { damping: 20, stiffness: 200, mass: 0.7 }),
    );
    scale.value = withDelay(
      delay,
      withSpring(1, { damping: 18, stiffness: 220, mass: 0.72 }),
    );
  }, [delay, opacity, scale, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }, { scale: scale.value }],
  }));

  return (
    <Animated.View
      layout={LinearTransition.springify().damping(22).stiffness(210).mass(0.75)}
      style={animatedStyle}
    >
      {children}
    </Animated.View>
  );
}
