import {
  Pressable,
  PressableProps,
  Text,
  View,
  ViewStyle,
} from "react-native";
import { cn } from "@/lib/utils";
import { useColors } from "@/hooks/use-colors";

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

  const sizeClasses = {
    sm: "px-4 py-3 rounded-2xl",
    md: "px-5 py-4 rounded-3xl",
    lg: "px-6 py-5 rounded-[28px]",
  };

  const variantStyle: Record<NonNullable<ElegantButtonProps["variant"]>, ViewStyle> = {
    primary: {
      backgroundColor: colors.primary,
      borderColor: colors.primary,
      borderWidth: 1,
      shadowColor: colors.primary,
      shadowOpacity: 0.18,
      shadowRadius: 16,
      shadowOffset: { width: 0, height: 10 },
      elevation: 4,
    },
    secondary: {
      backgroundColor: colors.surfaceAlt ?? colors.surface,
      borderColor: colors.border,
      borderWidth: 1,
    },
    outline: {
      backgroundColor: "transparent",
      borderColor: colors.primary,
      borderWidth: 1.25,
    },
    ghost: {
      backgroundColor: "transparent",
      borderColor: "transparent",
      borderWidth: 1,
    },
  };

  const textColorClasses = {
    primary: "text-background font-semibold",
    secondary: "text-foreground font-semibold",
    outline: "text-primary font-semibold",
    ghost: "text-primary font-semibold",
  };

  const baseClasses = "flex-row items-center justify-center gap-2";

  return (
    <Pressable
      {...props}
      style={({ pressed }) => [
        variantStyle[variant],
        {
          opacity: props.disabled ? 0.45 : pressed ? 0.88 : 1,
          transform: [{ scale: pressed ? 0.985 : 1 }],
        } as ViewStyle,
      ]}
      className={cn(
        baseClasses,
        sizeClasses[size],
        fullWidth && "w-full",
        className
      )}
    >
      {icon && <View>{icon}</View>}
      {typeof children === "string" ? (
        <Text className={cn("text-base", textColorClasses[variant])}>
          {children}
        </Text>
      ) : (
        children
      )}
    </Pressable>
  );
}
