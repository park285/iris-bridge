use iris_bridge_core_lib::protocol::bridge_protocol_contract_json;
use serde_json::json;

use super::envelope::json_catch_unwind;

pub fn dispatch_bridge_protocol_contract_json() -> String {
    json_catch_unwind(|| {
        Ok(json!({
            "contractJson": bridge_protocol_contract_json(),
        }))
    })
}
