from functools import lru_cache

from app.config import Settings, get_settings
from app.services.classeviva_gateway import StubClassevivaGateway


@lru_cache
def get_gateway() -> StubClassevivaGateway:
    return StubClassevivaGateway()


def get_app_settings() -> Settings:
    return get_settings()
