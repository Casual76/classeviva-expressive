import axios, { AxiosError, AxiosInstance, isAxiosError } from "axios";

import { addDays, formatTimeLabel, toLocalIsoDate } from "./date-utils";

const API_BASE_URL = "https://web.spaggiari.eu/rest/v1";
const API_KEY = "Tg1NWEwNGIgIC0K";
const USER_AGENT = "CVVS/std/4.2.3 Android/12";
const BASE64_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";

type UnknownRecord = Record<string, unknown>;

export interface LoginCredentials {
  username: string;
  password: string;
}

export interface AuthToken {
  token: string;
  expiresAt: number;
  profileHint?: StudentProfile;
}

export interface StudentProfile {
  id: string;
  name: string;
  surname: string;
  email: string;
  class: string;
  section: string;
  school: string;
  schoolYear: string;
}

export interface Grade {
  id: string;
  subject: string;
  grade: number | string;
  description?: string;
  date: string;
  type: string;
  weight?: number;
  notes?: string;
  period?: string;
  teacher?: string;
  color?: string;
}

export interface Lesson {
  id: string;
  subject: string;
  date: string;
  time: string;
  duration: number;
  topic?: string;
  teacher?: string;
  room?: string;
  endTime?: string;
  slot?: number;
  rawDate?: string;
  rawTime?: string;
}

export interface Homework {
  id: string;
  subject: string;
  description: string;
  dueDate: string;
  notes?: string;
  attachments?: string[];
}

export interface Absence {
  id: string;
  date: string;
  type: "assenza" | "ritardo" | "uscita";
  hours?: number;
  justified: boolean;
  justificationDate?: string;
  justificationReason?: string;
}

export interface Communication {
  id: string;
  pubId: string;
  evtCode: string;
  title: string;
  content: string;
  sender: string;
  date: string;
  read: boolean;
  attachments: NoticeboardAttachment[];
  category?: string;
  needsAck: boolean;
  needsReply: boolean;
  needsJoin: boolean;
  needsFile: boolean;
  hasAttachments: boolean;
}

export interface CapabilityState {
  status: "available" | "empty" | "external_only" | "unavailable";
  label: string;
  detail?: string;
}

export interface NoticeboardAttachment {
  id: string;
  title: string;
  fileName: string;
  url: string | null;
  mimeType: string | null;
  capabilityState: CapabilityState;
}

export interface NoticeboardAction {
  id: string;
  type: "ack" | "join" | "reply" | "download";
  label: string;
  enabled: boolean;
  detail?: string;
}

export interface NoticeboardItemDetail {
  id: string;
  pubId: string;
  evtCode: string;
  title: string;
  content: string;
  replyText?: string;
  attachments: NoticeboardAttachment[];
  actions: NoticeboardAction[];
  capabilityState: CapabilityState;
}

export interface Note {
  id: string;
  categoryCode: string;
  categoryLabel: string;
  title: string;
  contentPreview: string;
  date: string;
  author: string;
  read: boolean;
  severity: "info" | "warning" | "critical";
}

export interface NoteDetail extends Note {
  content: string;
}

export interface DidacticContent {
  id: string;
  teacherId: string;
  teacherName: string;
  folderId: string;
  folderName: string;
  title: string;
  objectId: string;
  objectType: string;
  sharedAt: string;
  sourceUrl?: string | null;
  capabilityState: CapabilityState;
}

export interface DidacticFolder {
  id: string;
  teacherId: string;
  teacherName: string;
  title: string;
  sharedAt: string;
  contents: DidacticContent[];
}

export interface DidacticAsset {
  id: string;
  title: string;
  objectType: string;
  mimeType: string | null;
  dataUrl: string | null;
  sourceUrl: string | null;
  textPreview: string | null;
  capabilityState: CapabilityState;
}

export interface Schoolbook {
  id: string;
  isbn: string;
  title: string;
  subtitle?: string;
  volume?: string;
  author?: string;
  publisher?: string;
  subject: string;
  price?: number;
  coverUrl?: string;
  toBuy: boolean;
  alreadyOwned: boolean;
  alreadyInUse: boolean;
  recommended: boolean;
  recommendedFor?: string;
  newAdoption: boolean;
}

export interface SchoolbookCourse {
  id: string;
  title: string;
  books: Schoolbook[];
}

export interface SchoolReport {
  id: string;
  title: string;
  confirmUrl?: string;
  viewUrl?: string;
  capabilityState: CapabilityState;
}

export interface Period {
  code: string;
  order: number;
  description: string;
  label: string;
  isFinal: boolean;
  startDate: string;
  endDate: string;
}

export interface Subject {
  id: string;
  description: string;
  order: number;
  teachers: string[];
}

export interface AgendaEvent {
  id: string;
  date: string;
  title: string;
  description?: string;
  subject: string;
  category: "lesson" | "homework" | "assessment" | "event";
  time?: string;
  rawDate?: string;
  rawDescription?: string;
  slot?: number;
}

export interface ApiError {
  code: string;
  message: string;
  details?: unknown;
  status?: number;
}

export class ClassevivaApiError extends Error implements ApiError {
  code: string;
  details?: unknown;
  status?: number;

  constructor({ code, message, details, status }: ApiError) {
    super(message);
    this.name = "ClassevivaApiError";
    this.code = code;
    this.details = details;
    this.status = status;
  }
}

function isRecord(value: unknown): value is UnknownRecord {
  return typeof value === "object" && value !== null;
}

function toRecord(value: unknown): UnknownRecord {
  return isRecord(value) ? value : {};
}

function pickString(...values: unknown[]): string | undefined {
  for (const value of values) {
    if (typeof value === "string" && value.trim().length > 0) {
      return value.trim();
    }

    if (typeof value === "number" && Number.isFinite(value)) {
      return String(value);
    }
  }

  return undefined;
}

