import {
  AgendaEvent,
  Absence,
  ClassevivaApiError,
  Grade,
  Homework,
  Lesson,
  StudentProfile,
  classeviva,
} from "./classeviva-client";
import {
  generateMockAbsences,
  generateMockGrades,
  generateMockHomeworks,
  generateMockLessons,
  mockStudentProfile,
} from "./mock-data";

export type DataMode = "real" | "demo";
export type CardTone = "neutral" | "primary" | "success" | "warning" | "error";
const DATA_REQUEST_TIMEOUT_MS = 10000;

export interface GradeRowViewModel {
  id: string;
  subject: string;
  valueLabel: string;
  numericValue: number | null;
  date: string;
  dateLabel: string;
  typeLabel: string;
  detail: string;
  tone: CardTone;
}

export interface AgendaItemViewModel {
  id: string;
  title: string;
  subtitle: string;
  date: string;
  dateLabel: string;
  shortDateLabel: string;
  category: "lesson" | "homework" | "assessment" | "event";
  detail: string;
  timeLabel: string;
  tone: CardTone;
}

export interface AbsenceRecordViewModel {
  id: string;
  date: string;
  dateLabel: string;
  type: Absence["type"];
  typeLabel: string;
  justified: boolean;
  statusLabel: string;
  detail: string;
  tone: CardTone;
}

export interface DashboardStat {
  id: string;
  label: string;
  value: string;
  detail: string;
  tone: CardTone;
}

export interface DashboardViewModel {
  headline: string;
  subheadline: string;
  averageLabel: string;
  averageNumeric: number | null;
  warning: string | null;
  stats: DashboardStat[];
  recentGrades: GradeRowViewModel[];
  upcomingItems: AgendaItemViewModel[];
  recentAbsences: AbsenceRecordViewModel[];
}

export interface AgendaSectionViewModel {
  id: string;
  label: string;
  items: AgendaItemViewModel[];
}

type DataSource = {
  getProfile: () => Promise<StudentProfile>;
  getGrades: () => Promise<Grade[]>;
  getAgenda: () => Promise<AgendaItemViewModel[]>;
  getAbsences: () => Promise<AbsenceRecordViewModel[]>;
};

function formatDate(value: string, options: Intl.DateTimeFormatOptions) {
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("it-IT", options).format(date);
}

function withDataTimeout<T>(promise: Promise<T>, label: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(
        new ClassevivaApiError({
          code: "REQUEST_TIMEOUT",
          message: `Tempo scaduto nel caricamento di ${label}.`,
        }),
      );
    }, DATA_REQUEST_TIMEOUT_MS);

    promise
      .then((value) => {
        clearTimeout(timeout);
        resolve(value);
      })
      .catch((error) => {
        clearTimeout(timeout);
        reject(error);
      });
  });
}

function toErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? error.message : fallback;
}

async function resolveDashboardSegment<T>(
  promise: Promise<T>,
  fallback: T,
  fallbackMessage: string,
  warnings: string[],
): Promise<T> {
  try {
    return await promise;
  } catch (error) {
    warnings.push(toErrorMessage(error, fallbackMessage));
    return fallback;
  }
}

function summarizeWarnings(warnings: string[]): string | null {
  if (warnings.length === 0) {
    return null;
  }

  if (warnings.length === 1) {
    return warnings[0];
  }

  return `${warnings[0]} Alcune sezioni non sono ancora sincronizzate.`;
}

function sortByDateDescending<T extends { date: string }>(items: T[]): T[] {
  return [...items].sort((left, right) => {
    return new Date(right.date).getTime() - new Date(left.date).getTime();
  });
}

function sortByDateAscending<T extends { date: string }>(items: T[]): T[] {
  return [...items].sort((left, right) => {
    return new Date(left.date).getTime() - new Date(right.date).getTime();
  });
}

function dedupeAgenda(items: AgendaItemViewModel[]): AgendaItemViewModel[] {
  const seen = new Set<string>();
  const result: AgendaItemViewModel[] = [];

  for (const item of items) {
    const key = `${item.category}:${item.date}:${item.title}:${item.subtitle}`;
    if (seen.has(key)) {
      continue;
    }

    seen.add(key);
    result.push(item);
  }

  return sortByDateAscending(result);
}

function getNumericGrade(grade: Grade["grade"]): number | null {
  if (typeof grade === "number" && Number.isFinite(grade)) {
    return grade;
  }

  if (typeof grade === "string") {
    const parsed = Number(grade.replace(",", "."));
    return Number.isFinite(parsed) ? parsed : null;
  }

  return null;
}

function getGradeTone(value: number | null): CardTone {
  if (value === null) {
    return "neutral";
  }

  if (value >= 8) {
    return "success";
  }

  if (value >= 6) {
    return "primary";
  }

  return "error";
}

function getAgendaTone(category: AgendaItemViewModel["category"]): CardTone {
  switch (category) {
    case "assessment":
      return "warning";
    case "homework":
      return "primary";
    case "lesson":
      return "neutral";
    default:
      return "success";
  }
}

