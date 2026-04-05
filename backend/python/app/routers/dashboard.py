from fastapi import APIRouter, Depends

from app.dependencies import get_gateway
from app.schemas import DashboardResponse
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1/dashboard", tags=["dashboard"])


@router.get("", response_model=DashboardResponse)
async def get_dashboard(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> DashboardResponse:
    return await gateway.get_dashboard()
