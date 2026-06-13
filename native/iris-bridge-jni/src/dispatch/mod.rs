mod capabilities;
mod decode;
mod dedupe;
mod discovery_hooks;
mod envelope;
mod handshake;
mod kakao_link_attachment;
mod kakao_link_template;
mod kakao_target;
mod lease;
mod member_extraction;
mod mentions_hash;
mod mux_session;
mod protocol_contract;
mod reply_attachment_text;
mod reply_hook;
mod reply_leverage_attachment;
mod reply_mention_attachment;
mod request_validation;
mod restart_policy;

use iris_bridge_core::server::token::validate_request;
use serde_json::json;

pub use capabilities::dispatch_current_bridge_capabilities;
pub use dedupe::{dispatch_dedupe_admit, dispatch_dedupe_complete};
#[cfg(test)]
pub use discovery_hooks::dispatch_send_block_reason;
pub use discovery_hooks::{
    DISCOVERY_HOOK_SNAPSHOT_INVALID_REASON, dispatch_send_block_reason_from_snapshot,
};
pub use handshake::{dispatch_handshake_on_client_proof, dispatch_handshake_on_hello};
pub use kakao_link_attachment::{
    dispatch_kakao_chat_log_attachment_crypto, dispatch_kakao_link_attachments_match,
    dispatch_kakao_link_leverage_encryption_type,
    dispatch_kakao_link_pending_cleanup_attachments_match,
};
pub use kakao_link_template::{
    dispatch_kakao_link_build_spec_send_attachment, dispatch_kakao_link_build_v4_encoded_query,
    dispatch_kakao_link_extract_app_key, dispatch_kakao_link_has_explicit_template_args,
    dispatch_kakao_link_has_resolved_iris_template, dispatch_kakao_link_patch_display_attachment,
};
pub use kakao_target::dispatch_resolve_kakao_target;
pub use lease::{
    dispatch_image_lease_facts_json, dispatch_image_lease_rejection_is_state_error,
    dispatch_verify_leases,
};
pub use member_extraction::{
    dispatch_member_enrichment_merge, dispatch_member_enrichment_missing_nicknames,
    dispatch_member_extraction_evaluate,
};
pub use mux_session::{
    dispatch_mux_session_create, dispatch_mux_session_destroy, dispatch_mux_session_is_cancelled,
    dispatch_mux_session_on_executor_rejected, dispatch_mux_session_on_frame,
    dispatch_mux_session_on_request_completed,
};
pub use reply_attachment_text::{
    dispatch_reply_attachment_session_id, dispatch_reply_attachment_text_looks_like,
};
pub use reply_hook::{dispatch_reply_hook_sign, dispatch_reply_hook_verify};
pub use reply_leverage_attachment::dispatch_merge_reply_leverage_attachment;
pub use request_validation::{
    dispatch_allowed_peer_uids, dispatch_classify_error_code, dispatch_failure_metric_bucket,
    dispatch_image_path_under_allowed_root, dispatch_is_truthy_flag,
    dispatch_materialize_image_path, dispatch_media_message_kind,
    dispatch_normalize_media_content_types, dispatch_normalize_security_mode,
    dispatch_request_admission, dispatch_request_dedupe_key, dispatch_request_requires_request_id,
    dispatch_revalidate_image_path_snapshot, dispatch_validate_image_paths,
    dispatch_validate_share_manager_image_media, dispatch_validate_text_request,
};
pub use restart_policy::dispatch_restart_delay_ms;

use crate::handles::{BridgeCoreContext, with_context};

use decode::TokenRequest;
#[cfg(test)]
pub use envelope::DispatchResult;
use envelope::bad_request;
pub use envelope::{invalid_handle_envelope, json_catch_unwind};
pub use mentions_hash::{dispatch_mentions_hash_from_attachment, dispatch_mentions_hash_from_json};
pub use protocol_contract::dispatch_bridge_protocol_contract_json;
pub use reply_mention_attachment::{
    dispatch_merge_reply_mention_attachment, dispatch_reply_mention_attachment_or_null,
};

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
