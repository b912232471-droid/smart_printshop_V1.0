import json
import sqlite3
import urllib.parse
from contextlib import contextmanager
from datetime import datetime, timezone
from typing import Any, Dict, Iterable, List, Optional, Tuple

from app.config import settings
from app.schemas import KnowledgeCreate, KnowledgeItem, KnowledgeUpdate


DEFAULT_KNOWLEDGE = [
    KnowledgeCreate(
        question="如何上传文件并下单打印？",
        answer="在首页选择打印服务，上传 PDF、Word、Excel 或图片文件，选择门店和打印参数后提交订单。当前支付为模拟支付，用于演示订单状态流转。",
        category="下单打印",
        tags=["上传", "下单", "打印", "模拟支付"],
    ),
    KnowledgeCreate(
        question="证件照可以做哪些处理？",
        answer="证件照功能支持上传照片后生成常用尺寸证件照，也支持白底、蓝底、红底等背景替换。处理完成后可以预览并保存到相册。",
        category="证件照",
        tags=["证件照", "换底色", "保存"],
    ),
    KnowledgeCreate(
        question="课表同步失败怎么办？",
        answer="请先确认已绑定正确的教务账号和密码。若提示教务同步适配器未配置，说明当前环境还没有接入真实学校教务系统，可先查看已缓存课表或稍后再试。",
        category="课表",
        tags=["课表", "教务", "同步"],
    ),
    KnowledgeCreate(
        question="如何联系人工客服？",
        answer="如 AI 客服无法解决问题，请拨打客服电话 400-778-1811，服务时间为 9:00-22:00。",
        category="客服",
        tags=["人工客服", "电话", "热线"],
    ),
]


