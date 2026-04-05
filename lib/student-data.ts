import {
  AgendaEvent,
  Absence,
  Communication,
  DidacticAsset,
  DidacticContent,
  DidacticFolder,
  Grade,
  Homework,
  Lesson,
  Note,
  NoteDetail,
  NoticeboardItemDetail,
  Period,
  SchoolReport,
  SchoolbookCourse,
  StudentProfile,
  Subject,
  classeviva,
  type CapabilityState,
  type NoticeboardAction,
  type NoticeboardAttachment,
  type Schoolbook,
} from "./classeviva-client";
import {
  markGradeSeen,
  readSeenGradeIds,
  readSubjectGoals,
  type SubjectGoal,
} from "./app-preferences";
import { addDays, compareDateValues, compareTimeLabels, formatDateLabel as formatDate, toLocalIsoDate } from "./date-utils";

export { compareTimeLabels, toLocalIsoDate } from "./date-utils";

export type CardTone = "neutral" | "primary" | "success" | "warning" | "error";
const DATA_REQUEST_TIMEOUT_MS = 12000;

export interface GradeRowViewModel {
  id: string;
  subject: string;
  valueLabel: string;
  numericValue: number | null;
  weight: number;
  date: string;
  dateLabel: string;
  typeLabel: string;
  detail: string;
  teacherLabel: string;
  periodLabel: string;
  periodKey: string;
  tone: CardTone;
}

export interface GradeSubjectSummaryViewModel {
  subject: string;
  averageLabel: string;
  averageNumeric: number | null;
  weightedAverageLabel: string;
  weightedAverageNumeric: number | null;
  count: number;
  teacherLabel: string;
  recentValues: number[];
  typeBreakdown: string;
  targetAverage: number | null;
  targetAverageLabel: string;
  goalMessage: string;
  goalStatus: "missing" | "required" | "minimum" | "encouraging" | "unreachable";
  tone: CardTone;
}

export interface GradeTrendPoint {
  id: string;
  label: string;
  value: number;
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
  slot?: number;
  tone: CardTone;
}

export interface AgendaSectionViewModel {
  id: string;
  label: string;
  items: AgendaItemViewModel[];
}

