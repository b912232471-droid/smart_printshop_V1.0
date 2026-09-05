"""
API认证模块
"""

from fastapi import Depends, HTTPException, Security
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from typing import Optional

from app.core.config import settings

# HTTP Bearer认证
security = HTTPBearer(auto_error=False)


async def verify_api_key(
    credentials: Optional[HTTPAuthorizationCredentials] = Security(security)
) -> Optional[str]:
    """
    验证API Key
    
    返回API Key字符串，如果验证失败则抛出异常
    """
    # 如果没有提供认证信息，返回None（允许无认证访问，用于测试）
    if credentials is None:
        return None
    
    # 验证Bearer token格式
    if credentials.scheme != "Bearer":
        raise HTTPException(
            status_code=401,
            detail="无效的认证格式，应为Bearer token"
        )
    
    api_key = credentials.credentials
    
    # TODO: 实现实际的API Key验证逻辑
    # 1. 从数据库或配置文件中查找API Key
    # 2. 检查Key是否有效且未过期
    # 3. 检查配额是否用完
    
    # 临时验证逻辑：接受任何非空的API Key
    if not api_key or len(api_key) < 10:
        raise HTTPException(
            status_code=401,
            detail="无效的API Key"
        )
    
    return api_key


async def get_current_user(api_key: Optional[str] = Depends(verify_api_key)):
    """
    获取当前用户信息
    
    根据API Key获取用户信息
    """
    if api_key is None:
        # 无认证用户
        return {
            "user_id": "anonymous",
            "api_key": None,
            "quota": 10,  # 匿名用户限制10次
            "used": 0
        }
    
    # TODO: 从数据库获取用户信息
    # 临时返回示例用户信息
    return {
        "user_id": "test_user",
        "api_key": api_key,
        "quota": 100,
        "used": 0
    }