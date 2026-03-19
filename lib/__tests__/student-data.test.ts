import { describe, expect, it } from "vitest";

import {
  groupAgendaByDate,
  homeworkToAgendaItem,
  lessonToAgendaItem,
  toAbsenceRecordViewModel,
  toGradeRowViewModel,
} from "../student-data";

describe("student data view models", () => {
  it("maps grades into compact cards", () => {
    const model = toGradeRowViewModel({
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

  it("groups agenda items by date", () => {
    const lesson = lessonToAgendaItem({
      id: "lesson-1",
      subject: "Italiano",
      date: "2026-03-20",
      time: "08:00",
      duration: 60,
      topic: "Promessi Sposi",
    });
    const homework = homeworkToAgendaItem({
      id: "homework-1",
      subject: "Matematica",
      description: "Esercizi pagina 45",
      dueDate: "2026-03-20",
    });

    const grouped = groupAgendaByDate([lesson, homework]);

    expect(grouped).toHaveLength(1);
    expect(grouped[0]?.items).toHaveLength(2);
  });

  it("maps absences with status wording", () => {
    const model = toAbsenceRecordViewModel({
      id: "absence-1",
      date: "2026-03-18",
      type: "ritardo",
      justified: false,
      justificationReason: "Autobus in ritardo",
    });

    expect(model.typeLabel).toBe("Ritardo");
    expect(model.statusLabel).toBe("Da giustificare");
    expect(model.tone).toBe("error");
  });
});
