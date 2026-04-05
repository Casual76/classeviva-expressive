import { useEffect } from "react";
import { View, type ViewProps } from "react-native";
import { useIsFocused } from "@react-navigation/native";
import Animated, {
  Easing,
  useAnimatedStyle,
  useSharedValue,
  withSpring,
  withTiming,
} from "react-native-reanimated";
import { SafeAreaView, type Edge } from "react-native-safe-area-context";

import { cn } from "@/lib/utils";

const AnimatedView = Animated.createAnimatedComponent(View);

export interface ScreenContainerProps extends ViewProps {
  /**
   * SafeArea edges to apply. Defaults to ["top", "left", "right"].
   * Bottom is typically handled by Tab Bar.
   */
  edges?: Edge[];
  /**
   * Tailwind className for the content area.
   */
  className?: string;
  /**
   * Additional className for the outer container (background layer).
   */
  containerClassName?: string;
  /**
   * Additional className for the SafeAreaView (content layer).
   */
  safeAreaClassName?: string;
  /**
   * Enables the shared page-focus transition used across app screens.
   */
  animateOnFocus?: boolean;
}

/**
 * A container component that properly handles SafeArea and background colors.
 *
 * The outer View extends to full screen (including status bar area) with the background color,
 * while the inner SafeAreaView ensures content is within safe bounds.
 *
 * Usage:
 * ```tsx
 * <ScreenContainer className="p-4">
 *   <Text className="text-2xl font-bold text-foreground">
 *     Welcome
 *   </Text>
 * </ScreenContainer>
 * ```
 */
export function ScreenContainer({
  children,
  edges = ["top", "left", "right"],
  className,
  containerClassName,
  safeAreaClassName,
  animateOnFocus = true,
  style,
  ...props
}: ScreenContainerProps) {
  const isFocused = useIsFocused();
  const opacity = useSharedValue(animateOnFocus ? 0 : 1);
  const translateY = useSharedValue(animateOnFocus ? 14 : 0);
  const scale = useSharedValue(animateOnFocus ? 0.992 : 1);

  useEffect(() => {
    if (!animateOnFocus) {
      opacity.value = 1;
      translateY.value = 0;
      scale.value = 1;
      return;
    }

    if (isFocused) {
      opacity.value = withTiming(1, {
        duration: 260,
        easing: Easing.out(Easing.cubic),
      });
      translateY.value = withTiming(0, {
        duration: 320,
        easing: Easing.out(Easing.cubic),
      });
      scale.value = withSpring(1, {
        damping: 20,
        stiffness: 230,
        mass: 0.78,
      });
      return;
    }

    opacity.value = withTiming(0.985, {
      duration: 180,
      easing: Easing.out(Easing.quad),
    });
    translateY.value = withTiming(6, {
      duration: 180,
      easing: Easing.out(Easing.quad),
    });
    scale.value = withTiming(0.997, {
      duration: 180,
      easing: Easing.out(Easing.quad),
    });
  }, [animateOnFocus, isFocused, opacity, scale, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    opacity: opacity.value,
    transform: [{ translateY: translateY.value }, { scale: scale.value }],
  }));

  return (
    <View
      className={cn(
        "flex-1",
        "bg-background",
        containerClassName
      )}
      {...props}
    >
      <SafeAreaView
        edges={edges}
        className={cn("flex-1", safeAreaClassName)}
        style={style}
      >
        <AnimatedView className={cn("flex-1", className)} style={animatedStyle}>
          {children}
        </AnimatedView>
      </SafeAreaView>
    </View>
  );
}