function pickNumber(...values: unknown[]): number | undefined {
  for (const value of values) {
    if (typeof value === "number" && Number.isFinite(value)) {
      return value;
    }

    if (typeof value === "string" && value.trim().length > 0) {
      const parsed = Number(value);
      if (Number.isFinite(parsed)) {
        return parsed;
      }
    }
  }

  return undefined;
}

function pickBoolean(...values: unknown[]): boolean | undefined {
  for (const value of values) {
    if (typeof value === "boolean") {
      return value;
    }

    if (typeof value === "number") {
      return value > 0;
    }

    if (typeof value === "string") {
      const normalized = value.trim().toLowerCase();
      if (["true", "1", "yes", "read", "letto", "done"].includes(normalized)) {
        return true;
      }

      if (["false", "0", "no", "unread", "nonletto"].includes(normalized)) {
        return false;
      }
    }
  }

  return undefined;
}

function normalizeStudentId(value: unknown): string | undefined {
  const raw = pickString(value);
  if (!raw) {
    return undefined;
  }

  const prefixedMatch = raw.match(/^[SG](\d+)(?:[A-Z]+)?$/i);
  return prefixedMatch ? prefixedMatch[1] : raw;
}

function normalizeDate(value: unknown): string {
  const parsed = pickString(value);
  if (!parsed) {
    return toLocalIsoDate();
  }

  const date = new Date(parsed);
  return Number.isNaN(date.getTime()) ? toLocalIsoDate() : toLocalIsoDate(date);
}

function normalizeTime(value: unknown): string | undefined {
  const raw = pickString(value);
  if (!raw) {
    return undefined;
  }

  if (/^\d{2}:\d{2}$/.test(raw)) {
    return raw;
  }

  return formatTimeLabel(raw, "Orario da confermare");
}

function deriveSlot(...values: unknown[]): number | undefined {
  for (const value of values) {
    const numeric = pickNumber(value);
    if (typeof numeric === "number" && Number.isFinite(numeric)) {
      return Math.max(1, Math.round(numeric));
    }
  }

  return undefined;
}

function deriveEndTime(startTime: string | undefined, durationMinutes?: number): string | undefined {
  if (!startTime || !durationMinutes) {
    return undefined;
  }

  const match = startTime.match(/^(\d{1,2}):(\d{2})$/);
  if (!match) {
    return undefined;
  }

  const hours = Number(match[1]);
  const minutes = Number(match[2]);
  if (!Number.isFinite(hours) || !Number.isFinite(minutes)) {
    return undefined;
  }

  const totalMinutes = hours * 60 + minutes + durationMinutes;
  const endHours = Math.floor(totalMinutes / 60);
  const endMinutes = totalMinutes % 60;
  return `${String(endHours).padStart(2, "0")}:${String(endMinutes).padStart(2, "0")}`;
}

function toApiDateParam(value: string): string {
  return value.replace(/-/g, "");
}

function normalizeSchoolYear(value?: string): string {
  if (value && value.trim().length > 0) {
    return value;
  }

  const today = new Date();
  const year = today.getFullYear();
  const startYear = today.getMonth() >= 7 ? year : year - 1;
  return `${startYear}-${startYear + 1}`;
}

function normalizeQuotedText(value?: string): string {
  if (!value) {
    return "";
  }

  return value.replace(/^"+|"+$/g, "").trim();
}

function joinSchoolName(parts: unknown[]): string {
  return parts
    .map((part) => normalizeQuotedText(pickString(part)))
    .filter((part): part is string => Boolean(part))
    .join(" ")
    .replace(/\s+/g, " ")
    .trim();
}

function extractArray(payload: unknown, keys: string[]): unknown[] {
  if (Array.isArray(payload)) {
    return payload;
  }

  const record = toRecord(payload);
  for (const key of keys) {
    const candidate = record[key];
    if (Array.isArray(candidate)) {
      return candidate;
    }
  }

  return [];
}

function deriveAbsenceHours(value: unknown): number | undefined {
  if (Array.isArray(value)) {
    return value.length > 0 ? value.length : undefined;
  }

  return pickNumber(value);
}

function parseExpire(value: unknown): number {
  const expireString = pickString(value);
  if (!expireString) {
    return Date.now() + 3 * 60 * 60 * 1000;
  }

  const parsed = new Date(expireString).getTime();
  return Number.isNaN(parsed) ? Date.now() + 3 * 60 * 60 * 1000 : parsed;
}

function extractErrorMessage(details: unknown): string {
  const record = toRecord(details);
  return pickString(record.error, record.message, details) ?? "";
}

function isRejectedApiKey(details: unknown): boolean {
  const source = extractErrorMessage(details).toLowerCase();
  return source.includes("cvvrestapi/apikey.3") || source.includes("apikey");
}

function normalizeAbsenceType(rawType: unknown, evtCode?: unknown): Absence["type"] {
  const value = pickString(rawType, evtCode)?.toLowerCase() ?? "";

  if (value.includes("rit")) {
    return "ritardo";
  }

  if (value.includes("usc") || value.includes("exit")) {
    return "uscita";
  }

  return "assenza";
}

function normalizeAgendaCategory(rawType: unknown, rawTitle: unknown): AgendaEvent["category"] {
  const source = [pickString(rawType), pickString(rawTitle)].filter(Boolean).join(" ").toLowerCase();

  if (source.includes("compit") || source.includes("homework")) {
    return "homework";
  }

  if (source.includes("verific") || source.includes("test") || source.includes("interrog")) {
    return "assessment";
  }

  if (source.includes("lezion") || source.includes("lesson")) {
    return "lesson";
  }

  return "event";
}

function createCapabilityState(
  status: CapabilityState["status"],
  label: string,
  detail?: string,
): CapabilityState {
  return { status, label, detail };
}

function toAbsoluteUrl(value: unknown): string | null {
  const raw = pickString(value);
  if (!raw) {
    return null;
  }

  if (/^(https?:|data:)/i.test(raw)) {
    return raw;
  }

  try {
    return new URL(raw, `${API_BASE_URL}/`).toString();
  } catch {
    return raw.startsWith("/") ? `https://web.spaggiari.eu${raw}` : null;
  }
}

