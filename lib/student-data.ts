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
  type Schoolbook,
} from "./classeviva-client";

export type CardTone = "neutral" | "primary" | "success" | "warning" | "error";
const DATA_REQUEST_TIMEOUT_MS = 12000;

export interface GradeRowViewModel {
  id: string;
  subject: string;
  valueLabel: string;
  numericValue: number | null;
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
  count: number;
  teacherLabel: string;
  recentValues: number[];
  typeBreakdown: string;
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
  stats: DashboardStat[];
  todayLessons: AgendaItemViewModel[];
  recentGrades: GradeRowViewModel[];
  upcomingAssessments: AgendaItemViewModel[];
  upcomingItems: AgendaItemViewModel[];
  recentAbsences: AbsenceRecordViewModel[];
  unreadCommunications: CommunicationRowViewModel[];
  highlightedNotes: NoteRowViewModel[];
  gradeTrend: GradeTrendPoint[];
  schoolReports: SchoolReportViewModel[];
}

export interface ProfileSnapshotViewModel {
  profile: StudentProfile;
  subjects: Subject[];
  periods: Period[];
}

const LOCAL_ISO_DATE_RE = /^\d{4}-\d{2}-\d{2}$/;

function parseDateValue(value: string): Date {
  if (LOCAL_ISO_DATE_RE.test(value)) {
    const [year, month, day] = value.split("-").map(Number);
    return new Date(year, month - 1, day);
  }

  return new Date(value);
}

export function toLocalIsoDate(date = new Date()): string {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
}

function addDays(date: Date, days: number): Date {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function compareDateValues(left: string, right: string): number {
  if (LOCAL_ISO_DATE_RE.test(left) && LOCAL_ISO_DATE_RE.test(right)) {
    return left.localeCompare(right);
  }

  return parseDateValue(left).getTime() - parseDateValue(right).getTime();
}

function parseTimeValue(value: string): number | null {
  const match = value.match(/(\d{1,2}):(\d{2})/);
  if (!match) {
    return null;
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
    return null;
  }

  return hours * 60 + minutes;
}

export function compareTimeLabels(left: string, right: string): number {
  const leftValue = parseTimeValue(left);
  const rightValue = parseTimeValue(right);

  if (leftValue !== null && rightValue !== null) {
    return leftValue - rightValue;
  }
  if (leftValue !== null) {
    return -1;
  }
  if (rightValue !== null) {
    return 1;
  }

  return left.localeCompare(right, "it-IT");
}

function formatDate(value: string, options: Intl.DateTimeFormatOptions) {
  const date = parseDateValue(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }

  return new Intl.DateTimeFormat("it-IT", options).format(date);
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

  if (value >= 8) {
    return "success";
  }

  if (value >= 6) {
    return "primary";
  }

  return "error";
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
  const periodLabel = grade.period || "Periodo non indicato";

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
    detail: grade.notes || grade.description || "Senza note aggiuntive",
    teacherLabel: grade.teacher || "Docente non indicato",
    periodLabel,
    periodKey: normalizePeriodKey(periodLabel),
    tone: getGradeTone(numericValue),
  };
}

export function summarizeGrades(rows: GradeRowViewModel[]): GradeSummariesViewModel {
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

      return {
        subject,
        averageLabel: subjectAverage === null ? "--" : subjectAverage.toFixed(1),
        averageNumeric: subjectAverage,
        count: items.length,
        teacherLabel: items[0]?.teacherLabel ?? "Docente non indicato",
        recentValues,
        typeBreakdown: parts.join(" · ") || `${items.length} valutazioni`,
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
    averageLabel: averageNumeric === null ? "--" : averageNumeric.toFixed(1),
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

export function toCommunicationRowViewModel(communication: Communication): CommunicationRowViewModel {
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
    }),
    statusLabel: communication.read ? "Letta" : "Da leggere",
    metadataLabel: communication.hasAttachments
      ? "Con allegati"
      : communication.needsAck || communication.needsReply
        ? "Richiede attenzione"
        : "Comunicazione",
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
    }),
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
    }),
    objectType: content.objectType,
    capabilityState: content.capabilityState,
    tone: content.capabilityState.status === "external_only" ? "primary" : "neutral",
  };
}

