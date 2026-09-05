"""
换底色API接口 - 优化版
"""

from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse
from typing import Optional
import io
from PIL import Image

from app.core.config import settings
from app.services.matting_service import MattingService
from app.utils.image_utils import image_to_base64, hex_to_rgb, enhance_portrait
from app.utils.validators import validate_color_code

router = APIRouter()

# 初始化抠图服务
matting_service = MattingService()


@router.post("/change-background", summary="证件照换底色")
async def change_background(
    image: UploadFile = File(..., description="证件照图片文件"),
    bg_color: str = Form(default="#FFFFFF", description="目标背景色，如 #FFFFFF, #0000FF, #FF0000"),
    output_format: str = Form(default="png", description="输出格式: png, jpg"),
    enhance: bool = Form(default=True, description="是否进行图像增强"),
    enhance_level: str = Form(default="normal", description="增强级别: light, normal, strong"),
    gradient_bg: bool = Form(default=True, description="是否使用渐变背景"),
    smooth_edge: bool = Form(default=True, description="是否进行边缘平滑处理")
):
    """
    证件照换底色接口 - 优化版
    
    - **image**: 证件照图片文件
    - **bg_color**: 目标背景色（十六进制颜色代码）
    - **output_format**: 输出格式（png/jpg）
    - **enhance**: 是否进行图像增强
    - **enhance_level**: 增强级别（light/normal/strong）
    - **gradient_bg**: 是否使用渐变背景
    - **smooth_edge**: 是否进行边缘平滑处理
    
    返回处理后的图片（Base64编码）
    """
    try:
        # 验证文件类型
        if not image.content_type.startswith('image/'):
            raise HTTPException(status_code=400, detail="请上传图片文件")
        
        # 验证颜色格式
        if not validate_color_code(bg_color):
            raise HTTPException(status_code=400, detail="颜色格式错误，应为 #RGB 或 #RRGGBB")
        
        # 验证输出格式
        if output_format.lower() not in ["png", "jpg", "jpeg"]:
            raise HTTPException(status_code=400, detail="输出格式应为 png 或 jpg")
        
        # 验证增强级别
        if enhance_level not in ["light", "normal", "strong"]:
            raise HTTPException(status_code=400, detail="增强级别应为 light, normal 或 strong")
        
        # 读取图片数据
        image_data = await image.read()
        
        # 检查文件大小
        if len(image_data) > settings.MAX_UPLOAD_SIZE:
            raise HTTPException(status_code=400, detail=f"文件大小超出限制，最大允许 {settings.MAX_UPLOAD_SIZE // (1024*1024)}MB")
        
        # 调用抠图服务去除背景
        rgba_image = matting_service.remove_background(image_data)
        
        # 替换背景色
        result_image = matting_service.replace_background(rgba_image, bg_color, gradient=gradient_bg)
        
        # 图像增强
        if enhance:
            import numpy as np
            result_array = np.array(result_image)
            enhanced_array = enhance_portrait(result_array, level=enhance_level)
            result_image = Image.fromarray(enhanced_array)
        
        # 转换为Base64
        fmt = "JPEG" if output_format.lower() in ["jpg", "jpeg"] else "PNG"
        base64_str = image_to_base64(result_image, fmt)
        
        return JSONResponse(
            status_code=200,
            content={
                "code": 0,
                "message": "success",
                "data": {
                    "image": base64_str,
                    "format": output_format.lower(),
                    "width": result_image.width,
                    "height": result_image.height,
                    "enhanced": enhance,
                    "enhance_level": enhance_level if enhance else None,
                    "gradient_bg": gradient_bg
                }
            }
        )
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"处理失败: {str(e)}")
