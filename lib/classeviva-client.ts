/**
 * Classeviva API client.
 *
 * Sources used to refresh the request shape:
 * - https://raw.githubusercontent.com/Lioydiano/Classeviva-Official-Endpoints/master/Authentication/login.md
 * - https://classeviva.readthedocs.io/it/latest/api.html
 *
 * Inference:
 * - The login payload is sent as { ident: null, pass, uid }.
 * - Student endpoints are called with the student identifier returned by login,
 *   then upgraded to usrId when the card endpoint returns it.
 */

import axios, { AxiosError, AxiosInstance, isAxiosError } from "axios";

const API_BASE_URL = "https://web.spaggiari.eu/rest/v1";
const API_KEY = "+zorro+";
const USER_AGENT = "zorro/1.0";

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
  title: string;
  content: string;
  sender: string;
  date: string;
  read: boolean;
  attachments?: string[];
}

export interface AgendaEvent {
  id: string;
  date: string;
  title: string;
  description?: string;
  subject: string;
  category: "lesson" | "homework" | "assessment" | "event";
  time?: string;
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

function normalizeDate(value: unknown): string {
  const parsed = pickString(value);
  if (!parsed) {
    return new Date().toISOString().slice(0, 10);
  }

  const date = new Date(parsed);
  return Number.isNaN(date.getTime()) ? parsed : date.toISOString().slice(0, 10);
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

function joinSchoolName(parts: unknown[]): string {
  return parts
    .map((part) => pickString(part))
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
    id: pickString(data.usrId, data.id, data.ident, fallbackId) ?? fallbackId ?? "",
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
    period: pickString(data.periodDesc, data.period),
    teacher: pickString(data.teacherName, data.teacher),
    color: pickString(data.color),
  };
}

export function normalizeLesson(payload: unknown): Lesson {
  const data = toRecord(payload);

  return {
    id: pickString(data.id, data.lessonId, data.evtId) ?? "",
    subject: pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ?? "",
    date: normalizeDate(data.data ?? data.date ?? data.evtDate),
    time: pickString(data.lessonHour, data.ora, data.time, data.startTime) ?? "",
    duration: pickNumber(data.duration, data.durata) ?? 60,
    topic: pickString(data.argomento, data.topic, data.lessonArg),
    teacher: pickString(data.teacherName, data.teacher),
    room: pickString(data.classroom, data.room),
  };
}

export function normalizeHomework(payload: unknown): Homework {
  const data = toRecord(payload);

  return {
    id: pickString(data.id, data.hwId, data.evtId) ?? "",
    subject: pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ?? "",
    description: pickString(data.contenuto, data.description, data.notes, data.title) ?? "",
    dueDate: normalizeDate(data.dataConsegna ?? data.dueDate ?? data.date ?? data.evtDate),
    notes: pickString(data.note, data.notesForFamily, data.notes),
    attachments: Array.isArray(data.allegati)
      ? data.allegati.filter((item): item is string => typeof item === "string")
      : [],
  };
}

export function normalizeAbsence(payload: unknown): Absence {
  const data = toRecord(payload);

  return {
    id: pickString(data.id, data.evtId, data.absenceId) ?? "",
    date: normalizeDate(data.evtDate ?? data.data ?? data.date),
    type: normalizeAbsenceType(data.tipo, data.evtCode),
    hours: deriveAbsenceHours(data.hoursAbsence ?? data.ore ?? data.hours),
    justified: Boolean(data.isJustified ?? data.giustificata ?? data.justified),
    justificationDate: pickString(data.dataGiustificazione, data.justificationDate),
    justificationReason: pickString(data.justifReasonDesc, data.motivoGiustificazione, data.justificationReason),
  };
}

export function normalizeCommunication(payload: unknown): Communication {
  const data = toRecord(payload);

  return {
    id: pickString(data.id, data.pubId, data.commId) ?? "",
    title: pickString(data.title, data.titolo, data.evtTitle) ?? "",
    content: pickString(data.testo, data.content, data.description, data.notes) ?? "",
    sender: pickString(data.authorName, data.mittente, data.sender) ?? "",
    date: normalizeDate(data.data ?? data.date ?? data.pubDT),
    read: Boolean(data.read ?? data.letto ?? data.isRead),
    attachments: Array.isArray(data.attachments)
      ? data.attachments.filter((item): item is string => typeof item === "string")
      : [],
  };
}

export function normalizeAgendaEvent(payload: unknown): AgendaEvent {
  const data = toRecord(payload);
  const title =
    pickString(data.title, data.evtTitle, data.notes, data.description, data.content) ??
    pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ??
    "Evento";
  const subject = pickString(data.subjectDesc, toRecord(data.materia).desc, data.subject) ?? "";
  const typeSource = pickString(data.type, data.eventTypeDesc, data.evtCode, data.eventCode);

  return {
    id: pickString(data.id, data.evtId) ?? "",
    date: normalizeDate(data.date ?? data.evtDate ?? data.data),
    title,
    description: pickString(data.description, data.notes, data.content),
    subject,
    category: normalizeAgendaCategory(typeSource, title),
    time: pickString(data.time, data.startTime, data.lessonHour, data.ora),
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
        "Z-Dev-Apikey": API_KEY,
      },
    });

    this.apiClient.interceptors.request.use((config) => {
      if (this.token) {
        config.headers.Authorization = `Bearer ${this.token}`;
      }

      return config;
    });

    this.apiClient.interceptors.response.use(
      (response) => response,
      (error) => {
        if (isAxiosError(error) && error.response?.status === 401) {
          this.logout();
        }

        return Promise.reject(error);
      },
    );
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
      this.studentId = pickString(data.ident, data.usrId, data.userId) ?? null;

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
    this.studentId = studentId;
  }

  getStudentId(): string | null {
    return this.studentId;
  }

  async getProfile(): Promise<StudentProfile> {
    try {
      if (!this.studentId) {
        throw new ClassevivaApiError({
          code: "MISSING_STUDENT_ID",
          message: "Studente non identificato. Effettua di nuovo il login.",
        });
      }

      const response = await this.apiClient.get(`/students/${this.studentId}/card`);
      const profile = normalizeProfile(response.data, this.studentId);
      this.studentId = profile.id || this.studentId;
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

      const today = startDate ?? new Date().toISOString().slice(0, 10);
      const until =
        endDate ??
        new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

      const response = await this.apiClient.get(`/students/${this.studentId}/lessons/${today}/${until}`);
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

  async getAgenda(startDate?: string, endDate?: string): Promise<AgendaEvent[]> {
    try {
      this.ensureStudentId();

      const today = startDate ?? new Date().toISOString().slice(0, 10);
      const until =
        endDate ??
        new Date(Date.now() + 14 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);

      const response = await this.apiClient.get(`/students/${this.studentId}/agenda/all/${today}/${until}`);
      return extractArray(response.data, ["agenda", "events", "items"]).map(normalizeAgendaEvent);
    } catch (error) {
      throw this.handleError(error, "Errore nel caricamento dell'agenda");
    }
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
          message: "Endpoint Classeviva non trovato.",
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
