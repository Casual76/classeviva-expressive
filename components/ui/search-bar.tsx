/**
 * Componente SearchBar elegante per Classeviva
 */

import React from "react";
import { View, TextInput, TouchableOpacity, Text } from "react-native";
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
      className={cn(
        "flex-row items-center gap-3 px-4 py-3 rounded-2xl border border-border bg-surface",
        className
      )}
    >
      <TextInput
        placeholder={placeholder}
        placeholderTextColor={colors.muted}
        value={value}
        onChangeText={onChangeText}
        className="flex-1 text-foreground text-base"
        style={{
          color: colors.foreground,
        }}
      />
      {value.length > 0 && onClear && (
        <TouchableOpacity
          onPress={onClear}
          activeOpacity={0.7}
          className="p-2"
        >
          <Text className="text-lg text-muted">✕</Text>
        </TouchableOpacity>
      )}
    </View>
  );
}