export interface CalendarDayViewModel {
  id: string;
  isoDate: string;
  dayLabel: string;
  isCurrentMonth: boolean;
  isToday: boolean;
  count: number;
  tones: CardTone[];
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

export interface CommunicationRowViewModel {
  id: string;
  pubId: string;
  evtCode: string;
  title: string;
  preview: string;
  sender: string;
  date: string;
  dateLabel: string;
  statusLabel: string;
  metadataLabel: string;
  needsAck: boolean;
  needsReply: boolean;
  needsJoin: boolean;
  hasAttachments: boolean;
  attachments: NoticeboardAttachment[];
  tone: CardTone;
}

export interface NoteRowViewModel {
  id: string;
  categoryCode: string;
  title: string;
  preview: string;
  author: string;
  date: string;
  dateLabel: string;
  badgeLabel: string;
  tone: CardTone;
}

export interface MaterialContentViewModel {
  id: string;
  title: string;
  teacherLabel: string;
  folderLabel: string;
  dateLabel: string;
  objectType: string;
  capabilityState: CapabilityState;
  tone: CardTone;
}

export interface SchoolbookViewModel {
  id: string;
  title: string;
  subtitle: string;
  subject: string;
  authorLabel: string;
  priceLabel: string;
  statusLabel: string;
  coverUrl?: string;
  tone: CardTone;
}

export interface SchoolbookCourseViewModel {
  id: string;
  title: string;
  books: SchoolbookViewModel[];
}

export interface SchoolReportViewModel {
  id: string;
  title: string;
  detail: string;
  viewUrl?: string;
  capabilityState: CapabilityState;
  tone: CardTone;
}

export interface DashboardStat {
  id: string;
  label: string;
  value: string;
  detail: string;
  tone: CardTone;
}

export interface GradeDistributionViewModel {
  insufficient: number;
  sufficient: number;
  good: number;
  veryGood: number;
  excellent: number;
}

export interface GradeSummariesViewModel {
  averageNumeric: number | null;
  averageLabel: string;
  subjectSummaries: GradeSubjectSummaryViewModel[];
  trend: GradeTrendPoint[];
  gradeDistribution: GradeDistributionViewModel;
}

export interface DashboardViewModel {
  headline: string;
  subheadline: string;
  averageLabel: string;
  averageNumeric: number | null;
  warning: string | null;
  todayLessons: AgendaItemViewModel[];
  unseenGrades: GradeRowViewModel[];
  unreadCommunications: CommunicationRowViewModel[];
}

export interface GradeSelectionSummary {
  rows: GradeRowViewModel[];
  summaries: GradeSummariesViewModel;
  currentPeriodCode: string | null;
  periods: Period[];
  goals: Record<string, SubjectGoal>;
}

export interface CommunicationDetailViewModel extends NoticeboardItemDetail {
  actions: NoticeboardAction[];
}

export interface ProfileSnapshotViewModel {
  profile: StudentProfile;
  subjects: Subject[];
  periods: Period[];
}

function formatCurrency(value?: number): string {
  if (typeof value !== "number" || Number.isNaN(value)) {
    return "Prezzo non disponibile";
  }

  return new Intl.NumberFormat("it-IT", {
    style: "currency",
    currency: "EUR",
  }).format(value);
}

function withDataTimeout<T>(promise: Promise<T>, label: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error(`Tempo scaduto nel caricamento di ${label}.`));
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

function sortByDateDescending<T extends { date: string }>(items: T[]): T[] {
  return [...items].sort((left, right) => compareDateValues(right.date, left.date));
}

function sortByDateAscending<T extends { date: string }>(items: T[]): T[] {
  return [...items].sort((left, right) => compareDateValues(left.date, right.date));
}

function summarizeWarnings(warnings: string[]): string | null {
  if (warnings.length === 0) {
    return null;
  }

  if (warnings.length === 1) {
    return warnings[0];
  }

  return `${warnings[0]} Alcune sezioni sono state caricate parzialmente.`;
}

function toErrorMessage(error: unknown, fallback: string): string {
  return error instanceof Error && error.message.trim().length > 0 ? error.message : fallback;
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

  if (value > 6) {
    return "success";
  }

  if (value >= 5) {
    return "warning";
  }

  return value < 5 ? "error" : "primary";
}

function normalizePeriodKey(value?: string | null): string {
  return (value ?? "")
    .normalize("NFD")
    .replace(/[\u0300-\u036f]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, " ")
    .trim();
}

export function getComparablePeriodKeys(period: Pick<Period, "code" | "label" | "description">): string[] {
  const keys = [period.code, period.label, period.description]
    .map(normalizePeriodKey)
    .filter((value) => value.length > 0);

  return Array.from(new Set(keys));
}

export function doesGradeMatchPeriod(
  grade: Pick<GradeRowViewModel, "periodKey" | "periodLabel">,
  period: Pick<Period, "code" | "label" | "description">,
): boolean {
  const gradeKeys = new Set([grade.periodKey, normalizePeriodKey(grade.periodLabel)].filter(Boolean));
  return getComparablePeriodKeys(period).some((key) => gradeKeys.has(key));
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
  if (!absence.justified && absence.type === "assenza") {
    return "error";
  }

  if (!absence.justified) {
    return "warning";
  }

  return "neutral";
}

function formatHoursLabel(hours?: number): string {
  if (!hours) {
    return "Nessun dettaglio aggiuntivo";
  }

  return `${hours} ${hours === 1 ? "ora" : "ore"} registrate`;
}

function formatDidacticObjectType(value: string): string {
  const normalized = value.trim().toLowerCase();
  if (!normalized) {
    return "Contenuto";
  }
  if (normalized.includes("link")) {
    return "Collegamento";
  }
  if (normalized.includes("file") || normalized.includes("pdf")) {
    return "File";
  }
  if (normalized.includes("video")) {
    return "Video";
  }

  return value;
}

function escapeRegExp(value: string): string {
  return value.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function formatAverageLabel(value: number | null): string {
  return value === null ? "--" : value.toFixed(1).replace(".", ",");
}

function isWithinDateRange(date: string, start: string, end: string): boolean {
  return compareDateValues(date, start) >= 0 && compareDateValues(date, end) <= 0;
}

function sanitizeAgendaText(value: string | undefined, fallback: string, candidates: string[] = []): string {
  if (!value) {
    return fallback;
  }

  let output = value
    .replace(/^\s*\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d+)?(?:Z|[+\-]\d{2}:\d{2})\s*/i, "")
    .replace(/^\s*\d{4}-\d{2}-\d{2}\s*/i, "")
    .trim();

  for (const candidate of candidates.filter(Boolean)) {
    output = output.replace(new RegExp(`^${escapeRegExp(candidate)}\\s*[:\\-./)]?\\s*`, "i"), "").trim();
  }

  output = output.replace(/\s+/g, " ").trim();
  return output || fallback;
}

function calculateWeightedAverage(rows: GradeRowViewModel[]): number | null {
  const weighted = rows
    .filter((row) => row.numericValue !== null)
    .map((row) => ({
      value: row.numericValue as number,
      weight: row.weight > 0 ? row.weight : 1,
    }));

  if (weighted.length === 0) {
    return null;
  }

  const totalWeight = weighted.reduce((sum, row) => sum + row.weight, 0);
  if (totalWeight <= 0) {
    return null;
  }

  const totalValue = weighted.reduce((sum, row) => sum + row.value * row.weight, 0);
  return Number((totalValue / totalWeight).toFixed(1));
}

function resolveGoalSummary(
  rows: GradeRowViewModel[],
  targetAverage: number | null,
): Pick<
  GradeSubjectSummaryViewModel,
  "targetAverage" | "targetAverageLabel" | "goalMessage" | "goalStatus" | "weightedAverageLabel" | "weightedAverageNumeric"
> {
  const weightedAverage = calculateWeightedAverage(rows);

  if (targetAverage === null) {
    return {
      weightedAverageNumeric: weightedAverage,
      weightedAverageLabel: formatAverageLabel(weightedAverage),
      targetAverage: null,
      targetAverageLabel: "--",
      goalMessage: "Imposta una media obiettivo per ricevere il calcolo del prossimo voto utile.",
      goalStatus: "missing",
    };
  }

  const numericRows = rows.filter((row) => row.numericValue !== null);
  const currentTotal = numericRows.reduce((sum, row) => sum + (row.numericValue as number) * row.weight, 0);
  const currentWeight = numericRows.reduce((sum, row) => sum + row.weight, 0);
  const requiredValue = Number((targetAverage * (currentWeight + 1) - currentTotal).toFixed(1));

  if (requiredValue <= 0) {
    return {
      weightedAverageNumeric: weightedAverage,
      weightedAverageLabel: formatAverageLabel(weightedAverage),
      targetAverage,
      targetAverageLabel: formatAverageLabel(targetAverage),
      goalMessage: "Non ti preoccupare!",
      goalStatus: "encouraging",
    };
  }

  if (requiredValue > 10) {
    return {
      weightedAverageNumeric: weightedAverage,
      weightedAverageLabel: formatAverageLabel(weightedAverage),
      targetAverage,
      targetAverageLabel: formatAverageLabel(targetAverage),
      goalMessage: "Media non raggiungibile con un singolo voto",
      goalStatus: "unreachable",
    };
  }

  if (weightedAverage !== null && weightedAverage >= targetAverage) {
    return {
      weightedAverageNumeric: weightedAverage,
      weightedAverageLabel: formatAverageLabel(weightedAverage),
      targetAverage,
      targetAverageLabel: formatAverageLabel(targetAverage),
      goalMessage: `Non prendere meno di ${requiredValue.toFixed(1).replace(".", ",")}`,
      goalStatus: "minimum",
    };
  }

  return {
    weightedAverageNumeric: weightedAverage,
    weightedAverageLabel: formatAverageLabel(weightedAverage),
    targetAverage,
    targetAverageLabel: formatAverageLabel(targetAverage),
    goalMessage: `Devi prendere almeno ${requiredValue.toFixed(1).replace(".", ",")} per raggiungere la media desiderata`,
    goalStatus: "required",
  };
}

export function getCurrentPeriod(periods: Period[], referenceDate = toLocalIsoDate()): Period | null {
  const sortedPeriods = [...periods].sort((left, right) => left.order - right.order);
  const current =
    sortedPeriods.find(
      (period) =>
        compareDateValues(period.startDate, referenceDate) <= 0 &&
        compareDateValues(period.endDate, referenceDate) >= 0,
    ) ?? null;

  if (current) {
    return current;
  }

  const future = sortedPeriods.find((period) => compareDateValues(period.startDate, referenceDate) >= 0);
  return future ?? sortedPeriods[sortedPeriods.length - 1] ?? null;
}

export async function markGradeRowAsSeen(studentId: string, row: Pick<GradeRowViewModel, "id">): Promise<void> {
  await markGradeSeen(studentId, row.id);
}

export function toGradeRowViewModel(grade: Grade): GradeRowViewModel {
  const numericValue = getNumericGrade(grade.grade);
  const periodLabel = grade.period || "Periodo non indicato";
  const valueLabel =
    numericValue !== null
      ? Number.isInteger(numericValue)
        ? String(numericValue)
        : numericValue.toFixed(1).replace(".", ",")
      : String(grade.grade || "--");

  return {
    id: grade.id,
    subject: grade.subject || "Materia non indicata",
    valueLabel,
    numericValue,
    date: grade.date,
    dateLabel: formatDate(grade.date, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }, "Data non disponibile"),
    weight: grade.weight ?? 1,
    typeLabel: grade.type || "Valutazione",
    detail: grade.notes || grade.description || "Nessuna nota disponibile",
    teacherLabel: grade.teacher || "Docente non indicato",
    periodLabel,
    periodKey: normalizePeriodKey(periodLabel),
    tone: getGradeTone(numericValue),
  };
}

