use iris_bridge_core_lib::server::reply_hook::{attachment_session_id, looks_like_attachment_text};

pub fn dispatch_reply_attachment_text_looks_like(value: &str) -> bool {
    looks_like_attachment_text(value)
}

pub fn dispatch_reply_attachment_session_id(attachment_text: &str) -> Option<String> {
    attachment_session_id(attachment_text)
}
