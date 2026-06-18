use iris_bridge_core_lib::server::discovery_hooks::{
    DiscoveryHookStatus as CoreDiscoveryHookStatus, send_block_reason,
};
#[cfg(test)]
use serde::Deserialize;

#[cfg(test)]
#[derive(Deserialize)]
struct DiscoveryHookStatus {
    name: String,
    installed: bool,
}

pub const DISCOVERY_HOOK_SNAPSHOT_INVALID_REASON: &str = "bridge discovery hook snapshot invalid";

#[cfg(test)]
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
        Err(_) => return Some(DISCOVERY_HOOK_SNAPSHOT_INVALID_REASON.to_owned()),
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

pub fn dispatch_send_block_reason_from_snapshot(
    install_attempted: bool,
    hook_names: &[String],
    hook_installed: &[bool],
    image_count: i32,
    thread_id: Option<i64>,
    thread_scope: Option<i32>,
) -> Option<String> {
    if !install_attempted {
        return send_block_reason(false, &[], image_count, thread_id, thread_scope);
    }
    if hook_names.len() != hook_installed.len() {
        return Some(DISCOVERY_HOOK_SNAPSHOT_INVALID_REASON.to_owned());
    }
    let hooks: Vec<CoreDiscoveryHookStatus> = hook_names
        .iter()
        .zip(hook_installed.iter().copied())
        .map(|(name, installed)| CoreDiscoveryHookStatus {
            name: name.clone(),
            installed,
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
