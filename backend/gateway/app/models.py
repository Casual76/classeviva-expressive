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


class StudentProfileModel(BaseModel):
    id: str = ""
    name: str = ""
    surname: str = ""
    email: str = ""
    schoolClass: str = ""
    section: str = ""
    school: str = ""
    schoolYear: str = ""


class GradeModel(BaseModel):
    id: str
    subject: str
    valueLabel: str
    numericValue: float | None = None
    description: str | None = None
    date: str
    type: str
    weight: float | None = None
    notes: str | None = None
    period: str | None = None
    periodCode: str | None = None
    teacher: str | None = None
    color: str | None = None


class PeriodModel(BaseModel):
    code: str
    order: int
    description: str
    label: str
    isFinal: bool
    startDate: str
    endDate: str


class SubjectModel(BaseModel):
    id: str
    description: str
    order: int
    teachers: list[str] = Field(default_factory=list)


class AgendaItemModel(BaseModel):
    id: str
    title: str
    subtitle: str
    date: str
    time: str | None = None
    detail: str | None = None
    subject: str | None = None
    category: str
    sharePayload: str | None = None


class LessonModel(BaseModel):
    id: str
    subject: str
    date: str
    time: str
    durationMinutes: int
    topic: str | None = None
    teacher: str | None = None
    room: str | None = None


class MaterialItemModel(BaseModel):
    id: str
    teacherId: str
    teacherName: str
    folderId: str
    folderName: str
    title: str
    objectId: str
    objectType: str
    sharedAt: str
    capabilityState: CapabilityState = Field(default_factory=CapabilityState)
    attachments: list[RemoteAttachment] = Field(default_factory=list)


class DocumentItemModel(BaseModel):
    id: str
    title: str
    detail: str
    viewUrl: str | None = None
    confirmUrl: str | None = None
    capabilityState: CapabilityState = Field(default_factory=CapabilityState)


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
