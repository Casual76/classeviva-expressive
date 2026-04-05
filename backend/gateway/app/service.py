from __future__ import annotations

import base64
import json
import re
from dataclasses import dataclass
from datetime import UTC, date, datetime
from typing import Any
from urllib.parse import urljoin

import httpx
from bs4 import BeautifulSoup, Tag
from fastapi import HTTPException

from .models import (
    AbsenceJustificationPayload,
    AbsenceRecordModel,
    AttachmentPayload,
    CapabilityState,
    CommunicationDetailModel,
    FeatureCapability,
    GatewayCredentials,
    GatewaySchoolYear,
    HomeworkDetailModel,
    HomeworkModel,
    HomeworkSubmissionPayload,
    HomeworkSubmissionReceiptModel,
    MeetingBookingModel,
    MeetingJoinLinkModel,
    MeetingSlotModel,
    MeetingSnapshotModel,
    MeetingTeacherModel,
    NoticeboardActionPayload,
    RemoteAttachment,
)

REST_BASE_URL = "https://web.spaggiari.eu/rest/"
PORTAL_LOGIN_URL = "https://web.spaggiari.eu/home/app/default/login.php"
USER_AGENT = "CVVS/std/4.1.7 Android/10"
DEV_API_KEY = "Tg1NWEwNGIgIC0K"


@dataclass(slots=True)
class RestContext:
    token: str
    student_id: str


@dataclass(slots=True)
class PortalSession:
    client: httpx.AsyncClient
    landing_html: str
    landing_url: str


