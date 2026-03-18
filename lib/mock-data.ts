/**
 * Mock Data Generator per Classeviva
 * Genera dati realistici per il testing senza credenziali reali
 */

import {
  StudentProfile,
  Grade,
  Lesson,
  Homework,
  Absence,
  Communication,
} from "./classeviva-client";

const SUBJECTS = [
  "Italiano",
  "Matematica",
  "Inglese",
  "Storia",
  "Geografia",
  "Scienze",
  "Educazione Fisica",
  "Arte",
  "Musica",
  "Informatica",
  "Fisica",
  "Chimica",
];

const TEACHERS = [
  "Prof. Rossi",
  "Prof. Bianchi",
  "Prof. Verdi",
  "Prof. Ferrari",
  "Prof. Russo",
  "Prof. Gallo",
  "Prof. Conti",
  "Prof. De Luca",
];

const HOMEWORK_DESCRIPTIONS = [
  "Esercizi pagina 45-50",
  "Leggere capitolo 3",
  "Preparare presentazione",
  "Risolvere problemi 1-10",
  "Analizzare testo",
  "Ricerca su tema",
  "Compiti di matematica",
  "Studio per verifica",
];

const COMMUNICATION_TITLES = [
  "Comunicazione importante",
  "Avviso genitori",
  "Cambio orario lezioni",
  "Gita scolastica",
  "Riunione genitori",
  "Risultati scrutinio",
  "Avviso assenze",
  "Comunicazione dirigente",
];

export const mockStudentProfile: StudentProfile = {
  id: "student-demo-001",
  name: "Marco",
  surname: "Rossi",
  email: "marco.rossi@example.com",
  class: "5",
  section: "A",
  school: "Liceo Scientifico Galileo Galilei",
  schoolYear: "2025-2026",
};

export function generateMockGrades(): Grade[] {
  const grades: Grade[] = [];
  const now = new Date();

  SUBJECTS.forEach((subject, index) => {
    for (let i = 0; i < 4; i++) {
      const daysAgo = Math.floor(Math.random() * 60) + i * 15;
      const date = new Date(now.getTime() - daysAgo * 24 * 60 * 60 * 1000);

      grades.push({
        id: `grade-${subject}-${i}`,
        subject,
        grade: Math.floor(Math.random() * 4) + 6, // 6-10
        description: ["Buono", "Ottimo", "Discreto", "Sufficiente"][
          Math.floor(Math.random() * 4)
        ],
        date: date.toISOString().split("T")[0],
        type: ["Compito", "Interrogazione", "Verifica", "Progetto"][
          Math.floor(Math.random() * 4)
        ],
        weight: Math.random() > 0.5 ? 1 : 2,
        notes: Math.random() > 0.7 ? "Buon lavoro!" : undefined,
      });
    }
  });

  return grades.sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
  );
}

export function generateMockLessons(): Lesson[] {
  const lessons: Lesson[] = [];
  const now = new Date();

  for (let day = 0; day < 30; day++) {
    const date = new Date(now.getTime() + day * 24 * 60 * 60 * 1000);
    const dayOfWeek = date.getDay();

    // No lessons on weekends
    if (dayOfWeek === 0 || dayOfWeek === 6) continue;

    for (let hour = 0; hour < 6; hour++) {
      const subject = SUBJECTS[Math.floor(Math.random() * SUBJECTS.length)];
      const time = `${8 + hour}:00`;

      lessons.push({
        id: `lesson-${date.toISOString().split("T")[0]}-${hour}`,
        subject,
        date: date.toISOString().split("T")[0],
        time,
        duration: 60,
        topic: `Lezione di ${subject}`,
      });
    }
  }

  return lessons;
}

export function generateMockHomeworks(): Homework[] {
  const homeworks: Homework[] = [];
  const now = new Date();

  for (let i = 0; i < 15; i++) {
    const daysFromNow = Math.floor(Math.random() * 30) + 1;
    const dueDate = new Date(now.getTime() + daysFromNow * 24 * 60 * 60 * 1000);
    const subject = SUBJECTS[Math.floor(Math.random() * SUBJECTS.length)];

    homeworks.push({
      id: `homework-${i}`,
      subject,
      description:
        HOMEWORK_DESCRIPTIONS[
          Math.floor(Math.random() * HOMEWORK_DESCRIPTIONS.length)
        ],
      dueDate: dueDate.toISOString().split("T")[0],
      notes: Math.random() > 0.6 ? "Importante!" : undefined,
      attachments: Math.random() > 0.7 ? ["documento.pdf"] : [],
    });
  }

  return homeworks.sort(
    (a, b) => new Date(a.dueDate).getTime() - new Date(b.dueDate).getTime()
  );
}

