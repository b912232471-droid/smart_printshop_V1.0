"""
图像工具函数 - 优化版
"""

import io
import base64
from typing import Tuple
import numpy as np
from PIL import Image, ImageEnhance, ImageFilter, ImageDraw


def hex_to_rgb(color: str) -> Tuple[int, int, int]:
    """
    将十六进制颜色代码转换为RGB元组
    
    Args:
        color: 十六进制颜色代码，如 "#FFFFFF" 或 "#FFF"
    
    Returns:
        RGB元组，如 (255, 255, 255)
    """
    color = color.lstrip('#')
    
    # 处理简写形式 #RGB
    if len(color) == 3:
        color = ''.join([c * 2 for c in color])
    
    if len(color) != 6:
        raise ValueError(f"无效的颜色代码: #{color}")
    
    try:
        return tuple(int(color[i:i+2], 16) for i in (0, 2, 4))
    except ValueError:
        raise ValueError(f"无效的颜色代码: #{color}")


def crop_and_resize(image: np.ndarray, box: Tuple[int, int, int, int], 
                   target: Tuple[int, int]) -> np.ndarray:
    """
    裁剪并调整到目标尺寸
    
    Args:
        image: 输入图像 (H, W, C)
        box: 裁剪框 (x, y, width, height)
        target: 目标尺寸 (width, height)
    
    Returns:
        裁剪并调整后的图像
    """
    x, y, w, h = box
    
    # 裁剪
    cropped = image[y:y+h, x:x+w]
    
    # 转换为PIL图像进行缩放
    pil_image = Image.fromarray(cropped)
    resized = pil_image.resize(target, Image.Resampling.LANCZOS)
    
    return np.array(resized)


def enhance_portrait(image: np.ndarray, level: str = "normal") -> np.ndarray:
    """
    人像美化增强 - 优化版
    
    Args:
        image: 输入图像 (H, W, C)
        level: 美化级别 ("light", "normal", "strong")
    
    Returns:
        增强后的图像
    """
    # 转换为PIL图像
    pil_image = Image.fromarray(image)
    
    # 根据级别设置参数
    if level == "light":
        brightness = 1.05
        contrast = 1.03
        saturation = 1.02
        sharpness = 1.1
        blur_radius = 0.3
    elif level == "strong":
        brightness = 1.15
        contrast = 1.1
        saturation = 1.08
        sharpness = 1.3
        blur_radius = 0.8
    else:  # normal
        brightness = 1.1
        contrast = 1.05
        saturation = 1.05
        sharpness = 1.2
        blur_radius = 0.5
    
    # 1. 亮度调整（提亮）
    enhancer = ImageEnhance.Brightness(pil_image)
    pil_image = enhancer.enhance(brightness)
    
    # 2. 对比度增强
    enhancer = ImageEnhance.Contrast(pil_image)
    pil_image = enhancer.enhance(contrast)
    
    # 3. 饱和度调整（让肤色更自然）
    enhancer = ImageEnhance.Color(pil_image)
    pil_image = enhancer.enhance(saturation)
    
    # 4. 轻微磨皮（高斯模糊）
    pil_image = pil_image.filter(ImageFilter.GaussianBlur(radius=blur_radius))
    
    # 5. 锐化（让细节更清晰）
    enhancer = ImageEnhance.Sharpness(pil_image)
    pil_image = enhancer.enhance(sharpness)
    
    return np.array(pil_image)


def smooth_edges(alpha: np.ndarray, radius: int = 3) -> np.ndarray:
    """
    Alpha通道边缘羽化 - 优化版
    
    Args:
        alpha: Alpha遮罩 (H, W)，值0-1
        radius: 羽化半径
    
    Returns:
        羽化后的Alpha遮罩
    """
    # 转换为PIL图像
    alpha_image = Image.fromarray((alpha * 255).astype(np.uint8), mode='L')
    
    # 高斯模糊（增大半径使边缘更平滑）
    smoothed = alpha_image.filter(ImageFilter.GaussianBlur(radius=radius))
    
    # 转换回numpy数组
    result = np.array(smoothed).astype(np.float32) / 255.0
    
    # 应用S曲线增强边缘对比度
    result = np.clip(result * 1.2 - 0.1, 0, 1)
    
    return result