class ClassevivaGatewayService:
    def __init__(self, transport: httpx.AsyncBaseTransport | None = None) -> None:
        self._transport = transport

    async def get_homeworks(
        self,
        credentials: GatewayCredentials,
        school_year: GatewaySchoolYear,
    ) -> list[HomeworkModel]:
        context = await self._login_rest(credentials)
        payload = await self._rest_json(
            path=f"v1/students/{context.student_id}/homeworks",
            token=context.token,
        )
        items = extract_array(payload, "homeworks", "items", "agenda")
        return [
            homework
            for homework in (normalize_homework(item) for item in items)
            if is_in_school_year(homework.dueDate, school_year)
        ]

    async def get_homework_detail(
        self,
        credentials: GatewayCredentials,
        school_year: GatewaySchoolYear,
        homework_id: str,
    ) -> HomeworkDetailModel:
        raw_id = decode_action_token(homework_id).get("homeworkId", homework_id)
        homeworks = await self.get_homeworks(credentials, school_year)
        homework = next((item for item in homeworks if item.id == raw_id), None)
        if homework is None:
            raise HTTPException(status_code=404, detail="Compito non trovato.")

        action_url = await self._discover_homework_action(credentials, homework)
        if action_url:
            homework.id = encode_action_token(
                {
                    "type": "homework-submit",
                    "homeworkId": raw_id,
                    "submitUrl": action_url,
                }
            )
        return HomeworkDetailModel(
            homework=homework,
            fullText="\n\n".join(part for part in [homework.description, homework.notes] if part).strip()
            or homework.description,
            assignedDate=homework.dueDate,
            capability=FeatureCapability(
                feature="HOMEWORKS",
                mode="GATEWAY" if action_url else "DIRECT_REST",
                enabled=True,
                label="Disponibile",
                detail="Consegna via gateway." if action_url else "Dettaglio disponibile, consegna tenant-dependent.",
            ),
        )

    async def submit_homework(
        self,
        credentials: GatewayCredentials,
        payload: HomeworkSubmissionPayload,
    ) -> HomeworkSubmissionReceiptModel:
        token = decode_action_token(payload.homeworkId)
        submit_url = token.get("submitUrl")
        if not submit_url:
            raise HTTPException(status_code=501, detail="Il tenant non espone ancora un flusso compiti inviabile dal gateway.")

        session = await self._login_portal(credentials)
        await self._submit_portal_action(
            session=session,
            page_url=submit_url,
            direct_url=submit_url,
            keywords=["consegna", "restituisci", "invia", "carica", "upload"],
            text_value=payload.text,
            attachment=payload.attachments[0] if payload.attachments else None,
        )
        return HomeworkSubmissionReceiptModel(
            homeworkId=payload.homeworkId,
            state="SUCCESS",
            submittedAt=datetime.now(UTC).isoformat(),
            message="Compito inviato tramite gateway.",
        )

    async def justify_absence(
        self,
        credentials: GatewayCredentials,
        school_year: GatewaySchoolYear,
        payload: AbsenceJustificationPayload,
    ) -> list[AbsenceRecordModel]:
        target_url = payload.justifyUrl or payload.detailUrl
        if not target_url:
            raise HTTPException(status_code=501, detail="Il tenant non espone un URL utilizzabile per la giustificazione.")

        session = await self._login_portal(credentials)
        await self._submit_portal_action(
            session=session,
            page_url=target_url,
            direct_url=payload.justifyUrl,
            keywords=["giustifica", "giustificazione", "justify", "conferma"],
            text_value=payload.reasonText,
            attachment=payload.attachment,
        )
        return await self.get_absences(credentials, school_year)

    async def reply_noticeboard(
        self,
        credentials: GatewayCredentials,
        payload: NoticeboardActionPayload,
    ) -> CommunicationDetailModel:
        session = await self._login_portal(credentials)
        detail = payload.detail
        await self._submit_portal_action(
            session=session,
            page_url=detail.replyUrl or detail.portalDetailUrl,
            direct_url=detail.replyUrl,
            keywords=["rispondi", "risposta", "reply", "invia"],
            text_value=payload.text,
            attachment=None,
        )
        detail.replyText = payload.text
        return detail

    async def join_noticeboard(
        self,
        credentials: GatewayCredentials,
        payload: NoticeboardActionPayload,
    ) -> CommunicationDetailModel:
        session = await self._login_portal(credentials)
        detail = payload.detail
        await self._submit_portal_action(
            session=session,
            page_url=detail.joinUrl or detail.portalDetailUrl,
            direct_url=detail.joinUrl,
            keywords=["adesione", "aderisci", "join", "partecipa"],
            text_value=None,
            attachment=None,
        )
        return detail

    async def upload_noticeboard(
        self,
        credentials: GatewayCredentials,
        payload: NoticeboardActionPayload,
    ) -> CommunicationDetailModel:
        session = await self._login_portal(credentials)
        detail = payload.detail
        await self._submit_portal_action(
            session=session,
            page_url=detail.fileUploadUrl or detail.portalDetailUrl,
            direct_url=detail.fileUploadUrl,
            keywords=["carica", "upload", "allega", "file"],
            text_value=None,
            attachment=payload.attachment,
        )
        return detail

    async def get_meetings(self, credentials: GatewayCredentials) -> MeetingSnapshotModel:
        session = await self._login_portal(credentials)
        page = await self._discover_portal_page(session, ["colloqui", "ricevimento", "prenot"])
        if page is None:
            raise HTTPException(status_code=501, detail="Modulo colloqui non rilevato per il tenant corrente.")
        snapshot = parse_meetings_snapshot(page[0], page[1])
        if not snapshot.slots and not snapshot.bookings:
            raise HTTPException(status_code=501, detail="Modulo colloqui rilevato ma non ancora parsabile in modo affidabile.")
        return snapshot

    async def book_meeting(self, credentials: GatewayCredentials, slot_id: str) -> MeetingBookingModel:
        slot_token = decode_action_token(slot_id)
        submit_url = slot_token.get("submitUrl")
        if not submit_url:
            raise HTTPException(status_code=501, detail="Slot colloquio non prenotabile dal gateway.")

        session = await self._login_portal(credentials)
        await self._submit_portal_action(
            session=session,
            page_url=submit_url,
            direct_url=submit_url,
            keywords=["prenota", "book", "conferma"],
            text_value=None,
            attachment=None,
        )
        teacher = MeetingTeacherModel(
            id=slot_token.get("teacherId", "teacher"),
            name=slot_token.get("teacherName", "Docente"),
            subject=slot_token.get("subject"),
        )
        slot = MeetingSlotModel(
            id=slot_id,
            teacherId=teacher.id,
            date=slot_token.get("date", date.today().isoformat()),
            startTime=slot_token.get("startTime", "00:00"),
            endTime=slot_token.get("endTime"),
            location=slot_token.get("location"),
            available=False,
            joinUrl=slot_token.get("joinUrl"),
        )
        return MeetingBookingModel(
            id=encode_action_token(
                {
                    "type": "meeting-booking",
                    "cancelUrl": slot_token.get("cancelUrl"),
                    "joinUrl": slot_token.get("joinUrl"),
                    "teacherId": teacher.id,
                    "teacherName": teacher.name,
                    "subject": teacher.subject,
                    "date": slot.date,
                    "startTime": slot.startTime,
                    "endTime": slot.endTime,
                    "location": slot.location,
                }
            ),
            teacher=teacher,
            slot=slot,
            status="BOOKED",
        )

    async def cancel_meeting(self, credentials: GatewayCredentials, booking_id: str) -> list[MeetingBookingModel]:
        token = decode_action_token(booking_id)
        cancel_url = token.get("cancelUrl")
        if not cancel_url:
            raise HTTPException(status_code=501, detail="Prenotazione non annullabile dal gateway.")
        session = await self._login_portal(credentials)
        await self._submit_portal_action(
            session=session,
            page_url=cancel_url,
            direct_url=cancel_url,
            keywords=["annulla", "cancel", "rimuovi"],
            text_value=None,
            attachment=None,
        )
        return []

    async def join_meeting(self, booking_id: str) -> MeetingJoinLinkModel:
        token = decode_action_token(booking_id)
        join_url = token.get("joinUrl")
        if not join_url:
            raise HTTPException(status_code=501, detail="Link colloquio non disponibile.")
        return MeetingJoinLinkModel(bookingId=booking_id, url=join_url)

    async def get_absences(
        self,
        credentials: GatewayCredentials,
        school_year: GatewaySchoolYear,
    ) -> list[AbsenceRecordModel]:
        context = await self._login_rest(credentials)
        begin, end = school_year_bounds(school_year)
        payload = await self._rest_json(
            path=f"v1/students/{context.student_id}/absences/details/{begin}/{end}",
            token=context.token,
        )
        return [normalize_absence(item) for item in extract_array(payload, "events", "absences", "items")]

    async def _login_rest(self, credentials: GatewayCredentials) -> RestContext:
        async with httpx.AsyncClient(
            base_url=REST_BASE_URL,
            headers={"User-Agent": USER_AGENT, "Z-Dev-ApiKey": DEV_API_KEY, "Content-Type": "application/json"},
            transport=self._transport,
            follow_redirects=True,
            timeout=20.0,
        ) as client:
            response = await client.post(
                "v1/auth/login",
                json={
                    "uid": credentials.username,
                    "pass": credentials.password,
                    "ident": None,
                    "app": "CVVS",
                    "login": "0",
                    "multipleToken": "multiple",
                },
            )
            response.raise_for_status()
            payload = response.json()
            token = payload.get("token")
            if not token:
                raise HTTPException(status_code=401, detail="Classeviva non ha restituito un token valido.")
            student_id = normalize_student_id(credentials.username) or normalize_student_id(payload.get("ident")) or normalize_student_id(payload.get("usrId")) or normalize_student_id(payload.get("userId"))
            if not student_id:
                raise HTTPException(status_code=401, detail="Studente non identificato dopo il login.")
            return RestContext(token=token, student_id=student_id)

    async def _rest_json(self, path: str, token: str) -> dict[str, Any]:
        async with httpx.AsyncClient(
            base_url=REST_BASE_URL,
            headers={
                "User-Agent": USER_AGENT,
                "Z-Dev-ApiKey": DEV_API_KEY,
                "Z-Auth-Token": token,
                "Content-Type": "application/json",
            },
            transport=self._transport,
            follow_redirects=True,
            timeout=20.0,
        ) as client:
            response = await client.get(path)
            response.raise_for_status()
            return response.json()

    async def _login_portal(self, credentials: GatewayCredentials) -> PortalSession:
        client = httpx.AsyncClient(
            headers={"User-Agent": USER_AGENT},
            transport=self._transport,
            follow_redirects=True,
            timeout=20.0,
        )
        login_page = await client.get(PORTAL_LOGIN_URL)
        login_page.raise_for_status()
        soup = BeautifulSoup(login_page.text, "html.parser")
        form = soup.find("form")
        if form is None:
            await client.aclose()
            raise HTTPException(status_code=501, detail="Form di login portale non trovato.")
        payload = {}
        for hidden in form.select("input[type=hidden]"):
            name = hidden.get("name")
            if name:
                payload[name] = hidden.get("value", "")
        payload[find_login_field(form, ["login", "user", "uid"]) or "login"] = credentials.username
        payload[find_login_field(form, ["password", "pass"]) or "password"] = credentials.password
        action = urljoin(str(login_page.url), form.get("action") or "")
        response = await client.post(action or PORTAL_LOGIN_URL, data=payload)
        response.raise_for_status()
        return PortalSession(client=client, landing_html=response.text, landing_url=str(response.url))

    async def _discover_homework_action(self, credentials: GatewayCredentials, homework: HomeworkModel) -> str | None:
        session = await self._login_portal(credentials)
        page = await self._discover_portal_page(session, ["compiti", "homework", "agenda", "lavori"])
        if page is None:
            return None
        html, base_url = page
        soup = BeautifulSoup(html, "html.parser")
        for node in soup.find_all(["a", "button", "form", "tr", "div", "li"]):
            text = normalize_text(node.get_text(" ", strip=True)).lower()
            if homework.description and homework.description.lower()[:40] in text:
                url = extract_click_target(node, base_url)
                if url:
                    return url
        for node in soup.find_all(["a", "button"]):
            text = normalize_text(node.get_text(" ", strip=True)).lower()
            if any(keyword in text for keyword in ["consegna", "restituisci", "upload", "carica"]):
                url = extract_click_target(node, base_url)
                if url:
                    return url
        return None

    async def _discover_portal_page(self, session: PortalSession, keywords: list[str]) -> tuple[str, str] | None:
        landing = BeautifulSoup(session.landing_html, "html.parser")
        if soup_matches(landing, keywords):
            return session.landing_html, session.landing_url
        for element in landing.select("a[href], button[formaction], input[formaction]"):
            target = extract_click_target(element, session.landing_url)
            if not target:
                continue
            source = normalize_text(" ".join(filter(None, [element.get_text(" ", strip=True), element.get("title"), element.get("aria-label"), target]))).lower()
            if any(keyword in source for keyword in keywords):
                response = await session.client.get(target)
                response.raise_for_status()
                return response.text, str(response.url)
        return None

    async def _submit_portal_action(
        self,
        session: PortalSession,
        page_url: str | None,
        direct_url: str | None,
        keywords: list[str],
        text_value: str | None,
        attachment: AttachmentPayload | None,
    ) -> None:
        if not page_url and not direct_url:
            raise HTTPException(status_code=501, detail="Azione portale non individuata.")
        for candidate in [url for url in [page_url, direct_url] if url]:
            response = await session.client.get(candidate)
            response.raise_for_status()
            soup = BeautifulSoup(response.text, "html.parser")
            form = find_matching_form(soup, keywords)
            if form is not None:
                await submit_form(session.client, str(response.url), form, keywords, text_value, attachment)
                return
            link = find_matching_link(soup, keywords, str(response.url))
            if link:
                follow = await session.client.get(link)
                follow.raise_for_status()
                return
        if direct_url and not text_value and attachment is None:
            response = await session.client.get(direct_url)
            response.raise_for_status()
            return
        raise HTTPException(status_code=501, detail="Azione tenant non parsabile dal gateway.")


