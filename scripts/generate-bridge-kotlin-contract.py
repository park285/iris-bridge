#!/usr/bin/env python3
"""Generate Kotlin bridge protocol constants from Rust bridge-core SSOT.

The input is intentionally the Rust source that owns the bridge contract:
- native/iris-bridge-core/src/protocol/constants.rs
- native/iris-bridge-core/src/protocol/actions.rs

This keeps imagebridge-protocol as a JVM serialization/model module while removing
hand-owned Kotlin protocol constants as an independent source of truth.
"""

from __future__ import annotations

import argparse
import json
import re
import subprocess
import sys
from dataclasses import dataclass
from pathlib import Path


def resolve_core_sources(repo_root: Path, manifest_path: Path | None) -> tuple[Path, Path]:
    """Locate iris-bridge-core's protocol sources.

    Monorepo layout keeps them under repo_root; after the iris-bridge split the
    core is a commit-pinned git-dep, so fall back to the cargo metadata checkout
    of the pinned rev — the single source of truth either way.
    """
    local_constants = repo_root / "native/iris-bridge-core/src/protocol/constants.rs"
    local_actions = repo_root / "native/iris-bridge-core/src/protocol/actions.rs"
    if local_constants.exists() and local_actions.exists():
        return local_constants, local_actions

    cargo_manifest = manifest_path or (repo_root / "native/Cargo.toml")
    metadata = json.loads(
        subprocess.check_output(
            ["cargo", "metadata", "--format-version", "1", "--manifest-path", str(cargo_manifest)],
            text=True,
        )
    )
    for package in metadata["packages"]:
        if package["name"] == "iris-bridge-core":
            core_dir = Path(package["manifest_path"]).parent
            return (
                core_dir / "src/protocol/constants.rs",
                core_dir / "src/protocol/actions.rs",
            )
    raise ValueError("iris-bridge-core not found via cargo metadata; cannot resolve protocol sources")


ACTION_CONSTS = [
    "ACTION_SEND_IMAGE",
    "ACTION_SEND_TEXT",
    "ACTION_SEND_MARKDOWN",
    "ACTION_OPEN_CHATROOM",
    "ACTION_MARK_CHATROOM_READ",
    "ACTION_INSPECT_CHATROOM",
    "ACTION_SNAPSHOT_CHATROOM_MEMBERS",
    "ACTION_FETCH_MEMBER_PROFILES",
    "ACTION_HEALTH",
]

STATUS_CONSTS = ["STATUS_SENT", "STATUS_FAILED", "STATUS_OK"]

ERROR_CONSTS = [
    "ERROR_UNSUPPORTED_PROTOCOL",
    "ERROR_UNAUTHORIZED",
    "ERROR_BAD_REQUEST",
    "ERROR_PATH_VALIDATION_FAILED",
    "ERROR_BRIDGE_BUSY",
    "ERROR_BRIDGE_SHUTTING_DOWN",
    "ERROR_SEND_FAILED",
    "ERROR_TIMEOUT",
    "ERROR_INTERNAL_ERROR",
    "ERROR_MISSING_REQUEST_ID",
    "ERROR_DUPLICATE_REQUEST",
    "ERROR_CANCELLED",
]

MUX_FRAME_CONSTS = [
    "MUX_FRAME_TYPE_REQUEST",
    "MUX_FRAME_TYPE_RESPONSE",
    "MUX_FRAME_TYPE_PING",
    "MUX_FRAME_TYPE_PONG",
    "MUX_FRAME_TYPE_CANCEL",
    "MUX_FRAME_TYPE_GOAWAY",
]

HANDSHAKE_FRAME_CONSTS = [
    "HANDSHAKE_FRAME_TYPE_HELLO",
    "HANDSHAKE_FRAME_TYPE_SERVER_PROOF",
    "HANDSHAKE_FRAME_TYPE_CLIENT_PROOF",
]