def create_gradient_background(size: Tuple[int, int], color: str, 
                              gradient_type: str = "radial") -> Image.Image:
    """
    创建渐变背景
    
    Args:
        size: 图像尺寸 (width, height)
        color: 主色调（十六进制）
        gradient_type: 渐变类型 ("radial", "linear", "none")
    
    Returns:
        渐变背景图像
    """
    width, height = size
    bg_rgb = hex_to_rgb(color)
    
    if gradient_type == "none":
        # 纯色背景
        return Image.new('RGB', size, bg_rgb)
    
    # 创建渐变背景
    background = Image.new('RGB', size, bg_rgb)
    draw = ImageDraw.Draw(background)
    
    if gradient_type == "radial":
        # 径向渐变（中心亮，边缘暗）
        center_x, center_y = width // 2, height // 2
        max_radius = max(width, height)
        
        for y in range(height):
            for x in range(width):
                # 计算到中心的距离
                dist = np.sqrt((x - center_x) ** 2 + (y - center_y) ** 2)
                ratio = min(dist / max_radius, 1.0)
                
                # 应用渐变（中心为原始颜色，边缘稍微变暗）
                factor = 1.0 - ratio * 0.15  # 最多变暗15%
                r = int(bg_rgb[0] * factor)
                g = int(bg_rgb[1] * factor)
                b = int(bg_rgb[2] * factor)
                
                draw.point((x, y), fill=(r, g, b))
    
    elif gradient_type == "linear":
        # 线性渐变（从上到下）
        for y in range(height):
            ratio = y / height
            # 从上到下稍微变暗
            factor = 1.0 - ratio * 0.1
            r = int(bg_rgb[0] * factor)
            g = int(bg_rgb[1] * factor)
            b = int(bg_rgb[2] * factor)
            
            draw.line([(0, y), (width, y)], fill=(r, g, b))
    
    return background


def blend_edges(image: np.ndarray, alpha: np.ndarray, 
                blend_radius: int = 5) -> np.ndarray:
    """
    边缘融合处理
    
    Args:
        image: 输入图像 (H, W, C)
        alpha: Alpha遮罩 (H, W)
        blend_radius: 融合半径
    
    Returns:
        融合后的图像
    """
    # 创建边缘遮罩
    alpha_pil = Image.fromarray((alpha * 255).astype(np.uint8), mode='L')
    
    # 膨胀和腐蚀得到边缘区域
    dilated = alpha_pil.filter(ImageFilter.MaxFilter(blend_radius * 2 + 1))
    eroded = alpha_pil.filter(ImageFilter.MinFilter(blend_radius * 2 + 1))
    
    # 计算边缘遮罩
    dilated_array = np.array(dilated).astype(np.float32) / 255.0
    eroded_array = np.array(eroded).astype(np.float32) / 255.0
    edge_mask = dilated_array - eroded_array
    
    # 对边缘区域进行模糊
    image_pil = Image.fromarray(image)
    blurred = image_pil.filter(ImageFilter.GaussianBlur(radius=blend_radius // 2))
    blurred_array = np.array(blurred)
    
    # 混合
    edge_mask_3d = np.expand_dims(edge_mask, axis=2)
    result = image * (1 - edge_mask_3d * 0.5) + blurred_array * (edge_mask_3d * 0.5)
    
    return result.astype(np.uint8)


def image_to_base64(image, fmt: str = "PNG") -> str:
    """
    图片转Base64字符串
    
    Args:
        image: 输入图像 (numpy数组或PIL图像)
        fmt: 图像格式 ("PNG", "JPEG")
    
    Returns:
        Base64编码的字符串
    """
    # 如果是numpy数组，转换为PIL图像
    if isinstance(image, np.ndarray):
        pil_image = Image.fromarray(image)
    elif isinstance(image, Image.Image):
        pil_image = image
    else:
        raise ValueError("不支持的图像类型，需要numpy数组或PIL图像")
    
    # 转换为字节流
    buffer = io.BytesIO()
    pil_image.save(buffer, format=fmt)
    
    # 转换为Base64
    return base64.b64encode(buffer.getvalue()).decode('utf-8')
