use serde_json::Value;

use super::super::{
    dispatch_image_path_under_allowed_root, dispatch_materialize_image_path,
    dispatch_revalidate_image_path_snapshot,
};
use super::payload::{req_i64, req_str, unknown_op, value_json};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "imagePath.underAllowedRoot" => value_json(dispatch_image_path_under_allowed_root(
            req_str(payload, "path").unwrap_or_default(),
            req_str(payload, "allowedRootsJson").unwrap_or_default(),
        )),
        "imagePath.materialize" => dispatch_materialize_image_path(
            req_str(payload, "path").unwrap_or_default(),
            req_str(payload, "allowedRootsJson").unwrap_or_default(),
        ),
        "imagePath.revalidateSnapshot" => dispatch_revalidate_image_path_snapshot(
            req_str(payload, "canonicalPath").unwrap_or_default(),
            req_str(payload, "allowedRootsJson").unwrap_or_default(),
            req_i64(payload, "sizeBytes").unwrap_or_default(),
            req_i64(payload, "lastModifiedEpochMs").unwrap_or_default(),
        ),
        _ => unknown_op(),
    }
}