function getAbsenceTone(absence: Absence): CardTone {
  if (!absence.justified) {
    return "error";
  }

  if (absence.type === "ritardo") {
    return "warning";
  }

  if (absence.type === "uscita") {
    return "primary";
  }

  return "neutral";
}

export function toGradeRowViewModel(grade: Grade): GradeRowViewModel {
  const numericValue = getNumericGrade(grade.grade);

  return {
    id: grade.id,
    subject: grade.subject || "Materia",
    valueLabel: String(grade.grade || "--"),
    numericValue,
    date: grade.date,
    dateLabel: formatDate(grade.date, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }),
    typeLabel: grade.type || "Valutazione",
    detail: grade.notes || grade.description || grade.period || "Senza note aggiuntive",
    tone: getGradeTone(numericValue),
  };
}

export function lessonToAgendaItem(lesson: Lesson): AgendaItemViewModel {
  return {
    id: `lesson-${lesson.id}`,
    title: lesson.topic || lesson.subject || "Lezione",
    subtitle: lesson.subject || "Lezione",
    date: lesson.date,
    dateLabel: formatDate(lesson.date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }),
    shortDateLabel: formatDate(lesson.date, { day: "2-digit", month: "short" }),
    category: "lesson",
    detail: lesson.teacher || lesson.room || "Programma lezione",
    timeLabel: lesson.time || "Da definire",
    tone: "neutral",
  };
}

export function homeworkToAgendaItem(homework: Homework): AgendaItemViewModel {
  return {
    id: `homework-${homework.id}`,
    title: homework.description || "Scadenza",
    subtitle: homework.subject || "Compito",
    date: homework.dueDate,
    dateLabel: formatDate(homework.dueDate, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }),
    shortDateLabel: formatDate(homework.dueDate, { day: "2-digit", month: "short" }),
    category: "homework",
    detail: homework.notes || "Da completare",
    timeLabel: "In giornata",
    tone: "primary",
  };
}

export function agendaEventToAgendaItem(event: AgendaEvent): AgendaItemViewModel {
  return {
    id: `agenda-${event.id}`,
    title: event.title || "Evento",
    subtitle: event.subject || "Agenda",
    date: event.date,
    dateLabel: formatDate(event.date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }),
    shortDateLabel: formatDate(event.date, { day: "2-digit", month: "short" }),
    category: event.category,
    detail: event.description || "Dettaglio non disponibile",
    timeLabel: event.time || "In giornata",
    tone: getAgendaTone(event.category),
  };
}

export function toAbsenceRecordViewModel(absence: Absence): AbsenceRecordViewModel {
  const typeLabel =
    absence.type === "ritardo" ? "Ritardo" : absence.type === "uscita" ? "Uscita" : "Assenza";

  return {
    id: absence.id,
    date: absence.date,
    dateLabel: formatDate(absence.date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }),
    type: absence.type,
    typeLabel,
    justified: absence.justified,
    statusLabel: absence.justified ? "Giustificata" : "Da giustificare",
    detail:
      absence.justificationReason ||
      (absence.hours ? `${absence.hours} ore registrate` : "Nessun dettaglio aggiuntivo"),
    tone: getAbsenceTone(absence),
  };
}

function createDemoSource(): DataSource {
  return {
    getProfile: async () => mockStudentProfile,
    getGrades: async () => sortByDateDescending(generateMockGrades()),
    getAgenda: async () => {
      const items = [
        ...generateMockLessons().slice(0, 12).map(lessonToAgendaItem),
        ...generateMockHomeworks().slice(0, 10).map(homeworkToAgendaItem),
      ];

      return dedupeAgenda(items);
    },
    getAbsences: async () => sortByDateDescending(generateMockAbsences()).map(toAbsenceRecordViewModel),
  };
}

