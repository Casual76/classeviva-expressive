from __future__ import annotations

from app.schemas import (
    AbsenceResponse,
    AgendaResponse,
    DashboardResponse,
    DocumentResponse,
    GradeResponse,
    LessonResponse,
    MaterialResponse,
    NoticeboardAction,
    NoticeboardAttachment,
    NoticeboardDetailResponse,
    NoticeboardItemResponse,
    NotificationTestResponse,
    PeriodResponse,
    SessionResponse,
    SubjectResponse,
)


class StubClassevivaGateway:
    """
    Placeholder gateway for the future cutover.

    Replace this class with a real upstream adapter before switching the
    Android app away from direct Classeviva access.
    """

    async def create_session(self, username: str, password: str) -> SessionResponse:
        return SessionResponse(
            access_token=f"stub-token-for-{username}",
            student_id="stub-student",
            full_name="Demo Student",
        )

    async def get_dashboard(self) -> DashboardResponse:
        return DashboardResponse()

    async def get_periods(self) -> list[PeriodResponse]:
        return [
            PeriodResponse(
                code="ALL",
                label="Anno intero",
                description="Scaffold backend phase 2",
                current=True,
            )
        ]

    async def get_subjects(self) -> list[SubjectResponse]:
        return []

    async def get_grades(self, period_code: str | None = None) -> list[GradeResponse]:
        return []

    async def get_lessons(self, day: str | None = None) -> list[LessonResponse]:
        return []

    async def get_agenda(self, day: str | None = None) -> list[AgendaResponse]:
        return []

    async def get_absences(self) -> list[AbsenceResponse]:
        return []

    async def get_noticeboard(self) -> list[NoticeboardItemResponse]:
        return [
            NoticeboardItemResponse(
                id="stub-notice",
                title="Backend scaffold notice",
                preview="Noticeboard routes are available and ready for integration.",
                date="2026-03-29",
                unread=True,
                actions=[
                    NoticeboardAction(type="ack", label="Conferma"),
                    NoticeboardAction(type="join", label="Aderisci"),
                    NoticeboardAction(type="reply", label="Rispondi"),
                ],
                attachments=[
                    NoticeboardAttachment(
                        id="stub-attachment",
                        name="example.pdf",
                        mime_type="application/pdf",
                        download_url="/v1/noticeboard/stub-notice/download/stub-attachment",
                    )
                ],
            )
        ]

    async def get_noticeboard_detail(self, notice_id: str) -> NoticeboardDetailResponse:
        return NoticeboardDetailResponse(
            id=notice_id,
            title="Backend scaffold notice",
            body="This is a stub detail payload. Replace the gateway with a real Classeviva adapter.",
            date="2026-03-29",
            actions=[
                NoticeboardAction(type="ack", label="Conferma"),
                NoticeboardAction(type="join", label="Aderisci"),
                NoticeboardAction(type="reply", label="Rispondi"),
                NoticeboardAction(type="download", label="Scarica allegato"),
            ],
            attachments=[
                NoticeboardAttachment(
                    id="stub-attachment",
                    name="example.pdf",
                    mime_type="application/pdf",
                    download_url=f"/v1/noticeboard/{notice_id}/download/stub-attachment",
                )
            ],
        )

    async def acknowledge_notice(self, notice_id: str) -> dict:
        return {"accepted": True, "notice_id": notice_id}

    async def join_notice(self, notice_id: str) -> dict:
        return {"accepted": True, "notice_id": notice_id}

    async def reply_notice(self, notice_id: str, message: str) -> dict:
        return {"accepted": True, "notice_id": notice_id, "message": message}

    async def get_materials(self) -> list[MaterialResponse]:
        return []

    async def get_material(self, item_id: str) -> MaterialResponse:
        return MaterialResponse(id=item_id, title="Stub material")

    async def get_documents(self) -> list[DocumentResponse]:
        return []

    async def get_document(self, document_id: str) -> DocumentResponse:
        return DocumentResponse(id=document_id, title="Stub document")

    async def send_test_notification(self) -> NotificationTestResponse:
        return NotificationTestResponse()
