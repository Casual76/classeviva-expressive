from __future__ import annotations

from app.models import GatewayCredentials, GatewaySchoolYear, HomeworkModel
from app.service import (
    ClassevivaGatewayService,
    decode_action_token,
    encode_action_token,
    parse_meetings_snapshot,
    school_year_bounds,
)


def test_action_token_roundtrip() -> None:
    payload = {"type": "meeting-slot", "submitUrl": "https://example.test/book", "date": "2026-04-07"}
    token = encode_action_token(payload)
    assert decode_action_token(token) == payload


def test_school_year_bounds() -> None:
    start, end = school_year_bounds(GatewaySchoolYear(startYear=2025, endYear=2026))
    assert start == "20250901"
    assert end == "20260831"


def test_parse_meetings_snapshot_builds_slots() -> None:
    html = """
    <table>
      <tr>
        <td>Prof. Rossi 2026-04-07 15:00 15:10</td>
        <td><a href="/book/slot-1">Prenota</a></td>
      </tr>
    </table>
    """
    snapshot = parse_meetings_snapshot(html, "https://example.test/meetings")
    assert len(snapshot.teachers) == 1
    assert len(snapshot.slots) == 1
    assert snapshot.slots[0].available is True


async def test_homework_detail_adds_submission_token(monkeypatch) -> None:
    service = ClassevivaGatewayService()

    async def fake_homeworks(credentials: GatewayCredentials, school_year: GatewaySchoolYear):
        del credentials, school_year
        return [
            HomeworkModel(
                id="hw-1",
                subject="Matematica",
                description="Esercizi pag. 12",
                dueDate="2026-04-07",
                notes="Consegnare in PDF",
            )
        ]

    async def fake_discovery(credentials: GatewayCredentials, homework: HomeworkModel):
        del credentials, homework
        return "https://example.test/homeworks/submit"

    monkeypatch.setattr(service, "get_homeworks", fake_homeworks)
    monkeypatch.setattr(service, "_discover_homework_action", fake_discovery)

    detail = await service.get_homework_detail(
        GatewayCredentials(username="student", password="secret"),
        GatewaySchoolYear(startYear=2025, endYear=2026),
        "hw-1",
    )
    assert decode_action_token(detail.homework.id)["submitUrl"] == "https://example.test/homeworks/submit"