function normalizeAttachment(value: unknown, index: number): NoticeboardAttachment | null {
  if (typeof value === "string") {
    const url = toAbsoluteUrl(value);
    const fileName = value.trim() || `Allegato ${index + 1}`;

    return {
      id: `attachment-${index}-${fileName}`,
      title: fileName,
      fileName,
      url,
      mimeType: null,
      capabilityState: url
        ? createCapabilityState("external_only", "Apribile esternamente", "Il file verra aperto fuori dall'app.")
        : createCapabilityState("unavailable", "Link mancante", "Il portale non ha restituito un link scaricabile."),
    };
  }

  const record = toRecord(value);
  const fileName =
    pickString(record.name, record.fileName, record.attachName, record.title, record.description) ??
    `Allegato ${index + 1}`;
  const url = toAbsoluteUrl(
    pickString(
      record.url,
      record.href,
      record.downloadUrl,
      record.downloadLink,
      record.viewUrl,
      record.link,
      record.path,
      record.src,
    ),
  );
  const mimeType = pickString(record.mimeType, record.contentType, record.type) ?? null;

  return {
    id: pickString(record.id, record.attachId, record.objectId) ?? `attachment-${index}-${fileName}`,
    title: fileName,
    fileName,
    url,
    mimeType,
    capabilityState: url
      ? createCapabilityState("external_only", "Apribile esternamente", "Il file verra aperto o scaricato fuori dall'app.")
      : createCapabilityState("unavailable", "Link mancante", "Il portale non ha restituito un link scaricabile."),
  };
}

function normalizeAttachments(value: unknown): NoticeboardAttachment[] {
  if (!Array.isArray(value)) {
    return [];
  }

  return value
    .map((item, index) => normalizeAttachment(item, index))
    .filter((item): item is NoticeboardAttachment => Boolean(item));
}

function collectAttachments(...values: unknown[]): NoticeboardAttachment[] {
  const attachments = values.flatMap((value) => normalizeAttachments(value));
  const seen = new Set<string>();

  return attachments.filter((attachment) => {
    const key = `${attachment.fileName}:${attachment.url ?? "missing"}`;
    if (seen.has(key)) {
      return false;
    }

    seen.add(key);
    return true;
  });
}

function buildNoticeboardActions(base: Pick<Communication, "needsAck" | "needsJoin" | "needsReply">, attachments: NoticeboardAttachment[]): NoticeboardAction[] {
  const actions: NoticeboardAction[] = [];

  if (base.needsAck) {
    actions.push({
      id: "ack",
      type: "ack",
      label: "Conferma",
      enabled: true,
      detail: "Invia la conferma richiesta dal portale.",
    });
  }

  if (base.needsJoin) {
    actions.push({
      id: "join",
      type: "join",
      label: "Aderisci",
      enabled: true,
      detail: "Registra l'adesione richiesta dalla comunicazione.",
    });
  }

  if (base.needsReply) {
    actions.push({
      id: "reply",
      type: "reply",
      label: "Risposta",
      enabled: false,
      detail: "Il portale richiede una risposta: se il testo e gia disponibile lo mostro nel dettaglio.",
    });
  }

  if (attachments.some((attachment) => attachment.url)) {
    actions.push({
      id: "download",
      type: "download",
      label: "Scarica file",
      enabled: true,
      detail: "Apri o scarica gli allegati disponibili.",
    });
  }

  return actions;
}

function normalizeReadStatus(value: unknown): boolean {
  return pickBoolean(value) ?? false;
}

function getNoteCategoryLabel(code: string): string {
  switch (code) {
    case "NTTE":
      return "Annotazione docente";
    case "NTCL":
      return "Annotazione";
    case "NTWN":
      return "Richiamo";
    case "NTST":
      return "Nota disciplinare";
    default:
      return "Nota";
  }
}

function getNoteSeverity(code: string): Note["severity"] {
  switch (code) {
    case "NTST":
      return "critical";
    case "NTWN":
      return "warning";
    case "NTTE":
      return "warning";
    default:
      return "info";
  }
}

function createPreviewText(value: string | undefined, fallback: string): string {
  if (!value) {
    return fallback;
  }

  return value.length > 150 ? `${value.slice(0, 147)}...` : value;
}

function arrayBufferToBase64(buffer: ArrayBuffer): string {
  const bytes = new Uint8Array(buffer);
  let output = "";

  for (let index = 0; index < bytes.length; index += 3) {
    const byte1 = bytes[index] ?? 0;
    const byte2 = bytes[index + 1] ?? 0;
    const byte3 = bytes[index + 2] ?? 0;

    const triplet = (byte1 << 16) | (byte2 << 8) | byte3;

    output += BASE64_CHARS[(triplet >> 18) & 0x3f];
    output += BASE64_CHARS[(triplet >> 12) & 0x3f];
    output += index + 1 < bytes.length ? BASE64_CHARS[(triplet >> 6) & 0x3f] : "=";
    output += index + 2 < bytes.length ? BASE64_CHARS[triplet & 0x3f] : "=";
  }

  return output;
}

function decodeAsciiPreview(buffer: ArrayBuffer): string | null {
  try {
    const bytes = new Uint8Array(buffer).slice(0, 512);
    const preview = String.fromCharCode(...bytes).replace(/\0/g, "").trim();
    return preview.length > 0 ? preview : null;
  } catch {
    return null;
  }
}

