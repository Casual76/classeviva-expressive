from fastapi import APIRouter, Depends

from app.dependencies import get_gateway
from app.schemas import DocumentResponse, MaterialResponse
from app.services.classeviva_gateway import StubClassevivaGateway

router = APIRouter(prefix="/v1", tags=["assets"])


@router.get("/materials", response_model=list[MaterialResponse])
async def get_materials(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[MaterialResponse]:
    return await gateway.get_materials()


@router.get("/materials/{item_id}", response_model=MaterialResponse)
async def get_material(
    item_id: str,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> MaterialResponse:
    return await gateway.get_material(item_id)


@router.get("/documents", response_model=list[DocumentResponse])
async def get_documents(
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> list[DocumentResponse]:
    return await gateway.get_documents()


@router.get("/documents/{document_id}", response_model=DocumentResponse)
async def get_document(
    document_id: str,
    gateway: StubClassevivaGateway = Depends(get_gateway),
) -> DocumentResponse:
    return await gateway.get_document(document_id)