async function getRealAgendaItems(): Promise<AgendaItemViewModel[]> {
  const startDate = new Date().toISOString().slice(0, 10);
  const endDate = new Date(Date.now() + 21 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

  const [lessonsResult, homeworksResult, agendaResult] = await Promise.allSettled([
    classeviva.getLessons(startDate, endDate),
    classeviva.getHomeworks(),
    classeviva.getAgenda(startDate, endDate),
  ]);

  const items: AgendaItemViewModel[] = [];
  const errors: Error[] = [];

  if (lessonsResult.status === "fulfilled") {
    items.push(...lessonsResult.value.map(lessonToAgendaItem));
  } else {
    errors.push(lessonsResult.reason as Error);
  }

  if (homeworksResult.status === "fulfilled") {
    items.push(...homeworksResult.value.map(homeworkToAgendaItem));
  } else {
    errors.push(homeworksResult.reason as Error);
  }

  if (agendaResult.status === "fulfilled") {
    items.push(...agendaResult.value.map(agendaEventToAgendaItem));
  } else {
    errors.push(agendaResult.reason as Error);
  }

  if (items.length === 0 && errors.length > 0) {
    throw errors[0];
  }

  return dedupeAgenda(items);
}

function createRealSource(): DataSource {
  return {
    getProfile: async () => withDataTimeout(classeviva.getProfile(), "profilo"),
    getGrades: async () => sortByDateDescending(await withDataTimeout(classeviva.getGrades(), "voti")),
    getAgenda: async () => withDataTimeout(getRealAgendaItems(), "agenda"),
    getAbsences: async () =>
      sortByDateDescending(await withDataTimeout(classeviva.getAbsences(), "assenze")).map(
        toAbsenceRecordViewModel,
      ),
  };
}

function getSource(mode: DataMode): DataSource {
  return mode === "demo" ? createDemoSource() : createRealSource();
}

export async function loadStudentProfile(mode: DataMode): Promise<StudentProfile> {
  return getSource(mode).getProfile();
}

export async function loadGradesView(mode: DataMode): Promise<GradeRowViewModel[]> {
  const grades = await getSource(mode).getGrades();
  return grades.map(toGradeRowViewModel);
}

export async function loadAgendaView(mode: DataMode): Promise<AgendaItemViewModel[]> {
  return getSource(mode).getAgenda();
}

export async function loadAbsencesView(mode: DataMode): Promise<AbsenceRecordViewModel[]> {
  return getSource(mode).getAbsences();
}

export async function loadDashboardView(
  mode: DataMode,
  providedProfile?: StudentProfile | null,
): Promise<DashboardViewModel> {
  const source = getSource(mode);
  const warnings: string[] = [];
  const fallbackProfile = providedProfile ?? (mode === "demo" ? mockStudentProfile : null);
  const [profile, grades, agenda, absences] = await Promise.all([
    resolveDashboardSegment(
      providedProfile ? Promise.resolve(providedProfile) : source.getProfile(),
      fallbackProfile,
      "Non riesco a caricare il profilo.",
      warnings,
    ),
    resolveDashboardSegment(loadGradesView(mode), [] as GradeRowViewModel[], "Non riesco a caricare i voti.", warnings),
    resolveDashboardSegment(source.getAgenda(), [] as AgendaItemViewModel[], "Non riesco a caricare l'agenda.", warnings),
    resolveDashboardSegment(
      source.getAbsences(),
      [] as AbsenceRecordViewModel[],
      "Non riesco a caricare le assenze.",
      warnings,
    ),
  ]);

  if (!profile) {
    throw new Error(summarizeWarnings(warnings) ?? "Non riesco a preparare la dashboard.");
  }

  const numericGrades = grades
    .map((grade) => grade.numericValue)
    .filter((value): value is number => value !== null);
  const averageNumeric =
    numericGrades.length > 0
      ? Number((numericGrades.reduce((sum, value) => sum + value, 0) / numericGrades.length).toFixed(1))
      : null;

  const upcomingItems = agenda.filter((item) => new Date(item.date).getTime() >= Date.now()).slice(0, 4);
  const unjustified = absences.filter((absence) => !absence.justified).length;
  const absenceCount = absences.filter((absence) => absence.type === "assenza").length;
  const homeworkCount = upcomingItems.filter(
    (item) => item.category === "homework" || item.category === "assessment",
  ).length;

  return {
    headline: `Ciao, ${profile.name || "studente"}`,
    subheadline:
      mode === "demo"
        ? "Anteprima curata per web e fallback demo."
        : "Riepilogo sincronizzato dalle sezioni principali di Classeviva.",
    averageLabel: averageNumeric === null ? "--" : averageNumeric.toFixed(1),
    averageNumeric,
    warning: summarizeWarnings(warnings),
    stats: [
      {
        id: "avg",
        label: "Media",
        value: averageNumeric === null ? "--" : averageNumeric.toFixed(1),
        detail: grades.length > 0 ? `${grades.length} valutazioni considerate` : "Nessun voto disponibile",
        tone: averageNumeric !== null && averageNumeric >= 6 ? "success" : "warning",
      },
      {
        id: "upcoming",
        label: "Scadenze",
        value: String(homeworkCount),
        detail: "Compiti o verifiche in arrivo",
        tone: homeworkCount > 0 ? "primary" : "neutral",
      },
      {
        id: "absences",
        label: "Assenze",
        value: String(absenceCount),
        detail: unjustified > 0 ? `${unjustified} da giustificare` : "Situazione allineata",
        tone: unjustified > 0 ? "error" : absenceCount > 0 ? "warning" : "neutral",
      },
    ],
    recentGrades: grades.slice(0, 4),
    upcomingItems,
    recentAbsences: absences.slice(0, 3),
  };
}

export function groupAgendaByDate(items: AgendaItemViewModel[]): AgendaSectionViewModel[] {
  const sections = new Map<string, AgendaItemViewModel[]>();

  for (const item of sortByDateAscending(items)) {
    const current = sections.get(item.date) ?? [];
    current.push(item);
    sections.set(item.date, current);
  }

  return Array.from(sections.entries()).map(([date, sectionItems]) => ({
    id: date,
    label: formatDate(date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }),
    items: sectionItems,
  }));
}
