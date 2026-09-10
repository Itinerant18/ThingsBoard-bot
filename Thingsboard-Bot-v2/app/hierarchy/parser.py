import re
from dataclasses import dataclass
from typing import Any

NODE_TYPES = {"HO", "FGMO", "LHO", "AO", "ZO", "RO", "RBO", "CO", "NBG", "BRANCH"}


def normalize(value: str) -> str:
    return " ".join(re.sub(r"[^A-Z0-9]+", " ", value.upper()).split())


# Bank-specific hierarchy definitions mapping ordered tiers to attribute keys.
# Empirically verified against ThingsBoard live Asset Relations and Device Attributes:
#   SBI:    HO -> LHO -> AO -> RBO -> BRANCH
#   BOB:    HO -> ZO -> RO -> BRANCH
#   BOI:    HO -> NBG -> ZO -> BRANCH
#   CANARA: HO -> CO -> RO -> BRANCH
#   GRAMIN: HO -> RO -> BRANCH
BANK_HIERARCHY_SCHEMAS: dict[str, list[dict[str, Any]]] = {
    "SBI": [
        {
            "type": "HO",
            "keys": ["bank_name"],
            "default": "State Bank of India",
        },
        {
            "type": "LHO",
            "keys": ["LhoName", "lho_name", "lhoName"],
        },
        {
            "type": "AO",
            "keys": ["AoName", "ao_name", "aoName", "zoName", "zo_name", "zone_name"],
            # Normalize legacy 'ZO Muzaffarpur' on SBI devices to 'AO Muzaffarpur'
            "normalize": lambda val: re.sub(r"^ZO\b", "AO", val, flags=re.IGNORECASE).strip(),
        },
        {
            "type": "RBO",
            "keys": ["RboName", "rbo_name", "rboName"],
        },
        {
            "type": "BRANCH",
            "keys": ["BranchName", "branchName", "branch_name"],
        },
    ],
    "BOB": [
        {
            "type": "HO",
            "keys": ["bank_name"],
            "default": "Bank of Baroda",
        },
        {
            "type": "ZO",
            "keys": ["zoName", "zo_name", "zone_name", "nbgName"],
        },
        {
            "type": "RO",
            "keys": ["roName", "ro_name", "region_name"],
        },
        {
            "type": "BRANCH",
            "keys": ["BranchName", "branchName", "branch_name"],
        },
    ],
    "BOI": [
        {
            "type": "HO",
            "keys": ["bank_name"],
            "default": "Bank of India",
        },
        {
            "type": "NBG",
            "keys": ["nbgName", "nbg_name"],
        },
        {
            "type": "ZO",
            "keys": ["zoName", "zo_name", "zone_name"],
        },
        {
            "type": "BRANCH",
            "keys": ["BranchName", "branchName", "branch_name"],
        },
    ],
    "CANARA": [
        {
            "type": "HO",
            "keys": ["bank_name"],
            "default": "Canara Bank",
        },
        {
            "type": "CO",
            "keys": ["coName", "co_name", "nbgName", "nbg_name"],
        },
        {
            "type": "RO",
            "keys": ["roName", "ro_name"],
        },
        {
            "type": "BRANCH",
            "keys": ["BranchName", "branchName", "branch_name"],
        },
    ],
    "GRAMIN": [
        {
            "type": "HO",
            "keys": ["bank_name"],
            "default": "Gramin Bank",
        },
        {
            "type": "RO",
            "keys": ["roName", "ro_name"],
        },
        {
            "type": "BRANCH",
            "keys": ["BranchName", "branchName", "branch_name"],
        },
    ],
}


def split_full_path(full_path: str | None, prefix: str, branch_name: str) -> list[str]:
    # Production ThingsBoard full_path uses the unicode arrow "→"; "->" and "/" are fallbacks.
    parts = [part.strip() for part in re.split(r"(?:→|->|/)", full_path or "") if part.strip()]
    if not parts:
        return [f"{prefix} Head Office", branch_name]
    first = normalize(parts[0])
    if not any(marker in first for marker in ("BANK", "HO", "HEAD OFFICE", normalize(prefix))):
        parts.insert(0, f"{prefix} Head Office")
    return parts


def node_type(segment: str, is_leaf: bool) -> str:
    if is_leaf:
        return "BRANCH"
    normalized = normalize(segment)
    lead = normalized.split(maxsplit=1)[0] if normalized else ""
    if lead in NODE_TYPES:
        return lead
    return "NBG" if "NBG" in normalized else "ZO"


