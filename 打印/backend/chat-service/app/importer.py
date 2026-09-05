import base64
import csv
import io
import re
import zipfile
import xml.etree.ElementTree as ET
from dataclasses import dataclass
from pathlib import PurePath
from typing import Dict, Iterable, List, Sequence

from pydantic import ValidationError

from app.schemas import KnowledgeCreate, KnowledgeImportError, KnowledgeImportResponse
from app.store import ChatStore


MAX_IMPORT_BYTES = 2 * 1024 * 1024
MAX_ERROR_DETAILS = 20

HEADER_ALIASES = {
    "question": {"question", "问题", "提问", "问法", "faq问题", "faq_question"},
    "answer": {"answer", "答案", "回答", "回复", "faq答案", "faq_answer"},
    "category": {"category", "分类", "类别"},
    "tags": {"tags", "标签", "关键词", "关键字"},
    "enabled": {"enabled", "启用", "是否启用", "状态"},
}


@dataclass
class RawImportRow:
    row_number: int
    values: Dict[str, str]


def import_knowledge_file(store: ChatStore, filename: str, content_base64: str) -> KnowledgeImportResponse:
    raw = decode_content(content_base64)
    suffix = PurePath(filename or "").suffix.lower()
    if suffix == ".csv":
        rows = parse_csv(raw)
    elif suffix == ".xlsx":
        rows = parse_xlsx(raw)
    else:
        raise ValueError("only .csv and .xlsx files are supported")

    existing_questions = {normalize_question(question) for question in store.list_knowledge_questions()}
    seen_questions = set()
    imported = 0
    skipped = 0
    failed = 0
    errors: List[KnowledgeImportError] = []

    for raw_row in rows:
        if is_blank_row(raw_row.values.values()):
            skipped += 1
            continue
        try:
            item = row_to_knowledge(raw_row.values)
        except ValueError as exc:
            failed += 1
            append_error(errors, raw_row.row_number, str(exc))
            continue
        except ValidationError as exc:
            failed += 1
            append_error(errors, raw_row.row_number, first_validation_message(exc))
            continue

        normalized_question = normalize_question(item.question)
        if normalized_question in existing_questions or normalized_question in seen_questions:
            skipped += 1
            continue

        store.create_knowledge(item)
        existing_questions.add(normalized_question)
        seen_questions.add(normalized_question)
        imported += 1

    return KnowledgeImportResponse(
        totalRows=len(rows),
        imported=imported,
        skipped=skipped,
        failed=failed,
        errors=errors,
    )


def decode_content(content_base64: str) -> bytes:
    try:
        raw = base64.b64decode(content_base64, validate=True)
    except Exception as exc:
        raise ValueError("file content must be valid base64") from exc
    if not raw:
        raise ValueError("file is empty")
    if len(raw) > MAX_IMPORT_BYTES:
        raise ValueError("file size exceeds 2MB")
    return raw


def parse_csv(raw: bytes) -> List[RawImportRow]:
    text = decode_text(raw)
    reader = csv.DictReader(io.StringIO(text))
    if not reader.fieldnames:
        raise ValueError("CSV header row is required")
    header_map = build_header_map(reader.fieldnames)
    rows = []
    for index, row in enumerate(reader, start=2):
        rows.append(RawImportRow(row_number=index, values=canonicalize_row(row, header_map)))
    return rows


def parse_xlsx(raw: bytes) -> List[RawImportRow]:
    try:
        with zipfile.ZipFile(io.BytesIO(raw)) as workbook:
            worksheet_name = first_worksheet_name(workbook)
            shared_strings = read_shared_strings(workbook)
            sheet_rows = read_sheet_rows(workbook, worksheet_name, shared_strings)
    except zipfile.BadZipFile as exc:
        raise ValueError("invalid .xlsx file") from exc

    if not sheet_rows:
        raise ValueError("Excel worksheet is empty")
    headers = [str(value).strip() for value in sheet_rows[0]]
    header_map = build_header_map(headers)
    rows = []
    for index, values in enumerate(sheet_rows[1:], start=2):
        row = {headers[column] if column < len(headers) else "": value for column, value in enumerate(values)}
        rows.append(RawImportRow(row_number=index, values=canonicalize_row(row, header_map)))
    return rows


def decode_text(raw: bytes) -> str:
    for encoding in ("utf-8-sig", "gb18030"):
        try:
            return raw.decode(encoding)
        except UnicodeDecodeError:
            continue
    raise ValueError("CSV file must be UTF-8 or GB18030 encoded")


