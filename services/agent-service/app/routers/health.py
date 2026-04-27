from fastapi import APIRouter

router = APIRouter()


@router.get("/actuator/health", summary="健康检查")
@router.get("/health", include_in_schema=False)
async def health():
    return {"status": "UP"}


@router.get("/actuator/info", summary="应用信息")
async def info():
    return {"app": "agent-service", "version": "1.3.0", "stack": "Python/FastAPI/LangChain"}
