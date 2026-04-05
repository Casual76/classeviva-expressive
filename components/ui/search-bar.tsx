import React from "react";
import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Pressable, TextInput, View } from "react-native";

import { useColors } from "@/hooks/use-colors";
import { cn } from "@/lib/utils";

interface SearchBarProps {
  placeholder?: string;
  value: string;
  onChangeText: (text: string) => void;
  onClear?: () => void;
  className?: string;
}

export function SearchBar({
  placeholder = "Cerca...",
  value,
  onChangeText,
  onClear,
  className,
}: SearchBarProps) {
  const colors = useColors();

  return (
    <View
      className={cn("flex-row items-center gap-3 rounded-[22px] px-4 py-3.5", className)}
      style={{
        backgroundColor: colors.surface ?? colors.background,
        borderWidth: 1,
        borderColor: colors.outlineVariant ?? colors.border,
      }}
    >
      <MaterialIcons color={colors.onSurfaceVariant ?? colors.muted} name="search" size={20} />

      <TextInput
        className="flex-1 text-base"
        autoCapitalize="none"
        autoCorrect={false}
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.onSurfaceVariant ?? colors.muted}
        style={{ color: colors.foreground }}
        value={value}
      />

      {value.length > 0 && onClear ? (
        <Pressable
          className="h-8 w-8 items-center justify-center rounded-full"
          onPress={onClear}
          style={{ backgroundColor: colors.surfaceContainerHigh ?? colors.surfaceAlt ?? colors.background }}
        >
          <MaterialIcons color={colors.onSurfaceVariant ?? colors.muted} name="close" size={18} />
        </Pressable>
      ) : null}
    </View>
  );
}
