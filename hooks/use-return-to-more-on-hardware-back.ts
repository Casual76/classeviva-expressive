import { useFocusEffect, useRouter } from "expo-router";
import { useCallback } from "react";
import { BackHandler } from "react-native";

export function useReturnToMoreOnHardwareBack() {
  const router = useRouter();

  useFocusEffect(
    useCallback(() => {
      const subscription = BackHandler.addEventListener("hardwareBackPress", () => {
        router.replace("/(tabs)/more");
        return true;
      });

      return () => subscription.remove();
    }, [router]),
  );
}
