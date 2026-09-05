import base64
import hashlib
import re
import uuid
from dataclasses import dataclass
from pathlib import Path, PurePath
from typing import List

from app.config import settings


@dataclass(frozen=True)
class MarkdownChunk:
    chunk_index: int
    heading: str
    content: str
    token_estimate: int


@dataclass(frozen=True)
class MarkdownUpload:
    filename: str
    title: str
    checksum: str
    storage_path: str


def save_markdown(filename: str, content_base64: str, title: str = "") -> MarkdownUpload:
    suffix = PurePath(filename or "").suffix.lower()
    if suffix not in {".md", ".markdown"}:
        raise ValueError("only .md and .markdown files are supported")
    try:
        raw = base64.b64decode(content_base64, validate=True)
    except Exception as exc:
        raise ValueError("file content must be valid base64") from exc
    if not raw:
        raise ValueError("file is empty")
    if len(raw) > settings.MAX_MARKDOWN_BYTES:
        raise ValueError(f"file size exceeds {settings.MAX_MARKDOWN_BYTES // 1024 // 1024}MB")
    try:
        text = raw.decode("utf-8-sig")
    except UnicodeDecodeError as exc:
        raise ValueError("Markdown file must be UTF-8 encoded") from exc
    if "\x00" in text:
        raise ValueError("Markdown file contains invalid characters")
    normalized = text.replace("\r\n", "\n").replace("\r", "\n").strip()
    if not normalized:
        raise ValueError("Markdown file has no readable content")
    document_dir = Path(settings.DOCUMENT_DIR).resolve()
    document_dir.mkdir(parents=True, exist_ok=True)
    storage_path = document_dir / f"{uuid.uuid4().hex}.md"
    storage_path.write_text(normalized + "\n", encoding="utf-8")
    detected_title = title.strip() or extract_title(normalized) or PurePath(filename).stem
    return MarkdownUpload(
        filename=PurePath(filename).name,
        title=detected_title[:255],
        checksum=hashlib.sha256(normalized.encode("utf-8")).hexdigest(),
        storage_path=str(storage_path),
    )


def load_and_chunk(storage_path: str) -> List[MarkdownChunk]:
    text = Path(storage_path).read_text(encoding="utf-8")
    return chunk_markdown(text, settings.CHUNK_SIZE, settings.CHUNK_OVERLAP)


def chunk_markdown(text: str, chunk_size: int, overlap: int) -> List[MarkdownChunk]:
    cleaned = sanitize_markdown(text)
    sections = split_sections(cleaned)
    chunks: List[MarkdownChunk] = []
    for heading, content in sections:
        for piece in split_content(content, max(200, chunk_size), max(0, min(overlap, chunk_size // 2))):
            body = piece.strip()
            if not body:
                continue
            chunks.append(
                MarkdownChunk(
                    chunk_index=len(chunks),
                    heading=heading[:500],
                    content=body,
                    token_estimate=max(1, len(body) // 2),
                )
            )
    if not chunks and cleaned.strip():
        body = cleaned.strip()[:chunk_size]
        chunks.append(MarkdownChunk(0, "", body, max(1, len(body) // 2)))
    return chunks


def extract_title(text: str) -> str:
    for line in text.splitlines():
        match = re.match(r"^#\s+(.+?)\s*$", line)
        if match:
            return strip_inline_markdown(match.group(1))
    return ""


def sanitize_markdown(text: str) -> str:
    value = re.sub(r"<script\b[^>]*>.*?</script>", "", text, flags=re.IGNORECASE | re.DOTALL)
    value = re.sub(r"<style\b[^>]*>.*?</style>", "", value, flags=re.IGNORECASE | re.DOTALL)
    value = re.sub(r"<!--.*?-->", "", value, flags=re.DOTALL)
    value = re.sub(r"<[^>]+>", " ", value)
    value = re.sub(r"\n{3,}", "\n\n", value)
    return value.strip()


def split_sections(text: str) -> List[tuple[str, str]]:
    headings: List[str] = []
    current_heading = ""
    buffer: List[str] = []
    sections: List[tuple[str, str]] = []
    for line in text.splitlines():
        match = re.match(r"^(#{1,6})\s+(.+?)\s*$", line)
        if match:
            if buffer and any(part.strip() for part in buffer):
                sections.append((current_heading, "\n".join(buffer).strip()))
            level = len(match.group(1))
            label = strip_inline_markdown(match.group(2))
            headings = headings[: level - 1]
            while len(headings) < level - 1:
                headings.append("")
            headings.append(label)
            current_heading = " / ".join(item for item in headings if item)
            buffer = []
        else:
            buffer.append(line)
    if buffer and any(part.strip() for part in buffer):
        sections.append((current_heading, "\n".join(buffer).strip()))
    return sections


def split_content(content: str, chunk_size: int, overlap: int) -> List[str]:
    paragraphs = [part.strip() for part in re.split(r"\n\s*\n", content) if part.strip()]
    pieces: List[str] = []
    current = ""
    for paragraph in paragraphs:
        fragments = split_long_text(paragraph, chunk_size)
        for fragment in fragments:
            candidate = f"{current}\n\n{fragment}".strip() if current else fragment
            if current and len(candidate) > chunk_size:
                pieces.append(current)
                prefix = current[-overlap:] if overlap else ""
                current = f"{prefix}\n{fragment}".strip()
            else:
                current = candidate
    if current:
        pieces.append(current)
    return pieces


def split_long_text(value: str, chunk_size: int) -> List[str]:
    if len(value) <= chunk_size:
        return [value]
    sentences = [item.strip() for item in re.split(r"(?<=[。！？；.!?;])", value) if item.strip()]
    if len(sentences) <= 1:
        return [value[index:index + chunk_size] for index in range(0, len(value), chunk_size)]
    result: List[str] = []
    current = ""
    for sentence in sentences:
        if current and len(current) + len(sentence) > chunk_size:
            result.append(current)
            current = sentence
        else:
            current += sentence
    if current:
        result.append(current)
    return result


def strip_inline_markdown(value: str) -> str:
    text = re.sub(r"!\[([^]]*)]\([^)]+\)", r"\1", value)
    text = re.sub(r"\[([^]]+)]\([^)]+\)", r"\1", text)
    text = re.sub(r"[`*_~]", "", text)
    return text.strip()
