import {
  Pressable,
  PressableProps,
  Text,
  View,
  ViewStyle,
} from "react-native";
import { cn, withAlpha } from "@/lib/utils";
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
    sm: "px-4 py-3 rounded-[20px]",
    md: "px-5 py-4 rounded-[24px]",
    lg: "px-6 py-5 rounded-[28px]",
  };

  const variantStyle: Record<NonNullable<ElegantButtonProps["variant"]>, ViewStyle> = {
    primary: {
      backgroundColor: colors.primary,
      borderColor: colors.primary,
      borderWidth: 1,
      shadowColor: colors.primary,
      shadowOpacity: 0.2,
      shadowRadius: 18,
      shadowOffset: { width: 0, height: 12 },
      elevation: 5,
    },
    secondary: {
      backgroundColor: withAlpha(colors.surfaceAlt ?? colors.surface, 0.94),
      borderColor: withAlpha(colors.border, 0.86),
      borderWidth: 1,
    },
    outline: {
      backgroundColor: withAlpha(colors.primary, 0.06),
      borderColor: withAlpha(colors.primary, 0.3),
      borderWidth: 1.25,
    },
    ghost: {
      backgroundColor: withAlpha(colors.surface, 0.3),
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
        <Text className={cn("text-base tracking-[0.2px]", textColorClasses[variant])}>
          {children}
        </Text>
      ) : (
        children
      )}
    </Pressable>
  );
}
