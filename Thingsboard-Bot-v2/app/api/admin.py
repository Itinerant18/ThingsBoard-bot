import hmac
from collections import defaultdict
from typing import Any, cast
from uuid import UUID

from fastapi import APIRouter, Header, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy import select

from app.db.models import Customer, HierarchyNode
from app.hierarchy.parser import ParsedNode, parse_device_path
from app.hierarchy.prefix import derive_prefix
from app.hierarchy.store import rebuild_ancestor_paths, upsert_nodes

router = APIRouter(prefix="/api/v1/admin", tags=["admin"])


def check_admin(request: Request, token: str | None) -> None:
    settings = request.app.state.settings
    if settings.require_admin_token and not settings.admin_token:
        raise HTTPException(503, "Admin token required but not configured")
    if settings.admin_token and not hmac.compare_digest(settings.admin_token, token or ""):
        raise HTTPException(403, "Invalid admin token")


class NodePayload(BaseModel):
    node_id: str
    customer_id: str
    parent_id: str | None = None
    node_type: str
    node_level: int
    display_name: str
    is_leaf: bool = False
    tb_device_id: UUID | None = None


@router.post("/import")
async def import_hierarchy(
    request: Request,
    payload: list[dict[str, object]],
    x_admin_token: str | None = Header(default=None),
) -> dict[str, object]:
    check_admin(request, x_admin_token)
    settings = request.app.state.settings
    async with request.app.state.session_factory() as session:
        prefixes = set(settings.prefixes) | {
            value for value in (await session.execute(select(Customer.prefix))).scalars() if value
        }
        nodes: list[ParsedNode] = []
        touched: set[str] = set()
        skipped = 0
        for device in payload:
            name = str(device.get("name") or "")
            telemetry = (
                cast(dict[str, Any], device.get("telemetry"))
                if isinstance(device.get("telemetry"), dict)
                else {}
            )
            attributes = (
                cast(dict[str, Any], device.get("serverAttributes"))
                if isinstance(device.get("serverAttributes"), dict)
                else {}
            )
            full_path = str(telemetry.get("full_path") or attributes.get("full_path") or "")
            prefix = derive_prefix(name, full_path, prefixes)
            if not prefix:
                skipped += 1
                continue
            nodes.extend(parse_device_path(prefix, name, str(device.get("id") or ""), full_path))
            touched.add(prefix)
        await upsert_nodes(session, nodes)
        for customer in touched:
            await rebuild_ancestor_paths(session, customer)
        await session.commit()
    return {
        "devices": len(payload),
        "nodes": len({node.node_id for node in nodes}),
        "customers": sorted(touched),
        "skipped": skipped,
    }


@router.get("/hierarchy")
async def hierarchy(
    request: Request, customer_id: str, x_admin_token: str | None = Header(default=None)
) -> list[dict[str, object]]:
    check_admin(request, x_admin_token)
    async with request.app.state.session_factory() as session:
        nodes = list(
            (
                await session.execute(
                    select(HierarchyNode).where(HierarchyNode.customer_id == customer_id)
                )
            ).scalars()
        )
    by_parent: dict[str | None, list[HierarchyNode]] = defaultdict(list)
    for node in nodes:
        by_parent[node.parent_id].append(node)

    def render(node: HierarchyNode) -> dict[str, object]:
        return {
            "node_id": node.node_id,
            "node_type": node.node_type,
            "display_name": node.display_name,
            "is_leaf": node.is_leaf,
            "children": [render(child) for child in by_parent[node.node_id]],
        }

    return [render(node) for node in by_parent[None]]


@router.post("/node")
async def upsert_node(
    request: Request, payload: NodePayload, x_admin_token: str | None = Header(default=None)
) -> dict[str, int]:
    check_admin(request, x_admin_token)
    async with request.app.state.session_factory() as session:
        count = await upsert_nodes(session, [ParsedNode(**payload.model_dump())])
        await rebuild_ancestor_paths(session, payload.customer_id)
        await session.commit()
    return {"nodes": count}


@router.post("/init", status_code=501)
@router.post("/replay", status_code=501)
async def unavailable(x_admin_token: str | None = Header(default=None)) -> None:
    if x_admin_token is None:
        raise HTTPException(403, "Admin token required")
    raise HTTPException(501, "Not implemented in this slice")
