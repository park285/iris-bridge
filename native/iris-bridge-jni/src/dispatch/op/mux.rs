use serde_json::Value;

use super::super::{
    dispatch_mux_session_create, dispatch_mux_session_destroy, dispatch_mux_session_is_cancelled,
    dispatch_mux_session_on_executor_rejected, dispatch_mux_session_on_frame,
    dispatch_mux_session_on_request_completed,
};
use super::payload::{req_i32, req_i64, req_str, unknown_op};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "mux.create" => {
            dispatch_mux_session_create(req_i32(payload, "maxInFlight").unwrap_or_default())
        }
        "mux.destroy" => {
            dispatch_mux_session_destroy(req_i64(payload, "handle").unwrap_or_default())
        }
        "mux.onFrame" => dispatch_mux_session_on_frame(
            req_i64(payload, "handle").unwrap_or_default(),
            req_str(payload, "frameJson").unwrap_or_default(),
        ),
        "mux.onExecutorRejected" => dispatch_mux_session_on_executor_rejected(
            req_i64(payload, "handle").unwrap_or_default(),
            req_str(payload, "correlationId").unwrap_or_default(),
        ),
        "mux.onRequestCompleted" => dispatch_mux_session_on_request_completed(
            req_i64(payload, "handle").unwrap_or_default(),
            req_str(payload, "correlationId").unwrap_or_default(),
        ),
        "mux.isCancelled" => dispatch_mux_session_is_cancelled(
            req_i64(payload, "handle").unwrap_or_default(),
            req_str(payload, "correlationId").unwrap_or_default(),
        ),
        _ => unknown_op(),
    }
}
