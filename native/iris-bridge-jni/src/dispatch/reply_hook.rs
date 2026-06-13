use iris_bridge_core::server::reply_hook::{
    REPLY_HOOK_TTL_MS, markdown_pending_context_request, mention_pending_context_request,
    sign_prepared, verify as reply_verify,
};
use iris_bridge_core::server::{ERROR_BAD_REQUEST, Rejection};
use serde_json::{Value, json};

use super::envelope::json_catch_unwind;

#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin ReplyHookSignatureProtocol.verify와 동결된 인자 계약"
)]
pub fn dispatch_reply_hook_sign(
    bridge_token: &str,
    room_id: i64,
    message_text: &str,
    session_id: &str,
    created_at_epoch_ms: i64,
    mentions_hash: Option<&str>,
) -> Option<String> {
    sign_prepared(
        bridge_token,
        room_id,
        message_text,
        session_id,
        created_at_epoch_ms,
        mentions_hash,
    )
}

#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin ReplyHookSignatureProtocol.verify와 동결된 인자 계약"
)]
pub fn dispatch_reply_hook_verify(
    bridge_token: &str,
    room_id: i64,
    message_text: &str,
    session_id: Option<&str>,
    created_at_epoch_ms: Option<i64>,
    mentions_hash: Option<&str>,
    signature: Option<&str>,
    now_epoch_ms: i64,
) -> bool {
    reply_verify(
        bridge_token,
        room_id,
        message_text,
        session_id,
        created_at_epoch_ms,
        mentions_hash,
        signature,
        now_epoch_ms,
        REPLY_HOOK_TTL_MS,
    )
}

pub fn dispatch_reply_markdown_pending_context(request_json: &str) -> String {
    json_catch_unwind(|| {
        let context = markdown_pending_context_request(request_json)
            .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error))?;
        Ok(context.unwrap_or_else(|| json!({ "context": Value::Null })))
    })
}

pub fn dispatch_reply_mention_pending_context(request_json: &str) -> String {
    json_catch_unwind(|| {
        let context = mention_pending_context_request(request_json)
            .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error))?;
        Ok(context.unwrap_or_else(|| json!({ "context": Value::Null })))
    })
}
