import { clsx, type ClassValue } from "clsx";
import { twMerge } from "tailwind-merge";

/**
 * Combines class names using clsx and tailwind-merge.
 * This ensures Tailwind classes are properly merged without conflicts.
 *
 * Usage:
 * ```tsx
 * cn("px-4 py-2", isActive && "bg-primary", className)
 * ```
 */
export function cn(...inputs: ClassValue[]) {
  return twMerge(clsx(inputs));
}

export function withAlpha(color: string, alpha: number): string {
  if (!/^#([0-9a-f]{6})$/i.test(color)) {
    return color;
  }

  const clamped = Math.max(0, Math.min(1, alpha));
  const hex = Math.round(clamped * 255)
    .toString(16)
    .padStart(2, "0");

  return `${color}${hex}`;
}