export function normalizeProfile(payload: unknown, fallbackId?: string): StudentProfile {
  const root = toRecord(payload);
  const data = toRecord(root.card ?? root);
  const school = joinSchoolName([
    data.schName,
    data.schDedication,
    toRecord(data.scuola).desc,
    data.school,
  ]);

  const rawClass = pickString(data.classDesc, toRecord(data.classe).desc, data.class) ?? "";
  const rawSection = pickString(toRecord(data.classe).sezione, data.section);

  return {
    id:
      normalizeStudentId(data.usrId) ??
      normalizeStudentId(data.id) ??
      normalizeStudentId(data.ident) ??
      normalizeStudentId(fallbackId) ??
      "",
    name: pickString(data.firstName, data.name) ?? "",
    surname: pickString(data.lastName, data.surname) ?? "",
    email: pickString(data.email) ?? "",
    class: rawClass,
    section: rawSection ?? "",
    school,
    schoolYear: normalizeSchoolYear(pickString(data.anno, data.schoolYear)),
  };
}

export function normalizeGrade(payload: unknown): Grade {
  const data = toRecord(payload);
  const numericGrade = pickNumber(data.decimalValue, data.votoDecimale, data.grade);

  return {
    id: pickString(data.id, data.evtId) ?? "",
    subject: pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ?? "",
    grade: numericGrade ?? pickString(data.displayValue, data.grade) ?? "",
    description: pickString(data.descrVoto, data.description),
    date: normalizeDate(data.evtDate ?? data.dataRegistrazione ?? data.date),
    type: pickString(data.componentDesc, toRecord(data.tipo).desc, data.type) ?? "Valutazione",
    weight: pickNumber(data.weightFactor, data.peso, data.weight),
    notes: pickString(data.notesForFamily, data.note, data.notes),
    period: pickString(data.periodDesc, data.periodLabel, data.period),
    teacher: pickString(data.teacherName, data.teacher),
    color: pickString(data.color),
  };
}

export function normalizeLesson(payload: unknown): Lesson {
  const data = toRecord(payload);
  const time = normalizeTime(data.lessonHour ?? data.ora ?? data.time ?? data.startTime);
  const duration = pickNumber(data.duration, data.durata, data.evtDuration) ?? 60;

  return {
    id: pickString(data.id, data.lessonId, data.evtId) ?? "",
    subject: pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ?? "",
    date: normalizeDate(data.data ?? data.date ?? data.evtDate),
    time: time ?? "",
    duration,
    topic: pickString(data.argomento, data.topic, data.lessonArg),
    teacher: pickString(data.teacherName, data.authorName, data.teacher),
    room: pickString(data.classroom, data.room),
    endTime: deriveEndTime(time, duration),
    slot: deriveSlot(data.evtHPos, data.lessonNum, data.lessonPos, data.hour, data.order),
    rawDate: pickString(data.data, data.date, data.evtDate),
    rawTime: pickString(data.lessonHour, data.ora, data.time, data.startTime),
  };
}

export function normalizeHomework(payload: unknown): Homework {
  const data = toRecord(payload);

  return {
    id: pickString(data.id, data.hwId, data.evtId, data.homeworkId) ?? "",
    subject: pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ?? "",
    description: pickString(data.contenuto, data.description, data.notes, data.title) ?? "",
    dueDate: normalizeDate(
      data.dataConsegna ?? data.dueDate ?? data.date ?? data.evtDate ?? data.evtDatetimeEnd,
    ),
    notes: pickString(data.note, data.notesForFamily, data.notes),
    attachments: collectAttachments(data.allegati, data.attachments).map((attachment) => attachment.fileName),
  };
}

export function normalizeAbsence(payload: unknown): Absence {
  const data = toRecord(payload);

  return {
    id: pickString(data.id, data.evtId, data.absenceId) ?? "",
    date: normalizeDate(data.evtDate ?? data.data ?? data.date),
    type: normalizeAbsenceType(data.tipo, data.evtCode),
    hours: deriveAbsenceHours(data.hoursAbsence ?? data.ore ?? data.hours),
    justified: pickBoolean(data.isJustified, data.giustificata, data.justified) ?? false,
    justificationDate: pickString(data.dataGiustificazione, data.justificationDate),
    justificationReason: pickString(data.justifReasonDesc, data.motivoGiustificazione, data.justificationReason),
  };
}

export function normalizeCommunication(payload: unknown): Communication {
  const data = toRecord(payload);
  const attachments = collectAttachments(data.attachments, data.allegati, data.files);
  const fallbackAttachmentUrl = toAbsoluteUrl(
    pickString(data.fileUrl, data.downloadUrl, data.downloadLink, data.attachUrl, data.link),
  );
  const enrichedAttachments =
    attachments.length > 0 || !fallbackAttachmentUrl
      ? attachments
      : [
          {
            id: `attachment-${pickString(data.id, data.pubId) ?? "noticeboard"}`,
            title: "Allegato",
            fileName: "Allegato",
            url: fallbackAttachmentUrl,
            mimeType: null,
            capabilityState: createCapabilityState(
              "external_only",
              "Apribile esternamente",
              "Il file verra aperto o scaricato fuori dall'app.",
            ),
          },
        ];

  return {
    id: pickString(data.id, data.pubId, data.commId, data.evento_id) ?? "",
    pubId: pickString(data.pubId, data.id, data.commId, data.evento_id) ?? "",
    evtCode: pickString(data.evtCode, data.code) ?? "",
    title: pickString(data.cntTitle, data.title, data.titolo, data.evtTitle) ?? "Comunicazione",
    content:
      pickString(data.itemText, data.texto, data.content, data.description, data.notes, data.cntCategory) ?? "",
    sender: pickString(data.authorName, data.mittente, data.sender) ?? "Scuola",
    date: normalizeDate(data.data ?? data.date ?? data.pubDT),
    read: normalizeReadStatus(data.read ?? data.letto ?? data.isRead ?? data.readStatus),
    attachments: enrichedAttachments,
    category: pickString(data.cntCategory, data.category),
    needsAck: pickBoolean(data.needSign) ?? false,
    needsReply: pickBoolean(data.needReply) ?? false,
    needsJoin: pickBoolean(data.needJoin) ?? false,
    needsFile: pickBoolean(data.needFile) ?? false,
    hasAttachments: (pickBoolean(data.cntHasAttach) ?? false) || enrichedAttachments.length > 0,
  };
}

