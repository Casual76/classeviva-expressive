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
      className={cn("flex-row items-center gap-3 rounded-[26px] border px-4 py-3", className)}
      style={{
        backgroundColor: colors.surface,
        borderColor: colors.border,
      }}
    >
      <MaterialIcons color={colors.muted} name="search" size={18} />

      <TextInput
        className="flex-1 text-base text-foreground"
        onChangeText={onChangeText}
        placeholder={placeholder}
        placeholderTextColor={colors.muted}
        style={{ color: colors.foreground }}
        value={value}
      />

      {value.length > 0 && onClear ? (
        <Pressable
          className="h-8 w-8 items-center justify-center rounded-full"
          onPress={onClear}
          style={{ backgroundColor: colors.surfaceAlt ?? colors.background }}
        >
          <MaterialIcons color={colors.muted} name="close" size={16} />
        </Pressable>
      ) : null}
    </View>
  );
}
