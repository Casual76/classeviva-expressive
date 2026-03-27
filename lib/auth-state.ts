import type { StudentProfile } from "@/lib/classeviva-client";

export type AuthStatus = "restoring" | "signed_out" | "signing_in" | "signed_in";
export type SessionMode = "real" | null;

export interface AuthState {
  status: AuthStatus;
  user: StudentProfile | null;
  token: string | null;
  sessionMode: SessionMode;
  error: string | null;
}

export type AuthAction =
  | { type: "RESTORE_START" }
  | { type: "RESTORE_SUCCESS"; payload: { token: string | null; user: StudentProfile | null; sessionMode: SessionMode } }
  | { type: "SIGN_IN_START" }
  | { type: "SIGN_IN_SUCCESS"; payload: { token: string | null; user: StudentProfile; sessionMode: Exclude<SessionMode, null> } }
  | { type: "SIGN_OUT" }
  | { type: "SET_ERROR"; payload: string | null };

export const initialAuthState: AuthState = {
  status: "restoring",
  user: null,
  token: null,
  sessionMode: null,
  error: null,
};

export function authReducer(state: AuthState, action: AuthAction): AuthState {
  switch (action.type) {
    case "RESTORE_START":
      return {
        ...state,
        status: "restoring",
        error: null,
      };
    case "RESTORE_SUCCESS":
      return {
        status: action.payload.user ? "signed_in" : "signed_out",
        token: action.payload.token,
        user: action.payload.user,
        sessionMode: action.payload.sessionMode,
        error: null,
      };
    case "SIGN_IN_START":
      return {
        ...state,
        status: "signing_in",
        error: null,
      };
    case "SIGN_IN_SUCCESS":
      return {
        status: "signed_in",
        token: action.payload.token,
        user: action.payload.user,
        sessionMode: action.payload.sessionMode,
        error: null,
      };
    case "SIGN_OUT":
      return {
        status: "signed_out",
        token: null,
        user: null,
        sessionMode: null,
        error: null,
      };
    case "SET_ERROR":
      return {
        ...state,
        status: state.user ? "signed_in" : "signed_out",
        error: action.payload,
      };
    default:
      return state;
  }
}

export function isBusyAuthState(state: AuthState): boolean {
  return state.status === "restoring" || state.status === "signing_in";
}
