from __future__ import annotations

from fastapi import FastAPI

from .models import (
    AbsenceJustificationPayload,
    AgendaItemModel,
    CommunicationDetailModel,
    CommunicationModel,
    DocumentItemModel,
    GatewayEnvelope,
    GradeModel,
    HomeworkDetailModel,
    HomeworkModel,
    HomeworkSubmissionPayload,
    HomeworkSubmissionReceiptModel,
    LessonModel,
    MaterialItemModel,
    MeetingBookingModel,
    MeetingJoinLinkModel,
    MeetingSnapshotModel,
    NoticeboardActionPayload,
    PeriodModel,
    StudentProfileModel,
    SubjectModel,
)
from .service import ClassevivaGatewayService

app = FastAPI(title="Classeviva Expressive Gateway", version="0.1.0")
service = ClassevivaGatewayService()


@app.get("/health")
async def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/gateway/profile", response_model=StudentProfileModel)
async def get_profile(envelope: GatewayEnvelope) -> StudentProfileModel:
    return await service.get_profile(envelope.credentials)


@app.post("/gateway/grades", response_model=list[GradeModel])
async def get_grades(envelope: GatewayEnvelope) -> list[GradeModel]:
    return await service.get_grades(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/periods", response_model=list[PeriodModel])
async def get_periods(envelope: GatewayEnvelope) -> list[PeriodModel]:
    return await service.get_periods(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/subjects", response_model=list[SubjectModel])
async def get_subjects(envelope: GatewayEnvelope) -> list[SubjectModel]:
    return await service.get_subjects(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/agenda", response_model=list[AgendaItemModel])
async def get_agenda(envelope: GatewayEnvelope) -> list[AgendaItemModel]:
    return await service.get_agenda(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/lessons", response_model=list[LessonModel])
async def get_lessons(envelope: GatewayEnvelope) -> list[LessonModel]:
    return await service.get_lessons(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/noticeboard", response_model=list[CommunicationModel])
async def get_noticeboard(envelope: GatewayEnvelope) -> list[CommunicationModel]:
    return await service.get_noticeboard(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/materials", response_model=list[MaterialItemModel])
async def get_materials(envelope: GatewayEnvelope) -> list[MaterialItemModel]:
    return await service.get_materials(envelope.credentials, envelope.schoolYear)


@app.post("/gateway/documents", response_model=list[DocumentItemModel])
async def get_documents(envelope: GatewayEnvelope) -> list[DocumentItemModel]:
    return await service.get_documents(envelope.credentials, envelope.schoolYear)


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
