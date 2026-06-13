use iris_bridge_core::server::member_extraction::{
    enrich_merge_request, enrich_missing_nicknames_request, evaluate_request,
};
use iris_bridge_core::server::{ERROR_BAD_REQUEST, Rejection};

use super::envelope::json_catch_unwind;

pub fn dispatch_member_extraction_evaluate(request_json: &str) -> String {
    json_catch_unwind(|| {
        evaluate_request(request_json)
            .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error.to_string()))
    })
}

pub fn dispatch_member_enrichment_missing_nicknames(request_json: &str) -> String {
    json_catch_unwind(|| {
        enrich_missing_nicknames_request(request_json)
            .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error.to_string()))
    })
}

pub fn dispatch_member_enrichment_merge(request_json: &str) -> String {
    json_catch_unwind(|| {
        enrich_merge_request(request_json)
            .map_err(|error| Rejection::new(ERROR_BAD_REQUEST, error.to_string()))
    })
}
