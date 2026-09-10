from __future__ import annotations

from app.models import GatewayCredentials, GatewaySchoolYear, HomeworkModel
import pytest
from fastapi import HTTPException

from app.service import (
    ClassevivaGatewayService,
    decode_action_token,
    encode_action_token,
    parse_meetings_snapshot,
    require_portal_url,
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


def test_require_portal_url_accepts_only_the_portal() -> None:
    assert require_portal_url("https://web.spaggiari.eu/sol/app/default/giustifica.php?id=1")
    assert require_portal_url("https://spaggiari.eu/x")

    # Gli URL delle azioni arrivano dal corpo della richiesta e vengono chiamati con la sessione
    # del portale gia' aperta: tutto cio' che non e' il portale, su https, va rifiutato.
    for hostile in [
        "http://169.254.169.254/latest/meta-data/",
        "https://169.254.169.254/latest/meta-data/",
        "http://web.spaggiari.eu/sol/app/default/giustifica.php",
        "https://web.spaggiari.eu.example.test/phish",
        "https://example.test/?next=web.spaggiari.eu",
        "file:///etc/passwd",
    ]:
        with pytest.raises(HTTPException) as raised:
            require_portal_url(hostile)
        assert raised.value.status_code == 400
