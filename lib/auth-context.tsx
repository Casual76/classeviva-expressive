import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer } from "react";

import {
  authReducer,
  initialAuthState,
  isBusyAuthState,
  type AuthStatus,
  type SessionMode,
} from "@/lib/auth-state";
import { ClassevivaApiError, type StudentProfile, classeviva } from "@/lib/classeviva-client";
import { notificationsService } from "@/lib/notifications-service";
import {
  clearStoredSession,
  readStoredSession,
  writeRealSession,
} from "@/lib/session-storage";

interface AuthContextType {
  status: AuthStatus;
  isLoading: boolean;
  isSignedIn: boolean;
  user: StudentProfile | null;
  token: string | null;
  sessionMode: SessionMode;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  restoreToken: () => Promise<void>;
  clearError: () => void;
  error: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);
const STORAGE_TIMEOUT_MS = 4000;
const PROFILE_TIMEOUT_MS = 12000;
const CLEANUP_TIMEOUT_MS = 2500;

function withTimeout<T>(promise: Promise<T>, timeoutMs: number, label: string): Promise<T> {
  return new Promise((resolve, reject) => {
    const timeout = setTimeout(() => {
      reject(new Error(`${label} timeout after ${timeoutMs}ms`));
    }, timeoutMs);

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

function toFriendlyAuthMessage(error: unknown): string {
  if (error instanceof ClassevivaApiError) {
    return error.message;
  }

  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }

  return "Si e verificato un errore inatteso durante l'accesso.";
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = useReducer(authReducer, initialAuthState);

  const safeClearStoredSession = useCallback(async () => {
    try {
      await withTimeout(clearStoredSession(), CLEANUP_TIMEOUT_MS, "clear session");
    } catch (error) {
      console.error("Session cleanup failed", error);
    }
  }, []);

  const restoreSignedOutState = useCallback(() => {
    classeviva.logout();
    dispatch({
      type: "RESTORE_SUCCESS",
      payload: { token: null, user: null, sessionMode: null },
    });
    void safeClearStoredSession();
  }, [safeClearStoredSession]);

  useEffect(() => {
    if (state.status !== "signed_in") {
      return;
    }

    void notificationsService.initialize();
  }, [state.status]);

  const restoreToken = useCallback(async () => {
    dispatch({ type: "RESTORE_START" });

    try {
      const stored = await withTimeout(readStoredSession(), STORAGE_TIMEOUT_MS, "restore storage");

      if (stored.sessionMode === "real" && stored.token && stored.studentId) {
        classeviva.setToken(stored.token, stored.studentId);
        const user = await withTimeout(classeviva.getProfile(), PROFILE_TIMEOUT_MS, "restore profile");

        dispatch({
          type: "RESTORE_SUCCESS",
          payload: { token: stored.token, user, sessionMode: "real" },
        });
        return;
      }

      restoreSignedOutState();
    } catch (error) {
      console.error("Restore session failed", error);
      restoreSignedOutState();
    }
  }, [restoreSignedOutState]);

  useEffect(() => {
    void restoreToken();
  }, [restoreToken]);

  const login = useCallback(async (username: string, password: string) => {
    dispatch({ type: "SIGN_IN_START" });

    try {
      const authToken = await classeviva.login({ username, password });
      const user = await withTimeout(classeviva.getProfile(), PROFILE_TIMEOUT_MS, "login profile");

      const studentId = classeviva.getStudentId() ?? user.id;
      if (!studentId) {
        throw new Error("Studente non identificato dopo il login.");
      }

      try {
        await withTimeout(writeRealSession(authToken.token, studentId), STORAGE_TIMEOUT_MS, "persist session");
      } catch (storageError) {
        console.error("Persist real session failed", storageError);
      }

      dispatch({
        type: "SIGN_IN_SUCCESS",
        payload: { token: authToken.token, user, sessionMode: "real" },
      });
    } catch (error) {
      console.error("Login failed", error);
      classeviva.logout();
      void safeClearStoredSession();
      dispatch({ type: "SET_ERROR", payload: toFriendlyAuthMessage(error) });
      throw error;
    }
  }, [safeClearStoredSession]);

  const logout = useCallback(async () => {
    classeviva.logout();
    dispatch({ type: "SIGN_OUT" });
    await safeClearStoredSession();
  }, [safeClearStoredSession]);

  const clearError = useCallback(() => {
    dispatch({ type: "SET_ERROR", payload: null });
  }, []);

  const value = useMemo<AuthContextType>(
    () => ({
      status: state.status,
      isLoading: isBusyAuthState(state),
      isSignedIn: Boolean(state.user),
      user: state.user,
      token: state.token,
      sessionMode: state.sessionMode,
      login,
      logout,
      restoreToken,
      clearError,
      error: state.error,
    }),
    [state, login, logout, restoreToken, clearError],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth deve essere usato dentro AuthProvider");
  }

  return context;
}
