from fastapi import APIRouter
from fastapi.responses import Response
from prometheus_client import CONTENT_TYPE_LATEST, generate_latest

router = APIRouter()


@router.get("/actuator/health", summary="健康检查")
@router.get("/health", include_in_schema=False)
async def health():
    return {"status": "UP"}


@router.get("/actuator/info", summary="应用信息")
async def info():
    return {"app": "agent-service", "version": "1.3.0", "stack": "Python/FastAPI/LangChain"}


@router.get("/actuator/prometheus", include_in_schema=False)
@router.get("/metrics", summary="Prometheus 指标")
async def metrics():
    """暴露 Prometheus 格式指标（同时兼容 /actuator/prometheus 路径）。"""
    return Response(generate_latest(), media_type=CONTENT_TYPE_LATEST)