CAPABILITY_CONSTS = [
    "CAPABILITY_SEND_IMAGE_REPLY",
    "CAPABILITY_SEND_TEXT_REPLY",
    "CAPABILITY_SEND_MARKDOWN_REPLY",
    "CAPABILITY_OPEN_CHATROOM",
    "CAPABILITY_MARK_CHATROOM_READ",
    "CAPABILITY_INSPECT_CHATROOM",
    "CAPABILITY_SNAPSHOT_CHATROOM_MEMBERS",
    "CAPABILITY_FETCH_MEMBER_PROFILES",
    "CAPABILITY_REPORT_HEALTH",
]

THREAT_CONSTS = [
    "THREAT_UNSOLICITED_MESSAGE",
    "THREAT_CONTENT_SPOOFING",
    "THREAT_PATH_TRAVERSAL",
    "THREAT_NAVIGATION_HIJACK",
    "THREAT_NOTIFICATION_ACTION",
    "THREAT_MEMBER_DATA_EXPOSURE",
    "THREAT_ROOM_METADATA_EXPOSURE",
    "THREAT_HEALTH_INFO_DISCLOSURE",
    "THREAT_RESOURCE_EXHAUSTION",
]


@dataclass(frozen=True)
class ActionSpec:
    wire_const: str
    capability_const: str
    has_side_effect: bool
    requires_auth_token: bool
    threat_consts: tuple[str, ...]


def parse_rust_constants(constants_rs: Path) -> dict[str, str | int]:
    constants: dict[str, str | int] = {}
    text = constants_rs.read_text(encoding="utf-8")
    pattern = re.compile(
        r'pub\s+const\s+([A-Z0-9_]+)\s*:\s*(?:&str|i32|usize)\s*=\s*("([^"]*)"|[0-9_]+)\s*;'
    )
    for match in pattern.finditer(text):
        name = match.group(1)
        if name in constants:
            raise ValueError(f"duplicate Rust protocol constant: {name}")
        raw = match.group(2)
        if raw.startswith('"'):
            constants[name] = match.group(3)
        else:
            constants[name] = int(raw.replace("_", ""))
    return constants


def parse_action_specs(actions_rs: Path) -> list[ActionSpec]:
    text = actions_rs.read_text(encoding="utf-8")
    specs: list[ActionSpec] = []
    for body in re.findall(r"ActionSpec\s*\{(.*?)\}", text, flags=re.S):
        if re.search(r"wire_name\s*:\s*[A-Z0-9_]+", body) is None:
            continue
        wire_const = required_field(body, "wire_name")
        capability_const = required_field(body, "capability_id")
        has_side_effect = required_bool_field(body, "has_side_effect")
        requires_auth_token = required_bool_field(body, "requires_auth_token")
        threat_consts = tuple(
            token.strip()
            for token in required_list_field(body, "threat_ids").split(",")
            if token.strip()
        )
        specs.append(
            ActionSpec(
                wire_const=wire_const,
                capability_const=capability_const,
                has_side_effect=has_side_effect,
                requires_auth_token=requires_auth_token,
                threat_consts=threat_consts,
            )
        )
    if not specs:
        raise ValueError(f"no ActionSpec entries found in {actions_rs}")
    return specs


def required_field(body: str, field: str) -> str:
    match = re.search(rf"{field}\s*:\s*([A-Z0-9_]+)", body)
    if not match:
        raise ValueError(f"missing ActionSpec field: {field}")
    return match.group(1)


def required_bool_field(body: str, field: str) -> bool:
    match = re.search(rf"{field}\s*:\s*(true|false)", body)
    if not match:
        raise ValueError(f"missing ActionSpec bool field: {field}")
    return match.group(1) == "true"


def required_list_field(body: str, field: str) -> str:
    match = re.search(rf"{field}\s*:\s*&\[(.*?)\]", body, flags=re.S)
    if not match:
        raise ValueError(f"missing ActionSpec list field: {field}")
    return match.group(1)


