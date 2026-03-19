import * as SecureStore from "expo-secure-store";
import { Platform } from "react-native";

import type { SessionMode } from "@/lib/auth-state";

const TOKEN_KEY = "classeviva_token";
const STUDENT_ID_KEY = "classeviva_student_id";
const SESSION_MODE_KEY = "classeviva_session_mode";

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

export async function readStoredSession(): Promise<StoredSession> {
  const [token, studentId, rawMode] = await Promise.all([
    getStoredValue(TOKEN_KEY),
    getStoredValue(STUDENT_ID_KEY),
    getStoredValue(SESSION_MODE_KEY),
  ]);

  return {
    token,
    studentId,
    sessionMode: rawMode === "real" || rawMode === "demo" ? rawMode : null,
  };
}

export async function writeRealSession(token: string, studentId: string): Promise<void> {
  await Promise.all([
    setStoredValue(TOKEN_KEY, token),
    setStoredValue(STUDENT_ID_KEY, studentId),
    setStoredValue(SESSION_MODE_KEY, "real"),
  ]);
}

export async function writeDemoSession(): Promise<void> {
  await Promise.all([
    removeStoredValue(TOKEN_KEY),
    removeStoredValue(STUDENT_ID_KEY),
    setStoredValue(SESSION_MODE_KEY, "demo"),
  ]);
}

export async function clearStoredSession(): Promise<void> {
  await Promise.all([
    removeStoredValue(TOKEN_KEY),
    removeStoredValue(STUDENT_ID_KEY),
    removeStoredValue(SESSION_MODE_KEY),
  ]);
}
