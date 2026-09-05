import base64
import hashlib
import hmac
import json
import time
import uuid
from dataclasses import dataclass, field
from typing import Optional

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
