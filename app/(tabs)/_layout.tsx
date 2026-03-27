import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Redirect, Tabs } from "expo-router";
import { Platform } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { HapticTab } from "@/components/haptic-tab";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

export default function TabLayout() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { status, isSignedIn } = useAuth();
  const bottomPadding = Platform.OS === "web" ? 14 : Math.max(insets.bottom, 10);
  const tabBarHeight = 62 + bottomPadding;

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
        tabBarActiveTintColor: colors.primary,
        tabBarInactiveTintColor: colors.muted,
        tabBarButton: HapticTab,
        tabBarHideOnKeyboard: true,
        tabBarLabelStyle: {
          fontSize: 11,
          fontWeight: "700",
          letterSpacing: 0.2,
        },
        tabBarItemStyle: {
          paddingTop: 5,
        },
        tabBarStyle: {
          position: "absolute",
          left: 14,
          right: 14,
          bottom: bottomPadding,
          height: tabBarHeight,
          backgroundColor: colors.surface,
          borderColor: colors.border,
          borderWidth: 1,
          borderTopWidth: 1,
          borderRadius: 30,
          paddingTop: 8,
          paddingBottom: 10,
          shadowColor: colors.secondary ?? colors.foreground,
          shadowOpacity: 0.16,
          shadowRadius: 18,
          shadowOffset: { width: 0, height: 12 },
          elevation: 12,
        },
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Home",
          tabBarIcon: ({ color }) => <MaterialIcons color={color} name="space-dashboard" size={24} />,
        }}
      />
      <Tabs.Screen
        name="grades"
        options={{
          title: "Voti",
          tabBarIcon: ({ color }) => <MaterialIcons color={color} name="leaderboard" size={24} />,
        }}
      />
      <Tabs.Screen
        name="calendar"
        options={{
          title: "Agenda",
          tabBarIcon: ({ color }) => <MaterialIcons color={color} name="calendar-month" size={24} />,
        }}
      />
      <Tabs.Screen
        name="absences"
        options={{
          title: "Assenze",
          tabBarIcon: ({ color }) => <MaterialIcons color={color} name="fact-check" size={24} />,
        }}
      />
      <Tabs.Screen
        name="more"
        options={{
          title: "Altro",
          tabBarIcon: ({ color }) => <MaterialIcons color={color} name="widgets" size={24} />,
        }}
      />
      <Tabs.Screen name="communications" options={{ href: null }} />
      <Tabs.Screen name="notes" options={{ href: null }} />
      <Tabs.Screen name="materials" options={{ href: null }} />
      <Tabs.Screen name="schoolbooks" options={{ href: null }} />
      <Tabs.Screen name="report-cards" options={{ href: null }} />
      <Tabs.Screen name="schedule" options={{ href: null }} />
      <Tabs.Screen name="profile" options={{ href: null }} />
    </Tabs>
  );
}
