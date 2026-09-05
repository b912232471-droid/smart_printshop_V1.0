import base64
import hashlib
import hmac
import json
import time
import urllib.parse
import uuid
from dataclasses import dataclass, field
from typing import Optional, Set, Tuple

from fastapi import Depends, HTTPException, Request

from app.config import settings


@dataclass(frozen=True)
class Principal:
    type: str
    id: int
    subject: str
    role: Optional[str] = None
    username: Optional[str] = None
    token: Optional[str] = field(default=None, repr=False, compare=False)


def _decode_base64_url(value: str) -> bytes:
    padded = value + "=" * (-len(value) % 4)
    return base64.urlsafe_b64decode(padded.encode("utf-8"))


def verify_jwt(token: str) -> Optional[Principal]:
    secret = (settings.JWT_SECRET or "").strip()
    if len(secret.encode("utf-8")) < 32:
        return None
    try:
        parts = token.split(".")
        if len(parts) != 3:
            return None
        header = json.loads(_decode_base64_url(parts[0]))
        if header.get("alg") != "HS256" or header.get("typ") != "JWT":
            return None
        signed = f"{parts[0]}.{parts[1]}".encode("utf-8")
        expected = hmac.new(secret.encode("utf-8"), signed, hashlib.sha256).digest()
        actual = _decode_base64_url(parts[2])
        if not hmac.compare_digest(expected, actual):
            return None
        claims = json.loads(_decode_base64_url(parts[1]))
        token_type = str(claims.get("type") or "")
        if token_type not in {"user", "admin"}:
            return None
        principal_id = int(claims.get("id") or 0)
        if principal_id <= 0:
            return None
        subject = str(claims.get("sub") or "")
        if subject != f"{token_type}:{principal_id}":
            return None
        issued_at = int(claims.get("iat") or 0)
        expires_at = int(claims.get("exp") or 0)
        now = int(time.time())
        if issued_at <= 0 or issued_at > now + 60 or expires_at < issued_at or now >= expires_at:
            return None
        uuid.UUID(str(claims.get("jti") or ""))
        if not str(claims.get("role") or "").strip() or not str(claims.get("username") or "").strip():
            return None
        return Principal(
            type=token_type,
            id=principal_id,
            subject=subject,
            role=claims.get("role"),
            username=claims.get("username"),
            token=token,
        )
    except Exception:
        return None


def _bearer_token(request: Request) -> Optional[str]:
    header = request.headers.get("authorization") or ""
    if header.startswith("Bearer "):
        token = header[7:].strip()
        return token or None
    return None


async def require_principal(request: Request) -> Principal:
    if not settings.AUTH_ENABLED:
        return Principal(type="user", id=0, subject="local-dev")
    token = _bearer_token(request)
    principal = verify_jwt(token) if token else None
    if principal is None:
        raise HTTPException(status_code=401, detail="unauthorized")
    return principal


async def require_admin(principal: Principal = Depends(require_principal)) -> Principal:
    if principal.type != "admin":
        raise HTTPException(status_code=403, detail="admin permission required")
    return principal


def _mysql_backend() -> bool:
    return settings.DATABASE_URL.startswith(("mysql://", "mysql+pymysql://"))


def _load_printshop_rbac(account_id: int) -> Tuple[Set[str], Set[str]]:
    """跨库查询 print_shop.v_account_roles / v_account_perms（与应用层共用同一 MySQL 账户）。"""
    import pymysql

    parsed = urllib.parse.urlparse(settings.DATABASE_URL.replace("mysql+pymysql://", "mysql://", 1))
    query = urllib.parse.parse_qs(parsed.query)
    conn = pymysql.connect(
        cursorclass=pymysql.cursors.DictCursor,
        autocommit=True,
        host=parsed.hostname or "mysql",
        port=parsed.port or 3306,
        user=urllib.parse.unquote(parsed.username or ""),
        password=urllib.parse.unquote(parsed.password or ""),
        database="print_shop",
        charset=query.get("charset", ["utf8mb4"])[0],
    )
    try:
        with conn.cursor() as cursor:
            cursor.execute("SELECT role_key FROM v_account_roles WHERE account_id = %s", [account_id])
            roles = {str(row["role_key"]) for row in cursor.fetchall()}
            cursor.execute("SELECT DISTINCT permission FROM v_account_perms WHERE account_id = %s", [account_id])
            perms = {str(row["permission"]) for row in cursor.fetchall()}
    finally:
        conn.close()
    return roles, perms


def require_permission(*permissions: str):
    """管理端权限点校验：superadmin 代码级直通，其余管理员按 print_shop RBAC 权限集合判定。

    print_shop 权限库不可用时 fail-closed（503），sqlite 本地兜底仅保留管理员类型检查。
    """

    async def dependency(principal: Principal = Depends(require_admin)) -> Principal:
        if not _mysql_backend():
            return principal
        try:
            roles, perms = _load_printshop_rbac(principal.id)
        except Exception:
            raise HTTPException(status_code=503, detail="permission service unavailable") from None
        if "superadmin" in roles or any(perm in perms for perm in permissions):
            return principal
        raise HTTPException(status_code=403, detail="permission denied: " + ", ".join(permissions))

    return dependency
