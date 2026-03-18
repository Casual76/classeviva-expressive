import { describe, it, expect, beforeEach, vi } from "vitest";
import { ClassevivaClient, Grade, Absence } from "../classeviva-client";

describe("ClassevivaClient", () => {
  let client: ClassevivaClient;

  beforeEach(() => {
    client = new ClassevivaClient();
  });

  describe("Authentication", () => {
    it("should initialize without authentication", () => {
      expect(client.isAuthenticated()).toBe(false);
    });

    it("should set token and student ID", () => {
      client.setToken("test-token", "student-123");
      expect(client.isAuthenticated()).toBe(true);
    });

    it("should clear authentication on logout", () => {
      client.setToken("test-token", "student-123");
      expect(client.isAuthenticated()).toBe(true);
      client.logout();
      expect(client.isAuthenticated()).toBe(false);
    });
  });

  describe("Grade Mapping", () => {
    it("should map grade data correctly", () => {
      const mockGrade = {
        id: "grade-1",
        materia: { desc: "Matematica" },
        votoDecimale: 8,
        descrVoto: "Buono",
        dataRegistrazione: "2026-03-18",
        tipo: { desc: "Compito" },
        peso: 1,
        note: "Bene",
      };

      // Use reflection to access private method for testing
      const mapGrade = (client as any).mapGrade.bind(client);
      const mapped = mapGrade(mockGrade);

      expect(mapped.id).toBe("grade-1");
      expect(mapped.subject).toBe("Matematica");
      expect(mapped.grade).toBe(8);
      expect(mapped.description).toBe("Buono");
      expect(mapped.type).toBe("Compito");
      expect(mapped.weight).toBe(1);
    });

    it("should handle missing grade fields", () => {
      const mockGrade = {
        id: "grade-2",
        grade: 7,
      };

      const mapGrade = (client as any).mapGrade.bind(client);
      const mapped = mapGrade(mockGrade);

      expect(mapped.subject).toBe("");
      expect(mapped.grade).toBe(7);
      expect(mapped.type).toBe("");
    });
  });

  describe("Absence Mapping", () => {
    it("should map absence data correctly", () => {
      const mockAbsence = {
        id: "absence-1",
        data: "2026-03-18",
        tipo: "assenza",
        ore: 6,
        giustificata: true,
        dataGiustificazione: "2026-03-19",
        motivoGiustificazione: "Malattia",
      };

      const mapAbsence = (client as any).mapAbsence.bind(client);
      const mapped = mapAbsence(mockAbsence);

      expect(mapped.id).toBe("absence-1");
      expect(mapped.date).toBe("2026-03-18");
      expect(mapped.type).toBe("assenza");
      expect(mapped.hours).toBe(6);
      expect(mapped.justified).toBe(true);
      expect(mapped.justificationReason).toBe("Malattia");
    });

    it("should handle different absence types", () => {
      const types = ["assenza", "ritardo", "uscita"];

      types.forEach((type) => {
        const mockAbsence = {
          id: `absence-${type}`,
          data: "2026-03-18",
          tipo: type,
          giustificata: false,
        };

        const mapAbsence = (client as any).mapAbsence.bind(client);
        const mapped = mapAbsence(mockAbsence);

        expect(mapped.type).toBe(type);
        expect(mapped.justified).toBe(false);
      });
    });
  });

  describe("Profile Mapping", () => {
    it("should map profile data correctly", () => {
      const mockProfile = {
        id: "student-123",
        firstName: "Marco",
        lastName: "Rossi",
        email: "marco.rossi@example.com",
        classe: { desc: "5A", sezione: "A" },
        scuola: { desc: "Liceo Scientifico" },
        anno: "2025-2026",
      };

      const mapProfile = (client as any).mapProfile.bind(client);
      const mapped = mapProfile(mockProfile);

      expect(mapped.id).toBe("student-123");
      expect(mapped.name).toBe("Marco");
      expect(mapped.surname).toBe("Rossi");
      expect(mapped.email).toBe("marco.rossi@example.com");
      expect(mapped.class).toBe("5A");
      expect(mapped.section).toBe("A");
      expect(mapped.school).toBe("Liceo Scientifico");
      expect(mapped.schoolYear).toBe("2025-2026");
    });
  });

  describe("Error Handling", () => {
    it("should handle network errors", () => {
      const error = {
        request: {},
        message: "Network error",
      };

      const handleError = (client as any).handleError.bind(client);
      const result = handleError(error, "Test error");

      expect(result.code).toBe("NETWORK_ERROR");
      expect(result.message).toContain("connessione");
    });

    it("should handle 401 unauthorized errors", () => {
      const error = {
        response: {
          status: 401,
          data: { message: "Unauthorized" },
        },
      };

      const handleError = (client as any).handleError.bind(client);
      const result = handleError(error, "Test error");

      expect(result.code).toBe("UNAUTHORIZED");
      expect(result.message).toContain("Credenziali");
    });

    it("should handle 429 rate limit errors", () => {
      const error = {
        response: {
          status: 429,
          data: { message: "Too many requests" },
        },
      };

      const handleError = (client as any).handleError.bind(client);
      const result = handleError(error, "Test error");

      expect(result.code).toBe("RATE_LIMITED");
      expect(result.message).toContain("Troppi tentativi");
    });

    it("should handle 500 server errors", () => {
      const error = {
        response: {
          status: 500,
          data: { message: "Internal server error" },
        },
      };

      const handleError = (client as any).handleError.bind(client);
      const result = handleError(error, "Test error");

      expect(result.code).toBe("SERVER_ERROR");
      expect(result.message).toContain("server");
    });
  });
});
