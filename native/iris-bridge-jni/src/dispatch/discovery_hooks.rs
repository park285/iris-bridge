use iris_bridge_core::server::discovery_hooks::{
    DiscoveryHookStatus as CoreDiscoveryHookStatus, send_block_reason,
};
use serde::Deserialize;

#[derive(Deserialize)]
struct DiscoveryHookStatus {
    name: String,
    installed: bool,
}

pub fn dispatch_send_block_reason(
    install_attempted: bool,
    hooks_json: &str,
    image_count: i32,
    thread_id: Option<i64>,
    thread_scope: Option<i32>,
) -> Option<String> {
    if !install_attempted {
        return send_block_reason(false, &[], image_count, thread_id, thread_scope);
    }

    let hooks: Vec<DiscoveryHookStatus> = match serde_json::from_str(hooks_json) {
        Ok(hooks) => hooks,
        Err(_) => return Some("bridge discovery hook snapshot invalid".to_owned()),
    };
    let hooks: Vec<CoreDiscoveryHookStatus> = hooks
        .into_iter()
        .map(|hook| CoreDiscoveryHookStatus {
            name: hook.name,
            installed: hook.installed,
        })
        .collect();
    send_block_reason(
        install_attempted,
        &hooks,
        image_count,
        thread_id,
        thread_scope,
    )
}
