/**
 * Servizio notifiche centralizzato con canali Android e preferenze locali.
 * In Expo Go le notifiche native restano disattivate.
 */

import AsyncStorage from "@react-native-async-storage/async-storage";
import Constants from "expo-constants";
import { Platform } from "react-native";

import {
  DEFAULT_NOTIFICATION_PREFERENCES,
  readNotificationPreferences,
  type NotificationPreferences,
  writeNotificationPreferences,
} from "@/lib/app-preferences";

type NotificationsModule = typeof import("expo-notifications");
type NotificationTriggerInput = import("expo-notifications").NotificationTriggerInput;
type PermissionStatus = "granted" | "denied" | "undetermined" | "unsupported";
type NotificationCategory = "homework" | "communications" | "absences" | "test";

export interface PermissionSnapshot {
  status: PermissionStatus;
  canAskAgain: boolean;
}

const SCHEDULED_NOTIFICATIONS_KEY = "scheduled_notifications";
const CHANNEL_IDS: Record<NotificationCategory, string> = {
  homework: "classeviva-homework",
  communications: "classeviva-communications",
  absences: "classeviva-absences",
  test: "classeviva-test",
};

function isExpoGoEnvironment() {
  const executionEnvironment = String((Constants as { executionEnvironment?: string }).executionEnvironment ?? "");
  const appOwnership = String((Constants as { appOwnership?: string }).appOwnership ?? "");

  return executionEnvironment === "storeClient" || appOwnership === "expo";
}

class NotificationsService {
  private scheduledNotifications: Map<string, string> = new Map();
  private notificationsModulePromise: Promise<NotificationsModule | null> | null = null;
  private listenersReady = false;
  private warnedUnsupportedEnvironment = false;
  private cachedPreferences: NotificationPreferences = DEFAULT_NOTIFICATION_PREFERENCES;

  private async getNotificationsModule(): Promise<NotificationsModule | null> {
    if (isExpoGoEnvironment()) {
      if (!this.warnedUnsupportedEnvironment) {
        console.warn("Notifiche native disattivate in Expo Go. Usa una development build per testarle.");
        this.warnedUnsupportedEnvironment = true;
      }

      return null;
    }

    if (!this.notificationsModulePromise) {
      this.notificationsModulePromise = import("expo-notifications")
        .then((module) => {
          module.setNotificationHandler({
            handleNotification: async () => ({
              shouldShowAlert: true,
              shouldPlaySound: true,
              shouldSetBadge: true,
              shouldShowBanner: true,
              shouldShowList: true,
            }),
          });

          return module;
        })
        .catch((error) => {
          console.error("Errore nel caricamento di expo-notifications:", error);
          return null;
        });
    }

    return this.notificationsModulePromise;
  }

  private async loadPreferences(): Promise<NotificationPreferences> {
    this.cachedPreferences = await readNotificationPreferences();
    return this.cachedPreferences;
  }

  private async getPreferences(): Promise<NotificationPreferences> {
    return this.cachedPreferences ?? this.loadPreferences();
  }

  private async configureAndroidChannels(Notifications: NotificationsModule): Promise<void> {
    if (Platform.OS !== "android") {
      return;
    }

    await Promise.all([
      Notifications.setNotificationChannelAsync(CHANNEL_IDS.homework, {
        name: "Compiti",
        description: "Promemoria dei compiti in scadenza.",
        importance: Notifications.AndroidImportance.HIGH,
        vibrationPattern: [0, 200, 100, 200],
        lightColor: "#3E7B67",
      }),
      Notifications.setNotificationChannelAsync(CHANNEL_IDS.communications, {
        name: "Comunicazioni",
        description: "Nuove comunicazioni e circolari dalla bacheca.",
        importance: Notifications.AndroidImportance.HIGH,
        vibrationPattern: [0, 180, 80, 180],
        lightColor: "#825500",
      }),
      Notifications.setNotificationChannelAsync(CHANNEL_IDS.absences, {
        name: "Assenze",
        description: "Promemoria per assenze, ritardi e giustificazioni.",
        importance: Notifications.AndroidImportance.DEFAULT,
        vibrationPattern: [0, 220, 120, 220],
        lightColor: "#B3261E",
      }),
      Notifications.setNotificationChannelAsync(CHANNEL_IDS.test, {
        name: "Test notifiche",
        description: "Canale dedicato per verificare che le notifiche funzionino.",
        importance: Notifications.AndroidImportance.HIGH,
        vibrationPattern: [0, 150, 70, 150],
        lightColor: "#6750A4",
      }),
    ]);
  }

