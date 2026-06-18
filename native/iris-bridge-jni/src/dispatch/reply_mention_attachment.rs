use iris_bridge_core_lib::server::mentions_hash::{
    mention_attachment_or_null, merge_mention_attachment,
};

pub fn dispatch_reply_mention_attachment_or_null(attachment_text: &str) -> Option<String> {
    mention_attachment_or_null(attachment_text)
}

pub fn dispatch_merge_reply_mention_attachment(
    target_attachment_text: &str,
    mention_attachment_text: &str,
) -> Option<String> {
    merge_mention_attachment(target_attachment_text, mention_attachment_text)
}
