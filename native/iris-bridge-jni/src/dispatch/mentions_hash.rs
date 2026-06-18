pub fn dispatch_mentions_hash_from_json(mentions_json: Option<&str>) -> Option<String> {
    iris_bridge_core_lib::server::mentions_hash::from_mentions_json(mentions_json)
}

pub fn dispatch_mentions_hash_from_attachment(attachment_text: Option<&str>) -> Option<String> {
    iris_bridge_core_lib::server::mentions_hash::from_attachment(attachment_text)
}
