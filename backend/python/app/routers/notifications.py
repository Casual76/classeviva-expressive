from fastapi import APIRouter, Depends

from app.dependencies import get_gateway
from app.schemas import NotificationTestResponse
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1/notifications", tags=["notifications"])


@router.post("/test", response_model=NotificationTestResponse)
async def send_test_notification(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> NotificationTestResponse:
    return await gateway.send_test_notification()
