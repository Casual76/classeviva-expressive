import AsyncStorage from "@react-native-async-storage/async-storage";
import { Platform } from "react-native";

export interface NotificationPreferences {
  enabled: boolean;
  homework: boolean;
  communications: boolean;
  absences: boolean;
  test: boolean;
}

export interface SeenGradeState {
  studentId: string;
  gradeIds: string[];
}

export interface SubjectGoal {
  studentId: string;
  subject: string;
  targetAverage: number;
  updatedAt: string;
}

const NOTIFICATION_PREFERENCES_KEY = "classeviva_notification_preferences";
const SEEN_GRADES_KEY = "classeviva_seen_grades";
const SUBJECT_GOALS_KEY = "classeviva_subject_goals";

export const DEFAULT_NOTIFICATION_PREFERENCES: NotificationPreferences = {
  enabled: true,
  homework: true,
  communications: true,
  absences: true,
  test: true,
};

function isWebStorageAvailable() {
  return Platform.OS === "web" && typeof window !== "undefined" && Boolean(window.localStorage);
}

async function getItem(key: string): Promise<string | null> {
  if (isWebStorageAvailable()) {
    return window.localStorage.getItem(key);
  }

  return AsyncStorage.getItem(key);
}

async function setItem(key: string, value: string): Promise<void> {
  if (isWebStorageAvailable()) {
    window.localStorage.setItem(key, value);
    return;
  }

  await AsyncStorage.setItem(key, value);
}

function normalizeStudentId(studentId: string) {
  return studentId.trim();
}

function parseJson<T>(value: string | null, fallback: T): T {
  if (!value) {
    return fallback;
  }

  try {
    return JSON.parse(value) as T;
  } catch {
    return fallback;
  }
}

async function readSeenGradesMap(): Promise<Record<string, string[]>> {
  return parseJson<Record<string, string[]>>(await getItem(SEEN_GRADES_KEY), {});
}

async function writeSeenGradesMap(next: Record<string, string[]>): Promise<void> {
  await setItem(SEEN_GRADES_KEY, JSON.stringify(next));
}

async function readSubjectGoalsMap(): Promise<Record<string, Record<string, SubjectGoal>>> {
  return parseJson<Record<string, Record<string, SubjectGoal>>>(await getItem(SUBJECT_GOALS_KEY), {});
}

async function writeSubjectGoalsMap(next: Record<string, Record<string, SubjectGoal>>): Promise<void> {
  await setItem(SUBJECT_GOALS_KEY, JSON.stringify(next));
}

export async function readNotificationPreferences(): Promise<NotificationPreferences> {
  const parsed = parseJson<Partial<NotificationPreferences>>(await getItem(NOTIFICATION_PREFERENCES_KEY), {});

  return {
    enabled: parsed.enabled ?? DEFAULT_NOTIFICATION_PREFERENCES.enabled,
    homework: parsed.homework ?? DEFAULT_NOTIFICATION_PREFERENCES.homework,
    communications: parsed.communications ?? DEFAULT_NOTIFICATION_PREFERENCES.communications,
    absences: parsed.absences ?? DEFAULT_NOTIFICATION_PREFERENCES.absences,
    test: parsed.test ?? DEFAULT_NOTIFICATION_PREFERENCES.test,
  };
}

export async function writeNotificationPreferences(
  next: Partial<NotificationPreferences>,
): Promise<NotificationPreferences> {
  const current = await readNotificationPreferences();
  const merged = { ...current, ...next };
  await setItem(NOTIFICATION_PREFERENCES_KEY, JSON.stringify(merged));
  return merged;
}

export async function readSeenGradeIds(studentId: string): Promise<string[]> {
  const normalizedStudentId = normalizeStudentId(studentId);
  if (!normalizedStudentId) {
    return [];
  }

  const map = await readSeenGradesMap();
  return map[normalizedStudentId] ?? [];
}

export async function markGradeSeen(studentId: string, gradeId: string): Promise<void> {
  const normalizedStudentId = normalizeStudentId(studentId);
  const normalizedGradeId = gradeId.trim();
  if (!normalizedStudentId || !normalizedGradeId) {
    return;
  }

  const map = await readSeenGradesMap();
  const current = new Set(map[normalizedStudentId] ?? []);
  current.add(normalizedGradeId);
  map[normalizedStudentId] = Array.from(current);
  await writeSeenGradesMap(map);
}

export async function markGradesSeen(studentId: string, gradeIds: string[]): Promise<void> {
  const normalizedStudentId = normalizeStudentId(studentId);
  if (!normalizedStudentId) {
    return;
  }

  const sanitizedIds = gradeIds.map((gradeId) => gradeId.trim()).filter(Boolean);
  if (sanitizedIds.length === 0) {
    return;
  }

  const map = await readSeenGradesMap();
  const current = new Set(map[normalizedStudentId] ?? []);
  for (const gradeId of sanitizedIds) {
    current.add(gradeId);
  }
  map[normalizedStudentId] = Array.from(current);
  await writeSeenGradesMap(map);
}

export async function readSubjectGoals(studentId: string): Promise<Record<string, SubjectGoal>> {
  const normalizedStudentId = normalizeStudentId(studentId);
  if (!normalizedStudentId) {
    return {};
  }

  const map = await readSubjectGoalsMap();
  return map[normalizedStudentId] ?? {};
}

export async function writeSubjectGoal(
  studentId: string,
  subject: string,
  targetAverage: number | null,
): Promise<Record<string, SubjectGoal>> {
  const normalizedStudentId = normalizeStudentId(studentId);
  const normalizedSubject = subject.trim();
  if (!normalizedStudentId || !normalizedSubject) {
    return {};
  }

  const map = await readSubjectGoalsMap();
  const current = { ...(map[normalizedStudentId] ?? {}) };

  if (targetAverage === null || Number.isNaN(targetAverage)) {
    delete current[normalizedSubject];
  } else {
    current[normalizedSubject] = {
      studentId: normalizedStudentId,
      subject: normalizedSubject,
      targetAverage: Number(targetAverage.toFixed(1)),
      updatedAt: new Date().toISOString(),
    };
  }

  map[normalizedStudentId] = current;
  await writeSubjectGoalsMap(map);
  return current;
}
