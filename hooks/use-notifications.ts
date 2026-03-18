/**
 * Hook custom per gestire le notifiche in Classeviva Expressive
 */

import { useEffect } from "react";
import { notificationsService } from "@/lib/notifications-service";

interface Homework {
  id: string;
  subject: string;
  description: string;
  dueDate: string;
}

interface Communication {
  id: string;
  title: string;
  sender: string;
}

interface Absence {
  id: string;
  date: string;
  type: string;
  justified: boolean;
}

export function useNotifications() {
  /**
   * Programma notifiche per i compiti
   */
  const scheduleHomeworkNotifications = async (homeworks: Homework[]) => {
    for (const homework of homeworks) {
      await notificationsService.scheduleHomeworkNotification(homework);
    }
  };

  /**
   * Programma notifiche per le comunicazioni
   */
  const scheduleCommunicationNotifications = async (
    communications: Communication[]
  ) => {
    for (const communication of communications) {
      await notificationsService.scheduleCommunicationNotification(communication);
    }
  };

  /**
   * Programma notifiche per le assenze non giustificate
   */
  const scheduleAbsenceNotifications = async (absences: Absence[]) => {
    const unjustifiedAbsences = absences.filter((a) => !a.justified);
    for (const absence of unjustifiedAbsences) {
      await notificationsService.scheduleAbsenceNotification(absence);
    }
  };

  /**
   * Cancella tutte le notifiche e riprogramma quelle attuali
   */
  const refreshNotifications = async (
    homeworks: Homework[],
    communications: Communication[],
    absences: Absence[]
  ) => {
    await notificationsService.cancelAllNotifications();
    await scheduleHomeworkNotifications(homeworks);
    await scheduleCommunicationNotifications(communications);
    await scheduleAbsenceNotifications(absences);
  };

  return {
    scheduleHomeworkNotifications,
    scheduleCommunicationNotifications,
    scheduleAbsenceNotifications,
    refreshNotifications,
  };
}