export function toSchoolbookViewModel(book: Schoolbook): SchoolbookViewModel {
  const statusLabel = book.toBuy
    ? "Da acquistare"
    : book.alreadyOwned
      ? "Gia tuo"
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
    items: sectionItems,
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

export async function loadMaterialAssetView(content: Pick<DidacticContent, "id" | "title" | "objectType">): Promise<DidacticAsset> {
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

  const [profileResult, gradesResult, agendaResult, absencesResult, communicationsResult, notesResult, reportsResult] =
    await Promise.allSettled([
      providedProfile ? Promise.resolve(providedProfile) : loadStudentProfile(),
      loadGradesView(),
      loadAgendaView(),
      loadAbsencesView(),
      loadCommunicationsView(),
      loadNotesView(),
      loadReportCardsView(),
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
  const absences =
    absencesResult.status === "fulfilled"
      ? absencesResult.value
      : (warnings.push(toErrorMessage(absencesResult.reason, "Non riesco a caricare le assenze.")), []);
  const communications =
    communicationsResult.status === "fulfilled"
      ? communicationsResult.value
      : (warnings.push(toErrorMessage(communicationsResult.reason, "Non riesco a caricare le comunicazioni.")), []);
  const notes =
    notesResult.status === "fulfilled"
      ? notesResult.value
      : (warnings.push(toErrorMessage(notesResult.reason, "Non riesco a caricare le note.")), []);
  const reports =
    reportsResult.status === "fulfilled"
      ? reportsResult.value
      : (warnings.push(toErrorMessage(reportsResult.reason, "Non riesco a caricare le pagelle.")), []);

  if (!profile) {
    throw new Error(summarizeWarnings(warnings) ?? "Non riesco a preparare la dashboard.");
  }

  const gradeSummary = summarizeGrades(grades);
  const todayLessons = agenda
    .filter((item) => item.category === "lesson" && item.date === todayIso)
    .sort((left, right) => compareTimeLabels(left.timeLabel, right.timeLabel));
  const upcomingAssessments = agenda
    .filter((item) => item.category === "assessment" && compareDateValues(item.date, todayIso) >= 0)
    .slice(0, 3);
  const upcomingItems = agenda.filter((item) => compareDateValues(item.date, todayIso) >= 0).slice(0, 4);
  const unjustified = absences.filter((absence) => !absence.justified).length;
  const unreadCommunications = communications.filter((item) => item.statusLabel === "Da leggere");
  const booksToBuy = reports.length > 0 ? "Pagelle disponibili" : "Nessun documento recente";

  return {
    headline: `Ciao, ${profile.name || "studente"}`,
    subheadline: "Registro live, leggibile e organizzato per uso quotidiano.",
    averageLabel: gradeSummary.averageLabel,
    averageNumeric: gradeSummary.averageNumeric,
    warning: summarizeWarnings(warnings),
    stats: [
      {
        id: "avg",
        label: "Media",
        value: gradeSummary.averageLabel,
        detail: grades.length > 0 ? `${grades.length} valutazioni sincronizzate` : "Nessun voto disponibile",
        tone: gradeSummary.averageNumeric !== null && gradeSummary.averageNumeric >= 6 ? "success" : "warning",
      },
      {
        id: "agenda",
        label: "Scadenze",
        value: String(upcomingItems.filter((item) => item.category !== "lesson").length),
        detail: upcomingItems.length > 0 ? "Compiti, verifiche o eventi in arrivo" : "Nessuna urgenza oggi",
        tone: upcomingItems.some((item) => item.category === "assessment") ? "warning" : "primary",
      },
      {
        id: "absences",
        label: "Assenze",
        value: String(absences.filter((item) => item.type === "assenza").length),
        detail: unjustified > 0 ? `${unjustified} da giustificare` : "Situazione allineata",
        tone: unjustified > 0 ? "error" : "neutral",
      },
      {
        id: "messages",
        label: "Novita",
        value: String(unreadCommunications.length),
        detail: booksToBuy,
        tone: unreadCommunications.length > 0 ? "primary" : "neutral",
      },
    ],
    todayLessons,
    recentGrades: grades.slice(0, 4),
    upcomingAssessments,
    upcomingItems,
    recentAbsences: absences.slice(0, 3),
    unreadCommunications: unreadCommunications.slice(0, 3),
    highlightedNotes: notes.slice(0, 2),
    gradeTrend: gradeSummary.trend,
    schoolReports: reports.slice(0, 2),
  };
}
