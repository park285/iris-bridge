use iris_bridge_core::server::kakao_chat_log_crypto::{decrypt_attachment, encrypt_attachment};
use iris_bridge_core::server::kakao_link_attachment::{
    attachments_match, leverage_encryption_type, pending_cleanup_attachments_match,
};
use iris_bridge_core::server::{ERROR_BAD_REQUEST, Rejection};
use serde_json::json;

use super::envelope::json_catch_unwind;

pub fn dispatch_kakao_chat_log_attachment_crypto(
    encrypt: bool,
    enc_type: i32,
    payload: &str,
    user_id: i64,
) -> String {
    json_catch_unwind(|| {
        if encrypt {
            let attachment = encrypt_attachment(enc_type, payload, user_id)
                .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error.to_string()))?;
            Ok(json!({ "attachment": attachment }))
        } else {
            let plaintext = decrypt_attachment(enc_type, payload, user_id)
                .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error.to_string()))?;
            Ok(json!({ "plaintext": plaintext }))
        }
    })
}

pub fn dispatch_kakao_link_attachments_match(
    expected_raw_attachment: &str,
    committed_raw_attachment: &str,
) -> bool {
    attachments_match(expected_raw_attachment, committed_raw_attachment)
}

pub fn dispatch_kakao_link_pending_cleanup_attachments_match(
    expected_raw_attachment: &str,
    pending_raw_attachment: &str,
) -> bool {
    pending_cleanup_attachments_match(expected_raw_attachment, pending_raw_attachment)
}

pub fn dispatch_kakao_link_leverage_encryption_type(value: &str) -> i32 {
    leverage_encryption_type(value)
}
