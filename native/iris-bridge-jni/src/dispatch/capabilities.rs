use iris_bridge_core_lib::server::capabilities::{
    BridgeCapabilitiesInput, CapabilitySnapshot, current_bridge_capabilities,
};
use serde_json::{Map, Value, json};

use super::envelope::json_catch_unwind;

pub fn dispatch_current_bridge_capabilities(input: &BridgeCapabilitiesInput) -> String {
    json_catch_unwind(|| {
        let capabilities = current_bridge_capabilities(input);
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
