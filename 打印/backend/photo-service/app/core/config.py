"""
核心配置文件
"""

import os
from typing import Optional
from pydantic_settings import BaseSettings


class Settings(BaseSettings):
    """应用配置"""
    
    # 应用信息
    APP_NAME: str = "证件照智能处理API"
    APP_VERSION: str = "1.0.0"
    DEBUG: bool = False
    
    # 模型配置
    MODEL_PATH: str = os.getenv("MODEL_PATH", "pretrained/modnet.onnx")
    MAX_IMAGE_SIZE: int = int(os.getenv("MAX_IMAGE_SIZE", os.getenv("PHOTO_MAX_IMAGE_WIDTH", "4096")))

    # AI 图片生成配置（TokenHub，10 元/百万 tokens 按张折算，未配 Key 时接口返回 503）
    IMAGE_API_BASE_URL: str = os.getenv("IMAGE_API_BASE_URL", "https://tokenhub.tencentmaas.com/v1")
    IMAGE_API_KEY: str = os.getenv("IMAGE_API_KEY", "")
    IMAGE_API_TIMEOUT_SECONDS: int = int(os.getenv("IMAGE_API_TIMEOUT_SECONDS", "60"))
    IMAGE_MODEL: str = os.getenv("IMAGE_MODEL", "seedream-image-v5.0-lite")

    # 可选图片生成模型目录（2026-09 核实，价格以控制台账单为准）
    # sizes 为推荐尺寸预置值，size 参数实际格式以方案 M0 联调确认为准，确认后直接改此处
    IMAGE_MODEL_CATALOG: dict = {
        "seedream-image-v5.0-lite": {
            "label": "Seedream v5.0 lite",
            "price_per_image": 0.22,
            "sync": True,
            "sizes": ["1024x1024", "1242x1660", "1660x1242", "720x1280", "1280x720"],
            "notes": "综合性价比主力，中文文字渲染强，适合海报/手抄报",
        },
        "hy-image-v3": {
            "label": "Hy-Image-3.0",
            "price_per_image": 0.20,
            "sync": True,
            "sizes": ["1024x1024", "1242x1660", "1660x1242", "720x1280", "1280x720"],
            "notes": "37 组预设尺寸贴合打印纸张比例，支持水印脚注",
        },
        "seedream-image-v5.0-pro": {
            "label": "Seedream v5.0 pro",
            "price_per_image": 0.30,
            "sync": True,
            "sizes": ["1024x1024", "1242x1660", "1660x1242", "720x1280", "1280x720"],
            "notes": "旗舰质量档，>261 万像素时 0.60 元/张",
        },
    }
    
    # API配置
    API_KEY_HEADER: str = "Authorization"
    API_KEY_PREFIX: str = "Bearer "
    
    # 文件上传配置
    MAX_UPLOAD_SIZE: int = int(os.getenv("PHOTO_MAX_UPLOAD_BYTES", str(20 * 1024 * 1024)))
    ALLOWED_EXTENSIONS: set = {".jpg", ".jpeg", ".png", ".bmp", ".tiff", ".tif", ".webp"}

    # 服务治理
    SERVICE_NAME: str = os.getenv("PHOTO_SERVICE_NAME", "photo-service")
    SERVICE_PORT: int = int(os.getenv("PHOTO_PORT", "8091"))
    NACOS_ENABLED: bool = os.getenv("NACOS_ENABLED", "false").lower() in {"1", "true", "yes", "on"}
    NACOS_SERVER_ADDR: str = os.getenv("NACOS_SERVER_ADDR", "127.0.0.1:8848")
    NACOS_NAMESPACE: str = os.getenv("NACOS_NAMESPACE", "public")
    NACOS_GROUP: str = os.getenv("NACOS_GROUP", "DEFAULT_GROUP")
    NACOS_IP: str = os.getenv("PHOTO_NACOS_IP", "")
    NACOS_HEARTBEAT_SECONDS: int = int(os.getenv("PHOTO_NACOS_HEARTBEAT_SECONDS", "10"))
    
    # 证件照尺寸标准
    ID_PHOTO_SIZES: dict = {
        "1寸": (295, 413),
        "小1寸": (260, 378),
        "2寸": (413, 579),
        "小2寸": (378, 522),
        "大1寸": (390, 567),
        "驾照": (260, 378),
        "签证(美国)": (600, 600),
        "签证(日本)": (480, 640),
    }
    
    class Config:
        case_sensitive = True


# 创建全局配置实例
settings = Settings()