def extract_array(payload: dict[str, Any], *keys: str) -> list[dict[str, Any]]:
    for key in keys:
        value = payload.get(key)
        if isinstance(value, list):
            return [item for item in value if isinstance(item, dict)]
        if isinstance(value, dict):
            nested = extract_array(value, key, "items", "events", "data")
            if nested:
                return nested
    return []


def normalize_homework(item: dict[str, Any]) -> HomeworkModel:
    attachments = []
    raw_attachments = item.get("allegati") or item.get("attachments") or []
    if isinstance(raw_attachments, list):
        for attachment in raw_attachments:
            if isinstance(attachment, dict):
                attachments.append(
                    RemoteAttachment(
                        id=str(attachment.get("id") or attachment.get("attachId") or attachment.get("name") or "attachment"),
                        name=normalize_text(attachment.get("name") or attachment.get("fileName") or attachment.get("desc") or "Allegato"),
                        url=attachment.get("url") or attachment.get("link"),
                        mimeType=attachment.get("mimeType") or attachment.get("contentType"),
                        portalOnly=False,
                    )
                )
    return HomeworkModel(
        id=str(item.get("id") or item.get("hwId") or item.get("evtId") or item.get("homeworkId") or ""),
        subject=normalize_text(item.get("subjectDesc") or item.get("subject") or "Materia"),
        description=normalize_text(item.get("contenuto") or item.get("description") or item.get("notes") or item.get("title") or "Compito"),
        dueDate=normalize_date(item.get("dataConsegna") or item.get("dueDate") or item.get("date") or item.get("evtDate")),
        notes=normalize_text(item.get("note") or item.get("notesForFamily") or item.get("notes")) or None,
        attachments=attachments,
    )


