use iris_bridge_core::server::reply_hook::{
    REPLY_HOOK_TTL_MS, sign_prepared, verify as reply_verify,
};

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
