import * as WebBrowser from "expo-web-browser";
import { Linking, Platform } from "react-native";

export async function openExternalContent(url: string): Promise<void> {
  if (Platform.OS === "web" && typeof window !== "undefined") {
    window.open(url, "_blank", "noopener,noreferrer");
    return;
  }

  const browserResult = await WebBrowser.openBrowserAsync(url, {
    showTitle: true,
    enableBarCollapsing: true,
  });

  if (browserResult.type === "cancel" || browserResult.type === "dismiss") {
    return;
  }

  if (browserResult.type === "opened") {
    return;
  }

  await Linking.openURL(url);
}
