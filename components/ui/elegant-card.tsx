import { View, ViewProps } from "react-native";
import { cn } from "@/lib/utils";

interface ElegantCardProps extends ViewProps {
  variant?: "elevated" | "filled" | "outlined" | "gradient";
  gradient?: "primary" | "success" | "warning" | "error";
}

export function ElegantCard({
  variant = "elevated",
  gradient = "primary",
  className,
  children,
  ...props
}: ElegantCardProps) {
  const baseClasses = "rounded-3xl overflow-hidden";

  const variantClasses = {
    elevated: "bg-surface border border-border shadow-lg",
    filled: "bg-surface",
    outlined: "bg-transparent border-2 border-primary",
    gradient: getGradientClasses(gradient),
  };

  return (
    <View
      className={cn(baseClasses, variantClasses[variant], className)}
      {...props}
    >
      {children}
    </View>
  );
}

function getGradientClasses(gradient: string): string {
  const gradients: Record<string, string> = {
    primary: "bg-gradient-to-br from-primary to-primary/80",
    success: "bg-gradient-to-br from-success to-success/80",
    warning: "bg-gradient-to-br from-warning to-warning/80",
    error: "bg-gradient-to-br from-error to-error/80",
  };
  return gradients[gradient] || gradients.primary;
}