  private async ensurePermissions(
    Notifications: NotificationsModule,
    options?: { requestIfNeeded?: boolean },
  ): Promise<boolean> {
    const current = await Notifications.getPermissionsAsync();
    if (current.status === "granted") {
      return true;
    }

    if (!options?.requestIfNeeded) {
      return false;
    }

    const requested = await Notifications.requestPermissionsAsync();
    return requested.status === "granted";
  }

  private async scheduleNotification(
    key: string,
    category: NotificationCategory,
    content: {
      title: string;
      body: string;
      data: Record<string, unknown>;
    },
    trigger: NotificationTriggerInput,
  ): Promise<void> {
    try {
      const preferences = await this.getPreferences();
      if (!preferences.enabled) {
        return;
      }

      if (
        (category === "homework" && !preferences.homework) ||
        (category === "communications" && !preferences.communications) ||
        (category === "absences" && !preferences.absences) ||
        (category === "test" && !preferences.test)
      ) {
        return;
      }

      const Notifications = await this.getNotificationsModule();
      if (!Notifications) {
        return;
      }

      await this.configureAndroidChannels(Notifications);

      const hasPermission = await this.ensurePermissions(Notifications);
      if (!hasPermission) {
        return;
      }

      const notificationId = await Notifications.scheduleNotificationAsync({
        content: {
          ...content,
          sound: "default",
          badge: 1,
          ...(Platform.OS === "android" ? { channelId: CHANNEL_IDS[category] } : {}),
        } as import("expo-notifications").NotificationContentInput,
        trigger,
      });

      this.scheduledNotifications.set(key, notificationId);
      await this.saveScheduledNotifications();
    } catch (error) {
      console.error("Errore nella programmazione notifica:", error);
    }
  }

  async initialize(options?: { requestPermissions?: boolean }): Promise<void> {
    try {
      await this.loadPreferences();

      const Notifications = await this.getNotificationsModule();
      if (!Notifications) {
        return;
      }

      await this.configureAndroidChannels(Notifications);
      const hasPermission = await this.ensurePermissions(Notifications, {
        requestIfNeeded: options?.requestPermissions ?? false,
      });
      if (!hasPermission) {
        return;
      }

      await this.loadScheduledNotifications();
      this.setupNotificationListeners(Notifications);
    } catch (error) {
      console.error("Errore nell'inizializzazione notifiche:", error);
    }
  }

  async getPermissionSnapshot(): Promise<PermissionSnapshot> {
    const Notifications = await this.getNotificationsModule();
    if (!Notifications) {
      return { status: "unsupported", canAskAgain: false };
    }

    const permissions = await Notifications.getPermissionsAsync();
    return {
      status: permissions.status,
      canAskAgain: permissions.canAskAgain ?? false,
    };
  }

  async requestPermissions(): Promise<PermissionSnapshot> {
    const Notifications = await this.getNotificationsModule();
    if (!Notifications) {
      return { status: "unsupported", canAskAgain: false };
    }

    await this.configureAndroidChannels(Notifications);
    const permissions = await Notifications.requestPermissionsAsync();
    if (permissions.status === "granted") {
      this.setupNotificationListeners(Notifications);
    }

    return {
      status: permissions.status,
      canAskAgain: permissions.canAskAgain ?? false,
    };
  }

  async readPreferences(): Promise<NotificationPreferences> {
    return this.loadPreferences();
  }

  async updatePreferences(next: Partial<NotificationPreferences>): Promise<NotificationPreferences> {
    this.cachedPreferences = await writeNotificationPreferences(next);
    if (!this.cachedPreferences.enabled) {
      await this.cancelAllNotifications();
    }

    return this.cachedPreferences;
  }

  async sendTestNotification(): Promise<boolean> {
    const Notifications = await this.getNotificationsModule();
    if (!Notifications) {
      return false;
    }

    const permissions = await this.requestPermissions();
    if (permissions.status !== "granted") {
      return false;
    }

    await this.scheduleNotification(
      `test_${Date.now()}`,
      "test",
      {
        title: "Test notifiche",
        body: "Le notifiche di Classeviva Expressive stanno funzionando correttamente.",
        data: { type: "test" },
      },
      {
        type: "timeInterval",
        seconds: 1,
      } as NotificationTriggerInput,
    );

    return true;
  }

