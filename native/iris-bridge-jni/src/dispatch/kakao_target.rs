use iris_bridge_core_lib::kakao_target::resolve_target;
use iris_bridge_core_lib::server::{ERROR_BAD_REQUEST, Rejection};
use serde_json::json;

use super::envelope::json_catch_unwind;

pub fn dispatch_resolve_kakao_target(package_name: &str) -> String {
    json_catch_unwind(|| {
        let target = resolve_target(package_name)
            .map_err(|message| Rejection::new(ERROR_BAD_REQUEST, message))?;
        Ok(json!({
            "packageName": target.package_name,
            "dexPackage": target.dex_package,
        }))
    })
}
