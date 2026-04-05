from functools import lru_cache

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    app_name: str = "Classeviva Expressive Backend"
    environment: str = "development"
    upstream_base_url: str = "https://web.spaggiari.eu"
    request_timeout_seconds: float = 20.0

    model_config = SettingsConfigDict(
        env_prefix="CLASSEVIVA_",
        case_sensitive=False,
    )


@lru_cache
def get_settings() -> Settings:
    return Settings()