def normalize_absence(item: dict[str, Any]) -> AbsenceRecordModel:
    evt_code = str(item.get("evtCode") or item.get("code") or item.get("eventCode") or item.get("type") or "").upper()
    source = " ".join(
        normalize_text(value)
        for value in [item.get("tipo"), item.get("title"), item.get("description"), item.get("evtText"), item.get("notes")]
        if value
    ).lower()
    absence_type = "ABSENCE"
    if evt_code == "RTD" or "ritard" in source or "late" in source:
        absence_type = "LATE"
    elif evt_code in {"UXC", "USC"} or "uscita" in source or "exit" in source:
        absence_type = "EXIT"
    hours = item.get("hoursAbsence") or item.get("ore") or item.get("hours") or item.get("hour") or item.get("evtHPos")
    return AbsenceRecordModel(
        id=str(item.get("id") or item.get("evtId") or item.get("absenceId") or ""),
        date=normalize_date(item.get("evtDate") or item.get("date") or item.get("data")),
        type=absence_type,
        hours=int(hours) if str(hours).isdigit() else None,
        justified=bool(item.get("isJustified") or item.get("giustificata") or item.get("justified") or False),
        canJustify=bool(item.get("justifyUrl") or item.get("detailUrl")),
        justificationDate=normalize_optional_date(item.get("dataGiustificazione") or item.get("justificationDate")),
        justificationReason=normalize_text(item.get("justifReasonDesc") or item.get("motivoGiustificazione") or item.get("justificationReason")) or None,
        justifyUrl=item.get("justifyUrl"),
        detailUrl=item.get("detailUrl"),
    )


