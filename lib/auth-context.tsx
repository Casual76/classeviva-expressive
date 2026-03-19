import React, { createContext, useCallback, useContext, useEffect, useMemo, useReducer } from "react";
import { Platform } from "react-native";

import {
  authReducer,
  initialAuthState,
  isBusyAuthState,
  type AuthStatus,
  type SessionMode,
} from "@/lib/auth-state";
import { ClassevivaApiError, type StudentProfile, classeviva } from "@/lib/classeviva-client";
import { mockStudentProfile } from "@/lib/mock-data";
import { notificationsService } from "@/lib/notifications-service";
import {
  clearStoredSession,
  readStoredSession,
  writeDemoSession,
  writeRealSession,
} from "@/lib/session-storage";

interface AuthContextType {
  status: AuthStatus;
  isLoading: boolean;
  isSignedIn: boolean;
  user: StudentProfile | null;
  token: string | null;
  sessionMode: SessionMode;
  isDemoMode: boolean;
  login: (username: string, password: string) => Promise<void>;
  loginDemo: () => Promise<void>;
  logout: () => Promise<void>;
  restoreToken: () => Promise<void>;
  clearError: () => void;
  error: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

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

  useEffect(() => {
    notificationsService.initialize();
  }, []);

  const restoreToken = useCallback(async () => {
    dispatch({ type: "RESTORE_START" });

    try {
      const stored = await readStoredSession();

      if (stored.sessionMode === "demo") {
        dispatch({
          type: "RESTORE_SUCCESS",
          payload: { token: null, user: mockStudentProfile, sessionMode: "demo" },
        });
        return;
      }

      if (stored.sessionMode === "real" && stored.token && stored.studentId && Platform.OS !== "web") {
        classeviva.setToken(stored.token, stored.studentId);
        const user = await classeviva.getProfile();

        dispatch({
          type: "RESTORE_SUCCESS",
          payload: { token: stored.token, user, sessionMode: "real" },
        });
        return;
      }

      await clearStoredSession();
      classeviva.logout();
      dispatch({
        type: "RESTORE_SUCCESS",
        payload: { token: null, user: null, sessionMode: null },
      });
    } catch (error) {
      console.error("Restore session failed", error);
      await clearStoredSession();
      classeviva.logout();
      dispatch({
        type: "RESTORE_SUCCESS",
        payload: { token: null, user: null, sessionMode: null },
      });
    }
  }, []);

  useEffect(() => {
    void restoreToken();
  }, [restoreToken]);

  const login = useCallback(async (username: string, password: string) => {
    if (Platform.OS === "web") {
      const message = "Il login reale e disponibile solo nell'app mobile. Sul web usa la demo.";
      dispatch({ type: "SET_ERROR", payload: message });
      throw new Error(message);
    }

    dispatch({ type: "SIGN_IN_START" });

    try {
      const authToken = await classeviva.login({ username, password });
      const user = await classeviva.getProfile().catch(() => authToken.profileHint ?? null);

      if (!user) {
        throw new Error("Accesso riuscito, ma il profilo non e ancora disponibile.");
      }

      const studentId = classeviva.getStudentId() ?? user.id;
      if (!studentId) {
        throw new Error("Studente non identificato dopo il login.");
      }

      await writeRealSession(authToken.token, studentId);

      dispatch({
        type: "SIGN_IN_SUCCESS",
        payload: { token: authToken.token, user, sessionMode: "real" },
      });
    } catch (error) {
      console.error("Login failed", error);
      classeviva.logout();
      await clearStoredSession();
      dispatch({ type: "SET_ERROR", payload: toFriendlyAuthMessage(error) });
      throw error;
    }
  }, []);

  const loginDemo = useCallback(async () => {
    dispatch({ type: "SIGN_IN_START" });

    try {
      await writeDemoSession();
      dispatch({
        type: "SIGN_IN_SUCCESS",
        payload: { token: null, user: mockStudentProfile, sessionMode: "demo" },
      });
    } catch (error) {
      console.error("Demo login failed", error);
      dispatch({ type: "SET_ERROR", payload: "Non riesco ad aprire la demo in questo momento." });
      throw error;
    }
  }, []);

  const logout = useCallback(async () => {
    try {
      classeviva.logout();
      await clearStoredSession();
    } finally {
      dispatch({ type: "SIGN_OUT" });
    }
  }, []);

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
      isDemoMode: state.sessionMode === "demo",
      login,
      loginDemo,
      logout,
      restoreToken,
      clearError,
      error: state.error,
    }),
    [state, login, loginDemo, logout, restoreToken, clearError],
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
