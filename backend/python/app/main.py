from fastapi import FastAPI

from app.config import get_settings
from app.routers.auth import router as auth_router
from app.routers.dashboard import router as dashboard_router
from app.routers.grades import router as grades_router
from app.routers.health import router as health_router
from app.routers.materials import router as materials_router
from app.routers.noticeboard import router as noticeboard_router
from app.routers.notifications import router as notifications_router
from app.routers.school import router as school_router

settings = get_settings()

app = FastAPI(
    title=settings.app_name,
    version="0.1.0",
    description="Phase-2 backend scaffold for the native Classeviva Expressive app.",
)

app.include_router(health_router)
app.include_router(auth_router)
app.include_router(dashboard_router)
app.include_router(grades_router)
app.include_router(school_router)
app.include_router(noticeboard_router)
app.include_router(materials_router)
app.include_router(notifications_router)
