import { beforeAll, beforeEach, describe, expect, it, vi } from "vitest";

const storage = new Map<string, string>();

vi.mock("react-native", () => ({
  Platform: { OS: "test" },
}));

vi.mock("@react-native-async-storage/async-storage", () => ({
  default: {
    getItem: vi.fn(async (key: string) => storage.get(key) ?? null),
    setItem: vi.fn(async (key: string, value: string) => {
      storage.set(key, value);
    }),
    removeItem: vi.fn(async (key: string) => {
      storage.delete(key);
    }),
  },
}));

let studentData!: typeof import("../student-data");
let appPreferences!: typeof import("../app-preferences");

beforeAll(async () => {
  studentData = await import("../student-data");
  appPreferences = await import("../app-preferences");
});

beforeEach(() => {
  storage.clear();
});

describe("student data view models", () => {
  it("maps grades into compact cards", () => {
    const model = studentData.toGradeRowViewModel({
      id: "grade-1",
      subject: "Matematica",
      grade: 8,
      date: "2026-03-18",
      type: "Compito",
      notes: "Buon recupero",
    });

    expect(model.subject).toBe("Matematica");
    expect(model.numericValue).toBe(8);
    expect(model.tone).toBe("success");
    expect(model.detail).toContain("Buon recupero");
  });

  it("groups agenda items by date and sorts lessons by slot", () => {
    const secondLesson = studentData.lessonToAgendaItem({
      id: "lesson-2",
      subject: "Storia",
      date: "2026-03-20",
      time: "",
      duration: 60,
      topic: "Illuminismo",
      slot: 2,
    });
    const firstLesson = studentData.lessonToAgendaItem({
      id: "lesson-1",
      subject: "Italiano",
      date: "2026-03-20",
      time: "",
      duration: 60,
      topic: "Promessi Sposi",
      slot: 1,
    });
    const homework = studentData.homeworkToAgendaItem({
      id: "homework-1",
      subject: "Matematica",
      description: "Esercizi pagina 45",
      dueDate: "2026-03-20",
    });

    const grouped = studentData.groupAgendaByDate([secondLesson, homework, firstLesson]);

    expect(grouped).toHaveLength(1);
    expect(grouped[0]?.items).toHaveLength(3);
    expect(grouped[0]?.items[0]?.slot).toBe(1);
    expect(grouped[0]?.items[1]?.slot).toBe(2);
  });

  it("sanitizes agenda details by removing ISO prefixes and duplicate titles", () => {
    const item = studentData.agendaEventToAgendaItem({
      id: "agenda-1",
      title: "Verifica scritta di chimica",
      description: "2026-03-31T00:00:00+02:00 Verifica scritta di chimica (1h) argomento: acidi e basi",
      rawDescription: "2026-03-31T00:00:00+02:00 Verifica scritta di chimica (1h) argomento: acidi e basi",
      date: "2026-03-31",
      rawDate: "2026-03-31T00:00:00+02:00",
      time: "",
      category: "assessment",
      subject: "Chimica",
      slot: 1,
    });

    expect(item.detail).toContain("argomento");
    expect(item.detail).not.toContain("2026-03-31T00:00:00+02:00");
    expect(item.detail).not.toMatch(/^Verifica scritta di chimica/i);
  });

  it("maps absences with status wording", () => {
    const model = studentData.toAbsenceRecordViewModel({
      id: "absence-1",
      date: "2026-03-18",
      type: "ritardo",
      justified: false,
      justificationReason: "Autobus in ritardo",
    });

    expect(model.typeLabel).toBe("Ritardo");
    expect(model.statusLabel).toBe("Da giustificare");
    expect(model.tone).toBe("warning");
  });

  it("computes weighted goals for each subject", () => {
    const rows = [
      studentData.toGradeRowViewModel({
        id: "grade-1",
        subject: "Matematica",
        grade: 7,
        date: "2026-03-10",
        type: "Compito",
        weight: 2,
      }),
      studentData.toGradeRowViewModel({
        id: "grade-2",
        subject: "Matematica",
        grade: 6,
        date: "2026-03-18",
        type: "Orale",
        weight: 1,
      }),
    ];

    const summaries = studentData.summarizeGrades(rows, {
      Matematica: {
        studentId: "student-1",
        subject: "Matematica",
        targetAverage: 7.5,
        updatedAt: "2026-03-28T10:00:00.000Z",
      },
    });
    const subject = summaries.subjectSummaries[0];

    expect(subject?.weightedAverageNumeric).toBe(6.7);
    expect(subject?.goalStatus).toBe("required");
    expect(subject?.goalMessage).toContain("10,0");
  });

  it("selects the current period from API dates", () => {
    const current = studentData.getCurrentPeriod(
      [
        {
          code: "T1",
          label: "Trimestre",
          description: "Primo periodo",
          startDate: "2025-09-01",
          endDate: "2025-12-31",
          order: 1,
          isFinal: false,
        },
        {
          code: "P2",
          label: "Pentamestre",
          description: "Secondo periodo",
          startDate: "2026-01-01",
          endDate: "2026-06-10",
          order: 2,
          isFinal: true,
        },
      ],
      "2026-03-28",
    );

    expect(current?.code).toBe("P2");
  });

  it("persists locally the grades that were already opened", async () => {
    await appPreferences.markGradeSeen("student-1", "grade-1");
    await appPreferences.markGradesSeen("student-1", ["grade-2", "grade-1"]);
    const seen = await appPreferences.readSeenGradeIds("student-1");

    expect(seen).toEqual(["grade-1", "grade-2"]);
  });
});
