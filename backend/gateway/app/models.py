from __future__ import annotations

from typing import Any

from pydantic import BaseModel, Field


class GatewayCredentials(BaseModel):
    username: str
    password: str


class GatewaySchoolYear(BaseModel):
    startYear: int
    endYear: int


class GatewayEnvelope(BaseModel):
    credentials: GatewayCredentials
    schoolYear: GatewaySchoolYear
    payload: dict[str, Any] | None = None


class AttachmentPayload(BaseModel):
    fileName: str
    mimeType: str | None = None
    base64Content: str


class RemoteAttachment(BaseModel):
    id: str
    name: str
    url: str | None = None
    mimeType: str | None = None
    portalOnly: bool = False


class HomeworkModel(BaseModel):
    id: str
    subject: str
    description: str
    dueDate: str
    notes: str | None = None
    attachments: list[RemoteAttachment] = Field(default_factory=list)


class FeatureCapability(BaseModel):
    feature: str
    mode: str
    enabled: bool = True
    label: str = ""
    detail: str | None = None


class HomeworkDetailModel(BaseModel):
    homework: HomeworkModel
    fullText: str
    assignedDate: str | None = None
    teacher: str | None = None
    capability: FeatureCapability | None = None


class HomeworkSubmissionPayload(BaseModel):
    homeworkId: str
    text: str | None = None
    attachments: list[AttachmentPayload] = Field(default_factory=list)


class HomeworkSubmissionReceiptModel(BaseModel):
    homeworkId: str
    state: str
    submittedAt: str | None = None
    message: str | None = None
    remoteReference: str | None = None


class AbsenceRecordModel(BaseModel):
    id: str
    date: str
    type: str
    hours: int | None = None
    justified: bool
    canJustify: bool = False
    justificationDate: str | None = None
    justificationReason: str | None = None
    justifyUrl: str | None = None
    detailUrl: str | None = None


class AbsenceJustificationPayload(BaseModel):
    absenceId: str
    reasonText: str | None = None
    attachment: AttachmentPayload | None = None
    justifyUrl: str | None = None
    detailUrl: str | None = None
    submissionMode: str = "AUTO"


class CapabilityState(BaseModel):
    status: str = "AVAILABLE"
    label: str = ""
    detail: str | None = None


class NoticeboardAction(BaseModel):
    type: str
    label: str
    url: str | None = None


class NoticeboardAttachment(BaseModel):
    id: str
    name: str
    url: str | None = None
    mimeType: str | None = None
    portalOnly: bool = False
    action: NoticeboardAction | None = None


class CommunicationModel(BaseModel):
    id: str
    pubId: str
    evtCode: str
    title: str
    contentPreview: str
    sender: str
    date: str
    read: bool
    attachments: list[RemoteAttachment] = Field(default_factory=list)
    category: str | None = None
    needsAck: bool = False
    needsReply: bool = False
    needsJoin: bool = False
    needsFile: bool = False
    actions: list[NoticeboardAction] = Field(default_factory=list)
    noticeboardAttachments: list[NoticeboardAttachment] = Field(default_factory=list)
    capabilityState: CapabilityState = Field(default_factory=CapabilityState)


class CommunicationDetailModel(BaseModel):
    communication: CommunicationModel
    content: str
    replyText: str | None = None
    portalDetailUrl: str | None = None
    acknowledgeUrl: str | None = None
    replyUrl: str | None = None
    joinUrl: str | None = None
    fileUploadUrl: str | None = None
    actions: list[NoticeboardAction] = Field(default_factory=list)


class NoticeboardActionPayload(BaseModel):
    detail: CommunicationDetailModel
    text: str | None = None
    attachment: AttachmentPayload | None = None


class MeetingTeacherModel(BaseModel):
    id: str
    name: str
    subject: str | None = None


class MeetingSlotModel(BaseModel):
    id: str
    teacherId: str
    date: str
    startTime: str
    endTime: str | None = None
    location: str | None = None
    available: bool = True
    joinUrl: str | None = None


class MeetingBookingModel(BaseModel):
    id: str
    teacher: MeetingTeacherModel
    slot: MeetingSlotModel
    status: str
    bookingPosition: str | None = None


class MeetingJoinLinkModel(BaseModel):
    bookingId: str
    url: str


class MeetingSnapshotModel(BaseModel):
    teachers: list[MeetingTeacherModel] = Field(default_factory=list)
    slots: list[MeetingSlotModel] = Field(default_factory=list)
    bookings: list[MeetingBookingModel] = Field(default_factory=list)