export function summarizeGrades(
  rows: GradeRowViewModel[],
  goals: Record<string, SubjectGoal> = {},
): GradeSummariesViewModel {
  const numericValues = rows
    .map((row) => row.numericValue)
    .filter((value): value is number => value !== null);
  const averageNumeric =
    numericValues.length > 0
      ? Number((numericValues.reduce((sum, value) => sum + value, 0) / numericValues.length).toFixed(1))
      : null;

  const grouped = new Map<string, GradeRowViewModel[]>();
  for (const row of rows) {
    const current = grouped.get(row.subject) ?? [];
    current.push(row);
    grouped.set(row.subject, current);
  }

  const subjectSummaries = Array.from(grouped.entries())
    .map(([subject, items]) => {
      const values = items
        .map((item) => item.numericValue)
        .filter((value): value is number => value !== null);
      const subjectAverage =
        values.length > 0
          ? Number((values.reduce((sum, value) => sum + value, 0) / values.length).toFixed(1))
          : null;
      const recentValues = sortByDateAscending(items)
        .filter((item) => item.numericValue !== null)
        .slice(-4)
        .map((item) => item.numericValue as number);
      const writtenTypes = ["scritto", "compito", "compito in classe", "written"];
      const oralTypes = ["orale", "interrogazione", "oral"];
      const written = items.filter((item) =>
        writtenTypes.some((type) => item.typeLabel.toLowerCase().includes(type)),
      ).length;
      const oral = items.filter((item) =>
        oralTypes.some((type) => item.typeLabel.toLowerCase().includes(type)),
      ).length;
      const other = items.length - written - oral;
      const parts: string[] = [];

      if (written > 0) {
        parts.push(`${written} ${written === 1 ? "scritto" : "scritti"}`);
      }
      if (oral > 0) {
        parts.push(`${oral} ${oral === 1 ? "orale" : "orali"}`);
      }
      if (other > 0) {
        parts.push(`${other} ${other === 1 ? "altro" : "altri"}`);
      }

      const goalSummary = resolveGoalSummary(items, goals[subject]?.targetAverage ?? null);

      return {
        subject,
        averageLabel: formatAverageLabel(subjectAverage),
        averageNumeric: subjectAverage,
        count: items.length,
        teacherLabel: items[0]?.teacherLabel ?? "Docente non indicato",
        recentValues,
        typeBreakdown: parts.length > 0 ? parts.join(" / ") : `${items.length} valutazioni`,
        weightedAverageLabel: goalSummary.weightedAverageLabel,
        weightedAverageNumeric: goalSummary.weightedAverageNumeric,
        targetAverage: goalSummary.targetAverage,
        targetAverageLabel: goalSummary.targetAverageLabel,
        goalMessage: goalSummary.goalMessage,
        goalStatus: goalSummary.goalStatus,
        tone: getGradeTone(subjectAverage),
      } satisfies GradeSubjectSummaryViewModel;
    })
    .sort((left, right) => left.subject.localeCompare(right.subject, "it-IT"));

  const trend = sortByDateAscending(rows)
    .filter((row) => row.numericValue !== null)
    .slice(-8)
    .map((row) => ({
      id: row.id,
      label: row.subject.slice(0, 3).toUpperCase(),
      value: row.numericValue ?? 0,
      tone: row.tone,
    }));

  return {
    averageNumeric,
    averageLabel: formatAverageLabel(averageNumeric),
    subjectSummaries,
    trend,
    gradeDistribution: {
      insufficient: numericValues.filter((value) => value < 6).length,
      sufficient: numericValues.filter((value) => value >= 6 && value < 7).length,
      good: numericValues.filter((value) => value >= 7 && value < 8).length,
      veryGood: numericValues.filter((value) => value >= 8 && value < 9).length,
      excellent: numericValues.filter((value) => value >= 9).length,
    },
  };
}