def school_year_bounds(school_year: GatewaySchoolYear) -> tuple[str, str]:
    return f"{school_year.startYear}0901", f"{school_year.endYear}0831"


def is_in_school_year(value: str, school_year: GatewaySchoolYear) -> bool:
    parsed = parse_date(value)
    if parsed is None:
        return False
    return date(school_year.startYear, 9, 1) <= parsed <= date(school_year.endYear, 8, 31)


def parse_date(value: str | None) -> date | None:
    if not value:
        return None
    raw = str(value).strip()
    if re.fullmatch(r"\d{8}", raw):
        return datetime.strptime(raw, "%Y%m%d").date()
    if re.fullmatch(r"\d{4}-\d{2}-\d{2}", raw):
        return datetime.strptime(raw, "%Y-%m-%d").date()
    if "T" in raw:
        try:
            return datetime.fromisoformat(raw.replace("Z", "+00:00")).date()
        except ValueError:
            return None
    return None


def normalize_date(value: Any) -> str:
    if value is None:
        return date.today().isoformat()
    parsed = parse_date(str(value))
    return parsed.isoformat() if parsed else str(value).strip()[:10]


def normalize_optional_date(value: Any) -> str | None:
    if value in (None, ""):
        return None
    return normalize_date(value)


def normalize_student_id(value: Any) -> str | None:
    if value in (None, ""):
        return None
    raw = str(value).strip()
    match = re.match(r"^[SG](\d+)(?:[A-Z]+)?$", raw, flags=re.IGNORECASE)
    if match:
        return match.group(1)
    digits = "".join(ch for ch in raw if ch.isdigit())
    return digits or raw


