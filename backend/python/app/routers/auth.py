from fastapi import APIRouter, Depends

from app.dependencies import get_gateway
from app.schemas import SessionRequest, SessionResponse
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1/auth", tags=["auth"])


@router.post("/session", response_model=SessionResponse)
async def create_session(
    payload: SessionRequest,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> SessionResponse:
    return await gateway.create_session(payload.username, payload.password)
