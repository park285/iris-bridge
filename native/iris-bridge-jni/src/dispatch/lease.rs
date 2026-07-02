use iris_bridge_core_lib::server::PathFacts;
use iris_bridge_core_lib::server::lease_verdict::{
    image_lease_rejection_is_state_error, path_facts_for_files,
};
use serde_json::json;

use crate::handles::BridgeCoreContext;

use super::decode::decode_path_facts;
use super::envelope::json_catch_unwind;
use super::json::parse_json;

pub fn dispatch_verify_leases(
    context: &BridgeCoreContext,
    room_id: i64,
    request_id: &str,
    leases_json: &str,
    facts_json: &str,
    now_ms: i64,
) -> String {
    json_catch_unwind(|| {
        let facts: Vec<PathFacts> = decode_path_facts(facts_json)?;
        context
            .lease_ledger
            .verify(room_id, request_id, leases_json, &facts, now_ms)?;
        Ok(json!({}))
    })
}

pub fn dispatch_image_lease_facts_json(canonical_paths_json: &str) -> String {
    json_catch_unwind(|| {
        let canonical_paths: Vec<String> =
            parse_json(canonical_paths_json, "image lease paths JSON invalid")?;
        let facts = path_facts_for_files(&canonical_paths)?;
        let facts_json = serde_json::to_string(
            &facts
                .iter()
                .map(|fact| {
                    json!({
                        "canonical_path": fact.canonical_path,
                        "sha256_hex": fact.sha256_hex,
                        "byte_length": fact.byte_length,
                        "last_modified_epoch_ms": fact.last_modified_epoch_ms,
                    })
                })
                .collect::<Vec<_>>(),
        )
        .expect("serialize path facts");
        Ok(json!({ "factsJson": facts_json }))
    })
}

pub fn dispatch_image_lease_rejection_is_state_error(message: &str) -> bool {
    image_lease_rejection_is_state_error(message)
}
