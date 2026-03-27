import {
  Pressable,
  PressableProps,
  Text,
  View,
  ViewStyle,
} from "react-native";
import Animated, {
  useAnimatedStyle,
  useSharedValue,
  withSpring,
} from "react-native-reanimated";
import { cn, withAlpha } from "@/lib/utils";
import { useColors } from "@/hooks/use-colors";

const AnimatedPressable = Animated.createAnimatedComponent(Pressable);

interface ElegantButtonProps extends Omit<PressableProps, "style"> {
  variant?: "primary" | "secondary" | "outline" | "ghost";
  size?: "sm" | "md" | "lg";
  children: string | React.ReactNode;
  icon?: React.ReactNode;
  fullWidth?: boolean;
}

export function ElegantButton({
  variant = "primary",
  size = "md",
  children,
  icon,
  fullWidth = false,
  className,
  ...props
}: ElegantButtonProps) {
  const colors = useColors();
  const scale = useSharedValue(1);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }],
  }));

  const handlePressIn = () => {
    scale.value = withSpring(0.96, { damping: 15, stiffness: 400 });
  };

  const handlePressOut = () => {
    scale.value = withSpring(1, { damping: 15, stiffness: 400 });
  };

  const sizeClasses = {
    sm: "px-4 py-3 rounded-full",
    md: "px-5 py-4 rounded-full",
    lg: "px-6 py-5 rounded-full",
  };

  const variantStyle: Record<NonNullable<ElegantButtonProps["variant"]>, ViewStyle> = {
    primary: {
      backgroundColor: colors.primary,
      shadowColor: colors.primary,
      shadowOpacity: 0.15,
      shadowRadius: 12,
      shadowOffset: { width: 0, height: 4 },
      elevation: 3,
    },
    secondary: {
      backgroundColor: colors.secondaryContainer ?? withAlpha(colors.secondary ?? colors.primary, 0.12),
    },
    outline: {
      backgroundColor: "transparent",
      borderColor: colors.outline ?? colors.border,
      borderWidth: 1,
    },
    ghost: {
      backgroundColor: "transparent",
    },
  };

  const textColorClasses = {
    primary: "text-onPrimary font-medium",
    secondary: "text-onSecondaryContainer font-medium",
    outline: "text-primary font-medium",
    ghost: "text-primary font-medium",
  };

  // Fallback text styles for colors that may not be in Tailwind
  const textColorStyle = {
    primary: { color: colors.onPrimary ?? "#FFFFFF" },
    secondary: { color: colors.onSecondaryContainer ?? colors.foreground },
    outline: { color: colors.primary },
    ghost: { color: colors.primary },
  };

  const baseClasses = "flex-row items-center justify-center gap-2";

  return (
    <AnimatedPressable
      {...props}
      style={[
        variantStyle[variant],
        animatedStyle,
        { opacity: props.disabled ? 0.38 : 1 },
      ]}
      className={cn(
        baseClasses,
        sizeClasses[size],
        fullWidth && "w-full",
        className
      )}
      onPressIn={(e) => {
        handlePressIn();
        props.onPressIn?.(e);
      }}
      onPressOut={(e) => {
        handlePressOut();
        props.onPressOut?.(e);
      }}
    >
      {icon && <View>{icon}</View>}
      {typeof children === "string" ? (
        <Text
          className={cn("text-base tracking-[0.1px]", textColorClasses[variant])}
          style={textColorStyle[variant]}
        >
          {children}
        </Text>
      ) : (
        children
      )}
    </AnimatedPressable>
  );
}