export function normalizeNoticeboardItemDetail(
  payload: unknown,
  base: Communication,
): NoticeboardItemDetail {
  const root = toRecord(payload);
  const item = toRecord(root.item ?? root.event ?? root);
  const reply = toRecord(root.reply);
  const content = pickString(item.text, item.evtText, item.content, item.description) ?? base.content;
  const attachments = collectAttachments(
    item.attachments,
    item.allegati,
    item.files,
    root.attachments,
    root.allegati,
    root.files,
    base.attachments,
  );
  const actions = buildNoticeboardActions(base, attachments);

  return {
    id: base.id,
    pubId: base.pubId,
    evtCode: base.evtCode,
    title: pickString(item.title, base.title) ?? "Comunicazione",
    content,
    replyText: pickString(reply.text, reply.replyText, reply.description),
    attachments,
    actions,
    capabilityState: content
      ? createCapabilityState("available", "Dettaglio disponibile", "Testo completo disponibile in app.")
      : createCapabilityState("empty", "Nessun testo disponibile", "Il portale non ha restituito un contenuto testuale."),
  };
}

export function normalizeNote(payload: unknown, categoryCode: string): Note {
  const data = toRecord(payload);
  const content = pickString(data.evtText, data.content, data.description) ?? "";

  return {
    id: pickString(data.evtId, data.id) ?? "",
    categoryCode,
    categoryLabel: getNoteCategoryLabel(categoryCode),
    title: createPreviewText(content, getNoteCategoryLabel(categoryCode)),
    contentPreview: createPreviewText(content, "Nessun dettaglio disponibile"),
    date: normalizeDate(data.evtDate ?? data.date),
    author: pickString(data.authorName, data.author) ?? "Docente",
    read: normalizeReadStatus(data.readStatus ?? data.read),
    severity: getNoteSeverity(categoryCode),
  };
}

export function normalizeNoteDetail(payload: unknown, base: Note): NoteDetail {
  const root = toRecord(payload);
  const event = toRecord(root.event ?? root);
  const content = pickString(event.evtText, event.text, event.content) ?? base.contentPreview;

  return {
    ...base,
    title: createPreviewText(content, base.title),
    contentPreview: createPreviewText(content, base.contentPreview),
    content,
  };
}

export function normalizeDidacticContent(
  payload: unknown,
  teacher: { teacherId: string; teacherName: string },
  folder: { folderId: string; folderName: string },
): DidacticContent {
  const data = toRecord(payload);
  const objectType = pickString(data.objectType) ?? "unknown";
  const sourceUrl = toAbsoluteUrl(
    pickString(data.url, data.href, data.link, data.contentUrl, data.downloadUrl, data.viewUrl),
  );

  return {
    id: pickString(data.contentId, data.id) ?? "",
    teacherId: teacher.teacherId,
    teacherName: teacher.teacherName,
    folderId: folder.folderId,
    folderName: folder.folderName,
    title: pickString(data.contentName, data.title, data.name) ?? "Materiale",
    objectId: pickString(data.objectId, data.id) ?? "",
    objectType,
    sharedAt: normalizeDate(data.shareDT ?? data.sharedAt),
    sourceUrl,
    capabilityState:
      objectType === "file"
        ? createCapabilityState("external_only", "Apribile come documento", "Il materiale verra aperto in una preview esterna.")
        : sourceUrl
          ? createCapabilityState("external_only", "Apribile come collegamento", "Il materiale verra aperto fuori dall'app.")
        : createCapabilityState("available", "Contenuto disponibile", "Il materiale puo essere letto direttamente."),
  };
}

export function normalizeDidacticFolder(payload: unknown): DidacticFolder {
  const data = toRecord(payload);
  const teacherId = pickString(data.teacherId) ?? "";
  const teacherName = pickString(data.teacherName, data.teacherLastName) ?? "Docente";
  const folderId = pickString(data.folderId, data.id) ?? "";
  const folderName = pickString(data.folderName, data.title) ?? "Materiali";

  return {
    id: folderId,
    teacherId,
    teacherName,
    title: folderName,
    sharedAt: normalizeDate(data.lastShareDT ?? data.shareDT),
    contents: extractArray(data.contents, ["contents"]).map((item) =>
      normalizeDidacticContent(item, { teacherId, teacherName }, { folderId, folderName }),
    ),
  };
}

