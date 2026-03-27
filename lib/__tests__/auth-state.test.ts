import { describe, expect, it } from "vitest";

import { authReducer, initialAuthState, isBusyAuthState } from "../auth-state";

describe("auth state reducer", () => {
  it("keeps the bootstrap state busy", () => {
    expect(isBusyAuthState(initialAuthState)).toBe(true);
  });

  it("restores a signed in session", () => {
    const state = authReducer(initialAuthState, {
      type: "RESTORE_SUCCESS",
      payload: {
        token: "token-1",
        sessionMode: "real",
        user: {
          id: "student-1",
          name: "Marco",
          surname: "Rossi",
          email: "marco@example.com",
          class: "5A",
          section: "A",
          school: "Liceo",
          schoolYear: "2025-2026",
        },
      },
    });

    expect(state.status).toBe("signed_in");
    expect(state.sessionMode).toBe("real");
    expect(state.token).toBe("token-1");
    expect(state.error).toBeNull();
  });

  it("switches to signed out when logout runs", () => {
    const signedIn = authReducer(initialAuthState, {
      type: "SIGN_IN_SUCCESS",
      payload: {
        token: "token-1",
        sessionMode: "real",
        user: {
          id: "student-1",
          name: "Marco",
          surname: "Rossi",
          email: "marco@example.com",
          class: "5A",
          section: "A",
          school: "Liceo",
          schoolYear: "2025-2026",
        },
      },
    });

    const signedOut = authReducer(signedIn, { type: "SIGN_OUT" });

    expect(signedOut.status).toBe("signed_out");
    expect(signedOut.user).toBeNull();
    expect(signedOut.sessionMode).toBeNull();
  });

  it("stores a readable error and exits busy state", () => {
    const signingIn = authReducer(initialAuthState, { type: "SIGN_IN_START" });
    const errored = authReducer(signingIn, {
      type: "SET_ERROR",
      payload: "Username o password non validi.",
    });

    expect(errored.status).toBe("signed_out");
    expect(errored.error).toBe("Username o password non validi.");
    expect(isBusyAuthState(errored)).toBe(false);
  });
});
