use iris_bridge_core_lib::server::{PathFacts, Rejection};
use serde::Deserialize;

use super::json::parse_json;

#[derive(Deserialize)]
pub(super) struct TokenRequest {
    pub action: Option<String>,
    #[serde(rename = "protocolVersion")]
    pub protocol_version: Option<i32>,
    pub token: Option<String>,
}

#[derive(Deserialize)]
struct PathFactsJson {
    canonical_path: String,
    sha256_hex: String,
    byte_length: usize,
    last_modified_epoch_ms: i64,
}

pub(super) fn decode_path_facts(facts_json: &str) -> Result<Vec<PathFacts>, Rejection> {
    let raw: Vec<PathFactsJson> = parse_json(facts_json, "path facts JSON invalid")?;
    Ok(raw
        .into_iter()
        .map(|fact| PathFacts {
            canonical_path: fact.canonical_path,
            sha256_hex: fact.sha256_hex,
            byte_length: fact.byte_length,
            last_modified_epoch_ms: fact.last_modified_epoch_ms,
        })
        .collect())
}
