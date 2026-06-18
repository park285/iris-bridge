use iris_bridge_core_lib::server::token::validate_request;
use serde_json::json;

use crate::handles::{BridgeCoreContext, with_context};

use super::decode::TokenRequest;
use super::envelope::{bad_request, json_catch_unwind};

pub fn dispatch_validate_request_token(context: &BridgeCoreContext, request_json: &str) -> String {
    json_catch_unwind(|| {
        let request: TokenRequest =
            serde_json::from_str(request_json).map_err(|_| bad_request("request JSON invalid"))?;
        validate_request(
            context.security_mode,
            &context.bridge_token,
            request.token.as_deref(),
            request.protocol_version.unwrap_or_default(),
            request.action.as_deref(),
        )?;
        Ok(json!({}))
    })
}

#[must_use]
pub fn dispatch_validate_request_token_handle(handle: i64, request_json: &str) -> String {
    match with_context(handle, |context| {
        dispatch_validate_request_token(context, request_json)
    }) {
        Ok(envelope) => envelope,
        Err(rejection) => json_catch_unwind(|| Err(rejection)),
    }
}