def build_header_map(headers: Sequence[str]) -> Dict[str, str]:
    normalized = {normalize_header(header): header for header in headers if str(header).strip()}
    result = {}
    for field, aliases in HEADER_ALIASES.items():
        for alias in aliases:
            header = normalized.get(normalize_header(alias))
            if header is not None:
                result[field] = header
                break
    missing = [field for field in ("question", "answer") if field not in result]
    if missing:
        raise ValueError("import file must contain question and answer columns")
    return result


def canonicalize_row(row: Dict[str, str], header_map: Dict[str, str]) -> Dict[str, str]:
    return {
        field: str(row.get(header, "") or "").strip()
        for field, header in header_map.items()
    }


def row_to_knowledge(row: Dict[str, str]) -> KnowledgeCreate:
    question = row.get("question", "").strip()
    answer = row.get("answer", "").strip()
    if not question or not answer:
        raise ValueError("question and answer are required")
    return KnowledgeCreate(
        question=question,
        answer=answer,
        category=row.get("category", "").strip() or "通用",
        tags=parse_tags(row.get("tags", "")),
        enabled=parse_enabled(row.get("enabled", "")),
    )


def parse_tags(value: str) -> List[str]:
    if not value:
        return []
    return [tag.strip() for tag in re.split(r"[,，;；\n]", value) if tag.strip()]


def parse_enabled(value: str) -> bool:
    normalized = value.strip().lower()
    if not normalized:
        return True
    if normalized in {"0", "false", "no", "n", "disabled", "off", "否", "禁用", "停用"}:
        return False
    return True


def first_worksheet_name(workbook: zipfile.ZipFile) -> str:
    candidates = sorted(name for name in workbook.namelist() if re.fullmatch(r"xl/worksheets/sheet\d+\.xml", name))
    if not candidates:
        raise ValueError("Excel workbook has no worksheet")
    return candidates[0]


def read_shared_strings(workbook: zipfile.ZipFile) -> List[str]:
    if "xl/sharedStrings.xml" not in workbook.namelist():
        return []
    root = ET.fromstring(workbook.read("xl/sharedStrings.xml"))
    strings = []
    for item in root:
        strings.append("".join(text.text or "" for text in item.iter() if strip_namespace(text.tag) == "t"))
    return strings


def read_sheet_rows(workbook: zipfile.ZipFile, worksheet_name: str, shared_strings: Sequence[str]) -> List[List[str]]:
    root = ET.fromstring(workbook.read(worksheet_name))
    rows = []
    for row_node in root.iter():
        if strip_namespace(row_node.tag) != "row":
            continue
        row_values: List[str] = []
        for cell in row_node:
            if strip_namespace(cell.tag) != "c":
                continue
            column_index = cell_column_index(cell.attrib.get("r", ""))
            while len(row_values) <= column_index:
                row_values.append("")
            row_values[column_index] = cell_value(cell, shared_strings)
        rows.append(row_values)
    return rows


def cell_value(cell: ET.Element, shared_strings: Sequence[str]) -> str:
    cell_type = cell.attrib.get("t", "")
    if cell_type == "inlineStr":
        return "".join(text.text or "" for text in cell.iter() if strip_namespace(text.tag) == "t").strip()
    value = ""
    for child in cell:
        if strip_namespace(child.tag) == "v":
            value = child.text or ""
            break
    if cell_type == "s":
        try:
            return shared_strings[int(value)].strip()
        except (ValueError, IndexError):
            return ""
    if cell_type == "b":
        return "TRUE" if value == "1" else "FALSE"
    return value.strip()


def cell_column_index(reference: str) -> int:
    letters = "".join(ch for ch in reference if ch.isalpha()).upper()
    if not letters:
        return 0
    index = 0
    for char in letters:
        index = index * 26 + (ord(char) - ord("A") + 1)
    return index - 1


def strip_namespace(tag: str) -> str:
    return tag.rsplit("}", 1)[-1]


def normalize_header(header: str) -> str:
    return re.sub(r"[\s_\-]+", "", str(header or "").strip().lower())


def normalize_question(question: str) -> str:
    return re.sub(r"\s+", "", question.strip().lower())


def is_blank_row(values: Iterable[str]) -> bool:
    return not any(str(value).strip() for value in values)


def append_error(errors: List[KnowledgeImportError], row: int, message: str):
    if len(errors) < MAX_ERROR_DETAILS:
        errors.append(KnowledgeImportError(row=row, message=message))


def first_validation_message(exc: ValidationError) -> str:
    errors = exc.errors()
    if not errors:
        return "invalid row"
    field = ".".join(str(part) for part in errors[0].get("loc", []))
    message = errors[0].get("msg", "invalid value")
    return f"{field}: {message}" if field else str(message)
