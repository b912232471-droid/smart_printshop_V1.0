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

    # AI 图片生成配置（阶跃星辰 StepFun，0.02 元/张，未配 Key 时接口返回 503）
    IMAGE_API_BASE_URL: str = os.getenv("IMAGE_API_BASE_URL", "https://api.stepfun.com/v1")
    IMAGE_API_KEY: str = os.getenv("IMAGE_API_KEY", "")
    IMAGE_API_TIMEOUT_SECONDS: int = int(os.getenv("IMAGE_API_TIMEOUT_SECONDS", "60"))
    IMAGE_MODEL: str = os.getenv("IMAGE_MODEL", "step-image-edit-2")

    # 可选图片生成模型目录（2026-09-06 真实 Key 联调核实）
    # sizes 为平台约定的 宽x高；StepFun API 实际为 高x宽（size_axis=HxW），适配层自动换轴
    # 注意：step-image-edit-2 官方公告 2026-10-10 下线，届时换模型只需改本目录 + 模板推荐项
    IMAGE_MODEL_CATALOG: dict = {
        "step-image-edit-2": {
            "label": "Step Image Edit 2",
            "price_per_image": 0.02,
            "sync": True,
            "sizes": ["1024x1024", "768x1360", "1360x768", "896x1184", "1184x896"],
            "size_axis": "HxW",
            "supports_watermark": False,
            "notes": "文生图+图像编辑一体，中文文字渲染强，秒级响应，适合海报/手抄报",
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
