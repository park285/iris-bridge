use serde_json::Value;

use super::super::{
    dispatch_kakao_chat_log_attachment_crypto, dispatch_kakao_link_attachments_match,
    dispatch_kakao_link_build_spec_send_attachment, dispatch_kakao_link_build_v4_encoded_query,
    dispatch_kakao_link_extract_app_key, dispatch_kakao_link_has_explicit_template_args,
    dispatch_kakao_link_has_resolved_iris_template, dispatch_kakao_link_leverage_encryption_type,
    dispatch_kakao_link_patch_display_attachment,
    dispatch_kakao_link_pending_cleanup_attachments_match, dispatch_resolve_kakao_target,
};
use super::payload::{opt_str, req_bool, req_i32, req_i64, req_str, unknown_op, value_json};

pub(super) fn dispatch_target(op: &str, payload: &Value) -> String {
    match op {
        "kakaoTarget.resolve" => {
            dispatch_resolve_kakao_target(req_str(payload, "packageName").unwrap_or_default())
        }
        _ => unknown_op(),
    }
}

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "kakaoLink.chatLogAttachmentCrypto" => dispatch_kakao_chat_log_attachment_crypto(
            req_bool(payload, "encrypt").unwrap_or_default(),
            req_i32(payload, "encType").unwrap_or_default(),
            req_str(payload, "payload").unwrap_or_default(),
            req_i64(payload, "userId").unwrap_or_default(),
        ),
        "kakaoLink.attachmentsMatch" => value_json(dispatch_kakao_link_attachments_match(
            req_str(payload, "expectedRawAttachment").unwrap_or_default(),
            req_str(payload, "committedRawAttachment").unwrap_or_default(),
        )),
        "kakaoLink.pendingCleanupAttachmentsMatch" => {
            value_json(dispatch_kakao_link_pending_cleanup_attachments_match(
                req_str(payload, "expectedRawAttachment").unwrap_or_default(),
                req_str(payload, "pendingRawAttachment").unwrap_or_default(),
            ))
        }
        "kakaoLink.leverageEncryptionType" => {
            value_json(dispatch_kakao_link_leverage_encryption_type(
                req_str(payload, "value").unwrap_or_default(),
            ))
        }
        "kakaoLink.hasExplicitTemplateArgs" => {
            value_json(dispatch_kakao_link_has_explicit_template_args(
                req_str(payload, "rawAttachment").unwrap_or_default(),
            ))
        }
        "kakaoLink.hasResolvedIrisTemplate" => {
            value_json(dispatch_kakao_link_has_resolved_iris_template(
                req_str(payload, "rawAttachment").unwrap_or_default(),
            ))
        }
        "kakaoLink.extractAppKey" => value_json(dispatch_kakao_link_extract_app_key(
            req_str(payload, "rawAttachment").unwrap_or_default(),
        )),
        "kakaoLink.buildV4EncodedQuery" => value_json(dispatch_kakao_link_build_v4_encoded_query(
            req_str(payload, "rawAttachment").unwrap_or_default(),
        )),
        "kakaoLink.buildSpecSendAttachment" => {
            value_json(dispatch_kakao_link_build_spec_send_attachment(
                req_str(payload, "rawAttachment").unwrap_or_default(),
            ))
        }
        "kakaoLink.patchDisplayAttachment" => {
            value_json(dispatch_kakao_link_patch_display_attachment(
                opt_str(payload, "committedAttachment"),
                req_str(payload, "rawAttachment").unwrap_or_default(),
            ))
        }
        _ => unknown_op(),
    }
}
