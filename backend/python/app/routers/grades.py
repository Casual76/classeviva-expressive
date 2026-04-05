from fastapi import APIRouter, Depends

from app.dependencies import get_gateway
from app.schemas import GradeResponse, PeriodResponse, SubjectResponse
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1/grades", tags=["grades"])


@router.get("", response_model=list[GradeResponse])
async def get_grades(
    period_code: str | None = None,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[GradeResponse]:
    return await gateway.get_grades(period_code=period_code)


@router.get("/periods", response_model=list[PeriodResponse])
async def get_periods(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[PeriodResponse]:
    return await gateway.get_periods()


@router.get("/subjects", response_model=list[SubjectResponse])
async def get_subjects(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[SubjectResponse]:
    return await gateway.get_subjects()
