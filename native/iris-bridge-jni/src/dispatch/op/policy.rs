use iris_bridge_core_lib::server::capabilities::{
    BridgeCapabilitiesInput, BridgeReadinessInput, TextBridgeRollout, TextCapability,
};
use serde_json::Value;

use super::super::{
    dispatch_allowed_peer_uids, dispatch_current_bridge_capabilities, dispatch_is_truthy_flag,
    dispatch_normalize_security_mode, dispatch_restart_delay_ms,
    dispatch_send_block_reason_from_snapshot,
};
use super::payload::{
    bool_vec, opt_i32, opt_i64, opt_str, req_bool, req_i32, req_str, string_vec, unknown_op,
    value_json,
};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "policy.isTruthyFlag" => value_json(dispatch_is_truthy_flag(
            req_str(payload, "raw").unwrap_or_default(),
        )),
        "policy.normalizeSecurityMode" => {
            value_json(dispatch_normalize_security_mode(opt_str(payload, "raw")))
        }
        "policy.allowedPeerUids" => value_json(dispatch_allowed_peer_uids(
            opt_str(payload, "securityModeRaw"),
            opt_str(payload, "extraUidsRaw"),
        )),
        "policy.sendBlockReason" => value_json(dispatch_send_block_reason_from_snapshot(
            req_bool(payload, "installAttempted").unwrap_or_default(),
            &string_vec(payload, "hookNames").unwrap_or_default(),
            &bool_vec(payload, "hookInstalled").unwrap_or_default(),
            req_i32(payload, "imageCount").unwrap_or_default(),
            opt_i64(payload, "threadId"),
            opt_i32(payload, "threadScope"),
        )),
        "policy.currentBridgeCapabilities" => {
            dispatch_current_bridge_capabilities(&capabilities_input(payload))
        }
        "policy.serverRestartDelayMs" => value_json(dispatch_restart_delay_ms(
            req_i32(payload, "failureCount").unwrap_or_default(),
        )),
        _ => unknown_op(),
    }
}

fn capabilities_input(payload: &Value) -> BridgeCapabilitiesInput {
    BridgeCapabilitiesInput {
        readiness: BridgeReadinessInput {
            registry_available: req_bool(payload, "registryAvailable").unwrap_or_default(),
            registry_error: opt_str(payload, "registryError").map(str::to_owned),
            spec_ready: req_bool(payload, "specReady").unwrap_or_default(),
        },
        notification_action_supported: req_bool(payload, "notificationActionSupported")
            .unwrap_or_default(),
        text_send_capability: Some(TextCapability {
            supported: req_bool(payload, "textSupported").unwrap_or_default(),
            ready: req_bool(payload, "textReady").unwrap_or_default(),
            reason: opt_str(payload, "textReason").map(str::to_owned),
        }),
        rollout: TextBridgeRollout {
            send_text_enabled: req_bool(payload, "sendTextEnabled").unwrap_or_default(),
            send_markdown_enabled: req_bool(payload, "sendMarkdownEnabled").unwrap_or_default(),
        },
    }
}
