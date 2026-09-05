"""
人脸检测服务
"""

import numpy as np
from typing import Dict, Tuple, Optional
import mediapipe as mp


class FaceService:
    """人脸检测服务类"""
    
    def __init__(self):
        """初始化MediaPipe人脸检测"""
        # 初始化MediaPipe人脸检测
        self.mp_face_detection = mp.solutions.face_detection
        self.mp_drawing = mp.solutions.drawing_utils
        
        # 创建人脸检测器
        self.face_detection = self.mp_face_detection.FaceDetection(
            model_selection=1,  # 0: 近距离模型, 1: 全距离模型
            min_detection_confidence=0.5
        )
    
    def detect(self, image: np.ndarray) -> Dict:
        """
        检测人脸
        
        Args:
            image: RGB图像 (H, W, 3)
        
        Returns:
            检测结果字典，包含:
            - detected: 是否检测到人脸
            - bbox: 人脸边界框 {x, y, w, h} (相对坐标0-1)
            - confidence: 置信度
        """
        # MediaPipe需要BGR格式
        if image.shape[2] == 3:
            # 转换RGB到BGR
            image_bgr = np.ascontiguousarray(image[:, :, ::-1])
        else:
            image_bgr = image
        
        # 检测人脸
        results = self.face_detection.process(image_bgr)
        
        if not results.detections:
            return {
                "detected": False,
                "bbox": None,
                "confidence": 0.0
            }
        
        # 获取最大的人脸
        max_area = 0
        best_detection = None
        
        for detection in results.detections:
            bbox = detection.location_data.relative_bounding_box
            area = bbox.width * bbox.height
            if area > max_area:
                max_area = area
                best_detection = detection
        
        if best_detection is None:
            return {
                "detected": False,
                "bbox": None,
                "confidence": 0.0
            }
        
        # 提取边界框
        bbox = best_detection.location_data.relative_bounding_box
        
        return {
            "detected": True,
            "bbox": {
                "x": bbox.xmin,
                "y": bbox.ymin,
                "w": bbox.width,
                "h": bbox.height
            },
            "confidence": best_detection.score[0]
        }
    
    def calc_crop_box(self, face_bbox: Dict, img_size: Tuple[int, int], 
                     target_size: Tuple[int, int]) -> Tuple[int, int, int, int]:
        """
        根据人脸位置计算裁剪框 - 优化版
        
        裁剪规则（符合证件照标准）:
        - 人像（头部+肩膀）占照片高度 70%-75%
        - 头顶留空 10%-12%
        - 脸部居中偏上
        - 保留肩膀区域
        
        Args:
            face_bbox: 人脸边界框 {x, y, w, h} (相对坐标0-1)
            img_size: 原始图像尺寸 (width, height)
            target_size: 目标证件照尺寸 (width, height)
        
        Returns:
            裁剪框 (x, y, width, height) 像素坐标
        """
        img_w, img_h = img_size
        target_w, target_h = target_size
        
        # 计算目标宽高比
        target_ratio = target_w / target_h
        
        # 人脸边界框（像素坐标）
        face_x = face_bbox['x'] * img_w
        face_y = face_bbox['y'] * img_h
        face_w = face_bbox['w'] * img_w
        face_h = face_bbox['h'] * img_h
        
        # 人脸中心
        face_center_x = face_x + face_w / 2
        face_center_y = face_y + face_h / 2
        
        # ========== 优化：更合理的比例 ==========
        # 根据证件照标准，人像（头部+肩膀）应该占高度的70-75%
        # 脸部通常占人像高度的50-60%
        # 所以脸部应该占照片高度的 35%-45%
        
        # 计算裁剪高度：脸部占高度的40%（给人像留出足够空间）
        crop_h = face_h / 0.40
        
        # 计算裁剪宽度
        crop_w = crop_h * target_ratio
        
        # ========== 优化：更合理的垂直位置 ==========
        # 脸部中心应该在裁剪框的35-40%位置（偏上，给肩膀留空间）
        # 这样头顶会有10-15%留白，下巴到肩膀会有足够空间
        crop_center_y = face_y + face_h * 0.3  # 脸部中心偏上30%位置
        
        # 水平居中
        crop_center_x = face_center_x
        
        # 计算裁剪框左上角
        crop_x = crop_center_x - crop_w / 2
        crop_y = crop_center_y - crop_h / 2
        
        # 边界检查和调整
        # 确保裁剪框不超出图像边界
        if crop_x < 0:
            crop_x = 0
        if crop_y < 0:
            crop_y = 0
        if crop_x + crop_w > img_w:
            crop_w = img_w - crop_x
            crop_h = crop_w / target_ratio
        if crop_y + crop_h > img_h:
            crop_h = img_h - crop_y
            crop_w = crop_h * target_ratio
        
        # 如果调整后仍然超出边界，重新计算
        if crop_w <= 0 or crop_h <= 0:
            # 使用整个图像
            crop_x, crop_y = 0, 0
            crop_w, crop_h = img_w, img_h
            # 调整为目标比例
            if img_w / img_h > target_ratio:
                # 图像太宽，调整宽度
                crop_w = img_h * target_ratio
                crop_x = (img_w - crop_w) / 2
            else:
                # 图像太高，调整高度
                crop_h = img_w / target_ratio
                crop_y = (img_h - crop_h) / 2
        
        return (int(crop_x), int(crop_y), int(crop_w), int(crop_h))
    
    def __del__(self):
        """清理资源"""
        if hasattr(self, 'face_detection'):
            self.face_detection.close()