import MaterialIcons from "@expo/vector-icons/MaterialIcons";
import { Redirect, Tabs } from "expo-router";
import { Platform, View } from "react-native";
import { useSafeAreaInsets } from "react-native-safe-area-context";

import { HapticTab } from "@/components/haptic-tab";
import { useColors } from "@/hooks/use-colors";
import { useAuth } from "@/lib/auth-context";

export default function TabLayout() {
  const colors = useColors();
  const insets = useSafeAreaInsets();
  const { status, isSignedIn } = useAuth();
  const bottomPadding = Platform.OS === "web" ? 0 : Math.max(insets.bottom, 0);
  const tabBarHeight = 80 + bottomPadding;
  const renderTabIcon = (name: keyof typeof MaterialIcons.glyphMap) => {
    const TabBarIcon = ({ color, focused }: { color: string; focused: boolean }) => (
      <View
        style={{
          alignItems: "center",
          justifyContent: "center",
          paddingHorizontal: 20,
          paddingVertical: 4,
          borderRadius: 16,
          backgroundColor: focused
            ? (colors.secondaryContainer ?? colors.primaryContainer)
            : "transparent",
        }}
      >
        <MaterialIcons color={color} name={name} size={24} />
      </View>
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
          letterSpacing: 0.4,
          marginTop: 2,
        },
        tabBarItemStyle: {
          paddingTop: 12,
          paddingBottom: 12,
          gap: 4,
        },
        tabBarStyle: {
          position: "absolute",
          left: 0,
          right: 0,
          bottom: 0,
          height: tabBarHeight,
          backgroundColor: colors.surfaceContainer ?? colors.surface,
          borderTopWidth: 0,
          elevation: 2,
          shadowColor: colors.foreground,
          shadowOpacity: 0.06,
          shadowRadius: 8,
          shadowOffset: { width: 0, height: -2 },
          paddingBottom: bottomPadding,
        },
        tabBarActiveBackgroundColor: "transparent",
      }}
    >
      <Tabs.Screen
        name="index"
        options={{
          title: "Home",
          tabBarIcon: renderTabIcon("space-dashboard"),
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
        name="schedule"
        options={{
          title: "Orario",
          tabBarIcon: renderTabIcon("view-week"),
        }}
      />
      <Tabs.Screen
        name="more"
        options={{
          title: "Altro",
          tabBarIcon: renderTabIcon("widgets"),
        }}
      />
      <Tabs.Screen name="absences" options={{ href: null }} />
      <Tabs.Screen name="communications" options={{ href: null }} />
      <Tabs.Screen name="notes" options={{ href: null }} />
      <Tabs.Screen name="materials" options={{ href: null }} />
      <Tabs.Screen name="schoolbooks" options={{ href: null }} />
      <Tabs.Screen name="report-cards" options={{ href: null }} />
      <Tabs.Screen name="profile" options={{ href: null }} />
    </Tabs>
  );
}