def normalize_text(value: Any) -> str:
    if value is None:
        return ""
    text = BeautifulSoup(str(value), "html.parser").get_text(" ", strip=True)
    return re.sub(r"\s+", " ", text.replace("\xa0", " ")).strip()


def encode_action_token(payload: dict[str, Any]) -> str:
    raw = json.dumps(payload, separators=(",", ":"), ensure_ascii=True).encode()
    return base64.urlsafe_b64encode(raw).decode().rstrip("=")


def decode_action_token(value: str) -> dict[str, Any]:
    if not value:
        return {}
    try:
        padded = value + "=" * (-len(value) % 4)
        decoded = base64.urlsafe_b64decode(padded.encode()).decode()
        payload = json.loads(decoded)
        return payload if isinstance(payload, dict) else {}
    except Exception:
        return {}


def find_login_field(form: Tag, keywords: list[str]) -> str | None:
    for input_tag in form.select("input"):
        name = (input_tag.get("name") or "").lower()
        if any(keyword in name for keyword in keywords):
            return input_tag.get("name")
    return None


def soup_matches(soup: BeautifulSoup, keywords: list[str]) -> bool:
    source = normalize_text(soup.get_text(" ", strip=True)).lower()
    return any(keyword in source for keyword in keywords)


def extract_click_target(element: Tag, base_url: str) -> str | None:
    href = element.get("href") or element.get("formaction")
    return urljoin(base_url, href) if href else None


def find_matching_form(soup: BeautifulSoup, keywords: list[str]) -> Tag | None:
    lowered = [keyword.lower() for keyword in keywords]
    for form in soup.select("form"):
        source = normalize_text(str(form)).lower()
        if any(keyword in source for keyword in lowered):
            return form
    return None


def find_matching_link(soup: BeautifulSoup, keywords: list[str], base_url: str) -> str | None:
    lowered = [keyword.lower() for keyword in keywords]
    for element in soup.select("a[href], button[formaction], input[formaction]"):
        source = normalize_text(" ".join(filter(None, [str(element), element.get_text(" ", strip=True)]))).lower()
        if any(keyword in source for keyword in lowered):
            return extract_click_target(element, base_url)
    return None