export function lessonToAgendaItem(lesson: Lesson): AgendaItemViewModel {
  const title = lesson.topic || lesson.subject || "Lezione";
  const subtitle = lesson.subject || "Lezione";

  return {
    id: `lesson-${lesson.id}`,
    title,
    subtitle,
    date: lesson.date,
    dateLabel: formatDate(lesson.date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }, "Data non disponibile"),
    shortDateLabel: formatDate(lesson.date, { day: "2-digit", month: "short" }, "Data"),
    category: "lesson",
    detail: sanitizeAgendaText(
      [lesson.teacher, lesson.room].filter(Boolean).join(" / "),
      "Dettagli lezione non disponibili",
      [title, subtitle],
    ),
    timeLabel:
      lesson.time && lesson.endTime
        ? `${lesson.time} - ${lesson.endTime}`
        : lesson.time || (lesson.slot ? `${lesson.slot}a ora` : "Orario da confermare"),
    slot: lesson.slot,
    tone: "neutral",
  };
}

export function homeworkToAgendaItem(homework: Homework): AgendaItemViewModel {
  const title = sanitizeAgendaText(homework.description, "Scadenza");
  const subtitle = homework.subject || "Compito";

  return {
    id: `homework-${homework.id}`,
    title,
    subtitle,
    date: homework.dueDate,
    dateLabel: formatDate(homework.dueDate, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }, "Data non disponibile"),
    shortDateLabel: formatDate(homework.dueDate, { day: "2-digit", month: "short" }, "Data"),
    category: "homework",
    detail: sanitizeAgendaText(homework.notes, "Da completare", [title, subtitle]),
    timeLabel: "In giornata",
    tone: "primary",
  };
}

