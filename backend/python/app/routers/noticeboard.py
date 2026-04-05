from fastapi import APIRouter, Depends
from fastapi.responses import JSONResponse, RedirectResponse

from app.dependencies import get_gateway
from app.schemas import NoticeboardDetailResponse, NoticeboardItemResponse, NoticeboardReplyRequest
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1/noticeboard", tags=["noticeboard"])


@router.get("", response_model=list[NoticeboardItemResponse])
async def get_noticeboard(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[NoticeboardItemResponse]:
    return await gateway.get_noticeboard()


@router.get("/{notice_id}", response_model=NoticeboardDetailResponse)
async def get_noticeboard_detail(
    notice_id: str,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> NoticeboardDetailResponse:
    return await gateway.get_noticeboard_detail(notice_id)


@router.post("/{notice_id}/ack")
async def acknowledge_notice(
    notice_id: str,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> dict:
    return await gateway.acknowledge_notice(notice_id)


@router.post("/{notice_id}/join")
async def join_notice(
    notice_id: str,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> dict:
    return await gateway.join_notice(notice_id)


@router.post("/{notice_id}/reply")
async def reply_notice(
    notice_id: str,
    payload: NoticeboardReplyRequest,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> dict:
    return await gateway.reply_notice(notice_id, payload.message)


@router.get("/{notice_id}/download/{attachment_id}")
async def download_notice_attachment(
    notice_id: str,
    attachment_id: str,
    gateway: StubClassevivaGateway = Depends(get_gateway),
):
    detail = await gateway.get_noticeboard_detail(notice_id)
    attachment = next((item for item in detail.attachments if item.id == attachment_id), None)
    if attachment and attachment.download_url and attachment.download_url.startswith("http"):
        return RedirectResponse(url=attachment.download_url)
    return JSONResponse(
        status_code=202,
        content={
            "accepted": True,
            "notice_id": notice_id,
            "attachment_id": attachment_id,
            "detail": "Stub download endpoint wired. Replace with streaming proxy before cutover.",
        },
    )
