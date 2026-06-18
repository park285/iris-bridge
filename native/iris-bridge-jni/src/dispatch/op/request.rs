use serde_json::Value;

use super::super::{
    dispatch_classify_error_code, dispatch_failure_metric_bucket, dispatch_request_admission,
    dispatch_request_dedupe_key, dispatch_request_requires_request_id,
    dispatch_validate_image_paths, dispatch_validate_request_token_handle,
    dispatch_validate_text_request,
};
use super::payload::{
    opt_i64, opt_str, req_bool, req_i32, req_i64, req_str, unknown_op, value_json,
    with_context_envelope,
};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "request.validateToken" => dispatch_validate_request_token_handle(
            req_i64(payload, "handle").unwrap_or_default(),
            req_str(payload, "requestJson").unwrap_or_default(),
        ),
        "request.validateAdmission" => with_context_envelope(payload, |_| {
            Ok(dispatch_request_admission(
                req_str(payload, "action")?,
                opt_str(payload, "requestId"),
            ))
        }),
        "request.validateText" => with_context_envelope(payload, |_| {
            Ok(dispatch_validate_text_request(
                opt_i64(payload, "roomId"),
                opt_str(payload, "message"),
                req_bool(payload, "markdown")?,
                opt_str(payload, "attachmentJson"),
                opt_str(payload, "mentionsJson"),
            ))
        }),
        "request.validateImagePaths" => with_context_envelope(payload, |_| {
            Ok(dispatch_validate_image_paths(
                req_str(payload, "imagePathsJson")?,
                usize::try_from(req_i32(payload, "maxPathCount")?).unwrap_or_default(),
                usize::try_from(req_i32(payload, "maxPathLength")?).unwrap_or_default(),
            ))
        }),
        "request.classifyErrorCode" => dispatch_classify_error_code(
            req_str(payload, "message").unwrap_or_default(),
            req_bool(payload, "isIllegalArgument").unwrap_or_default(),
        ),
        "request.failureMetricBucket" => value_json(dispatch_failure_metric_bucket(
            req_str(payload, "errorCode").unwrap_or_default(),
        )),
        "request.requiresRequestId" => value_json(dispatch_request_requires_request_id(
            req_str(payload, "action").unwrap_or_default(),
        )),
        "request.dedupeKey" => value_json(dispatch_request_dedupe_key(
            req_str(payload, "action").unwrap_or_default(),
            opt_str(payload, "requestId"),
        )),
        _ => unknown_op(),
    }
}
