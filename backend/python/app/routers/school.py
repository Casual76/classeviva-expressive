from fastapi import APIRouter, Depends

from app.dependencies import get_gateway
from app.schemas import AbsenceResponse, AgendaResponse, LessonResponse
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1", tags=["school"])


@router.get("/lessons", response_model=list[LessonResponse])
async def get_lessons(
    day: str | None = None,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[LessonResponse]:
    return await gateway.get_lessons(day=day)


@router.get("/agenda", response_model=list[AgendaResponse])
async def get_agenda(
    day: str | None = None,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[AgendaResponse]:
    return await gateway.get_agenda(day=day)


@router.get("/absences", response_model=list[AbsenceResponse])
async def get_absences(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[AbsenceResponse]:
    return await gateway.get_absences()