export function agendaEventToAgendaItem(event: AgendaEvent): AgendaItemViewModel {
  const title = sanitizeAgendaText(event.title, "Evento");
  const subtitle = event.subject || "Agenda";

  return {
    id: `agenda-${event.id}`,
    title,
    subtitle,
    date: event.date,
    dateLabel: formatDate(event.date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }, "Data non disponibile"),
    shortDateLabel: formatDate(event.date, { day: "2-digit", month: "short" }, "Data"),
    category: event.category,
    detail: sanitizeAgendaText(event.rawDescription ?? event.description, "Dettaglio non disponibile", [
      title,
      subtitle,
    ]),
    timeLabel: event.time || (event.slot ? `${event.slot}a ora` : "In giornata"),
    slot: event.slot,
    tone: getAgendaTone(event.category),
  };
}

export function toAbsenceRecordViewModel(absence: Absence): AbsenceRecordViewModel {
  const typeLabel =
    absence.type === "ritardo" ? "Ritardo" : absence.type === "uscita" ? "Uscita anticipata" : "Assenza";

  return {
    id: absence.id,
    date: absence.date,
    dateLabel: formatDate(absence.date, {
      weekday: "long",
      day: "numeric",
      month: "long",
    }, "Data non disponibile"),
    type: absence.type,
    typeLabel,
    justified: absence.justified,
    statusLabel: absence.justified ? "Giustificata" : "Da giustificare",
    detail: absence.justificationReason || formatHoursLabel(absence.hours),
    tone: getAbsenceTone(absence),
  };
}

export function toCommunicationRowViewModel(communication: Communication): CommunicationRowViewModel {
  const actionLabels = [
    communication.needsAck ? "Conferma" : null,
    communication.needsJoin ? "Adesione" : null,
    communication.needsReply ? "Risposta" : null,
  ].filter(Boolean);

  return {
    id: communication.id,
    pubId: communication.pubId,
    evtCode: communication.evtCode,
    title: communication.title,
    preview: communication.content || communication.category || "Dettaglio disponibile in lettura.",
    sender: communication.sender,
    date: communication.date,
    dateLabel: formatDate(communication.date, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }, "Data non disponibile"),
    statusLabel: communication.read ? "Letta" : "Da leggere",
    metadataLabel:
      actionLabels.length > 0
        ? actionLabels.join(" / ")
        : communication.hasAttachments
          ? "Con allegati"
          : communication.sender || "Comunicazione",
    needsAck: communication.needsAck,
    needsReply: communication.needsReply,
    needsJoin: communication.needsJoin,
    hasAttachments: communication.hasAttachments,
    attachments: communication.attachments,
    tone: communication.read ? "neutral" : "primary",
  };
}

