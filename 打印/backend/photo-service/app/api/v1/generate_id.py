"""
生成证件照API接口
"""

from fastapi import APIRouter, UploadFile, File, Form, HTTPException
from fastapi.responses import JSONResponse

from app.core.config import settings
from app.services.id_photo_service import IdPhotoService

router = APIRouter()

# 初始化服务
id_photo_service = IdPhotoService()


@router.post("/generate-id-photo", summary="生成证件照")
async def generate_id_photo(
    image: UploadFile = File(..., description="原始照片"),
    size: str = Form(default="1寸", description="证件照尺寸: 1寸, 2寸, 小1寸, 小2寸, 大1寸, 驾照, 签证(美国), 签证(日本)"),
    bg_color: str = Form(default="#FFFFFF", description="背景色"),
    enhance: bool = Form(default=True, description="是否进行图像增强")
):
    """
    生成标准证件照
    
    - **image**: 原始照片文件
    - **size**: 证件照尺寸规格
    - **bg_color**: 背景色（十六进制颜色代码）
    - **enhance**: 是否进行图像增强
    
    返回生成的证件照（Base64编码）
    """
    try:
        # 读取图片数据
        image_data = await image.read()
        
        # 检查文件大小
        if len(image_data) > settings.MAX_UPLOAD_SIZE:
            raise HTTPException(
                status_code=400, 
                detail=f"文件大小超出限制，最大允许 {settings.MAX_UPLOAD_SIZE // (1024*1024)}MB"
            )
        
        # 验证尺寸规格
        if size not in settings.ID_PHOTO_SIZES:
            raise HTTPException(
                status_code=400,
                detail=f"不支持的尺寸，可选: {', '.join(settings.ID_PHOTO_SIZES.keys())}"
            )
        
        # 生成证件照
        result = id_photo_service.generate_id_photo(
            image_bytes=image_data,
            size=size,
            bg_color=bg_color,
            enhance=enhance
        )
        
        if not result.get("success"):
            raise HTTPException(
                status_code=500,
                detail=result.get("message", "处理失败")
            )
        
        # 构建响应
        return JSONResponse(
            status_code=200,
            content={
                "code": 0,
                "message": "success",
                "data": {
                    "image": result.get("image"),
                    "width": result.get("width"),
                    "height": result.get("height"),
                    "format": result.get("format", "png"),
                    "size": size
                }
            }
        )
        
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=f"处理失败: {str(e)}")
