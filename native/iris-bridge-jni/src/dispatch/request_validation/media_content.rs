use iris_bridge_core_lib::server::Rejection;
use iris_bridge_core_lib::server::image_path::{
    MediaContentTypeLease, MediaMessageKind, normalize_media_content_types,
    normalize_media_content_types_from_leases, select_media_message_kind,
    validate_share_manager_image_media,
};
use serde::Deserialize;
use serde_json::json;

use super::super::envelope::{bad_request, json_catch_unwind};

pub fn dispatch_normalize_media_content_types(
    image_count: i32,
    content_types_json: &str,
) -> String {
    json_catch_unwind(|| {
        let image_count = image_count_usize(image_count)?;
        let content_types = decode_content_types(content_types_json)?;
        let normalized = normalize_media_content_types(image_count, &content_types)?;
        Ok(json!({ "normalizedContentTypes": normalized }))
    })
}

pub fn dispatch_normalize_media_content_types_from_leases(
    image_count: i32,
    leases_json: &str,
) -> String {
    json_catch_unwind(|| {
        let image_count = image_count_usize(image_count)?;
        let leases = decode_media_content_type_leases(leases_json)?;
        let normalized = normalize_media_content_types_from_leases(image_count, &leases)?;
        Ok(json!({ "normalizedContentTypes": normalized }))
    })
}

pub fn dispatch_media_message_kind(image_count: i32, content_types_json: &str) -> String {
    json_catch_unwind(|| {
        let image_count = image_count_usize(image_count)?;
        let content_types = decode_content_types(content_types_json)?;
        let kind = select_media_message_kind(image_count, &content_types)?;
        Ok(json!({ "messageKind": media_message_kind_wire_name(kind) }))
    })
}

pub fn dispatch_validate_share_manager_image_media(content_types_json: &str) -> String {
    json_catch_unwind(|| {
        let content_types = decode_content_types(content_types_json)?;
        validate_share_manager_image_media(&content_types)?;
        Ok(json!({}))
    })
}

fn image_count_usize(image_count: i32) -> Result<usize, Rejection> {
    usize::try_from(image_count).map_err(|_| bad_request("image count must be non-negative"))
}

fn decode_content_types(content_types_json: &str) -> Result<Vec<String>, Rejection> {
    serde_json::from_str(content_types_json).map_err(|_| bad_request("content types JSON invalid"))
}

fn decode_media_content_type_leases(
    leases_json: &str,
) -> Result<Vec<MediaContentTypeLease>, Rejection> {
    let leases: Vec<MediaContentTypeLeaseWire> = serde_json::from_str(leases_json)
        .map_err(|_| bad_request("lease content types JSON invalid"))?;
    Ok(leases
        .into_iter()
        .map(|lease| MediaContentTypeLease {
            image_index: lease.image_index,
            content_type: lease.content_type,
        })
        .collect())
}

#[derive(Deserialize)]
#[serde(rename_all = "camelCase")]
struct MediaContentTypeLeaseWire {
    image_index: usize,
    content_type: String,
}

const fn media_message_kind_wire_name(kind: MediaMessageKind) -> &'static str {
    match kind {
        MediaMessageKind::Photo => "photo",
        MediaMessageKind::MultiPhoto => "multiPhoto",
        MediaMessageKind::Video => "video",
    }
}
