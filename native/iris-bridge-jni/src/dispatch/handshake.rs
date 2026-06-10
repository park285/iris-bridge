use serde_json::json;

use crate::handles::BridgeCoreContext;

use super::envelope::json_catch_unwind;

pub fn dispatch_handshake_on_hello(
    context: &BridgeCoreContext,
    frame_json: &str,
    now_ms: i64,
    socket_name: &str,
) -> String {
    json_catch_unwind(|| {
        let frame = context.open_handshake_session(frame_json, now_ms, socket_name)?;
        Ok(json!({ "frameJson": frame }))
    })
}

pub fn dispatch_handshake_on_client_proof(context: &BridgeCoreContext, frame_json: &str) -> String {
    json_catch_unwind(|| {
        context.resolve_client_proof(frame_json)?;
        Ok(json!({}))
    })
}