  async scheduleHomeworkNotification(homework: {
    id: string;
    subject: string;
    description: string;
    dueDate: string;
  }) {
    const dueDate = new Date(homework.dueDate);
    const now = new Date();

    if (dueDate <= now) {
      return;
    }

    const notificationDate = new Date(dueDate);
    notificationDate.setDate(notificationDate.getDate() - 1);
    notificationDate.setHours(9, 0, 0, 0);

    if (notificationDate <= now) {
      return;
    }

    await this.scheduleNotification(
      `homework_${homework.id}`,
      "homework",
      {
        title: `Compito in scadenza: ${homework.subject}`,
        body: homework.description,
        data: {
          type: "homework",
          homeworkId: homework.id,
        },
      },
      {
        type: "calendar",
        dateComponents: {
          year: notificationDate.getFullYear(),
          month: notificationDate.getMonth() + 1,
          day: notificationDate.getDate(),
          hour: notificationDate.getHours(),
          minute: notificationDate.getMinutes(),
        },
      } as NotificationTriggerInput,
    );
  }

  async scheduleCommunicationNotification(communication: {
    id: string;
    title: string;
    sender: string;
  }) {
    await this.scheduleNotification(
      `communication_${communication.id}`,
      "communications",
      {
        title: `Nuova comunicazione da ${communication.sender}`,
        body: communication.title,
        data: {
          type: "communication",
          communicationId: communication.id,
        },
      },
      {
        type: "timeInterval",
        seconds: 2,
      } as NotificationTriggerInput,
    );
  }

  async scheduleAbsenceNotification(absence: {
    id: string;
    date: string;
    type: string;
  }) {
    const absenceDate = new Date(absence.date);
    const notificationDate = new Date(absenceDate);
    notificationDate.setHours(18, 0, 0, 0);

    await this.scheduleNotification(
      `absence_${absence.id}`,
      "absences",
      {
        title: "Evento da giustificare",
        body: `Hai un ${absence.type} da verificare del ${absenceDate.toLocaleDateString("it-IT")}.`,
        data: {
          type: "absence",
          absenceId: absence.id,
        },
      },
      {
        type: "calendar",
        dateComponents: {
          year: notificationDate.getFullYear(),
          month: notificationDate.getMonth() + 1,
          day: notificationDate.getDate(),
          hour: notificationDate.getHours(),
          minute: notificationDate.getMinutes(),
        },
      } as NotificationTriggerInput,
    );
  }

  async cancelNotification(key: string) {
    try {
      const Notifications = await this.getNotificationsModule();
      if (!Notifications) {
        return;
      }

      const notificationId = this.scheduledNotifications.get(key);
      if (notificationId) {
        await Notifications.cancelScheduledNotificationAsync(notificationId);
        this.scheduledNotifications.delete(key);
        await this.saveScheduledNotifications();
      }
    } catch (error) {
      console.error("Errore nella cancellazione notifica:", error);
    }
  }

  async cancelAllNotifications() {
    try {
      const Notifications = await this.getNotificationsModule();
      if (!Notifications) {
        return;
      }

      await Notifications.cancelAllScheduledNotificationsAsync();
      this.scheduledNotifications.clear();
      await AsyncStorage.removeItem(SCHEDULED_NOTIFICATIONS_KEY);
    } catch (error) {
      console.error("Errore nella cancellazione notifiche:", error);
    }
  }

  private async saveScheduledNotifications() {
    try {
      const data = Object.fromEntries(this.scheduledNotifications);
      await AsyncStorage.setItem(SCHEDULED_NOTIFICATIONS_KEY, JSON.stringify(data));
    } catch (error) {
      console.error("Errore nel salvataggio notifiche:", error);
    }
  }

  private async loadScheduledNotifications() {
    try {
      const data = await AsyncStorage.getItem(SCHEDULED_NOTIFICATIONS_KEY);
      if (data) {
        const parsed = JSON.parse(data) as Record<string, string>;
        this.scheduledNotifications = new Map(Object.entries(parsed));
      }
    } catch (error) {
      console.error("Errore nel caricamento notifiche:", error);
    }
  }

  private setupNotificationListeners(Notifications: NotificationsModule) {
    if (this.listenersReady) {
      return;
    }

    Notifications.addNotificationReceivedListener((notification) => {
      console.log("Notifica ricevuta:", notification);
    });

    Notifications.addNotificationResponseReceivedListener((response) => {
      const { data } = response.notification.request.content;
      console.log("Notifica toccata:", data);
    });

    this.listenersReady = true;
  }
}

export const notificationsService = new NotificationsService();
