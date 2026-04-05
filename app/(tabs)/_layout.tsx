import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Redirect, Tabs } from "expo-router";
import { useEffect } from "react";
import { Platform, View } from "react-native";
import Animated, { useAnimatedStyle, useSharedValue, withSpring } from "react-native-reanimated";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { HapticTab } from "@/components/haptic-tab";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

function AnimatedTabIcon({
  color,
  focused,
  name,
}: {
  color: string;
  focused: boolean;
  name: keyof typeof MaterialIcons.glyphMap;
}) {
  const colors = useColors();
  const scale = useSharedValue(focused ? 1 : 0.94);
  const translateY = useSharedValue(focused ? -1 : 0);

  useEffect(() => {
    scale.value = withSpring(focused ? 1 : 0.94, {
      damping: 17,
      stiffness: 260,
      mass: 0.72,
    });
    translateY.value = withSpring(focused ? -1 : 0, {
      damping: 18,
      stiffness: 240,
      mass: 0.7,
    });
  }, [focused, scale, translateY]);

  const animatedStyle = useAnimatedStyle(() => ({
    transform: [{ scale: scale.value }, { translateY: translateY.value }],
  }));

  return (
    <Animated.View style={animatedStyle}>
      <View
        style={{
          alignItems: "center",
          justifyContent: "center",
          paddingHorizontal: 18,
          paddingVertical: 6,
          borderRadius: 18,
          backgroundColor: focused
            ? (colors.primaryContainer ?? colors.surfaceContainerHigh)
            : "transparent",
          borderWidth: focused ? 1 : 0,
          borderColor: focused ? (colors.outlineVariant ?? colors.border) : "transparent",
        }}
      >
        <MaterialIcons color={color} name={name} size={24} />
      </View>
    </Animated.View>
  );
}

export default function TabLayout() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { status, isSignedIn } = useAuth();
  const bottomPadding = Platform.OS === "web" ? 0 : Math.max(insets.bottom, 0);
  const renderTabIcon = (name: keyof typeof MaterialIcons.glyphMap) => {
    const TabBarIcon = ({ color, focused }: { color: string; focused: boolean }) => (
      <AnimatedTabIcon color={color} focused={focused} name={name} />
    );

    TabBarIcon.displayName = `${name}TabBarIcon`;
    return TabBarIcon;
  };

  if (status === "restoring") {
    return null;
  }

  if (!isSignedIn) {
    return <Redirect href="/login" />;
  }

  return (
    <Tabs
      screenOptions={{
        headerShown: false,
        sceneStyle: {
          backgroundColor: colors.background,
        },
        tabBarActiveTintColor: colors.onSecondaryContainer ?? colors.primary,
        tabBarInactiveTintColor: colors.onSurfaceVariant ?? colors.muted,
        tabBarButton: HapticTab,
        tabBarHideOnKeyboard: true,
        tabBarLabelStyle: {
          fontSize: 12,
          fontWeight: "500",
          letterSpacing: 0.2,
          marginTop: 1,
        },
        tabBarItemStyle: {
          paddingTop: 8,
          paddingBottom: 8,
          gap: 4,
        },
        tabBarStyle: {
          position: "absolute",
          left: 14,
          right: 14,
          bottom: Math.max(bottomPadding, 12),
          height: 74 + bottomPadding,
          backgroundColor: colors.surface ?? colors.background,
          borderTopWidth: 1,
          borderColor: colors.outlineVariant ?? colors.border,
          borderRadius: 28,
          elevation: 2,
          shadowColor: colors.foreground,
          shadowOpacity: 0.06,
          shadowRadius: 18,
          shadowOffset: { width: 0, height: 6 },
          paddingBottom: Math.max(bottomPadding, 10),
          paddingTop: 6,
        },
        tabBarActiveBackgroundColor: "transparent",
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Oggi",
          tabBarIcon: renderTabIcon("home"),
        }}
      />
      <Tabs.Screen
        name="grades"
        options={{
          title: "Voti",
          tabBarIcon: renderTabIcon("leaderboard"),
        }}
      />
      <Tabs.Screen
        name="calendar"
        options={{
          title: "Agenda",
          tabBarIcon: renderTabIcon("calendar-month"),
        }}
      />
      <Tabs.Screen
        name="communications"
        options={{
          title: "Bacheca",
          tabBarIcon: renderTabIcon("campaign"),
        }}
      />
      <Tabs.Screen
        name="more"
        options={{
          title: "Altro",
          tabBarIcon: renderTabIcon("widgets"),
        }}
      />
      <Tabs.Screen name="schedule" options={{ href: null }} />
      <Tabs.Screen name="absences" options={{ href: null }} />
      <Tabs.Screen name="notes" options={{ href: null }} />
      <Tabs.Screen name="materials" options={{ href: null }} />
      <Tabs.Screen name="schoolbooks" options={{ href: null }} />
      <Tabs.Screen name="report-cards" options={{ href: null }} />
      <Tabs.Screen name="profile" options={{ href: null }} />
    </Tabs>
  );
}