export function toNoteRowViewModel(note: Note): NoteRowViewModel {
  return {
    id: note.id,
    categoryCode: note.categoryCode,
    title: note.title,
    preview: note.contentPreview,
    author: note.author,
    date: note.date,
    dateLabel: formatDate(note.date, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }, "Data non disponibile"),
    badgeLabel: note.categoryLabel,
    tone: note.severity === "critical" ? "error" : note.severity === "warning" ? "warning" : "neutral",
  };
}

export function toMaterialContentViewModel(content: DidacticContent): MaterialContentViewModel {
  return {
    id: content.id,
    title: content.title,
    teacherLabel: content.teacherName,
    folderLabel: content.folderName,
    dateLabel: formatDate(content.sharedAt, {
      day: "2-digit",
      month: "short",
      year: "numeric",
    }, "Data non disponibile"),
    objectType: formatDidacticObjectType(content.objectType),
    capabilityState: content.capabilityState,
    tone: content.capabilityState.status === "external_only" ? "primary" : "neutral",
  };
}

export function toSchoolbookViewModel(book: Schoolbook): SchoolbookViewModel {
  const statusLabel = book.toBuy
    ? "Da acquistare"
    : book.alreadyOwned
      ? "Gia in possesso"
      : book.recommended
        ? "Consigliato"
        : "In uso";

  return {
    id: book.id,
    title: book.title,
    subtitle: book.subtitle || book.volume || book.publisher || "Scheda libro",
    subject: book.subject,
    authorLabel: book.author || "Autore non indicato",
    priceLabel: formatCurrency(book.price),
    statusLabel,
    coverUrl: book.coverUrl,
    tone: book.toBuy ? "warning" : book.recommended ? "primary" : "neutral",
  };
}

export function toSchoolbookCourseViewModel(course: SchoolbookCourse): SchoolbookCourseViewModel {
  return {
    id: course.id,
    title: course.title,
    books: course.books.map(toSchoolbookViewModel),
  };
}

export function toSchoolReportViewModel(report: SchoolReport): SchoolReportViewModel {
  return {
    id: report.id,
    title: report.title,
    detail: report.capabilityState.detail || "Documento disponibile nel portale.",
    viewUrl: report.viewUrl,
    capabilityState: report.capabilityState,
    tone: report.capabilityState.status === "external_only" ? "primary" : "neutral",
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
    items: [...sectionItems].sort((left, right) => {
      const slotDiff = (left.slot ?? Number.MAX_SAFE_INTEGER) - (right.slot ?? Number.MAX_SAFE_INTEGER);
      return slotDiff !== 0 ? slotDiff : compareTimeLabels(left.timeLabel, right.timeLabel);
    }),
  }));
}

export function buildCalendarMonth(monthDate: Date, items: AgendaItemViewModel[]): CalendarDayViewModel[] {
  const year = monthDate.getFullYear();
  const month = monthDate.getMonth();
  const firstOfMonth = new Date(year, month, 1);
  const startDay = (firstOfMonth.getDay() + 6) % 7;
  const gridStart = new Date(year, month, 1 - startDay);
  const todayIso = toLocalIsoDate();

  const itemMap = new Map<string, AgendaItemViewModel[]>();
  for (const item of items) {
    const current = itemMap.get(item.date) ?? [];
    current.push(item);
    itemMap.set(item.date, current);
  }

  return Array.from({ length: 42 }, (_, index) => {
    const date = new Date(gridStart);
    date.setDate(gridStart.getDate() + index);
    const isoDate = toLocalIsoDate(date);
    const dayItems = itemMap.get(isoDate) ?? [];

    return {
      id: `${isoDate}-${index}`,
      isoDate,
      dayLabel: String(date.getDate()),
      isCurrentMonth: date.getMonth() === month,
      isToday: isoDate === todayIso,
      count: dayItems.length,
      tones: Array.from(new Set(dayItems.slice(0, 3).map((item) => item.tone))),
    };
  });
}

