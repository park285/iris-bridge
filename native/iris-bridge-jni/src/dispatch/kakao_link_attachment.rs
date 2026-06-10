use iris_bridge_core::server::kakao_link_attachment::{
    attachments_match, leverage_encryption_type, pending_cleanup_attachments_match,
};

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
