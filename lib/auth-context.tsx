import React, { createContext, useContext, useEffect, useState } from "react";
import * as SecureStore from "expo-secure-store";
import { classeviva, StudentProfile, AuthToken } from "./classeviva-client";
import { mockStudentProfile } from "./mock-data";

interface AuthContextType {
  isLoading: boolean;
  isSignedIn: boolean;
  user: StudentProfile | null;
  token: string | null;
  isDemoMode: boolean;
  login: (username: string, password: string) => Promise<void>;
  loginDemo: () => Promise<void>;
  logout: () => Promise<void>;
  restoreToken: () => Promise<void>;
  error: string | null;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const [state, dispatch] = React.useReducer(
    (prevState: any, action: any) => {
      switch (action.type) {
        case "RESTORE_TOKEN":
          return {
            ...prevState,
            isLoading: false,
            isSignedIn: action.payload.token !== null,
            user: action.payload.user,
            token: action.payload.token,
            error: null,
          };
        case "SIGN_IN":
          return {
            ...prevState,
            isSignedIn: true,
            user: action.payload.user,
            token: action.payload.token,
            isDemoMode: action.payload.isDemoMode || false,
            error: null,
          };
        case "SIGN_OUT":
          return {
            ...prevState,
            isSignedIn: false,
            user: null,
            token: null,
            error: null,
          };
        case "SET_ERROR":
          return {
            ...prevState,
            error: action.payload,
          };
        default:
          return prevState;
      }
    },
    {
      isLoading: true,
      isSignedIn: false,
      user: null,
      token: null,
      isDemoMode: false,
      error: null,
    }
  );

  // Ripristina il token al mount
  useEffect(() => {
    const bootstrapAsync = async () => {
      let token = null;
      let user = null;

      try {
        // Prova a ripristinare il token da secure storage
        token = await SecureStore.getItemAsync("classeviva_token");
        const studentId = await SecureStore.getItemAsync("classeviva_student_id");

        if (token && studentId) {
          classeviva.setToken(token, studentId);
          // Verifica che il token sia ancora valido
          user = await classeviva.getProfile();
        }
      } catch (e) {
        // Ignora errori durante il ripristino
        console.error("Errore durante il ripristino del token:", e);
      }

      dispatch({
        type: "RESTORE_TOKEN",
        payload: { token, user },
      });
    };

    bootstrapAsync();
  }, []);

  const authContext: AuthContextType = {
    isLoading: state.isLoading,
    isSignedIn: state.isSignedIn,
    user: state.user,
    token: state.token,
    isDemoMode: state.isDemoMode,
    error: state.error,

    login: async (username: string, password: string) => {
      try {
        const authToken = await classeviva.login({
          username,
          password,
        });

        const user = await classeviva.getProfile();

        // Salva il token in secure storage
        await SecureStore.setItemAsync("classeviva_token", authToken.token);
        await SecureStore.setItemAsync("classeviva_student_id", user.id);

        dispatch({
          type: "SIGN_IN",
          payload: { token: authToken.token, user },
        });
      } catch (error: any) {
        const errorMessage =
          error.message || "Errore durante il login. Riprova.";
        dispatch({
          type: "SET_ERROR",
          payload: errorMessage,
        });
        throw error;
      }
    },

    loginDemo: async () => {
      try {
        dispatch({
          type: "SIGN_IN",
          payload: { token: "demo-token", user: mockStudentProfile, isDemoMode: true },
        });
      } catch (error: any) {
        const errorMessage = error.message || "Errore durante il login demo";
        dispatch({
          type: "SET_ERROR",
          payload: errorMessage,
        });
        throw error;
      }
    },

    logout: async () => {
      try {
        classeviva.logout();
        await SecureStore.deleteItemAsync("classeviva_token");
        await SecureStore.deleteItemAsync("classeviva_student_id");
        dispatch({ type: "SIGN_OUT" });
      } catch (error) {
        console.error("Errore durante il logout:", error);
      }
    },

    restoreToken: async () => {
      try {
        const token = await SecureStore.getItemAsync("classeviva_token");
        const studentId = await SecureStore.getItemAsync(
          "classeviva_student_id"
        );

        if (token && studentId) {
          classeviva.setToken(token, studentId);
          const user = await classeviva.getProfile();
          dispatch({
            type: "RESTORE_TOKEN",
            payload: { token, user },
          });
        }
      } catch (error) {
        console.error("Errore durante il ripristino del token:", error);
      }
    },
  };

  return (
    <AuthContext.Provider value={authContext}>{children}</AuthContext.Provider>
  );
}

export function useAuth(): AuthContextType {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error("useAuth deve essere usato dentro AuthProvider");
  }
  return context;
}
