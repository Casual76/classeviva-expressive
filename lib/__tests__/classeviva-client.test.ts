import { beforeEach, describe, expect, it } from "vitest";

import {
  ClassevivaClient,
  normalizeAbsence,
  normalizeCommunication,
  normalizeGrade,
  normalizeLesson,
  normalizeNote,
  normalizeNoticeboardItemDetail,
  normalizeProfile,
} from "../classeviva-client";

describe("ClassevivaClient", () => {
  let client: ClassevivaClient;

  beforeEach(() => {
    client = new ClassevivaClient();
  });

  describe("Authentication", () => {
    it("initializes without authentication", () => {
      expect(client.isAuthenticated()).toBe(false);
    });

    it("sets token and student ID", () => {
      client.setToken("test-token", "student-123");
      expect(client.isAuthenticated()).toBe(true);
      expect(client.getStudentId()).toBe("student-123");
    });

    it("normalizes student IDs with Classeviva prefixes", () => {
      client.setToken("test-token", "S1234567");
      expect(client.getStudentId()).toBe("1234567");
    });

    it("normalizes student IDs with Classeviva suffixes", () => {
      client.setToken("test-token", "S1234567Q");
      expect(client.getStudentId()).toBe("1234567");
    });

    it("clears authentication on logout", () => {
      client.setToken("test-token", "student-123");
      client.logout();

      expect(client.isAuthenticated()).toBe(false);
      expect(client.getStudentId()).toBeNull();
    });
  });

  describe("Grade mapping", () => {
    it("maps modern grade payloads", () => {
      const mapped = normalizeGrade({
        evtId: 223044,
        subjectDesc: "Matematica",
        decimalValue: 8,
        displayValue: "8",
        evtDate: "2026-03-18",
        componentDesc: "Compito",
        weightFactor: 1,
        notesForFamily: "Buona verifica",
        periodDesc: "Trimestre",
      });

      expect(mapped.id).toBe("223044");
      expect(mapped.subject).toBe("Matematica");
      expect(mapped.grade).toBe(8);
      expect(mapped.type).toBe("Compito");
      expect(mapped.weight).toBe(1);
      expect(mapped.notes).toBe("Buona verifica");
      expect(mapped.period).toBe("Trimestre");
    });

    it("handles sparse grade payloads", () => {
      const mapped = normalizeGrade({
        id: "grade-2",
        grade: 7,
      });

      expect(mapped.subject).toBe("");
      expect(mapped.grade).toBe(7);
      expect(mapped.type).toBe("Valutazione");
    });
  });

  describe("Absence mapping", () => {
    it("maps standard absence payloads", () => {
      const mapped = normalizeAbsence({
        evtId: 100,
        evtDate: "2026-03-18",
        tipo: "assenza",
        ore: 6,
        giustificata: true,
        motivoGiustificazione: "Malattia",
      });

      expect(mapped.id).toBe("100");
      expect(mapped.date).toBe("2026-03-18");
      expect(mapped.type).toBe("assenza");
      expect(mapped.hours).toBe(6);
      expect(mapped.justified).toBe(true);
      expect(mapped.justificationReason).toBe("Malattia");
    });

    it("detects different absence types", () => {
      const ritardo = normalizeAbsence({ id: "r", data: "2026-03-18", tipo: "ritardo" });
      const uscita = normalizeAbsence({ id: "u", data: "2026-03-18", tipo: "uscita" });

      expect(ritardo.type).toBe("ritardo");
      expect(uscita.type).toBe("uscita");
    });
  });

  describe("Lesson mapping", () => {
    it("derives end time and slot information", () => {
      const mapped = normalizeLesson({
        evtId: 400,
        evtDate: "2026-03-18",
        subjectDesc: "Matematica",
        lessonHour: "08:00",
        duration: 50,
        lessonNum: 2,
      });

      expect(mapped.time).toBe("08:00");
      expect(mapped.endTime).toBe("08:50");
      expect(mapped.slot).toBe(2);
    });
  });

  describe("Communication mapping", () => {
    it("maps typed attachments and available noticeboard actions", () => {
      const communication = normalizeCommunication({
        id: "comm-1",
        pubId: "pub-1",
        evtCode: "EVT101",
        title: "Circolare",
        content: "Dettaglio sintetico",
        fileUrl: "https://example.com/allegato.pdf",
        needSign: true,
        needJoin: true,
        needReply: true,
      });

      const detail = normalizeNoticeboardItemDetail(
        {
          item: { content: "Testo completo" },
          reply: { text: "Rispondi tramite il portale." },
        },
        communication,
      );

      expect(communication.attachments).toHaveLength(1);
      expect(communication.attachments[0]?.fileName).toContain("Allegato");
      expect(communication.hasAttachments).toBe(true);
      expect(detail.actions.map((action) => action.type)).toEqual(
        expect.arrayContaining(["ack", "join", "reply", "download"]),
      );
      expect(detail.replyText).toBe("Rispondi tramite il portale.");
    });
  });

  describe("Notes mapping", () => {
    it("remaps category codes into clearer labels and severity", () => {
      const disciplinary = normalizeNote(
        {
          evtId: "note-1",
          evtDate: "2026-03-18",
          evtText: "Comportamento scorretto durante la lezione",
        },
        "NTST",
      );
      const annotation = normalizeNote(
        {
          evtId: "note-2",
          evtDate: "2026-03-19",
          evtText: "Compito non consegnato",
        },
        "NTCL",
      );

      expect(disciplinary.categoryLabel).toBe("Nota disciplinare");
      expect(disciplinary.severity).toBe("critical");
      expect(annotation.categoryLabel).toBe("Annotazione");
      expect(annotation.severity).toBe("info");
    });
  });

  describe("Profile mapping", () => {
    it("maps card payloads", () => {
      const mapped = normalizeProfile({
        card: {
          usrId: 8733880,
          firstName: "Marco",
          lastName: "Rossi",
          email: "marco.rossi@example.com",
          classe: { desc: "5A", sezione: "A" },
          schName: "Liceo Scientifico",
          schDedication: "Galileo Galilei",
          anno: "2025-2026",
        },
      });

      expect(mapped.id).toBe("8733880");
      expect(mapped.name).toBe("Marco");
      expect(mapped.surname).toBe("Rossi");
      expect(mapped.email).toBe("marco.rossi@example.com");
      expect(mapped.class).toBe("5A");
      expect(mapped.section).toBe("A");
      expect(mapped.school).toBe("Liceo Scientifico Galileo Galilei");
      expect(mapped.schoolYear).toBe("2025-2026");
    });

    it("normalizes prefixed profile identifiers", () => {
      const mapped = normalizeProfile({
        card: {
          ident: "S8733880",
          firstName: "Marco",
          lastName: "Rossi",
        },
      });

      expect(mapped.id).toBe("8733880");
    });

    it("normalizes profile identifiers with trailing suffixes", () => {
      const mapped = normalizeProfile({
        card: {
          ident: "S8733880I",
          firstName: "Marco",
          lastName: "Rossi",
        },
      });

      expect(mapped.id).toBe("8733880");
    });
  });

  describe("Error handling", () => {
    it("maps network errors", () => {
      const handleAxiosError = (client as any).handleAxiosError.bind(client);
      const result = handleAxiosError(
        {
          request: {},
          message: "Network error",
        },
        "Test error",
      );

      expect(result.code).toBe("NETWORK_ERROR");
      expect(result.message).toContain("Connessione");
    });

    it("maps 401 errors", () => {
      const handleAxiosError = (client as any).handleAxiosError.bind(client);
      const result = handleAxiosError(
        {
          response: {
            status: 401,
            data: { message: "Unauthorized" },
          },
        },
        "Test error",
      );

      expect(result.code).toBe("UNAUTHORIZED");
      expect(result.message).toContain("Username o password");
    });

    it("maps 429 errors", () => {
      const handleAxiosError = (client as any).handleAxiosError.bind(client);
      const result = handleAxiosError(
        {
          response: {
            status: 429,
            data: { message: "Too many requests" },
          },
        },
        "Test error",
      );

      expect(result.code).toBe("RATE_LIMITED");
      expect(result.message).toContain("Troppi tentativi");
    });

    it("maps rejected api key errors", () => {
      const handleAxiosError = (client as any).handleAxiosError.bind(client);
      const result = handleAxiosError(
        {
          response: {
            status: 400,
            data: { statusCode: 400, error: "203:CvvRestApi/apikey.3" },
          },
        },
        "Test error",
      );

      expect(result.code).toBe("API_KEY_REJECTED");
      expect(result.message).toContain("rifiutando temporaneamente");
      expect(result.message).toContain("portale ufficiale");
    });

    it("maps 500 errors", () => {
      const handleAxiosError = (client as any).handleAxiosError.bind(client);
      const result = handleAxiosError(
        {
          response: {
            status: 500,
            data: { message: "Internal server error" },
          },
        },
        "Test error",
      );

      expect(result.code).toBe("SERVER_ERROR");
      expect(result.message).toContain("Classeviva");
    });
  });
});
