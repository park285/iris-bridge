pub fn dispatch_merge_reply_leverage_attachment(
    generated_attachment: Option<&str>,
    raw_attachment: &str,
) -> String {
    iris_bridge_core_lib::server::reply_hook::merge_leverage_attachment(
        generated_attachment,
        raw_attachment,
    )
}
