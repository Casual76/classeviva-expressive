/**
 * Servizio di Notifiche Push per Classeviva Expressive
 * Gestisce notifiche locali per compiti in scadenza, comunicazioni e assenze
 */

import * as Notifications from "expo-notifications";
import AsyncStorage from "@react-native-async-storage/async-storage";

// Configura il comportamento delle notifiche
Notifications.setNotificationHandler({
  handleNotification: async () => ({
    shouldShowAlert: true,
    shouldPlaySound: true,
    shouldSetBadge: true,
    shouldShowBanner: true,
    shouldShowList: true,
  }),
});

interface ScheduledNotification {
  id: string;
  title: string;
  body: string;
  data: Record<string, any>;
  trigger: Notifications.NotificationTriggerInput;
}

class NotificationsService {
  private scheduledNotifications: Map<string, string> = new Map();

  /**
   * Inizializza il servizio di notifiche
   */
  async initialize() {
    try {
      // Richiedi permessi
      const { status } = await Notifications.requestPermissionsAsync();
      if (status !== "granted") {
        console.warn("Permessi notifiche non concessi");
        return;
      }

      // Carica notifiche programmate precedentemente
      await this.loadScheduledNotifications();

      // Ascolta notifiche ricevute
      this.setupNotificationListeners();
    } catch (error) {
      console.error("Errore nell'inizializzazione notifiche:", error);
    }
  }

  /**
   * Programma una notifica per un compito in scadenza
   */
  async scheduleHomeworkNotification(homework: {
    id: string;
    subject: string;
    description: string;
    dueDate: string;
  }) {
    try {
      const dueDate = new Date(homework.dueDate);
      const now = new Date();

      // Non programmare se la data è già passata
      if (dueDate <= now) {
        return;
      }

      // Programma notifica 1 giorno prima della scadenza
      const notificationDate = new Date(dueDate);
      notificationDate.setDate(notificationDate.getDate() - 1);
      notificationDate.setHours(9, 0, 0, 0);

      if (notificationDate > now) {
        const notificationId = await Notifications.scheduleNotificationAsync({
          content: {
            title: `Compito in scadenza: ${homework.subject}`,
            body: homework.description,
            data: {
              type: "homework",
              homeworkId: homework.id,
            },
            sound: "default",
            badge: 1,
          },
        trigger: {
          type: "calendar",
          dateComponents: {
            year: notificationDate.getFullYear(),
            month: notificationDate.getMonth() + 1,
            day: notificationDate.getDate(),
            hour: notificationDate.getHours(),
            minute: notificationDate.getMinutes(),
          },
        } as any,
        });

        // Salva l'ID della notifica
        this.scheduledNotifications.set(`homework_${homework.id}`, notificationId);
        await this.saveScheduledNotifications();
      }
    } catch (error) {
      console.error("Errore nella programmazione notifica compito:", error);
    }
  }

  /**
   * Programma una notifica per una comunicazione importante
   */
  async scheduleCommunicationNotification(communication: {
    id: string;
    title: string;
    sender: string;
  }) {
    try {
      const notificationId = await Notifications.scheduleNotificationAsync({
        content: {
          title: `Nuova comunicazione da ${communication.sender}`,
          body: communication.title,
          data: {
            type: "communication",
            communicationId: communication.id,
          },
          sound: "default",
          badge: 1,
        },
        trigger: {
          type: "timeInterval",
          seconds: 2,
        } as any,
      });

      this.scheduledNotifications.set(
        `communication_${communication.id}`,
        notificationId
      );
      await this.saveScheduledNotifications();
    } catch (error) {
      console.error("Errore nella programmazione notifica comunicazione:", error);
    }
  }

  /**
   * Programma una notifica per un'assenza non giustificata
   */
  async scheduleAbsenceNotification(absence: {
    id: string;
    date: string;
    type: string;
  }) {
    try {
      const absenceDate = new Date(absence.date);
      const notificationDate = new Date(absenceDate);
      notificationDate.setHours(18, 0, 0, 0); // Notifica alle 18:00 del giorno dell'assenza

      const notificationId = await Notifications.scheduleNotificationAsync({
        content: {
          title: "Assenza non giustificata",
          body: `Hai un'${absence.type} non giustificata del ${absenceDate.toLocaleDateString("it-IT")}`,
          data: {
            type: "absence",
            absenceId: absence.id,
          },
          sound: "default",
          badge: 1,
        },
        trigger: {
          type: "calendar",
          dateComponents: {
            year: notificationDate.getFullYear(),
            month: notificationDate.getMonth() + 1,
            day: notificationDate.getDate(),
            hour: notificationDate.getHours(),
            minute: notificationDate.getMinutes(),
          },
        } as any,
      });

      this.scheduledNotifications.set(`absence_${absence.id}`, notificationId);
      await this.saveScheduledNotifications();
    } catch (error) {
      console.error("Errore nella programmazione notifica assenza:", error);
    }
  }

  /**
   * Cancella una notifica programmata
   */
  async cancelNotification(key: string) {
    try {
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

  /**
   * Cancella tutte le notifiche
   */
  async cancelAllNotifications() {
    try {
      await Notifications.cancelAllScheduledNotificationsAsync();
      this.scheduledNotifications.clear();
      await AsyncStorage.removeItem("scheduled_notifications");
    } catch (error) {
      console.error("Errore nella cancellazione notifiche:", error);
    }
  }

  /**
   * Salva le notifiche programmate in storage
   */
  private async saveScheduledNotifications() {
    try {
      const data = Object.fromEntries(this.scheduledNotifications);
      await AsyncStorage.setItem(
        "scheduled_notifications",
        JSON.stringify(data)
      );
    } catch (error) {
      console.error("Errore nel salvataggio notifiche:", error);
    }
  }

  /**
   * Carica le notifiche programmate da storage
   */
  private async loadScheduledNotifications() {
    try {
      const data = await AsyncStorage.getItem("scheduled_notifications");
      if (data) {
        const parsed = JSON.parse(data);
        this.scheduledNotifications = new Map(Object.entries(parsed));
      }
    } catch (error) {
      console.error("Errore nel caricamento notifiche:", error);
    }
  }

  /**
   * Configura i listener per le notifiche
   */
  private setupNotificationListeners() {
    // Notifica ricevuta mentre l'app è in foreground
    const foregroundSubscription =
      Notifications.addNotificationReceivedListener((notification) => {
        console.log("Notifica ricevuta:", notification);
      });

    // Notifica toccata/aperta
    const responseSubscription =
      Notifications.addNotificationResponseReceivedListener((response) => {
        const { data } = response.notification.request.content;
        console.log("Notifica toccata:", data);
        // Qui puoi navigare a schermate specifiche in base al tipo di notifica
      });

    return () => {
      foregroundSubscription.remove();
      responseSubscription.remove();
    };
  }
}

export const notificationsService = new NotificationsService();
