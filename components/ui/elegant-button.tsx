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
    sm: "px-4 py-2 rounded-lg",
    md: "px-6 py-3 rounded-xl",
    lg: "px-8 py-4 rounded-2xl",
  };

  const variantClasses = {
    primary: "bg-primary",
    secondary: "bg-surface border-2 border-primary",
    outline: "border-2 border-primary",
    ghost: "bg-transparent",
  };

  const textColorClasses = {
    primary: "text-background font-semibold",
    secondary: "text-primary font-semibold",
    outline: "text-primary font-semibold",
    ghost: "text-primary font-semibold",
  };

  const baseClasses = "flex-row items-center justify-center gap-2";

  return (
    <Pressable
      {...props}
      style={({ pressed }) => [
        {
          opacity: pressed ? 0.8 : 1,
          transform: [{ scale: pressed ? 0.98 : 1 }],
        } as ViewStyle,
      ]}
      className={cn(
        baseClasses,
        sizeClasses[size],
        variantClasses[variant],
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