export function normalizeSchoolbookCourse(payload: unknown): SchoolbookCourse {
  const data = toRecord(payload);
  const books = extractArray(data.books, ["books"]).map((item) => {
    const book = toRecord(item);

    return {
      id: pickString(book.bookId, book.id) ?? "",
      isbn: pickString(book.isbnCode, book.isbn) ?? "",
      title: pickString(book.title) ?? "Libro",
      subtitle: pickString(book.subheading),
      volume: pickString(book.volume),
      author: pickString(book.author),
      publisher: pickString(book.publisher),
      subject: pickString(book.subjectDesc, book.subject) ?? "Materia",
      price: pickNumber(book.price),
      coverUrl: pickString(book.coverUrl)?.replace(/^\/\//, "https://"),
      toBuy: Boolean(book.toBuy),
      alreadyOwned: Boolean(book.alreadyOwned),
      alreadyInUse: Boolean(book.alreadyInUse),
      recommended: Boolean(book.recommended),
      recommendedFor: pickString(book.recommendedFor),
      newAdoption: Boolean(book.newAdoption),
    } satisfies Schoolbook;
  });

  return {
    id: pickString(data.courseId, data.id) ?? "",
    title: pickString(data.courseDesc, data.description) ?? "Corso",
    books,
  };
}

export function normalizeSchoolReport(payload: unknown): SchoolReport {
  const data = toRecord(payload);
  const title = pickString(data.desc, data.title) ?? "Documento";
  const viewUrl = pickString(data.viewLink);

  return {
    id: pickString(data.viewLink, data.confirmLink, data.desc) ?? title,
    title,
    confirmUrl: pickString(data.confirmLink),
    viewUrl,
    capabilityState: viewUrl
      ? createCapabilityState("external_only", "Apri documento", "Il documento viene aperto nel portale ufficiale.")
      : createCapabilityState("unavailable", "Documento non disponibile", "Il portale non ha fornito un link di apertura."),
  };
}

export function normalizePeriod(payload: unknown): Period {
  const data = toRecord(payload);

  return {
    code: pickString(data.periodCode, data.code) ?? "",
    order: pickNumber(data.periodPos, data.order) ?? 0,
    description: pickString(data.periodDesc, data.description) ?? "Periodo",
    label: pickString(data.periodLabel, data.label, data.periodDesc) ?? "Periodo",
    isFinal: Boolean(data.isFinal),
    startDate: normalizeDate(data.dateStart ?? data.startDate),
    endDate: normalizeDate(data.dateEnd ?? data.endDate),
  };
}

export function normalizeSubject(payload: unknown): Subject {
  const data = toRecord(payload);
  const teachers = extractArray(data.teachers, ["teachers"])
    .map((teacher) => pickString(toRecord(teacher).teacherName, toRecord(teacher).name))
    .filter((teacher): teacher is string => Boolean(teacher));

  return {
    id: pickString(data.id, data.subjectId) ?? "",
    description: pickString(data.description, data.subjectDesc) ?? "Materia",
    order: pickNumber(data.order, data.ord) ?? 0,
    teachers,
  };
}

export function normalizeAgendaEvent(payload: unknown): AgendaEvent {
  const data = toRecord(payload);
  const title =
    pickString(data.title, data.evtTitle, data.notes, data.description, data.content) ??
    pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ??
    "Evento";
  const subject = pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject, data.classDesc) ?? "";
  const typeSource = pickString(data.type, data.eventTypeDesc, data.evtCode, data.eventCode);

  return {
    id: pickString(data.id, data.evtId) ?? "",
    date: normalizeDate(data.date ?? data.evtDate ?? data.data ?? data.evtDatetimeBegin),
    title,
    description: pickString(data.description, data.notes, data.content),
    subject,
    category: normalizeAgendaCategory(typeSource, title),
    time: normalizeTime(data.time ?? data.startTime ?? data.lessonHour ?? data.ora ?? data.evtDatetimeBegin),
    rawDate: pickString(data.date, data.evtDate, data.data, data.evtDatetimeBegin),
    rawDescription: pickString(data.description, data.notes, data.content),
    slot: deriveSlot(data.evtHPos, data.hour, data.order),
  };
}

export class ClassevivaClient {
  private apiClient: AxiosInstance;
  private token: string | null = null;
  private studentId: string | null = null;

  constructor() {
    this.apiClient = axios.create({
      baseURL: API_BASE_URL,
      timeout: 15000,
      headers: {
        "Content-Type": "application/json",
        "User-Agent": USER_AGENT,
        "Z-Dev-ApiKey": API_KEY,
      },
    });

    this.apiClient.interceptors.request.use((config) => {
      if (this.token) {
        config.headers["Z-Auth-Token"] = this.token;
      }

      return config;
    });
  }

  async login(credentials: LoginCredentials): Promise<AuthToken> {
    try {
      const response = await this.apiClient.post("/auth/login", {
        ident: null,
        pass: credentials.password,
        uid: credentials.username,
      });

      const data = toRecord(response.data);
      const token = pickString(data.token);
      if (!token) {
        throw new ClassevivaApiError({
          code: "INVALID_LOGIN_RESPONSE",
          message: "Classeviva non ha restituito un token valido.",
          details: response.data,
        });
      }

      this.token = token;
      this.studentId =
        normalizeStudentId(credentials.username) ??
        normalizeStudentId(data.ident) ??
        normalizeStudentId(data.usrId) ??
        normalizeStudentId(data.userId) ??
        null;

      const profileHint = normalizeProfile(
        {
          id: data.ident,
          ident: data.ident,
          firstName: data.firstName,
          lastName: data.lastName,
        },
        this.studentId ?? undefined,
      );

      return {
        token,
        expiresAt: parseExpire(data.expire),
        profileHint,
      };
    } catch (error) {
      throw this.handleError(error, "Errore durante il login");
    }
  }

  logout(): void {
    this.token = null;
    this.studentId = null;
  }

  isAuthenticated(): boolean {
    return this.token !== null && this.studentId !== null;
  }

  setToken(token: string, studentId: string): void {
    this.token = token;
    this.studentId = normalizeStudentId(studentId) ?? null;
  }

  getStudentId(): string | null {
    return this.studentId;
  }