export async function loadStudentProfile(): Promise<StudentProfile> {
  return withDataTimeout(classeviva.getProfile(), "profilo");
}

export async function loadGradesView(): Promise<GradeRowViewModel[]> {
  const grades = await withDataTimeout(classeviva.getGrades(), "voti");
  return sortByDateDescending(grades).map(toGradeRowViewModel);
}

export async function loadGradeSelectionSummary(studentId: string): Promise<GradeSelectionSummary> {
  const [rows, periods, goals] = await Promise.all([
    loadGradesView(),
    withDataTimeout(classeviva.getPeriods(), "periodi").catch(() => [] as Period[]),
    readSubjectGoals(studentId),
  ]);
  const sortedPeriods = [...periods].sort((left, right) => left.order - right.order);
  const currentPeriod = getCurrentPeriod(sortedPeriods);

  return {
    rows,
    summaries: summarizeGrades(rows, goals),
    currentPeriodCode: currentPeriod?.code ?? null,
    periods: sortedPeriods,
    goals,
  };
}

export async function loadAgendaView(startDate?: string, endDate?: string): Promise<AgendaItemViewModel[]> {
  const safeStart = startDate ?? toLocalIsoDate();
  const safeEnd = endDate ?? toLocalIsoDate(addDays(new Date(), 21));

  const [lessonsResult, homeworksResult, agendaResult] = await Promise.allSettled([
    withDataTimeout(classeviva.getLessons(safeStart, safeEnd), "lezioni"),
    withDataTimeout(classeviva.getHomeworks(), "compiti"),
    withDataTimeout(classeviva.getAgenda(safeStart, safeEnd), "agenda"),
  ]);

  const items: AgendaItemViewModel[] = [];
  const errors: string[] = [];

  if (lessonsResult.status === "fulfilled") {
    items.push(...lessonsResult.value.map(lessonToAgendaItem));
  } else {
    errors.push(toErrorMessage(lessonsResult.reason, "Le lezioni non sono disponibili."));
  }

  if (homeworksResult.status === "fulfilled") {
    items.push(...homeworksResult.value.map(homeworkToAgendaItem));
  } else {
    errors.push(toErrorMessage(homeworksResult.reason, "I compiti non sono disponibili."));
  }

  if (agendaResult.status === "fulfilled") {
    items.push(...agendaResult.value.map(agendaEventToAgendaItem));
  } else {
    errors.push(toErrorMessage(agendaResult.reason, "L'agenda non e disponibile."));
  }

  if (items.length === 0 && errors.length > 0) {
    throw new Error(errors[0]);
  }

  const seen = new Set<string>();
  return sortByDateAscending(
    items.filter((item) => {
      if (!isWithinDateRange(item.date, safeStart, safeEnd)) {
        return false;
      }

      const key = `${item.category}:${item.date}:${item.title}:${item.subtitle}`;
      if (seen.has(key)) {
        return false;
      }

      seen.add(key);
      return true;
    }),
  );
}

export async function loadAbsencesView(): Promise<AbsenceRecordViewModel[]> {
  const absences = await withDataTimeout(classeviva.getAbsences(), "assenze");
  return sortByDateDescending(absences).map(toAbsenceRecordViewModel);
}

export async function loadCommunicationsView(): Promise<CommunicationRowViewModel[]> {
  const items = await withDataTimeout(classeviva.getCommunications(), "comunicazioni");
  return sortByDateDescending(items).map(toCommunicationRowViewModel);
}

export async function loadCommunicationDetailView(
  communication: Pick<CommunicationRowViewModel, "pubId" | "evtCode">,
): Promise<NoticeboardItemDetail> {
  return withDataTimeout(
    classeviva.getCommunicationDetail(communication),
    "dettaglio comunicazione",
  );
}

export async function acknowledgeCommunicationView(
  communication: Pick<CommunicationRowViewModel, "pubId" | "evtCode">,
): Promise<NoticeboardItemDetail> {
  return withDataTimeout(classeviva.acknowledgeCommunication(communication), "conferma comunicazione");
}

export async function joinCommunicationView(
  communication: Pick<CommunicationRowViewModel, "pubId" | "evtCode">,
): Promise<NoticeboardItemDetail> {
  return withDataTimeout(classeviva.joinCommunication(communication), "adesione comunicazione");
}

