use std::panic::{AssertUnwindSafe, catch_unwind};

use iris_bridge_core_lib::server::Admit;
use serde_json::json;

use crate::handles::BridgeCoreContext;

use super::envelope::json_catch_unwind;

pub fn dispatch_dedupe_admit(context: &BridgeCoreContext, key: &str, now_ms: i64) -> String {
    json_catch_unwind(|| {
        let extra = match context.dedupe_ledger.admit(key, now_ms) {
            Admit::Fresh => json!({ "state": "fresh" }),
            Admit::InFlight => json!({ "state": "inFlight" }),
            Admit::Cached(response_json) => {
                json!({ "state": "cached", "responseJson": response_json })
            }
        };
        Ok(extra)
    })
}

pub fn dispatch_dedupe_complete(
    context: &BridgeCoreContext,
    key: &str,
    response_json: &str,
    now_ms: i64,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        context.dedupe_ledger.complete(key, response_json, now_ms);
    }));
}