async def submit_form(
    client: httpx.AsyncClient,
    base_url: str,
    form: Tag,
    keywords: list[str],
    text_value: str | None,
    attachment: AttachmentPayload | None,
) -> None:
    action_url = urljoin(base_url, form.get("action") or "")
    method = (form.get("method") or "post").upper()
    params: dict[str, str] = {}
    for input_tag in form.select("input"):
        name = input_tag.get("name")
        if not name:
            continue
        input_type = (input_tag.get("type") or "").lower()
        if input_type == "hidden":
            params[name] = input_tag.get("value", "")
        elif input_type in {"checkbox", "radio"} and input_tag.has_attr("checked"):
            params[name] = input_tag.get("value") or "on"
        elif input_type == "submit" and source_matches(input_tag, keywords):
            params[name] = input_tag.get("value") or "1"

    button = next((candidate for candidate in form.select("button[name]") if source_matches(candidate, keywords)), None)
    if button is not None:
        params[button.get("name")] = button.get("value") or "1"

    text_field = form.select_one("textarea, input[type=text], input:not([type])")
    if text_value and text_field is not None:
        field_name = text_field.get("name") or text_field.get("id")
        if field_name:
            params[field_name] = text_value

    if attachment is None:
        response = await client.request(
            method=method,
            url=action_url,
            data=params if method != "GET" else None,
            params=params if method == "GET" else None,
        )
        response.raise_for_status()
        return

    file_input = form.select_one("input[type=file]")
    field_name = file_input.get("name") if file_input is not None else "file"
    response = await client.post(
        action_url,
        data=params,
        files={
            field_name: (
                attachment.fileName,
                base64.b64decode(attachment.base64Content.encode()),
                attachment.mimeType or "application/octet-stream",
            )
        },
    )
    response.raise_for_status()


def source_matches(node: Tag, keywords: list[str]) -> bool:
    source = normalize_text(str(node)).lower()
    return any(keyword.lower() in source for keyword in keywords)


def parse_meetings_snapshot(html: str, base_url: str) -> MeetingSnapshotModel:
    soup = BeautifulSoup(html, "html.parser")
    teachers: dict[str, MeetingTeacherModel] = {}
    slots: list[MeetingSlotModel] = []
    bookings: list[MeetingBookingModel] = []
    time_pattern = re.compile(r"(\d{1,2}:\d{2})")
    date_pattern = re.compile(r"(\d{4}-\d{2}-\d{2}|\d{2}/\d{2}/\d{4})")

    for row in soup.select("tr"):
        text = normalize_text(row.get_text(" ", strip=True))
        times = time_pattern.findall(text)
        if not times:
            continue
        date_match = date_pattern.search(text)
        teacher_name = text.split(times[0])[0].strip(" -:") or "Docente"
        teacher_id = teacher_name.lower().replace(" ", "-")
        teachers.setdefault(teacher_id, MeetingTeacherModel(id=teacher_id, name=teacher_name))
        target_url = find_matching_link(BeautifulSoup(str(row), "html.parser"), ["prenota", "book", "annulla", "cancel"], base_url)
        join_url = find_matching_link(BeautifulSoup(str(row), "html.parser"), ["join", "meet", "collegati", "entra"], base_url)
        token = encode_action_token(
            {
                "type": "meeting-slot",
                "submitUrl": target_url,
                "cancelUrl": target_url,
                "joinUrl": join_url,
                "teacherId": teacher_id,
                "teacherName": teacher_name,
                "date": normalize_date(date_match.group(1)) if date_match else date.today().isoformat(),
                "startTime": times[0].zfill(5),
                "endTime": times[1].zfill(5) if len(times) > 1 else None,
            }
        )
        slot = MeetingSlotModel(
            id=token,
            teacherId=teacher_id,
            date=normalize_date(date_match.group(1)) if date_match else date.today().isoformat(),
            startTime=times[0].zfill(5),
            endTime=times[1].zfill(5) if len(times) > 1 else None,
            available=bool(target_url),
            joinUrl=join_url,
        )
        if "prenot" in text.lower() and not slot.available:
            bookings.append(
                MeetingBookingModel(
                    id=encode_action_token(
                        {
                            "type": "meeting-booking",
                            "cancelUrl": target_url,
                            "joinUrl": join_url,
                            "teacherId": teacher_id,
                            "teacherName": teacher_name,
                            "date": slot.date,
                            "startTime": slot.startTime,
                            "endTime": slot.endTime,
                        }
                    ),
                    teacher=teachers[teacher_id],
                    slot=slot,
                    status="BOOKED",
                )
            )
        else:
            slots.append(slot)
    return MeetingSnapshotModel(teachers=list(teachers.values()), slots=slots, bookings=bookings)