@dataclass(frozen=True)
class ParsedNode:
    node_id: str
    customer_id: str
    parent_id: str | None
    node_type: str
    node_level: int
    display_name: str
    is_leaf: bool
    tb_device_id: str | None


def parse_device_path(
    prefix: str, device_name: str, tb_device_id: str, full_path: str | None
) -> list[ParsedNode]:
    segments = split_full_path(full_path, prefix, device_name)
    # The ThingsBoard path may contain a stale branch label; device name is authoritative.
    segments[-1] = device_name
    nodes: list[ParsedNode] = []
    parent_id: str | None = None
    for index, segment in enumerate(segments):
        leaf = index == len(segments) - 1
        # Leaf wins over root: a single-segment path is the branch itself, never the HO node.
        if leaf:
            kind = "BRANCH"
            identifier = device_name
        elif index == 0:
            kind = "HO"  # root is always the head office, whatever the segment says
            identifier = f"{prefix}_HO"
        else:
            kind = node_type(segment, leaf)
            identifier = f"{prefix}:{normalize(segment)}"
        nodes.append(
            ParsedNode(
                identifier,
                prefix,
                parent_id,
                kind,
                index + 1,
                segment,
                leaf,
                tb_device_id if leaf else None,
            )
        )
        parent_id = identifier
    return nodes


def parse_device_from_attrs(
    prefix: str,
    device_name: str,
    tb_device_id: str,
    attrs: dict[str, Any] | None,
    full_path: str | None = None,
) -> list[ParsedNode]:
    """Parse hierarchy nodes for a device using bank-specific attribute schemas.

    If prefix is not configured in BANK_HIERARCHY_SCHEMAS or the required intermediate
    container attributes are absent, falls back to full_path parsing.
    """
    if not attrs or prefix not in BANK_HIERARCHY_SCHEMAS:
        return parse_device_path(prefix, device_name, tb_device_id, full_path)

    schema = BANK_HIERARCHY_SCHEMAS[prefix]

    # Check if we have at least one intermediate tier attribute present
    intermediate_tiers = schema[1:-1]
    has_any_intermediate = False
    for tier in intermediate_tiers:
        for k in tier["keys"]:
            val = attrs.get(k)
            if val and str(val).strip():
                has_any_intermediate = True
                break
        if has_any_intermediate:
            break

    if not has_any_intermediate:
        # Fall back to full_path parsing
        return parse_device_path(prefix, device_name, tb_device_id, full_path)

    # Build nodes according to schema
    extracted_levels: list[tuple[str, str, bool]] = []

    for i, tier in enumerate(schema):
        kind = str(tier["type"])
        leaf = i == len(schema) - 1

        if i == 0:
            # Root HO node
            display = None
            for k in tier["keys"]:
                raw = attrs.get(k)
                if raw and str(raw).strip():
                    norm_raw = normalize(str(raw))
                    if norm_raw not in (normalize(device_name), "UNKNOWN") and any(
                        m in norm_raw for m in ("BANK", "HO", "HEAD OFFICE", normalize(prefix))
                    ):
                        display = str(raw).strip()
                        break
            if not display:
                display = str(tier.get("default") or f"{prefix} Head Office")
            extracted_levels.append((kind, display, False))
        elif leaf:
            # Leaf branch node: device_name is authoritative
            extracted_levels.append((kind, device_name, True))
        else:
            # Intermediate tier
            val = None
            for k in tier["keys"]:
                raw = attrs.get(k)
                if raw and str(raw).strip():
                    val = str(raw).strip()
                    if "normalize" in tier and callable(tier["normalize"]):
                        val = tier["normalize"](val)
                    break
            if val:
                extracted_levels.append((kind, val, False))

    nodes: list[ParsedNode] = []
    parent_id: str | None = None

    for index, (kind, display, leaf) in enumerate(extracted_levels):
        if leaf:
            identifier = device_name
        elif index == 0:
            identifier = f"{prefix}_HO"
        else:
            identifier = f"{prefix}:{normalize(display)}"

        nodes.append(
            ParsedNode(
                node_id=identifier,
                customer_id=prefix,
                parent_id=parent_id,
                node_type=kind,
                node_level=index + 1,
                display_name=display,
                is_leaf=leaf,
                tb_device_id=tb_device_id if leaf else None,
            )
        )
        parent_id = identifier

    return nodes

