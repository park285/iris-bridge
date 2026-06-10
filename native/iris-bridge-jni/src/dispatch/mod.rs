mod capabilities;
mod decode;
mod dedupe;
mod discovery_hooks;
mod envelope;
mod handshake;
mod lease;
mod mentions_hash;
mod reply_hook;
mod request_validation;
mod restart_policy;

use iris_bridge_core::server::token::validate_request;
use serde_json::json;

pub use capabilities::dispatch_current_bridge_capabilities;
pub use dedupe::{dispatch_dedupe_admit, dispatch_dedupe_complete};
pub use discovery_hooks::dispatch_send_block_reason;
pub use handshake::{dispatch_handshake_on_client_proof, dispatch_handshake_on_hello};
pub use lease::{
    dispatch_image_lease_facts_json, dispatch_image_lease_rejection_is_state_error,
    dispatch_verify_leases,
};
pub use reply_hook::{dispatch_reply_hook_sign, dispatch_reply_hook_verify};
pub use request_validation::{
    dispatch_allowed_peer_uids, dispatch_classify_error_code,
    dispatch_image_path_under_allowed_root, dispatch_is_truthy_flag,
    dispatch_materialize_image_path, dispatch_normalize_security_mode, dispatch_request_admission,
    dispatch_request_dedupe_key, dispatch_request_requires_request_id,
    dispatch_revalidate_image_path_snapshot, dispatch_validate_image_paths,
    dispatch_validate_text_request,
};
pub use restart_policy::dispatch_restart_delay_ms;

use crate::handles::{BridgeCoreContext, with_context};

use decode::TokenRequest;
#[cfg(test)]
pub use envelope::DispatchResult;
use envelope::bad_request;
pub use envelope::{invalid_handle_envelope, json_catch_unwind};
pub use mentions_hash::{dispatch_mentions_hash_from_attachment, dispatch_mentions_hash_from_json};

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
