use iris_bridge_core_lib::server::Rejection;
use iris_bridge_core_lib::server::bridge_flags::is_truthy_flag;
use iris_bridge_core_lib::server::error_classification::{
    classify_error_code, failure_metric_bucket,
};
use iris_bridge_core_lib::server::image_path::{
    MaterializedImagePath, image_path_under_allowed_root, materialize_image_path,
    revalidate_image_path_snapshot, validate_image_paths,
};
use iris_bridge_core_lib::server::peer_identity::allowed_peer_uids;
use iris_bridge_core_lib::server::text_request::{TextRequestInput, validate_text_request};
use iris_bridge_core_lib::server::token::{SecurityMode, canonical_security_mode_raw};
use serde_json::json;

use super::envelope::json_catch_unwind;
use super::json::parse_json;

mod media_content;

pub use media_content::{
    dispatch_media_message_kind, dispatch_normalize_media_content_types,
    dispatch_normalize_media_content_types_from_leases,
    dispatch_validate_share_manager_image_media,
};

pub fn dispatch_request_admission(action: &str, request_id: Option<&str>) -> String {
    json_catch_unwind(|| {
        iris_bridge_core_lib::server::admission::validate_request_id(action, request_id)?;
        let requires_request_id =
            iris_bridge_core_lib::server::admission::requires_request_id(action);
        let dedupe_key =
            iris_bridge_core_lib::server::admission::request_dedupe_key(action, request_id);
        Ok(json!({
            "requiresRequestId": requires_request_id,
            "dedupeKey": dedupe_key,
        }))
    })
}

#[must_use]
pub fn dispatch_request_requires_request_id(action: &str) -> bool {
    iris_bridge_core_lib::server::admission::requires_request_id(action)
}

pub fn dispatch_request_dedupe_key(action: &str, request_id: Option<&str>) -> Option<String> {
    iris_bridge_core_lib::server::admission::request_dedupe_key(action, request_id)
}

pub fn dispatch_is_truthy_flag(raw: &str) -> bool {
    is_truthy_flag(raw)
}

pub fn dispatch_normalize_security_mode(raw: Option<&str>) -> &'static str {
    canonical_security_mode_raw(raw)
}

pub fn dispatch_allowed_peer_uids(
    security_mode_raw: Option<&str>,
    extra_uids_raw: Option<&str>,
) -> Vec<i32> {
    allowed_peer_uids(SecurityMode::from_raw(security_mode_raw), extra_uids_raw)
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
        let image_paths: Vec<String> = parse_json(image_paths_json, "image paths JSON invalid")?;
        validate_image_paths(&image_paths, max_path_count, max_path_length)?;
        Ok(json!({}))
    })
}

pub fn dispatch_materialize_image_path(path: &str, allowed_roots_json: &str) -> String {
    json_catch_unwind(|| {
        let allowed_roots = decode_allowed_roots(allowed_roots_json)?;
        let materialized = materialize_image_path(path, &allowed_roots)?;
        Ok(materialized_image_path_json(&materialized))
    })
}

pub fn dispatch_revalidate_image_path_snapshot(
    canonical_path: &str,
    allowed_roots_json: &str,
    size_bytes: i64,
    last_modified_epoch_ms: i64,
) -> String {
    json_catch_unwind(|| {
        let allowed_roots = decode_allowed_roots(allowed_roots_json)?;
        let materialized = revalidate_image_path_snapshot(
            canonical_path,
            &allowed_roots,
            size_bytes,
            last_modified_epoch_ms,
        )?;
        Ok(materialized_image_path_json(&materialized))
    })
}

pub fn dispatch_image_path_under_allowed_root(path: &str, allowed_roots_json: &str) -> bool {
    let Ok(allowed_roots) = decode_allowed_roots(allowed_roots_json) else {
        return false;
    };
    image_path_under_allowed_root(path, &allowed_roots)
}

pub fn dispatch_classify_error_code(message: &str, is_illegal_argument: bool) -> String {
    json_catch_unwind(|| {
        Ok(json!({
            "classifiedErrorCode": classify_error_code(message, is_illegal_argument),
        }))
    })
}

pub fn dispatch_failure_metric_bucket(error_code: &str) -> &'static str {
    failure_metric_bucket(error_code)
}

fn decode_allowed_roots(allowed_roots_json: &str) -> Result<Vec<String>, Rejection> {
    parse_json(allowed_roots_json, "allowed roots JSON invalid")
}

fn materialized_image_path_json(materialized: &MaterializedImagePath) -> serde_json::Value {
    json!({
        "canonicalPath": materialized.canonical_path,
        "sizeBytes": materialized.size_bytes,
        "lastModifiedEpochMs": materialized.last_modified_epoch_ms,
    })
}
