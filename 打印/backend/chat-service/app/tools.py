import json
import urllib.error
import urllib.request
from typing import Any, Dict, List

from app.config import settings
from app.schemas import ToolExecutionResult
from app.security import Principal


TOOL_DEFINITIONS = {
    "search_knowledge": {
        "type": "function",
        "function": {
            "name": "search_knowledge",
            "description": "搜索平台专属 FAQ 和 Markdown 知识库。需要核实平台规则、流程或说明时使用。",
            "parameters": {
                "type": "object",
                "properties": {"query": {"type": "string", "description": "要检索的问题或关键词"}},
                "required": ["query"],
                "additionalProperties": False,
            },
        },
    },
    "get_my_order": {
        "type": "function",
        "function": {
            "name": "get_my_order",
            "description": "查询当前登录用户自己的指定订单。用户提到具体订单编号时使用。",
            "parameters": {
                "type": "object",
                "properties": {"order_id": {"type": "integer", "description": "订单编号"}},
                "required": ["order_id"],
                "additionalProperties": False,
            },
        },
    },
    "list_my_orders": {
        "type": "function",
        "function": {
            "name": "list_my_orders",
            "description": "查询当前登录用户最近的订单列表。",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
    },
    "get_store_info": {
        "type": "function",
        "function": {
            "name": "get_store_info",
            "description": "查询营业门店、地址、营业时间和联系电话。",
            "parameters": {
                "type": "object",
                "properties": {"keyword": {"type": "string", "description": "可选的门店名称或地址关键词"}},
                "additionalProperties": False,
            },
        },
    },
    "get_service_price": {
        "type": "function",
        "function": {
            "name": "get_service_price",
            "description": "查询打印服务项目、说明和基础单价。",
            "parameters": {
                "type": "object",
                "properties": {"keyword": {"type": "string", "description": "可选的服务名称或分类关键词"}},
                "additionalProperties": False,
            },
        },
    },
    "get_schedule_status": {
        "type": "function",
        "function": {
            "name": "get_schedule_status",
            "description": "查询当前登录用户是否绑定教务账号及课表同步状态。",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
    },
    "get_photo_service_status": {
        "type": "function",
        "function": {
            "name": "get_photo_service_status",
            "description": "查询证件照服务当前是否可用。",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
    },
    "handoff_to_human": {
        "type": "function",
        "function": {
            "name": "handoff_to_human",
            "description": "当问题无法可靠解决或用户要求人工客服时，提供人工客服渠道。",
            "parameters": {"type": "object", "properties": {}, "additionalProperties": False},
        },
    },
}


ORDER_STATUS = {0: "待处理", 1: "打印中", 2: "待取件", 3: "已完成", 4: "已取消"}


class PlatformToolExecutor:
    def definitions(self, allowed_tools: List[str]) -> List[Dict[str, Any]]:
        return [TOOL_DEFINITIONS[name] for name in allowed_tools if name in TOOL_DEFINITIONS]

    def execute(
        self,
        name: str,
        arguments: Dict[str, Any],
        principal: Principal,
        hotline: str,
        service_hours: str,
        handoff_message: str,
    ) -> ToolExecutionResult:
        if name == "get_my_order":
            order_id = int(arguments.get("order_id") or 0)
            if order_id <= 0:
                return ToolExecutionResult(ok=False, summary="订单编号无效")
            data = self._get(f"/api/print/order/{order_id}", principal)
            clean = self._clean_order(data)
            return ToolExecutionResult(ok=True, data=clean, summary=f"已查询订单 {order_id}，状态为{clean.get('status', '未知')}")
        if name == "list_my_orders":
            data = self._get(f"/api/print/order/user/{principal.id}", principal)
            orders = [self._clean_order(item) for item in (data if isinstance(data, list) else [])[:10]]
            return ToolExecutionResult(ok=True, data=orders, summary=f"已查询最近 {len(orders)} 个订单")
        if name == "get_store_info":
            data = self._get("/api/print/store/active", principal)
            stores = [self._clean_store(item) for item in data if isinstance(item, dict)] if isinstance(data, list) else []
            keyword = str(arguments.get("keyword") or "").strip().lower()
            if keyword:
                stores = [item for item in stores if keyword in json.dumps(item, ensure_ascii=False).lower()]
            return ToolExecutionResult(ok=True, data=stores[:20], summary=f"找到 {len(stores[:20])} 个营业门店")
        if name == "get_service_price":
            data = self._get("/api/print/service/", principal)
            services = [self._clean_service(item) for item in data if isinstance(item, dict)] if isinstance(data, list) else []
            keyword = str(arguments.get("keyword") or "").strip().lower()
            if keyword:
                services = [item for item in services if keyword in json.dumps(item, ensure_ascii=False).lower()]
            return ToolExecutionResult(ok=True, data=services[:30], summary=f"找到 {len(services[:30])} 个服务项目")
        if name == "get_schedule_status":
            data = self._get("/api/schedule/bind-status", principal)
            clean = {key: value for key, value in data.items() if key.lower() not in {"jwpassword", "password"}} if isinstance(data, dict) else data
            return ToolExecutionResult(ok=True, data=clean, summary="已查询课表绑定状态")
        if name == "get_photo_service_status":
            data = self._get("/api/photo/health", principal)
            available = isinstance(data, dict) and data.get("status") == "ok"
            return ToolExecutionResult(ok=available, data={"available": available}, summary="证件照服务可用" if available else "证件照服务暂不可用")
        if name == "handoff_to_human":
            data = {"message": handoff_message, "hotline": hotline, "serviceHours": service_hours}
            return ToolExecutionResult(ok=True, data=data, summary=handoff_message)
        return ToolExecutionResult(ok=False, summary="工具不在允许列表中")

    def _get(self, path: str, principal: Principal) -> Any:
        headers = {"Accept": "application/json"}
        if principal.token:
            headers["Authorization"] = f"Bearer {principal.token}"
        request = urllib.request.Request(settings.INTERNAL_GATEWAY_URL.rstrip("/") + path, headers=headers, method="GET")
        try:
            with urllib.request.urlopen(request, timeout=settings.TOOL_TIMEOUT_SECONDS) as response:
                return json.loads(response.read().decode("utf-8"))
        except urllib.error.HTTPError as exc:
            if exc.code in {401, 403, 404}:
                detail = self._error_message(exc)
                raise ValueError(detail or f"站内接口返回 {exc.code}") from exc
            raise RuntimeError(f"站内服务返回 {exc.code}") from exc
        except (OSError, json.JSONDecodeError) as exc:
            raise RuntimeError("站内服务暂不可用") from exc

    def _error_message(self, exc: urllib.error.HTTPError) -> str:
        try:
            data = json.loads(exc.read().decode("utf-8"))
            return str(data.get("message") or data.get("detail") or "")
        except (OSError, json.JSONDecodeError):
            return ""

    def _clean_order(self, item: Any) -> Dict[str, Any]:
        if not isinstance(item, dict):
            return {}
        status_value = item.get("orderStatus")
        return {
            "orderId": item.get("id"),
            "status": ORDER_STATUS.get(status_value, "未知"),
            "serviceName": item.get("serviceName"),
            "storeName": item.get("storeName"),
            "storeAddress": item.get("storeAddress"),
            "appointTime": item.get("appointTime"),
            "queueNumber": item.get("queueNumber"),
            "totalPrice": item.get("totalPrice"),
            "copies": item.get("copies"),
            "pageCount": item.get("pageCount"),
            "duplex": "双面" if item.get("duplex") == 1 else "单面",
            "colorMode": item.get("colorMode"),
            "paperSize": item.get("paperSize"),
            "createdAt": item.get("createTime"),
        }

    def _clean_store(self, item: Dict[str, Any]) -> Dict[str, Any]:
        return {key: item.get(key) for key in ("id", "name", "shortName", "address", "phone", "hours", "services")}

    def _clean_service(self, item: Dict[str, Any]) -> Dict[str, Any]:
        return {key: item.get(key) for key in ("id", "name", "category", "price", "description", "unit", "status")}
