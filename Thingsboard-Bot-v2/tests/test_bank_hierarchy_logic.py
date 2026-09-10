"""Unit tests for bank-specific hierarchy logic and AO regional scoping.

Verifies:
1. SBI: HO -> LHO -> AO -> RBO -> BRANCH (with ZO -> AO normalization)
2. BOB: HO -> ZO -> RO -> BRANCH
3. BOI: HO -> NBG -> ZO -> BRANCH
4. CANARA: HO -> CO -> RO -> BRANCH
5. GRAMIN: HO -> RO -> BRANCH
6. Fallback to full_path for missing attributes or unknown banks
7. Regional scope resolution for AO (Administrative Office)
"""

from app.hierarchy.parser import (
    NODE_TYPES,
    parse_device_from_attrs,
    parse_device_path,
)
from app.hierarchy.scope import (
    REGION_PREFIXES,
    extract_region,
)


def test_ao_is_registered_in_node_types_and_region_prefixes() -> None:
    assert "AO" in NODE_TYPES
    assert "AO" in REGION_PREFIXES


def test_sbi_hierarchy_produces_exact_five_tier_chain() -> None:
    attrs = {
        "bank_name": "PARIHAR",
        "LhoName": "LHO Patna",
        "zoName": "ZO Muzaffapur",
        "RboName": "RBO Madhubani",
        "BranchName": "SBI PARIHAR",
    }
    nodes = parse_device_from_attrs("SBI", "SBI PARIHAR", "dev-sbi-1", attrs)

    assert [n.node_type for n in nodes] == ["HO", "LHO", "AO", "RBO", "BRANCH"]
    assert [n.node_level for n in nodes] == [1, 2, 3, 4, 5]

    # Verify display names and normalization of ZO -> AO
    assert nodes[0].display_name == "State Bank of India"
    assert nodes[0].node_id == "SBI_HO"
    assert nodes[0].parent_id is None

    assert nodes[1].display_name == "LHO Patna"
    assert nodes[1].node_id == "SBI:LHO PATNA"
    assert nodes[1].parent_id == "SBI_HO"

    assert nodes[2].display_name == "AO Muzaffapur"
    assert nodes[2].node_id == "SBI:AO MUZAFFAPUR"
    assert nodes[2].parent_id == "SBI:LHO PATNA"

    assert nodes[3].display_name == "RBO Madhubani"
    assert nodes[3].node_id == "SBI:RBO MADHUBANI"
    assert nodes[3].parent_id == "SBI:AO MUZAFFAPUR"

    assert nodes[4].display_name == "SBI PARIHAR"
    assert nodes[4].node_id == "SBI PARIHAR"
    assert nodes[4].parent_id == "SBI:RBO MADHUBANI"
    assert nodes[4].is_leaf is True
    assert nodes[4].tb_device_id == "dev-sbi-1"


def test_sbi_hierarchy_with_explicit_ao_attribute() -> None:
    attrs = {
        "bank_name": "STATE BANK OF INDIA",
        "LhoName": "LHO Kolkata",
        "AoName": "AO Siliguri",
        "RboName": "RBO- ii",
    }
    nodes = parse_device_from_attrs("SBI", "BRANCH HESTIA 4s", "dev-sbi-4", attrs)

    assert [n.node_type for n in nodes] == ["HO", "LHO", "AO", "RBO", "BRANCH"]
    assert nodes[2].display_name == "AO Siliguri"
    assert nodes[2].node_id == "SBI:AO SILIGURI"


def test_bob_hierarchy_produces_four_tier_chain() -> None:
    attrs = {
        "bank_name": "BANK OF BARODA",
        "zoName": "ZO Kolkata",
        "roName": "RO KMR",
        "BranchName": "BRANCH APC ROAD",
    }
    nodes = parse_device_from_attrs("BOB", "BOB-APC-ROAD", "dev-bob-1", attrs)

    assert [n.node_type for n in nodes] == ["HO", "ZO", "RO", "BRANCH"]
    assert [n.node_level for n in nodes] == [1, 2, 3, 4]
    assert nodes[0].node_id == "BOB_HO"
    assert nodes[1].node_id == "BOB:ZO KOLKATA"
    assert nodes[2].node_id == "BOB:RO KMR"
    assert nodes[3].node_id == "BOB-APC-ROAD"
    assert nodes[3].parent_id == "BOB:RO KMR"


def test_boi_hierarchy_produces_nbg_and_zo_chain() -> None:
    attrs = {
        "bank_name": "BANK OF INDIA",
        "nbgName": "NBG ODISHA",
        "zoName": "ZO BARIPADA",
        "BranchName": "BRANCH BASTA",
    }
    nodes = parse_device_from_attrs("BOI", "BOI-BASTA", "dev-boi-1", attrs)

    assert [n.node_type for n in nodes] == ["HO", "NBG", "ZO", "BRANCH"]
    assert [n.node_level for n in nodes] == [1, 2, 3, 4]
    assert nodes[1].display_name == "NBG ODISHA"
    assert nodes[2].display_name == "ZO BARIPADA"


def test_canara_hierarchy_produces_co_and_ro_chain() -> None:
    attrs = {
        "bank_name": "CANARA BANK",
        "coName": "CO Kolkata",
        "roName": "RO KOLKATA - I",
        "BranchName": "BRANCH CHETLA",
    }
    nodes = parse_device_from_attrs("CANARA", "CANARA-CHETLA", "dev-can-1", attrs)

    assert [n.node_type for n in nodes] == ["HO", "CO", "RO", "BRANCH"]
    assert [n.node_level for n in nodes] == [1, 2, 3, 4]
    assert nodes[1].display_name == "CO Kolkata"
    assert nodes[2].display_name == "RO KOLKATA - I"


def test_gramin_bank_hierarchy_produces_three_tier_chain() -> None:
    attrs = {
        "bank_name": "Gramin Bank",
        "roName": "RO-GB-I",
        "BranchName": "BRANCH S-Intrution",
    }
    nodes = parse_device_from_attrs("GRAMIN", "BRANCH S-Intrution", "dev-gb-1", attrs)

    assert [n.node_type for n in nodes] == ["HO", "RO", "BRANCH"]
    assert [n.node_level for n in nodes] == [1, 2, 3]


def test_empty_attrs_falls_back_to_full_path() -> None:
    path = "PNB Head Office → PNB PATNA → PNB-BRANCH-1"
    nodes = parse_device_from_attrs("PNB", "PNB-BRANCH-1", "dev-pnb-1", {}, full_path=path)
    assert [n.node_type for n in nodes] == ["HO", "ZO", "BRANCH"]


def test_full_path_with_ao_segment_parses_as_ao_type() -> None:
    path = "SBI Head Office → LHO Patna → AO Muzaffarpur → RBO Bettiah → SBI-BRANCH"
    nodes = parse_device_path("SBI", "SBI-BRANCH", "dev-sbi-x", path)
    assert [n.node_type for n in nodes] == ["HO", "LHO", "AO", "RBO", "BRANCH"]


def test_ao_regional_scope_extraction_explicit() -> None:
    scope = extract_region({"firstName": "AO Muzaffarpur"})
    assert scope.explicit is True
    assert scope.name == "AO Muzaffarpur"


def test_ao_regional_scope_extraction_from_email() -> None:
    scope = extract_region({"email": "ao.muzaffarpur@sbi.co.in"})
    assert scope.explicit is False
    assert scope.name == "ao muzaffarpur"
