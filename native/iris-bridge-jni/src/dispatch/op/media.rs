use serde_json::Value;

use super::super::{
    dispatch_media_message_kind, dispatch_normalize_media_content_types,
    dispatch_normalize_media_content_types_from_leases,
    dispatch_validate_share_manager_image_media,
};
use super::payload::{req_i32, req_str, unknown_op};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "media.normalizeContentTypes" => dispatch_normalize_media_content_types(
            req_i32(payload, "imageCount").unwrap_or_default(),
            req_str(payload, "contentTypesJson").unwrap_or_default(),
        ),
        "media.normalizeContentTypesFromLeases" => {
            dispatch_normalize_media_content_types_from_leases(
                req_i32(payload, "imageCount").unwrap_or_default(),
                req_str(payload, "leasesJson").unwrap_or_default(),
            )
        }
        "media.messageKind" => dispatch_media_message_kind(
            req_i32(payload, "imageCount").unwrap_or_default(),
            req_str(payload, "contentTypesJson").unwrap_or_default(),
        ),
        "media.validateShareManagerImage" => dispatch_validate_share_manager_image_media(
            req_str(payload, "contentTypesJson").unwrap_or_default(),
        ),
        _ => unknown_op(),
    }
}