class ChatStore:
    def __init__(self, database_url: str):
        self.database_url = database_url
        self.driver = "mysql" if database_url.startswith(("mysql://", "mysql+pymysql://")) else "sqlite"
        self.sqlite_path = self._sqlite_path(database_url) if self.driver == "sqlite" else ""
        self.mysql_config = self._mysql_config(database_url) if self.driver == "mysql" else {}
        self.ensure_schema()
        if settings.SEED_DEFAULTS:
            self.seed_defaults()

    def ensure_schema(self):
        if self.driver == "mysql":
            statements = [
                """
                CREATE TABLE IF NOT EXISTS knowledge_items (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    question VARCHAR(500) NOT NULL,
                    answer TEXT NOT NULL,
                    category VARCHAR(80) NOT NULL DEFAULT '通用',
                    tags VARCHAR(1000) DEFAULT NULL,
                    enabled TINYINT(1) NOT NULL DEFAULT 1,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                    FULLTEXT KEY idx_knowledge_fulltext (question, answer, category, tags)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
                """
                CREATE TABLE IF NOT EXISTS chat_logs (
                    id BIGINT PRIMARY KEY AUTO_INCREMENT,
                    user_type VARCHAR(20) NOT NULL,
                    user_id BIGINT NOT NULL,
                    session_id VARCHAR(80) NOT NULL,
                    question VARCHAR(1000) NOT NULL,
                    answer TEXT NOT NULL,
                    source VARCHAR(40) NOT NULL,
                    source_ids VARCHAR(200) DEFAULT NULL,
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    KEY idx_chat_logs_user (user_type, user_id),
                    KEY idx_chat_logs_session (session_id)
                ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci
                """,
            ]
        else:
            statements = [
                """
                CREATE TABLE IF NOT EXISTS knowledge_items (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    question TEXT NOT NULL,
                    answer TEXT NOT NULL,
                    category TEXT NOT NULL DEFAULT '通用',
                    tags TEXT,
                    enabled INTEGER NOT NULL DEFAULT 1,
                    created_at TEXT NOT NULL,
                    updated_at TEXT NOT NULL
                )
                """,
                """
                CREATE TABLE IF NOT EXISTS chat_logs (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    user_type TEXT NOT NULL,
                    user_id INTEGER NOT NULL,
                    session_id TEXT NOT NULL,
                    question TEXT NOT NULL,
                    answer TEXT NOT NULL,
                    source TEXT NOT NULL,
                    source_ids TEXT,
                    created_at TEXT NOT NULL
                )
                """,
            ]
        with self.connection() as conn:
            for statement in statements:
                self.execute(conn, statement)
            self.commit(conn)

    def seed_defaults(self):
        if self.count_knowledge() > 0:
            return
        for item in DEFAULT_KNOWLEDGE:
            self.create_knowledge(item)

    def count_knowledge(self) -> int:
        with self.connection() as conn:
            rows = self.query(conn, "SELECT COUNT(*) AS total FROM knowledge_items")
            return int(rows[0]["total"])

    def list_knowledge(self, keyword: str = "", category: str = "", include_disabled: bool = False) -> Tuple[List[KnowledgeItem], int]:
        clauses = []
        params: List[Any] = []
        if not include_disabled:
            clauses.append("enabled = %s")
            params.append(1)
        if category:
            clauses.append("category = %s")
            params.append(category)
        if keyword:
            like = f"%{keyword}%"
            clauses.append("(question LIKE %s OR answer LIKE %s OR tags LIKE %s)")
            params.extend([like, like, like])
        where = " WHERE " + " AND ".join(clauses) if clauses else ""
        with self.connection() as conn:
            rows = self.query(conn, f"SELECT * FROM knowledge_items{where} ORDER BY updated_at DESC, id DESC", params)
            total_rows = self.query(conn, f"SELECT COUNT(*) AS total FROM knowledge_items{where}", params)
        return [self._row_to_item(row) for row in rows], int(total_rows[0]["total"])

    def enabled_knowledge(self) -> List[KnowledgeItem]:
        items, _ = self.list_knowledge(include_disabled=False)
        return items

    def list_knowledge_questions(self) -> List[str]:
        with self.connection() as conn:
            rows = self.query(conn, "SELECT question FROM knowledge_items")
        return [str(row["question"]) for row in rows]

    def get_knowledge(self, item_id: int) -> Optional[KnowledgeItem]:
        with self.connection() as conn:
            rows = self.query(conn, "SELECT * FROM knowledge_items WHERE id = %s", [item_id])
        return self._row_to_item(rows[0]) if rows else None

    def create_knowledge(self, item: KnowledgeCreate) -> KnowledgeItem:
        now = self.now()
        with self.connection() as conn:
            cursor = self.execute(
                conn,
                """
                INSERT INTO knowledge_items (question, answer, category, tags, enabled, created_at, updated_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
                """,
                [item.question.strip(), item.answer.strip(), item.category.strip() or "通用", json.dumps(item.tags, ensure_ascii=False), int(item.enabled), now, now],
            )
            item_id = cursor.lastrowid
            self.commit(conn)
        created = self.get_knowledge(int(item_id))
        if created is None:
            raise RuntimeError("created knowledge item not found")
        return created

    def update_knowledge(self, item_id: int, item: KnowledgeUpdate) -> KnowledgeItem:
        now = self.now()
        with self.connection() as conn:
            cursor = self.execute(
                conn,
                """
                UPDATE knowledge_items
                SET question = %s, answer = %s, category = %s, tags = %s, enabled = %s, updated_at = %s
                WHERE id = %s
                """,
                [item.question.strip(), item.answer.strip(), item.category.strip() or "通用", json.dumps(item.tags, ensure_ascii=False), int(item.enabled), now, item_id],
            )
            self.commit(conn)
            if cursor.rowcount == 0:
                raise KeyError(item_id)
        updated = self.get_knowledge(item_id)
        if updated is None:
            raise KeyError(item_id)
        return updated

    def delete_knowledge(self, item_id: int):
        with self.connection() as conn:
            cursor = self.execute(conn, "DELETE FROM knowledge_items WHERE id = %s", [item_id])
            self.commit(conn)
            if cursor.rowcount == 0:
                raise KeyError(item_id)

    def log_chat(self, user_type: str, user_id: int, session_id: str, question: str, answer: str, source: str, source_ids: Iterable[int]):
        with self.connection() as conn:
            self.execute(
                conn,
                """
                INSERT INTO chat_logs (user_type, user_id, session_id, question, answer, source, source_ids, created_at)
                VALUES (%s, %s, %s, %s, %s, %s, %s, %s)
                """,
                [user_type, user_id, session_id, question, answer, source, ",".join(str(x) for x in source_ids), self.now()],
            )
            self.commit(conn)

    @contextmanager
    def connection(self):
        if self.driver == "mysql":
            try:
                import pymysql
            except ImportError as exc:
                raise RuntimeError("PyMySQL is required for MySQL CHAT_DATABASE_URL") from exc
            conn = pymysql.connect(cursorclass=pymysql.cursors.DictCursor, autocommit=False, **self.mysql_config)
        else:
            conn = sqlite3.connect(self.sqlite_path)
            conn.row_factory = sqlite3.Row
        try:
            yield conn
        finally:
            conn.close()

    def execute(self, conn, sql: str, params: Optional[List[Any]] = None):
        cursor = conn.cursor()
        cursor.execute(self._sql(sql), params or [])
        return cursor

    def query(self, conn, sql: str, params: Optional[List[Any]] = None) -> List[Dict[str, Any]]:
        cursor = self.execute(conn, sql, params)
        rows = cursor.fetchall()
        return [dict(row) for row in rows]

    def commit(self, conn):
        conn.commit()

    def _sql(self, sql: str) -> str:
        if self.driver == "sqlite":
            return sql.replace("%s", "?")
        return sql

    def _row_to_item(self, row: Dict[str, Any]) -> KnowledgeItem:
        tags_raw = row.get("tags") or "[]"
        try:
            tags = json.loads(tags_raw)
        except json.JSONDecodeError:
            tags = [x.strip() for x in str(tags_raw).split(",") if x.strip()]
        return KnowledgeItem(
            id=int(row["id"]),
            question=row["question"],
            answer=row["answer"],
            category=row.get("category") or "通用",
            tags=tags,
            enabled=bool(row.get("enabled")),
            createdAt=self._parse_datetime(row.get("created_at")),
            updatedAt=self._parse_datetime(row.get("updated_at")),
        )

    def _parse_datetime(self, value) -> datetime:
        if isinstance(value, datetime):
            return value
        return datetime.fromisoformat(str(value).replace("Z", "+00:00"))

    def now(self) -> str:
        return datetime.now(timezone.utc).replace(microsecond=0).strftime("%Y-%m-%d %H:%M:%S")

    def _sqlite_path(self, database_url: str) -> str:
        if database_url.startswith("sqlite:///"):
            return database_url[len("sqlite:///"):]
        if database_url == "sqlite:///:memory:":
            return ":memory:"
        return "./chatbot.sqlite3"

    def _mysql_config(self, database_url: str) -> Dict[str, Any]:
        parsed = urllib.parse.urlparse(database_url.replace("mysql+pymysql://", "mysql://", 1))
        query = urllib.parse.parse_qs(parsed.query)
        return {
            "host": parsed.hostname or "mysql",
            "port": parsed.port or 3306,
            "user": urllib.parse.unquote(parsed.username or ""),
            "password": urllib.parse.unquote(parsed.password or ""),
            "database": parsed.path.lstrip("/") or "chatbot_db",
            "charset": query.get("charset", ["utf8mb4"])[0],
        }