def require_constants(constants: dict[str, str | int], names: list[str]) -> None:
    missing = [name for name in names if name not in constants]
    if missing:
        raise ValueError(f"missing Rust protocol constants: {', '.join(missing)}")


def require_unique(label: str, values: list[str]) -> None:
    seen: set[str] = set()
    duplicates: list[str] = []
    for value in values:
        if value in seen:
            duplicates.append(value)
        seen.add(value)
    if duplicates:
        raise ValueError(f"duplicate {label}: {', '.join(duplicates)}")


def validate_contract(constants: dict[str, str | int], action_specs: list[ActionSpec]) -> None:
    action_order = [spec.wire_const for spec in action_specs]
    if action_order != ACTION_CONSTS:
        raise ValueError(
            "Rust ACTION_SPECS order drifted from generated Kotlin API: "
            + ", ".join(action_order)
        )
    require_unique("action wire constants", action_order)
    require_unique("capability constants", [spec.capability_const for spec in action_specs])
    for spec in action_specs:
        require_constants(constants, [spec.wire_const, spec.capability_const, *spec.threat_consts])
        unknown_threats = [name for name in spec.threat_consts if name not in THREAT_CONSTS]
        if unknown_threats:
            raise ValueError(
                f"ActionSpec {spec.wire_const} references unknown threats: {', '.join(unknown_threats)}"
            )

    side_effects = [spec.wire_const for spec in action_specs if spec.has_side_effect]
    expected_side_effects = [
        "ACTION_SEND_IMAGE",
        "ACTION_SEND_TEXT",
        "ACTION_SEND_MARKDOWN",
        "ACTION_OPEN_CHATROOM",
        "ACTION_MARK_CHATROOM_READ",
    ]
    if side_effects != expected_side_effects:
        raise ValueError("side-effect action contract drifted: " + ", ".join(side_effects))


def string_literal(value: str | int) -> str:
    if isinstance(value, int):
        return str(value)
    return '"' + value.replace("\\", "\\\\").replace('"', '\\"') + '"'


def kotlin_bool(value: bool) -> str:
    return "true" if value else "false"


def append_list_property(lines: list[str], name: str, refs: list[str] | tuple[str, ...]) -> None:
    if not refs:
        lines.append(f"    val {name}: List<String> = emptyList()")
        return
    if len(refs) == 1:
        lines.append(f"    val {name}: List<String> = listOf({refs[0]})")
        return

    lines.append(f"    val {name}: List<String> =")
    lines.append("        listOf(")
    for ref in refs:
        lines.append(f"            {ref},")
    lines.append("        )")


def action_suffix(action_const: str) -> str:
    return action_const.removeprefix("ACTION_")


