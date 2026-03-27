import * as SecureStore from "expo-secure-store";
import AsyncStorage from "@react-native-async-storage/async-storage";
import { Platform } from "react-native";

import type { SessionMode } from "@/lib/auth-state";

const TOKEN_KEY = "classeviva_token";
const STUDENT_ID_KEY = "classeviva_student_id";
const SESSION_MODE_KEY = "classeviva_session_mode";
const STORAGE_VERSION_KEY = "classeviva_storage_version";
const STORAGE_VERSION = "20260327-real-first-session";
const LEGACY_SECURE_KEYS = ["app_session_token", "manus-runtime-user-info"];
const LEGACY_ASYNC_KEYS = ["scheduled_notifications"];

export interface StoredSession {
  token: string | null;
  studentId: string | null;
  sessionMode: SessionMode;
}

function isWebStorageAvailable() {
  return Platform.OS === "web" && typeof window !== "undefined" && Boolean(window.localStorage);
}

async function getStoredValue(key: string): Promise<string | null> {
  if (isWebStorageAvailable()) {
    return window.localStorage.getItem(key);
  }

  return SecureStore.getItemAsync(key);
}

async function setStoredValue(key: string, value: string): Promise<void> {
  if (isWebStorageAvailable()) {
    window.localStorage.setItem(key, value);
    return;
  }

  await SecureStore.setItemAsync(key, value);
}

async function removeStoredValue(key: string): Promise<void> {
  if (isWebStorageAvailable()) {
    window.localStorage.removeItem(key);
    return;
  }

  await SecureStore.deleteItemAsync(key);
}

async function clearLegacyKeys(): Promise<void> {
  const secureRemovals = LEGACY_SECURE_KEYS.map((key) => removeStoredValue(key));
  const asyncRemovals = LEGACY_ASYNC_KEYS.map((key) => AsyncStorage.removeItem(key));
  await Promise.all([...secureRemovals, ...asyncRemovals]);
}

async function clearCurrentSessionKeys(): Promise<void> {
  await Promise.all([
    removeStoredValue(TOKEN_KEY),
    removeStoredValue(STUDENT_ID_KEY),
    removeStoredValue(SESSION_MODE_KEY),
  ]);
}

export async function migrateSessionStorage(): Promise<void> {
  const version = await getStoredValue(STORAGE_VERSION_KEY);
  if (version === STORAGE_VERSION) {
    return;
  }

  await clearCurrentSessionKeys();
  await clearLegacyKeys();
  await setStoredValue(STORAGE_VERSION_KEY, STORAGE_VERSION);
}

export async function readStoredSession(): Promise<StoredSession> {
  await migrateSessionStorage();

  const [token, studentId, rawMode] = await Promise.all([
    getStoredValue(TOKEN_KEY),
    getStoredValue(STUDENT_ID_KEY),
    getStoredValue(SESSION_MODE_KEY),
  ]);

  return {
    token,
    studentId,
    sessionMode: rawMode === "real" ? "real" : null,
  };
}

export async function writeRealSession(token: string, studentId: string): Promise<void> {
  await Promise.all([
    setStoredValue(TOKEN_KEY, token),
    setStoredValue(STUDENT_ID_KEY, studentId),
    setStoredValue(SESSION_MODE_KEY, "real"),
  ]);
}

export async function clearStoredSession(): Promise<void> {
  await clearCurrentSessionKeys();
}
