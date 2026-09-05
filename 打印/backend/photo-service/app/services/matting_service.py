"""
人像抠图服务 - 优化版
"""

import io
import numpy as np
from typing import Optional, Tuple
from PIL import Image, ImageFilter
import onnxruntime as ort

from app.core.config import settings
from app.utils.image_utils import smooth_edges, hex_to_rgb, create_gradient_background, blend_edges


class MattingService:
    """人像抠图服务类 - 优化版"""
    
    def __init__(self, model_path: Optional[str] = None):
        """
        初始化抠图服务
        
        Args:
            model_path: ONNX模型路径
        """
        self.model_path = model_path or settings.MODEL_PATH
        self.session = None
        self.input_name = None
        self.output_name = None
        
        # 尝试加载模型
        try:
            self._load_model()
        except Exception as e:
            print(f"警告: 模型加载失败 - {e}")
    
    def _load_model(self):
        """加载ONNX模型"""
        try:
            # 创建ONNX Runtime会话
            self.session = ort.InferenceSession(self.model_path)
            
            # 获取输入输出名称
            self.input_name = self.session.get_inputs()[0].name
            self.output_name = self.session.get_outputs()[0].name
            
            print(f"模型加载成功: {self.model_path}")
            
        except Exception as e:
            raise RuntimeError(f"模型加载失败: {e}")
    
    def preprocess(self, image: np.ndarray) -> np.ndarray:
        """
        图像预处理
        
        Args:
            image: RGB图像 (H, W, 3)
        
        Returns:
            预处理后的图像 (1, 3, 512, 512)
        """
        # 调整尺寸到512x512
        pil_image = Image.fromarray(image)
        resized = pil_image.resize((512, 512), Image.Resampling.LANCZOS)
        
        # 转换为numpy数组
        img_array = np.array(resized).astype(np.float32)
        
        # 归一化到0-1
        img_array = img_array / 255.0
        
        # 调整维度顺序 (H, W, C) -> (C, H, W)
        img_array = np.transpose(img_array, (2, 0, 1))
        
        # 添加batch维度
        img_array = np.expand_dims(img_array, axis=0)
        
        return img_array
    
    def inference(self, input_data: np.ndarray) -> np.ndarray:
        """
        模型推理
        
        Args:
            input_data: 预处理后的图像 (1, 3, 512, 512)
        
        Returns:
            Alpha遮罩 (H, W)，值0-1
        """
        if self.session is None:
            raise RuntimeError("模型未加载")
        
        # 运行推理
        outputs = self.session.run([self.output_name], {self.input_name: input_data})
        
        # 获取输出
        alpha = outputs[0]
        
        # 移除batch和channel维度
        alpha = alpha.squeeze()
        
        # 确保值在0-1范围内
        alpha = np.clip(alpha, 0, 1)
        
        return alpha
    
    def postprocess(self, alpha: np.ndarray, original_size: Tuple[int, int]) -> np.ndarray:
        """
        后处理 - 优化版
        
        Args:
            alpha: Alpha遮罩 (512, 512)
            original_size: 原始图像尺寸 (width, height)
        
        Returns:
            调整尺寸后的Alpha遮罩
        """
        # 调整尺寸到原始大小
        alpha_image = Image.fromarray((alpha * 255).astype(np.uint8), mode='L')
        resized_alpha = alpha_image.resize(original_size, Image.Resampling.LANCZOS)
        
        # 转换回numpy数组
        alpha_array = np.array(resized_alpha).astype(np.float32) / 255.0
        
        # 1. 边缘羽化（增大半径使边缘更平滑）
        alpha_array = smooth_edges(alpha_array, radius=3)
        
        # 2. 应用形态学操作改善边缘
        alpha_pil = Image.fromarray((alpha_array * 255).astype(np.uint8), mode='L')
        
        # 轻微的开运算去除噪点
        alpha_pil = alpha_pil.filter(ImageFilter.MinFilter(3))
        alpha_pil = alpha_pil.filter(ImageFilter.MaxFilter(3))
        
        # 再次平滑
        alpha_pil = alpha_pil.filter(ImageFilter.GaussianBlur(radius=1))
        
        alpha_array = np.array(alpha_pil).astype(np.float32) / 255.0
        
        # 3. 增强边缘对比度（S曲线）
        alpha_array = np.clip(alpha_array * 1.15 - 0.075, 0, 1)
        
        return alpha_array
    
    def remove_background(self, image_bytes: bytes) -> Image.Image:
        """
        去除背景 - 优化版
        
        Args:
            image_bytes: 图片二进制数据
        
        Returns:
            RGBA PIL图像
        """
        # 读取图像
        image = Image.open(io.BytesIO(image_bytes))
        
        # 转换为RGB
        if image.mode != 'RGB':
            image = image.convert('RGB')
        
        # 转换为numpy数组
        img_array = np.array(image)
        
        # 预处理
        input_data = self.preprocess(img_array)
        
        # 推理
        alpha = self.inference(input_data)
        
        # 后处理
        alpha = self.postprocess(alpha, image.size)
        
        # 创建RGBA图像
        rgba_image = image.convert('RGBA')
        
        # 设置Alpha通道
        rgba_array = np.array(rgba_image)
        rgba_array[:, :, 3] = (alpha * 255).astype(np.uint8)
        
        # 对前景进行轻微的边缘融合
        rgba_array = self._blend_foreground_edges(rgba_array, alpha)
        
        return Image.fromarray(rgba_array, 'RGBA')
    
    def _blend_foreground_edges(self, rgba_array: np.ndarray, alpha: np.ndarray) -> np.ndarray:
        """
        前景边缘融合
        
        Args:
            rgba_array: RGBA图像数组
            alpha: Alpha遮罩
        
        Returns:
            融合后的RGBA数组
        """
        # 提取RGB通道
        rgb_array = rgba_array[:, :, :3]
        
        # 应用边缘融合
        blended_rgb = blend_edges(rgb_array, alpha, blend_radius=3)
        
        # 更新RGBA数组
        rgba_array[:, :, :3] = blended_rgb
        
        return rgba_array
    
    def replace_background(self, rgba_image: Image.Image, bg_color: str, 
                          gradient: bool = True) -> Image.Image:
        """
        替换背景色 - 优化版
        
        Args:
            rgba_image: RGBA PIL图像
            bg_color: 背景色十六进制代码
            gradient: 是否使用渐变背景
        
        Returns:
            RGB PIL图像
        """
        # 创建背景图像
        if gradient:
            background = create_gradient_background(rgba_image.size, bg_color, "radial")
        else:
            bg_rgb = hex_to_rgb(bg_color)
            background = Image.new('RGB', rgba_image.size, bg_rgb)
        
        # 合成图像
        background.paste(rgba_image, mask=rgba_image.split()[3])
        
        # 对最终结果进行轻微的色彩平衡
        result = self._color_balance(background)
        
        return result
    
    def _color_balance(self, image: Image.Image) -> Image.Image:
        """
        色彩平衡
        
        Args:
            image: 输入图像
        
        Returns:
            平衡后的图像
        """
        from PIL import ImageEnhance
        
        # 轻微增强饱和度
        enhancer = ImageEnhance.Color(image)
        image = enhancer.enhance(1.02)
        
        # 轻微增强对比度
        enhancer = ImageEnhance.Contrast(image)
        image = enhancer.enhance(1.03)
        
        return image