def render(constants: dict[str, str | int], action_specs: list[ActionSpec]) -> str:
    required = (
        [
            "PROTOCOL_VERSION",
            "MAX_FRAME_SIZE",
            "MUX_VERSION",
            "ABI_VERSION",
            "DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME",
        ]
        + ACTION_CONSTS
        + STATUS_CONSTS
        + ERROR_CONSTS
        + MUX_FRAME_CONSTS
        + HANDSHAKE_FRAME_CONSTS
        + CAPABILITY_CONSTS
        + THREAT_CONSTS
    )
    require_constants(constants, required)
    validate_contract(constants, action_specs)

    lines: list[str] = [
        "package party.qwer.iris.generated",
        "",
        "/**",
        " * Generated from native/iris-bridge-core/src/protocol.",
        " *",
        " * Do not edit this file by hand. Run:",
        " * `python3 scripts/generate-bridge-kotlin-contract.py --repo-root <repo> --output <this-file>`",
        " */",
        "object GeneratedBridgeProtocolContract {",
    ]

    for name in [
        "PROTOCOL_VERSION",
        "MAX_FRAME_SIZE",
        "MUX_VERSION",
        "ABI_VERSION",
        "DEFAULT_IMAGE_BRIDGE_MUX_SOCKET_NAME",
        *ACTION_CONSTS,
        *STATUS_CONSTS,
        *ERROR_CONSTS,
        *MUX_FRAME_CONSTS,
        *HANDSHAKE_FRAME_CONSTS,
        *CAPABILITY_CONSTS,
        *THREAT_CONSTS,
    ]:
        value = constants[name]
        kotlin_type = "Int" if isinstance(value, int) else "String"
        lines.append(f"    const val {name}: {kotlin_type} = {string_literal(value)}")

    lines.append("")
    for spec in action_specs:
        suffix = action_suffix(spec.wire_const)
        lines.append(f"    const val ACTION_{suffix}_CAPABILITY_ID: String = {spec.capability_const}")
        lines.append(
            f"    const val ACTION_{suffix}_HAS_SIDE_EFFECT: Boolean = {kotlin_bool(spec.has_side_effect)}"
        )
        lines.append(
            f"    const val ACTION_{suffix}_REQUIRES_AUTH_TOKEN: Boolean = {kotlin_bool(spec.requires_auth_token)}"
        )
        append_list_property(lines, f"ACTION_{suffix}_THREAT_IDS", spec.threat_consts)
        lines.append("")

    collections = [
        ("ACTION_VALUES", [spec.wire_const for spec in action_specs]),
        ("STATUS_VALUES", STATUS_CONSTS),
        ("ERROR_VALUES", ERROR_CONSTS),
        ("MUX_FRAME_TYPES", MUX_FRAME_CONSTS),
        ("HANDSHAKE_FRAME_TYPES", HANDSHAKE_FRAME_CONSTS),
        ("CAPABILITY_IDS", CAPABILITY_CONSTS),
        ("THREAT_IDS", THREAT_CONSTS),
    ]
    for name, refs in collections:
        append_list_property(lines, name, refs)
        lines.append("")

    lines.extend(
        [
            "    data class ActionSpec(",
            "        val wireName: String,",
            "        val capabilityId: String,",
            "        val hasSideEffect: Boolean,",
            "        val requiresAuthToken: Boolean,",
            "        val threatIds: List<String>,",
            "    )",
            "",
            "    val ACTION_SPECS: List<ActionSpec> =",
            "        listOf(",
        ]
    )
    for spec in action_specs:
        suffix = action_suffix(spec.wire_const)
        lines.extend(
            [
                "            ActionSpec(",
                f"                wireName = {spec.wire_const},",
                f"                capabilityId = ACTION_{suffix}_CAPABILITY_ID,",
                f"                hasSideEffect = ACTION_{suffix}_HAS_SIDE_EFFECT,",
                f"                requiresAuthToken = ACTION_{suffix}_REQUIRES_AUTH_TOKEN,",
                f"                threatIds = ACTION_{suffix}_THREAT_IDS,",
                "            ),",
            ]
        )
    lines.extend(
        [
            "        )",
            "",
            "    fun actionSpec(wireName: String): ActionSpec? = ACTION_SPECS_BY_WIRE_NAME[wireName]",
            "",
            "    private val ACTION_SPECS_BY_WIRE_NAME: Map<String, ActionSpec> = ACTION_SPECS.associateBy { it.wireName }",
            "}",
            "",
        ]
    )
    return "\n".join(lines)


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--repo-root", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    parser.add_argument("--manifest-path", type=Path)
    parser.add_argument("--check", action="store_true")
    args = parser.parse_args()

    repo_root = args.repo_root.resolve()
    constants_rs, actions_rs = resolve_core_sources(repo_root, args.manifest_path)

    generated = render(parse_rust_constants(constants_rs), parse_action_specs(actions_rs))

    if args.check:
        current = args.output.read_text(encoding="utf-8") if args.output.exists() else ""
        if current != generated:
            print(f"{args.output} is stale; regenerate bridge protocol contract", file=sys.stderr)
            return 1
        return 0

    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(generated, encoding="utf-8")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
