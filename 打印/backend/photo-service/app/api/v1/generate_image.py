"""
AI 图片生成API接口 - 模型适配端点（业务编排见 print-service imagegen 包）
"""

import re
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import JSONResponse
from pydantic import BaseModel

from app.core.config import settings
from app.core.security import get_current_user
from app.services.image_generation_service import ImageGenerationService

router = APIRouter()

SIZE_PATTERN = re.compile(r"^\d{2,5}x\d{2,5}$")
MAX_PROMPT_LENGTH = 500

image_generation_service = ImageGenerationService()


class ImageGenerateRequest(BaseModel):
    model: str
    prompt: str
    size: str = ""
    watermark_text: Optional[str] = None


@router.post("/image-generate", summary="AI 图片生成")
async def image_generate(body: ImageGenerateRequest, _user: dict = Depends(get_current_user)):
    """
    生成一张图片，返回 Base64 编码结果。

    - **model**: 模型标识，须在 /api/photo/image-models 目录内
    - **prompt**: 图片描述（≤500 字）
    - **size**: 尺寸，格式 宽x高（如 1242x1660）
    - **watermark_text**: 可选水印脚注（仅声明支持的模型生效）
    """
    prompt = (body.prompt or "").strip()
    if not prompt:
        raise HTTPException(status_code=400, detail="请输入图片描述")
    if len(prompt) > MAX_PROMPT_LENGTH:
        raise HTTPException(status_code=400, detail=f"图片描述过长，请控制在 {MAX_PROMPT_LENGTH} 字以内")
    model = (body.model or "").strip()
    if model not in settings.IMAGE_MODEL_CATALOG:
        raise HTTPException(status_code=400, detail="不支持的模型")
    size = (body.size or "").strip().lower()
    if not SIZE_PATTERN.match(size):
        raise HTTPException(status_code=400, detail="尺寸格式错误，应为 宽x高，如 1242x1660")

    result = image_generation_service.generate(model, prompt, size, (body.watermark_text or "").strip() or None)
    return JSONResponse(
        status_code=200,
        content={
            "code": 0,
            "message": "success",
            "data": result,
        },
    )


@router.get("/image-models", summary="图片生成模型目录")
async def image_models(_user: dict = Depends(get_current_user)):
    """返回可选模型与推荐尺寸，前端下拉走本接口，不硬编码"""
    models = []
    for model_id, item in settings.IMAGE_MODEL_CATALOG.items():
        models.append(
            {
                "id": model_id,
                "label": item.get("label", model_id),
                "pricePerImage": item.get("price_per_image"),
                "sizes": item.get("sizes", []),
                "notes": item.get("notes", ""),
            }
        )
    return JSONResponse(
        status_code=200,
        content={
            "code": 0,
            "message": "success",
            "data": {"models": models, "count": len(models)},
        },
    )
