"""
参数验证函数
"""

import re


def validate_color_code(color: str) -> bool:
    """
    验证十六进制颜色代码格式

    Args:
        color: 颜色代码，如 "#FFFFFF" 或 "#FFF"

    Returns:
        是否有效
    """
    pattern = r'^#([A-Fa-f0-9]{6}|[A-Fa-f0-9]{3})$'
    return bool(re.match(pattern, color))
