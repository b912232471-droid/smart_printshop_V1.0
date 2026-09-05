"""
图像处理服务
"""

from typing import Tuple
import numpy as np

from app.utils.image_utils import crop_and_resize


class ImageService:
    """图像处理服务类"""

    def __init__(self):
        pass

    def crop_id_photo(self, image: np.ndarray, crop_box: Tuple[int, int, int, int],
                     target_size: Tuple[int, int]) -> np.ndarray:
        """
        裁剪证件照

        Args:
            image: 输入图像 (H, W, C)
            crop_box: 裁剪框 (x, y, width, height)
            target_size: 目标尺寸 (width, height)

        Returns:
            裁剪并调整尺寸后的图像
        """
        return crop_and_resize(image, crop_box, target_size)
