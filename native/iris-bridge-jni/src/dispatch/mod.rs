mod decode;
mod envelope;

use std::panic::{AssertUnwindSafe, catch_unwind};

use iris_bridge_core::server::error_classification::classify_error_code;
use iris_bridge_core::server::image_path::validate_image_paths;
use iris_bridge_core::server::reply_hook::{
    REPLY_HOOK_TTL_MS, sign_prepared, verify as reply_verify,
};
use iris_bridge_core::server::text_request::{TextRequestInput, validate_text_request};
use iris_bridge_core::server::token::validate_request;
use iris_bridge_core::server::{Admit, PathFacts};
use serde_json::json;

use crate::handles::{BridgeCoreContext, with_context};

use decode::{TokenRequest, decode_path_facts};
#[cfg(test)]
pub use envelope::DispatchResult;
use envelope::bad_request;
pub use envelope::{invalid_handle_envelope, json_catch_unwind};

pub fn dispatch_validate_request_token(context: &BridgeCoreContext, request_json: &str) -> String {
    json_catch_unwind(|| {
        let request: TokenRequest =
            serde_json::from_str(request_json).map_err(|_| bad_request("request JSON invalid"))?;
        let _ = request.action;
        validate_request(
            context.security_mode,
            &context.bridge_token,
            request.token.as_deref(),
            request.protocol_version.unwrap_or_default(),
        )?;
        Ok(json!({}))
    })
}

pub fn dispatch_validate_request_token_handle(handle: i64, request_json: &str) -> String {
    match with_context(handle, |context| {
        dispatch_validate_request_token(context, request_json)
    }) {
        Ok(envelope) => envelope,
        Err(rejection) => json_catch_unwind(|| Err(rejection)),
    }
}

pub fn dispatch_verify_leases(
    context: &BridgeCoreContext,
    room_id: i64,
    request_id: &str,
    leases_json: &str,
    facts_json: &str,
    now_ms: i64,
) -> String {
    json_catch_unwind(|| {
        let facts: Vec<PathFacts> = decode_path_facts(facts_json)?;
        context
            .lease_ledger
            .verify(room_id, request_id, leases_json, &facts, now_ms)?;
        Ok(json!({}))
    })
}

pub fn dispatch_dedupe_admit(context: &BridgeCoreContext, key: &str, now_ms: i64) -> String {
    json_catch_unwind(|| {
        let extra = match context.dedupe_ledger.admit(key, now_ms) {
            Admit::Fresh => json!({ "state": "fresh" }),
            Admit::InFlight => json!({ "state": "inFlight" }),
            Admit::Cached(response_json) => {
                json!({ "state": "cached", "responseJson": response_json })
            }
        };
        Ok(extra)
    })
}

pub fn dispatch_dedupe_complete(
    context: &BridgeCoreContext,
    key: &str,
    response_json: &str,
    now_ms: i64,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        context.dedupe_ledger.complete(key, response_json, now_ms);
    }));
}

pub fn dispatch_request_admission(action: &str, request_id: Option<&str>) -> String {
    json_catch_unwind(|| {
        iris_bridge_core::server::admission::validate_request_id(action, request_id)?;
        Ok(json!({}))
    })
}

pub fn dispatch_request_requires_request_id(action: &str) -> bool {
    iris_bridge_core::server::admission::requires_request_id(action)
}

pub fn dispatch_validate_text_request(
    room_id: Option<i64>,
    message: Option<&str>,
    markdown: bool,
    attachment_json: Option<&str>,
    mentions_json: Option<&str>,
) -> String {
    json_catch_unwind(|| {
        let verdict = validate_text_request(TextRequestInput {
            room_id,
            message,
            markdown,
            attachment_json,
            mentions_json,
        })?;
        let extra = verdict.attachment_json.map_or_else(
            || json!({}),
            |attachment_json| json!({ "attachmentJson": attachment_json }),
        );
        Ok(extra)
    })
}

pub fn dispatch_validate_image_paths(
    image_paths_json: &str,
    max_path_count: usize,
    max_path_length: usize,
) -> String {
    json_catch_unwind(|| {
        let image_paths: Vec<String> = serde_json::from_str(image_paths_json)
            .map_err(|_| bad_request("image paths JSON invalid"))?;
        validate_image_paths(&image_paths, max_path_count, max_path_length)?;
        Ok(json!({}))
    })
}

pub fn dispatch_classify_error_code(message: &str, is_illegal_argument: bool) -> String {
    json_catch_unwind(|| {
        Ok(json!({
            "classifiedErrorCode": classify_error_code(message, is_illegal_argument),
        }))
    })
}

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

pub fn dispatch_mentions_hash_from_json(mentions_json: Option<&str>) -> Option<String> {
    iris_bridge_core::server::mentions_hash::from_mentions_json(mentions_json)
}

pub fn dispatch_mentions_hash_from_attachment(attachment_text: Option<&str>) -> Option<String> {
    iris_bridge_core::server::mentions_hash::from_attachment(attachment_text)
}
