use serde_json::{Value, json};

use super::super::{
    dispatch_dedupe_admit, dispatch_dedupe_complete, dispatch_image_lease_facts_json,
    dispatch_image_lease_rejection_is_state_error, dispatch_verify_leases, json_catch_unwind,
};
use super::payload::{req_i64, req_str, unknown_op, value_json, with_context_envelope};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "lease.verify" => with_context_envelope(payload, |context| {
            Ok(dispatch_verify_leases(
                context,
                req_i64(payload, "roomId")?,
                req_str(payload, "requestId")?,
                req_str(payload, "leasesJson")?,
                req_str(payload, "factsJson")?,
                req_i64(payload, "nowMs")?,
            ))
        }),
        "lease.buildImageFacts" => dispatch_image_lease_facts_json(
            req_str(payload, "canonicalPathsJson").unwrap_or_default(),
        ),
        "lease.rejectionIsStateError" => value_json(dispatch_image_lease_rejection_is_state_error(
            req_str(payload, "message").unwrap_or_default(),
        )),
        "lease.dedupeAdmit" => with_context_envelope(payload, |context| {
            Ok(dispatch_dedupe_admit(
                context,
                req_str(payload, "key")?,
                req_i64(payload, "nowMs")?,
            ))
        }),
        "lease.dedupeComplete" => with_context_envelope(payload, |context| {
            dispatch_dedupe_complete(
                context,
                req_str(payload, "key")?,
                req_str(payload, "responseJson")?,
                req_i64(payload, "nowMs")?,
            );
            Ok(json_catch_unwind(|| Ok(json!({}))))
        }),
        _ => unknown_op(),
    }
}
