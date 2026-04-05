from enum import Enum

from pydantic import BaseModel, Field


class ServiceStatus(str, Enum):
    stub = "stub"
    ready = "ready"


class ActionType(str, Enum):
    acknowledge = "ack"
    join = "join"
    reply = "reply"
    download = "download"
    upload = "upload"


class SessionRequest(BaseModel):
    username: str
    password: str


class SessionResponse(BaseModel):
    access_token: str
    refresh_token: str | None = None
    student_id: str
    full_name: str
    status: ServiceStatus = ServiceStatus.stub


class DashboardResponse(BaseModel):
    today_lessons: list[dict] = Field(default_factory=list)
    unseen_grades: list[dict] = Field(default_factory=list)
    unread_communications: list[dict] = Field(default_factory=list)
    status: ServiceStatus = ServiceStatus.stub


class PeriodResponse(BaseModel):
    code: str
    label: str
    description: str | None = None
    current: bool = False
    start_date: str | None = None
    end_date: str | None = None


class SubjectResponse(BaseModel):
    code: str | None = None
    label: str


class GradeResponse(BaseModel):
    id: str
    subject: str
    label: str
    numeric_value: float | None = None
    date: str
    type: str
    weight: float | None = None
    period_code: str | None = None


class LessonResponse(BaseModel):
    id: str
    subject: str
    date: str
    start_time: str | None = None
    end_time: str | None = None
    topic: str | None = None


class AgendaResponse(BaseModel):
    id: str
    category: str
    date: str
    title: str
    detail: str | None = None
    start_time: str | None = None
    end_time: str | None = None


class AbsenceResponse(BaseModel):
    id: str
    date: str
    type: str
    detail: str | None = None
    justified: bool | None = None


class NoticeboardAction(BaseModel):
    type: ActionType
    enabled: bool = True
    label: str | None = None
    url: str | None = None


class NoticeboardAttachment(BaseModel):
    id: str
    name: str
    mime_type: str | None = None
    download_url: str | None = None


class NoticeboardItemResponse(BaseModel):
    id: str
    title: str
    preview: str | None = None
    date: str
    unread: bool = False
    actions: list[NoticeboardAction] = Field(default_factory=list)
    attachments: list[NoticeboardAttachment] = Field(default_factory=list)


class NoticeboardDetailResponse(BaseModel):
    id: str
    title: str
    body: str | None = None
    date: str
    actions: list[NoticeboardAction] = Field(default_factory=list)
    attachments: list[NoticeboardAttachment] = Field(default_factory=list)
    status: ServiceStatus = ServiceStatus.stub


class NoticeboardReplyRequest(BaseModel):
    message: str


class MaterialResponse(BaseModel):
    id: str
    title: str
    folder_id: str | None = None
    kind: str | None = None
    source_url: str | None = None


class DocumentResponse(BaseModel):
    id: str
    title: str
    date: str | None = None
    source_url: str | None = None


class NotificationTestResponse(BaseModel):
    accepted: bool = True
    status: ServiceStatus = ServiceStatus.stub
    message: str = "Test notification request accepted"
