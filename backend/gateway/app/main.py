from __future__ import annotations

from fastapi import FastAPI

from .models import (
    AbsenceJustificationPayload,
    CommunicationDetailModel,
    GatewayEnvelope,
    HomeworkDetailModel,
    HomeworkModel,
    HomeworkSubmissionPayload,
    HomeworkSubmissionReceiptModel,
    MeetingBookingModel,
    MeetingJoinLinkModel,
    MeetingSnapshotModel,
    NoticeboardActionPayload,
)
from .service import ClassevivaGatewayService

app = FastAPI(title="Classeviva Expressive Gateway", version="0.1.0")
service = ClassevivaGatewayService()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/gateway/homeworks", response_model=list[HomeworkModel])
async def get_homeworks(envelope: GatewayEnvelope) -> list[HomeworkModel]:
    return await service.get_homeworks(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/homeworks/{homework_id}", response_model=HomeworkDetailModel)
async def get_homework_detail(homework_id: str, envelope: GatewayEnvelope) -> HomeworkDetailModel:
    return await service.get_homework_detail(envelope.credentials, envelope.schoolYear, homework_id)


@app.post("/gateway/homeworks/{homework_id}/submit", response_model=HomeworkSubmissionReceiptModel)
async def submit_homework(homework_id: str, envelope: GatewayEnvelope) -> HomeworkSubmissionReceiptModel:
    payload = HomeworkSubmissionPayload.model_validate(envelope.payload or {"homeworkId": homework_id})
    if not payload.homeworkId:
        payload.homeworkId = homework_id
    return await service.submit_homework(envelope.credentials, payload)


@app.post("/gateway/absences/{absence_id}/justify")
async def justify_absence(absence_id: str, envelope: GatewayEnvelope):
    payload = AbsenceJustificationPayload.model_validate(envelope.payload or {"absenceId": absence_id})
    if not payload.absenceId:
        payload.absenceId = absence_id
    return await service.justify_absence(envelope.credentials, envelope.schoolYear, payload)


@app.post("/gateway/noticeboard/{evt_code}/{pub_id}/reply", response_model=CommunicationDetailModel)
async def reply_noticeboard(evt_code: str, pub_id: str, envelope: GatewayEnvelope) -> CommunicationDetailModel:
    return await service.reply_noticeboard(
        envelope.credentials,
        NoticeboardActionPayload.model_validate(envelope.payload or {}),
    )


@app.post("/gateway/noticeboard/{evt_code}/{pub_id}/join", response_model=CommunicationDetailModel)
async def join_noticeboard(evt_code: str, pub_id: str, envelope: GatewayEnvelope) -> CommunicationDetailModel:
    return await service.join_noticeboard(
        envelope.credentials,
        NoticeboardActionPayload.model_validate(envelope.payload or {}),
    )


@app.post("/gateway/noticeboard/{evt_code}/{pub_id}/upload", response_model=CommunicationDetailModel)
async def upload_noticeboard(evt_code: str, pub_id: str, envelope: GatewayEnvelope) -> CommunicationDetailModel:
    return await service.upload_noticeboard(
        envelope.credentials,
        NoticeboardActionPayload.model_validate(envelope.payload or {}),
    )


@app.post("/gateway/meetings", response_model=MeetingSnapshotModel)
async def get_meetings(envelope: GatewayEnvelope) -> MeetingSnapshotModel:
    return await service.get_meetings(envelope.credentials)


@app.post("/gateway/meetings/book", response_model=MeetingBookingModel)
async def book_meeting(envelope: GatewayEnvelope) -> MeetingBookingModel:
    return await service.book_meeting(envelope.credentials, (envelope.payload or {}).get("slotId", ""))


@app.post("/gateway/meetings/{booking_id}/cancel")
async def cancel_meeting(booking_id: str, envelope: GatewayEnvelope):
    return await service.cancel_meeting(envelope.credentials, booking_id)


@app.post("/gateway/meetings/{booking_id}/join", response_model=MeetingJoinLinkModel)
async def join_meeting(booking_id: str, envelope: GatewayEnvelope) -> MeetingJoinLinkModel:
    del envelope
    return await service.join_meeting(booking_id)