export async function loadNotesView(): Promise<NoteRowViewModel[]> {
  const notes = await withDataTimeout(classeviva.getNotes(), "note");
  return notes.map(toNoteRowViewModel);
}

export async function loadNoteDetailView(note: Pick<NoteRowViewModel, "id" | "categoryCode">): Promise<NoteDetail> {
  return withDataTimeout(classeviva.getNoteDetail(note), "dettaglio nota");
}

export async function loadMaterialsView(): Promise<DidacticFolder[]> {
  return withDataTimeout(classeviva.getDidactics(), "materiale didattico");
}

export async function loadMaterialAssetView(
  content: Pick<DidacticContent, "id" | "title" | "objectType" | "sourceUrl">,
): Promise<DidacticAsset> {
  return withDataTimeout(classeviva.getDidacticAsset(content), "materiale");
}

export async function loadSchoolbooksView(): Promise<SchoolbookCourseViewModel[]> {
  const courses = await withDataTimeout(classeviva.getSchoolbooks(), "libri");
  return courses.map(toSchoolbookCourseViewModel);
}

export async function loadReportCardsView(): Promise<SchoolReportViewModel[]> {
  const reports = await withDataTimeout(classeviva.getSchoolReports(), "pagelle");
  return reports.map(toSchoolReportViewModel);
}

export async function loadProfileSnapshot(user?: StudentProfile | null): Promise<ProfileSnapshotViewModel> {
  const [profile, subjects, periods] = await Promise.all([
    user ? Promise.resolve(user) : loadStudentProfile(),
    withDataTimeout(classeviva.getSubjects(), "materie"),
    withDataTimeout(classeviva.getPeriods(), "periodi"),
  ]);

  return {
    profile,
    subjects: subjects.sort((left, right) => left.order - right.order),
    periods: periods.sort((left, right) => left.order - right.order),
  };
}

export async function loadDashboardView(providedProfile?: StudentProfile | null): Promise<DashboardViewModel> {
  const warnings: string[] = [];
  const todayIso = toLocalIsoDate();

  const [profileResult, gradesResult, agendaResult, communicationsResult] =
    await Promise.allSettled([
      providedProfile ? Promise.resolve(providedProfile) : loadStudentProfile(),
      loadGradesView(),
      loadAgendaView(),
      loadCommunicationsView(),
    ]);

  const profile =
    profileResult.status === "fulfilled"
      ? profileResult.value
      : (() => {
          warnings.push(toErrorMessage(profileResult.reason, "Non riesco a caricare il profilo."));
          return providedProfile ?? null;
        })();

  const grades =
    gradesResult.status === "fulfilled"
      ? gradesResult.value
      : (warnings.push(toErrorMessage(gradesResult.reason, "Non riesco a caricare i voti.")), []);
  const agenda =
    agendaResult.status === "fulfilled"
      ? agendaResult.value
      : (warnings.push(toErrorMessage(agendaResult.reason, "Non riesco a caricare l'agenda.")), []);
  const communications =
    communicationsResult.status === "fulfilled"
      ? communicationsResult.value
      : (warnings.push(toErrorMessage(communicationsResult.reason, "Non riesco a caricare le comunicazioni.")), []);

  if (!profile) {
    throw new Error(summarizeWarnings(warnings) ?? "Non riesco a preparare la dashboard.");
  }

  const [seenGradeIds, goals] = await Promise.all([readSeenGradeIds(profile.id), readSubjectGoals(profile.id)]);
  const seenGradeSet = new Set(seenGradeIds);
  const gradeSummary = summarizeGrades(grades, goals);
  const todayLessons = agenda
    .filter((item) => item.category === "lesson" && item.date === todayIso)
    .sort((left, right) => compareTimeLabels(left.timeLabel, right.timeLabel));
  const unseenGrades = grades.filter((grade) => !seenGradeSet.has(grade.id)).slice(0, 6);
  const unreadCommunications = communications.filter((item) => item.statusLabel === "Da leggere");

  return {
    headline: `Ciao, ${profile.name || "studente"}`,
    subheadline: "Solo cio che ti serve adesso: lezioni di oggi, nuovi voti e bacheca non letta.",
    averageLabel: gradeSummary.averageLabel,
    averageNumeric: gradeSummary.averageNumeric,
    warning: summarizeWarnings(warnings),
    todayLessons,
    unseenGrades,
    unreadCommunications: unreadCommunications.slice(0, 3),
  };
}