  async getProfile(): Promise<StudentProfile> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/card`);
      const profile = normalizeProfile(response.data, this.studentId ?? undefined);
      this.studentId = normalizeStudentId(profile.id) ?? this.studentId;
      return profile;
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento del profilo");
    }
  }

  async getGrades(): Promise<Grade[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/grades`);
      return extractArray(response.data, ["grades", "events", "items"]).map(normalizeGrade);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dei voti");
    }
  }

  async getLessons(startDate?: string, endDate?: string): Promise<Lesson[]> {
    try {
      this.ensureStudentId();

      const today = startDate ?? toLocalIsoDate();
      const until = endDate ?? toLocalIsoDate(addDays(new Date(), 14));

      const response = await this.apiClient.get(
        `/students/${this.studentId}/lessons/${toApiDateParam(today)}/${toApiDateParam(until)}`,
      );
      return extractArray(response.data, ["lessons", "agenda", "items"]).map(normalizeLesson);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle lezioni");
    }
  }

  async getHomeworks(): Promise<Homework[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/homeworks`);
      return extractArray(response.data, ["homeworks", "items", "agenda"]).map(normalizeHomework);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle scadenze");
    }
  }

  async getAbsences(): Promise<Absence[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/absences/details`);
      return extractArray(response.data, ["events", "absences", "items"]).map(normalizeAbsence);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle assenze");
    }
  }

  async getCommunications(): Promise<Communication[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/noticeboard`);
      return extractArray(response.data, ["items", "noticeboard", "communications"]).map(
        normalizeCommunication,
      );
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle comunicazioni");
    }
  }

  async getCommunicationDetail(communication: Pick<Communication, "pubId" | "evtCode">): Promise<NoticeboardItemDetail> {
    try {
      this.ensureStudentId();
      const base = await this.getCommunicationBase(communication);

      const response = await this.apiClient.post(`/students/${this.studentId}/noticeboard/read/${communication.evtCode}/${communication.pubId}/101`);

      return normalizeNoticeboardItemDetail(response.data, base);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento del dettaglio comunicazione");
    }
  }

  async acknowledgeCommunication(communication: Pick<Communication, "pubId" | "evtCode">): Promise<NoticeboardItemDetail> {
    return this.performCommunicationAction(communication, "ack");
  }

  async joinCommunication(communication: Pick<Communication, "pubId" | "evtCode">): Promise<NoticeboardItemDetail> {
    return this.performCommunicationAction(communication, "join");
  }

  async getAgenda(startDate?: string, endDate?: string): Promise<AgendaEvent[]> {
    try {
      this.ensureStudentId();

      const today = startDate ?? toLocalIsoDate();
      const until = endDate ?? toLocalIsoDate(addDays(new Date(), 14));

      const response = await this.apiClient.get(
        `/students/${this.studentId}/agenda/all/${toApiDateParam(today)}/${toApiDateParam(until)}`,
      );
      return extractArray(response.data, ["agenda", "events", "items"]).map(normalizeAgendaEvent);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dell'agenda");
    }
  }

  async getNotes(): Promise<Note[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/notes/all`);
      const payload = toRecord(response.data);

      return Object.entries(payload)
        .flatMap(([categoryCode, items]) => {
          if (!Array.isArray(items)) {
            return [];
          }

          return items.map((item) => normalizeNote(item, categoryCode));
        })
        .sort((left, right) => new Date(right.date).getTime() - new Date(left.date).getTime());
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle note");
    }
  }

  async getNoteDetail(note: Pick<Note, "id" | "categoryCode">): Promise<NoteDetail> {
    try {
      this.ensureStudentId();
      const base = await this.getNotes().then((items) =>
        items.find((item) => item.id === note.id && item.categoryCode === note.categoryCode),
      );

      if (!base) {
        throw new ClassevivaApiError({
          code: "NOT_FOUND",
          message: "Nota non trovata.",
        });
      }

      const response = await this.apiClient.post(
        `/students/${this.studentId}/notes/${note.categoryCode}/read/${note.id}`,
      );

      return normalizeNoteDetail(response.data, base);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento del dettaglio nota");
    }
  }

  async getDidactics(): Promise<DidacticFolder[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/didactics`);
      const teachers = extractArray(response.data, ["didacticts"]);

      return teachers
        .flatMap((teacherPayload) => {
          const teacher = toRecord(teacherPayload);
          const teacherId = pickString(teacher.teacherId) ?? "";
          const teacherName = pickString(teacher.teacherName, teacher.teacherLastName) ?? "Docente";

          return extractArray(teacher.folders, ["folders"]).map((folderPayload) =>
            normalizeDidacticFolder({
              ...toRecord(folderPayload),
              teacherId,
              teacherName,
            }),
          );
        })
        .sort((left, right) => new Date(right.sharedAt).getTime() - new Date(left.sharedAt).getTime());
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento del materiale didattico");
    }
  }

  async getDidacticAsset(content: Pick<DidacticContent, "id" | "title" | "objectType" | "sourceUrl">): Promise<DidacticAsset> {
    try {
      this.ensureStudentId();

      if (content.sourceUrl && content.objectType.toLowerCase().includes("link")) {
        return {
          id: content.id,
          title: content.title,
          objectType: content.objectType,
          mimeType: null,
          dataUrl: null,
          sourceUrl: content.sourceUrl,
          textPreview: null,
          capabilityState: createCapabilityState(
            "external_only",
            "Apri collegamento",
            "Il materiale verra aperto nel browser esterno.",
          ),
        };
      }

      const response = await this.apiClient.get<ArrayBuffer>(`/students/${this.studentId}/didactics/item/${content.id}`, {
        responseType: "arraybuffer",
      });

      const mimeType = pickString(response.headers["content-type"])?.split(";")[0] ?? null;
      const dataUrl = mimeType
        ? `data:${mimeType};base64,${arrayBufferToBase64(response.data)}`
        : null;
      const textPreview = mimeType?.startsWith("text/") ? decodeAsciiPreview(response.data) : null;
      const capabilityState = dataUrl
        ? createCapabilityState(
            "external_only",
            "Apri il documento",
            mimeType === "application/pdf"
              ? "Il materiale viene aperto in una preview esterna PDF."
              : "Il materiale viene aperto in una preview esterna.",
          )
        : createCapabilityState(
            "unavailable",
            "Preview non disponibile",
            "Il portale non ha restituito un contenuto apribile.",
          );

      return {
        id: content.id,
        title: content.title,
        objectType: content.objectType,
        mimeType,
        dataUrl,
        sourceUrl: content.sourceUrl ?? null,
        textPreview,
        capabilityState,
      };
    } catch (error) {
      if (content.sourceUrl) {
        return {
          id: content.id,
          title: content.title,
          objectType: content.objectType,
          mimeType: null,
          dataUrl: null,
          sourceUrl: content.sourceUrl,
          textPreview: null,
          capabilityState: createCapabilityState(
            "external_only",
            "Apri contenuto",
            "Il file non e leggibile inline ma il portale ha restituito un collegamento esterno.",
          ),
        };
      }

      throw this.handleError(error, "Errore nel caricamento del materiale");
    }
  }

  async getSchoolbooks(): Promise<SchoolbookCourse[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/schoolbooks`);
      return extractArray(response.data, ["schoolbooks"]).map(normalizeSchoolbookCourse);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dei libri");
    }
  }

  async getSchoolReports(): Promise<SchoolReport[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.post(`/students/${this.studentId}/documents`);
      return extractArray(response.data, ["schoolReports"]).map(normalizeSchoolReport);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle pagelle");
    }
  }

  async getPeriods(): Promise<Period[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/periods`);
      return extractArray(response.data, ["periods"]).map(normalizePeriod);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dei periodi");
    }
  }

  async getSubjects(): Promise<Subject[]> {
    try {
      this.ensureStudentId();
      const response = await this.apiClient.get(`/students/${this.studentId}/subjects`);
      return extractArray(response.data, ["subjects"]).map(normalizeSubject);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento delle materie");
    }
  }

  private async getCommunicationBase(communication: Pick<Communication, "pubId" | "evtCode">): Promise<Communication> {
    const base = await this.getCommunications().then((items) =>
      items.find((item) => item.pubId === communication.pubId && item.evtCode === communication.evtCode),
    );

    if (!base) {
      throw new ClassevivaApiError({
        code: "NOT_FOUND",
        message: "Comunicazione non trovata.",
      });
    }

    return base;
  }

  private async performCommunicationAction(
    communication: Pick<Communication, "pubId" | "evtCode">,
    action: "ack" | "join",
  ): Promise<NoticeboardItemDetail> {
    try {
      this.ensureStudentId();
      const studentId = this.studentId as string;
      const actionCandidates =
        action === "ack"
          ? ["sign", "ack", "confirm"]
          : ["join", "adhere", "accept"];
      const paths = actionCandidates.flatMap((candidate) => [
        `/students/${studentId}/noticeboard/${candidate}/${communication.evtCode}/${communication.pubId}`,
        `/students/${studentId}/noticeboard/${candidate}/${communication.evtCode}/${communication.pubId}/101`,
      ]);

      await this.tryPostCandidates(paths);
      return this.getCommunicationDetail(communication);
    } catch (error) {
      throw this.handleError(
        error,
        action === "ack"
          ? "Errore durante la conferma della comunicazione"
          : "Errore durante l'adesione alla comunicazione",
      );
    }
  }

  private async tryPostCandidates(paths: string[]): Promise<void> {
    let lastError: unknown = null;

    for (const path of paths) {
      try {
        await this.apiClient.post(path);
        return;
      } catch (error) {
        lastError = error;
        if (!isAxiosError(error)) {
          break;
        }

        const status = error.response?.status;
        if (![400, 404, 405, 422].includes(status ?? -1)) {
          break;
        }
      }
    }

    throw lastError ?? new Error("Azione comunicazione non disponibile.");
  }

  private ensureStudentId(): void {
    if (!this.studentId) {
      throw new ClassevivaApiError({
        code: "MISSING_STUDENT_ID",
        message: "Studente non identificato. Effettua di nuovo il login.",
      });
    }
  }

  private handleError(error: unknown, defaultMessage: string): ClassevivaApiError {
    if (error instanceof ClassevivaApiError) {
      return error;
    }

    if (isAxiosError(error)) {
      return this.handleAxiosError(error, defaultMessage);
    }

    if (error instanceof Error) {
      return new ClassevivaApiError({
        code: "UNKNOWN_ERROR",
        message: error.message || defaultMessage,
        details: error,
      });
    }

    return new ClassevivaApiError({
      code: "UNKNOWN_ERROR",
      message: defaultMessage,
      details: error,
    });
  }

  private handleAxiosError(error: AxiosError, defaultMessage: string): ClassevivaApiError {
    const status = error.response?.status;
    const details = error.response?.data ?? error.message;

    if (!status && error.request) {
      return new ClassevivaApiError({
        code: "NETWORK_ERROR",
        message: "Connessione non disponibile. Verifica rete o VPN.",
        details,
      });
    }

    switch (status) {
      case 400:
        if (isRejectedApiKey(details)) {
          return new ClassevivaApiError({
            code: "API_KEY_REJECTED",
            message:
              "Classeviva sta rifiutando temporaneamente le API dell'app. Riprova piu tardi dal dispositivo oppure accedi dal portale ufficiale.",
            details,
            status,
          });
        }

        return new ClassevivaApiError({
          code: "BAD_REQUEST",
          message: "Classeviva ha rifiutato la richiesta inviata dall'app.",
          details,
          status,
        });
      case 401:
      case 422:
        return new ClassevivaApiError({
          code: "UNAUTHORIZED",
          message: "Username o password non validi.",
          details,
          status,
        });
      case 403:
        return new ClassevivaApiError({
          code: "FORBIDDEN",
          message: "Accesso rifiutato da Classeviva.",
          details,
          status,
        });
      case 404:
        return new ClassevivaApiError({
          code: "NOT_FOUND",
          message: "Contenuto non trovato su Classeviva.",
          details,
          status,
        });
      case 405:
        return new ClassevivaApiError({
          code: "METHOD_NOT_ALLOWED",
          message: "Classeviva richiede una modalita diversa per questa azione.",
          details,
          status,
        });
      case 429:
        return new ClassevivaApiError({
          code: "RATE_LIMITED",
          message: "Troppi tentativi ravvicinati. Riprova tra poco.",
          details,
          status,
        });
      default:
        if (status && status >= 500) {
          return new ClassevivaApiError({
            code: "SERVER_ERROR",
            message: "Classeviva non risponde correttamente in questo momento.",
            details,
            status,
          });
        }

        return new ClassevivaApiError({
          code: "UNKNOWN_ERROR",
          message: defaultMessage,
          details,
          status,
        });
    }
  }
}

export const classeviva = new ClassevivaClient();
