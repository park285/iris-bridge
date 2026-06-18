use iris_bridge_core_lib::server::reply_hook::{
    REPLY_HOOK_TTL_MS, ReplyHookVerifyInput, markdown_pending_context_request,
    mention_pending_context_request, sign_prepared, verify as reply_verify,
};
use iris_bridge_core_lib::server::{ERROR_BAD_REQUEST, Rejection};
use serde_json::{Value, json};

use super::envelope::json_catch_unwind;

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

pub fn dispatch_reply_hook_verify(input: &ReplyHookVerifyInput<'_>) -> bool {
    reply_verify(input, REPLY_HOOK_TTL_MS)
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
