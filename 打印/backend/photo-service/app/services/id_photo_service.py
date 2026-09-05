"""
证件照生成服务 - 优化版传统算法
"""

import io
from typing import Dict, Any
from PIL import Image, ImageEnhance, ImageFilter
import numpy as np

from app.core.config import settings
from app.services.matting_service import MattingService
from app.services.face_service import FaceService
from app.services.image_service import ImageService
from app.utils.image_utils import image_to_base64, enhance_portrait


class IdPhotoService:
    """证件照生成服务 - 优化版"""
    
    def __init__(self):
        self.matting_service = MattingService()
        self.face_service = FaceService()
        self.image_service = ImageService()
    
    def generate_id_photo(
        self,
        image_bytes: bytes,
        size: str = "1寸",
        bg_color: str = "#FFFFFF",
        enhance: bool = True
    ) -> Dict[str, Any]:
        """
        生成证件照
        
        Args:
            image_bytes: 原始照片
            size: 证件照尺寸
            bg_color: 背景色
            enhance: 是否增强
        
        Returns:
            处理结果
        """
        try:
            # 读取图像
            image = Image.open(io.BytesIO(image_bytes))
            
            # 转换为RGB模式
            if image.mode != 'RGB':
                image = image.convert('RGB')
            
            # 转换为numpy数组
            img_array = np.array(image)
            
            # 人脸检测
            face_result = self.face_service.detect(img_array)
            
            if not face_result["detected"]:
                return {
                    "success": False,
                    "error": "未检测到人脸",
                    "message": "请上传包含清晰人脸的照片"
                }
            
            # 获取目标尺寸
            target_size = settings.ID_PHOTO_SIZES.get(size)
            
            if not target_size:
                return {
                    "success": False,
                    "error": "不支持的尺寸",
                    "message": f"不支持的尺寸: {size}"
                }
            
            # 计算裁剪框（优化：更宽松的裁剪，保留更多头部）
            crop_box = self.face_service.calc_crop_box(
                face_result["bbox"],
                (img_array.shape[1], img_array.shape[0]),
                target_size
            )
            
            # 裁剪并调整尺寸
            cropped_image = self.image_service.crop_id_photo(img_array, crop_box, target_size)
            
            # 图像增强（优化：更自然的增强）
            if enhance:
                cropped_image = self._natural_enhance(cropped_image)
            
            # 抠图去除背景
            pil_cropped = Image.fromarray(cropped_image)
            buffer = io.BytesIO()
            pil_cropped.save(buffer, format="PNG")
            cropped_bytes = buffer.getvalue()
            
            # 去除背景（优化：更精细的边缘处理）
            rgba_image = self.matting_service.remove_background(cropped_bytes)
            
            # 替换背景色（优化：更自然的边缘融合）
            result_image = self._replace_background_naturally(rgba_image, bg_color)
            
            # 转换为Base64
            base64_str = image_to_base64(result_image, "PNG")
            
            return {
                "success": True,
                "image": base64_str,
                "format": "png",
                "width": target_size[0],
                "height": target_size[1],
                "size": size
            }
            
        except Exception as e:
            return {
                "success": False,
                "error": str(e),
                "message": "处理失败"
            }
    
    def _natural_enhance(self, image: np.ndarray) -> np.ndarray:
        """
        自然的图像增强（不过度美化）
        """
        pil_image = Image.fromarray(image)
        
        # 1. 轻微提亮（只在光线不足时）
        brightness = ImageEnhance.Brightness(pil_image)
        pil_image = brightness.enhance(1.05)  # 非常轻微的提亮
        
        # 2. 轻微增强对比度
        contrast = ImageEnhance.Contrast(pil_image)
        pil_image = contrast.enhance(1.03)
        
        # 3. 轻微锐化（让细节更清晰）
        sharpness = ImageEnhance.Sharpness(pil_image)
        pil_image = sharpness.enhance(1.1)
        
        return np.array(pil_image)
    
    def _replace_background_naturally(self, rgba_image: Image.Image, bg_color: str) -> Image.Image:
        """
        更自然的背景替换（边缘羽化）
        """
        from app.utils.image_utils import hex_to_rgb
        
        # 转换背景色
        bg_rgb = hex_to_rgb(bg_color)
        
        # 创建背景
        background = Image.new('RGB', rgba_image.size, bg_rgb)
        
        # 获取alpha通道
        alpha = rgba_image.split()[3]
        
        # 对alpha通道进行轻微模糊（边缘羽化）
        alpha_blurred = alpha.filter(ImageFilter.GaussianBlur(radius=1))
        
        # 合成图像
        background.paste(rgba_image, mask=alpha_blurred)
        
        return background
