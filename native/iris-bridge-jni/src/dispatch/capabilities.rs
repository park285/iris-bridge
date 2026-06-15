use iris_bridge_core::server::capabilities::{
    BridgeCapabilitiesInput, BridgeReadinessInput, CapabilitySnapshot, TextBridgeRollout,
    TextCapability, current_bridge_capabilities,
};
use serde_json::{Map, Value, json};

use super::envelope::json_catch_unwind;

#[allow(
    clippy::fn_params_excessive_bools,
    clippy::too_many_arguments,
    reason = "Kotlin capability snapshot input is a flat JNI boundary"
)]
pub fn dispatch_current_bridge_capabilities(
    registry_available: bool,
    registry_error: Option<&str>,
    spec_ready: bool,
    notification_action_supported: bool,
    text_supported: bool,
    text_ready: bool,
    text_reason: Option<&str>,
    send_text_enabled: bool,
    send_markdown_enabled: bool,
) -> String {
    json_catch_unwind(|| {
        let capabilities = current_bridge_capabilities(&BridgeCapabilitiesInput {
            readiness: BridgeReadinessInput {
                registry_available,
                registry_error: registry_error.map(str::to_owned),
                spec_ready,
            },
            notification_action_supported,
            text_send_capability: Some(TextCapability {
                supported: text_supported,
                ready: text_ready,
                reason: text_reason.map(str::to_owned),
            }),
            rollout: TextBridgeRollout {
                send_text_enabled,
                send_markdown_enabled,
            },
        });
        let mut fields = Map::new();
        insert_capability_fields(
            &mut fields,
            "inspectChatRoom",
            &capabilities.inspect_chat_room,
        );
        insert_capability_fields(&mut fields, "openChatRoom", &capabilities.open_chat_room);
        insert_capability_fields(
            &mut fields,
            "markChatRoomRead",
            &capabilities.mark_chat_room_read,
        );
        insert_capability_fields(
            &mut fields,
            "snapshotChatRoomMembers",
            &capabilities.snapshot_chat_room_members,
        );
        insert_capability_fields(&mut fields, "sendText", &capabilities.send_text);
        insert_capability_fields(&mut fields, "sendMarkdown", &capabilities.send_markdown);
        Ok(Value::Object(fields))
    })
}

fn insert_capability_fields(
    fields: &mut Map<String, Value>,
    prefix: &str,
    capability: &CapabilitySnapshot,
) {
    fields.insert(format!("{prefix}Supported"), json!(capability.supported));
    fields.insert(format!("{prefix}Ready"), json!(capability.ready));
    if let Some(reason) = capability.reason.as_deref() {
        fields.insert(format!("{prefix}Reason"), json!(reason));
    }
}
