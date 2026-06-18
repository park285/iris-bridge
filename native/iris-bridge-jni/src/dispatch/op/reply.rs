use iris_bridge_core_lib::server::reply_hook::ReplyHookVerifyInput;
use serde_json::Value;

use super::super::{
    dispatch_mentions_hash_from_attachment, dispatch_mentions_hash_from_json,
    dispatch_merge_reply_leverage_attachment, dispatch_merge_reply_mention_attachment,
    dispatch_reply_attachment_session_id, dispatch_reply_attachment_text_looks_like,
    dispatch_reply_hook_sign, dispatch_reply_hook_verify, dispatch_reply_markdown_pending_context,
    dispatch_reply_mention_attachment_or_null, dispatch_reply_mention_pending_context,
};
use super::payload::{opt_i64, opt_str, req_i64, req_str, unknown_op, value_json};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "reply.sign" => value_json(dispatch_reply_hook_sign(
            req_str(payload, "bridgeToken").unwrap_or_default(),
            req_i64(payload, "roomId").unwrap_or_default(),
            req_str(payload, "messageText").unwrap_or_default(),
            req_str(payload, "sessionId").unwrap_or_default(),
            req_i64(payload, "createdAtEpochMs").unwrap_or_default(),
            opt_str(payload, "mentionsHash"),
        )),
        "reply.verify" => value_json(dispatch_reply_hook_verify(&ReplyHookVerifyInput {
            bridge_token: req_str(payload, "bridgeToken").unwrap_or_default(),
            room_id: req_i64(payload, "roomId").unwrap_or_default(),
            message_text: req_str(payload, "messageText").unwrap_or_default(),
            session_id: opt_str(payload, "sessionId"),
            created_at_epoch_ms: opt_i64(payload, "createdAtEpochMs"),
            mentions_hash: opt_str(payload, "mentionsHash"),
            signature: opt_str(payload, "signature"),
            now_epoch_ms: req_i64(payload, "nowEpochMs").unwrap_or_default(),
        })),
        "reply.mentionsHashFromJson" => value_json(dispatch_mentions_hash_from_json(opt_str(
            payload,
            "mentionsJson",
        ))),
        "reply.mentionsHashFromAttachment" => value_json(dispatch_mentions_hash_from_attachment(
            opt_str(payload, "attachmentText"),
        )),
        "reply.mentionAttachmentOrNull" => value_json(dispatch_reply_mention_attachment_or_null(
            req_str(payload, "attachmentText").unwrap_or_default(),
        )),
        "reply.mergeMentionAttachment" => value_json(dispatch_merge_reply_mention_attachment(
            req_str(payload, "targetAttachmentText").unwrap_or_default(),
            req_str(payload, "mentionAttachmentText").unwrap_or_default(),
        )),
        "reply.mergeLeverageAttachment" => value_json(dispatch_merge_reply_leverage_attachment(
            opt_str(payload, "generatedAttachment"),
            req_str(payload, "rawAttachment").unwrap_or_default(),
        )),
        "reply.attachmentTextLooksLike" => value_json(dispatch_reply_attachment_text_looks_like(
            req_str(payload, "value").unwrap_or_default(),
        )),
        "reply.attachmentSessionId" => value_json(dispatch_reply_attachment_session_id(
            req_str(payload, "attachmentText").unwrap_or_default(),
        )),
        "reply.markdownPendingContext" => dispatch_reply_markdown_pending_context(
            req_str(payload, "requestJson").unwrap_or_default(),
        ),
        "reply.mentionPendingContext" => dispatch_reply_mention_pending_context(
            req_str(payload, "requestJson").unwrap_or_default(),
        ),
        _ => unknown_op(),
    }
}
