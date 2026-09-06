"""
证件照智能处理API - 主入口
"""

from contextlib import asynccontextmanager
import time

from fastapi import FastAPI, Request, Response
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse, HTMLResponse, PlainTextResponse
from fastapi.staticfiles import StaticFiles

from app.api.v1 import change_bg, generate_id, generate_image
from app.core.config import settings
from app.core.metrics import metrics
from app.core.nacos import NacosRegistration

nacos_registration = NacosRegistration()


@asynccontextmanager
async def lifespan(app: FastAPI):
    nacos_registration.start()
    yield
    nacos_registration.stop()

# 创建FastAPI应用
app = FastAPI(
    title="证件照智能处理API",
    description="证件照智能换底 + 自动生成证件照",
    version="1.0.0",
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan
)

# 挂载静态文件目录
app.mount("/static", StaticFiles(directory="static"), name="static")

# 配置CORS
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.middleware("http")
async def collect_metrics(request: Request, call_next):
    started = time.perf_counter()
    status_code = 500
    try:
        response = await call_next(request)
        status_code = response.status_code
        return response
    finally:
        metrics.record(request.url.path, status_code, time.perf_counter() - started)


# 注册路由
app.include_router(change_bg.router, prefix="/api/v1", tags=["换底色"])
app.include_router(generate_id.router, prefix="/api/v1", tags=["生成证件照"])
app.include_router(generate_image.router, prefix="/api/v1", tags=["图片生成"])
app.include_router(change_bg.router, prefix="/api/photo", tags=["换底色"])
app.include_router(generate_id.router, prefix="/api/photo", tags=["生成证件照"])
app.include_router(generate_image.router, prefix="/api/photo", tags=["图片生成"])


@app.get("/health", summary="健康检查")
async def health_check():
    """健康检查接口"""
    return {"status": "ok", "message": "服务正常运行", "service": settings.SERVICE_NAME}


@app.get("/api/photo/health", summary="Gateway健康检查")
async def photo_health_check():
    """Gateway路径下的健康检查接口"""
    return {"status": "ok", "message": "服务正常运行", "service": settings.SERVICE_NAME}


@app.get("/api/photo/metrics", summary="Gateway路径监控指标")
async def photo_metrics():
    """JSON metrics for service-level monitoring."""
    return metrics.snapshot()


@app.get("/metrics", summary="Prometheus监控指标", response_class=PlainTextResponse)
async def prometheus_metrics():
    """Prometheus text metrics."""
    return PlainTextResponse(metrics.prometheus(), media_type="text/plain; version=0.0.4")


@app.get("/", summary="API信息")
async def root(request: Request):
    """返回API基本信息"""
    # 返回Web界面
    with open("static/index.html", "r", encoding="utf-8") as f:
        html_content = f.read()
    return HTMLResponse(content=html_content, status_code=200)


@app.get("/api", summary="API信息")
async def api_info():
    """返回API基本信息"""
    return {
        "name": "证件照智能处理API",
        "version": "1.0.0",
        "docs": "/docs",
        "health": "/health",
        "web": "/",
        "features": [
            "证件照生成",
            "换底色",
        ],
    }


# 全局异常处理
@app.exception_handler(Exception)
async def global_exception_handler(request, exc):
    """全局异常处理"""
    return JSONResponse(
        status_code=500,
        content={
            "code": 1,
            "message": f"服务器内部错误: {str(exc)}"
        }
    )