export function generateMockAbsences(): Absence[] {
  const absences: Absence[] = [];
  const now = new Date();

  // Aggiungi alcune assenze
  for (let i = 0; i < 8; i++) {
    const daysAgo = Math.floor(Math.random() * 90);
    const date = new Date(now.getTime() - daysAgo * 24 * 60 * 60 * 1000);

    absences.push({
      id: `absence-${i}`,
      date: date.toISOString().split("T")[0],
      type: ["assenza", "ritardo", "uscita"][Math.floor(Math.random() * 3)] as
        | "assenza"
        | "ritardo"
        | "uscita",
      hours: Math.random() > 0.5 ? 6 : undefined,
      justified: Math.random() > 0.4,
      justificationDate:
        Math.random() > 0.4
          ? new Date(now.getTime() - (daysAgo - 1) * 24 * 60 * 60 * 1000)
              .toISOString()
              .split("T")[0]
          : undefined,
      justificationReason:
        Math.random() > 0.5 ? "Malattia" : "Motivi personali",
    });
  }

  return absences.sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
  );
}

export function generateMockCommunications(): Communication[] {
  const communications: Communication[] = [];
  const now = new Date();

  for (let i = 0; i < 10; i++) {
    const daysAgo = Math.floor(Math.random() * 30);
    const date = new Date(now.getTime() - daysAgo * 24 * 60 * 60 * 1000);

    communications.push({
      id: `comm-${i}`,
      title:
        COMMUNICATION_TITLES[
          Math.floor(Math.random() * COMMUNICATION_TITLES.length)
        ],
      content: `Contenuto della comunicazione numero ${i + 1}. Questa è una comunicazione importante dalla scuola.`,
      sender: TEACHERS[Math.floor(Math.random() * TEACHERS.length)],
      date: date.toISOString().split("T")[0],
      read: Math.random() > 0.4,
      attachments: Math.random() > 0.7 ? ["allegato.pdf"] : [],
    });
  }

  return communications.sort(
    (a, b) => new Date(b.date).getTime() - new Date(a.date).getTime()
  );
}

export interface MockReportCard {
  id: string;
  period: string; // "Trimestre 1", "Pentamestre"
  date: string;
  grades: Array<{
    subject: string;
    grade: number;
    teacher: string;
  }>;
  overallGrade?: string;
  credits?: number;
}

export function generateMockReportCards(): MockReportCard[] {
  return [
    {
      id: "report-1",
      period: "Trimestre",
      date: "2025-12-15",
      grades: SUBJECTS.map((subject) => ({
        subject,
        grade: Math.floor(Math.random() * 4) + 6,
        teacher: TEACHERS[Math.floor(Math.random() * TEACHERS.length)],
      })),
      overallGrade: "Buono",
      credits: 8,
    },
    {
      id: "report-2",
      period: "Pentamestre",
      date: "2024-06-10",
      grades: SUBJECTS.map((subject) => ({
        subject,
        grade: Math.floor(Math.random() * 4) + 6,
        teacher: TEACHERS[Math.floor(Math.random() * TEACHERS.length)],
      })),
      overallGrade: "Discreto",
      credits: 7,
    },
  ];
}

export interface MockDisciplinaryNote {
  id: string;
  date: string;
  teacher: string;
  description: string;
  severity: "low" | "medium" | "high";
}

export function generateMockDisciplinaryNotes(): MockDisciplinaryNote[] {
  return [
    {
      id: "note-1",
      date: "2026-02-15",
      teacher: "Prof. Rossi",
      description: "Ritardo ingiustificato all'inizio della lezione",
      severity: "low",
    },
    {
      id: "note-2",
      date: "2026-01-20",
      teacher: "Prof. Bianchi",
      description: "Comportamento scorretto durante la lezione",
      severity: "medium",
    },
  ];
}

export interface MockSchedule {
  day: string;
  hours: Array<{
    time: string;
    subject: string;
    teacher: string;
    room: string;
  }>;
}

export function generateMockSchedule(): MockSchedule[] {
  const days = [
    "Lunedì",
    "Martedì",
    "Mercoledì",
    "Giovedì",
    "Venerdì",
  ];
  const schedule: MockSchedule[] = [];

  days.forEach((day) => {
    const hours = [];
    for (let i = 0; i < 6; i++) {
      hours.push({
        time: `${8 + i}:00 - ${9 + i}:00`,
        subject: SUBJECTS[Math.floor(Math.random() * SUBJECTS.length)],
        teacher: TEACHERS[Math.floor(Math.random() * TEACHERS.length)],
        room: `Aula ${Math.floor(Math.random() * 20) + 1}`,
      });
    }
    schedule.push({ day, hours });
  });

  return schedule;
}
