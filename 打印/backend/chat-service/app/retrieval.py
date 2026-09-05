import re
import uuid
from dataclasses import dataclass
from typing import List, Sequence

from app.schemas import AskResponse, AskSource, KnowledgeItem
from app.store import ChatStore
from app.deepseek import DeepSeekClient


FALLBACK_ANSWER = "我暂时没有找到足够准确的答案。你可以换一种问法，或拨打客服电话 400-778-1811 咨询人工客服。"


@dataclass(frozen=True)
class Match:
    item: KnowledgeItem
    score: float


class ChatEngine:
    def __init__(self, store: ChatStore, llm: DeepSeekClient):
        self.store = store
        self.llm = llm

    def ask(self, question: str, session_id: str | None, user_type: str, user_id: int) -> AskResponse:
        normalized_question = question.strip()
        resolved_session_id = session_id or uuid.uuid4().hex
        matches = self.search(normalized_question)
        answer = FALLBACK_ANSWER
        source = "fallback"

        if self.llm.available:
            generated = self.llm.generate(normalized_question, [match.item for match in matches[:3]])
            if generated:
                answer = generated
                source = "deepseek"
        if source == "fallback" and matches:
            answer = matches[0].item.answer
            source = "knowledge"

        source_ids = [match.item.id for match in matches[:3]]
        self.store.log_chat(user_type, user_id, resolved_session_id, normalized_question, answer, source, source_ids)
        return AskResponse(
            answer=answer,
            source=source,
            sources=[
                AskSource(id=match.item.id, question=match.item.question, category=match.item.category, score=match.score)
                for match in matches[:3]
            ],
            sessionId=resolved_session_id,
        )

    def search(self, question: str) -> List[Match]:
        items = self.store.enabled_knowledge()
        scored = []
        for item in items:
            score = self._score(question, item)
            if score > 0:
                scored.append(Match(item=item, score=score))
        scored.sort(key=lambda match: match.score, reverse=True)
        return scored

    def _score(self, question: str, item: KnowledgeItem) -> float:
        q = normalize(question)
        iq = normalize(item.question)
        answer = normalize(item.answer)
        category = normalize(item.category)
        tags = [normalize(tag) for tag in item.tags]
        score = 0.0
        if q and (q in iq or iq in q):
            score += 30
        if category and category in q:
            score += 6
        for tag in tags:
            if tag and tag in q:
                score += 10
        q_terms = set(terms(q))
        item_terms = set(terms(" ".join([iq, answer, category, " ".join(tags)])))
        if q_terms and item_terms:
            overlap = len(q_terms & item_terms)
            score += overlap * 2
        return score


def normalize(value: str) -> str:
    return re.sub(r"\s+", "", (value or "").lower())


def terms(value: str) -> Sequence[str]:
    words = re.findall(r"[a-z0-9]+|[\u4e00-\u9fff]", value.lower())
    chinese = "".join(part for part in words if re.match(r"[\u4e00-\u9fff]", part))
    grams = [chinese[i:i + 2] for i in range(max(0, len(chinese) - 1))]
    return [part for part in words if len(part) > 1 or not re.match(r"[\u4e00-\u9fff]", part)] + grams
