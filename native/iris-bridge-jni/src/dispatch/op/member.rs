use serde_json::Value;

use super::super::{
    dispatch_member_enrichment_merge, dispatch_member_enrichment_missing_nicknames,
    dispatch_member_extraction_evaluate, dispatch_member_profile_payload,
    dispatch_member_profile_user_ids,
};
use super::payload::{req_str, unknown_op};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "member.extractionEvaluate" => {
            dispatch_member_extraction_evaluate(req_str(payload, "requestJson").unwrap_or_default())
        }
        "member.enrichmentMissingNicknames" => dispatch_member_enrichment_missing_nicknames(
            req_str(payload, "requestJson").unwrap_or_default(),
        ),
        "member.enrichmentMerge" => {
            dispatch_member_enrichment_merge(req_str(payload, "requestJson").unwrap_or_default())
        }
        "member.profileUserIds" => {
            dispatch_member_profile_user_ids(req_str(payload, "requestJson").unwrap_or_default())
        }
        "member.profilePayload" => {
            dispatch_member_profile_payload(req_str(payload, "requestJson").unwrap_or_default())
        }
        _ => unknown_op(),
    }
}
