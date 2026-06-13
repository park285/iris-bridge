use iris_bridge_core::mux_session::{MuxCommand, MuxSessionCore};
use iris_bridge_core::protocol::mux::parse_mux_frame_summary;
use iris_bridge_core::server::Rejection;
use serde_json::{Value, json};

use crate::mux_handles::{drop_mux_session_handle, into_mux_session_handle, with_mux_session};

use super::envelope::{DispatchResult, bad_request, json_catch_unwind};

pub fn dispatch_mux_session_create(max_in_flight: i32) -> String {
    json_catch_unwind(|| {
        let max_in_flight = usize::try_from(max_in_flight)
            .map_err(|_| bad_request("maxInFlight must be non-negative"))?;
        let handle = into_mux_session_handle(MuxSessionCore::new(max_in_flight));
        Ok(json!({ "handle": handle }))
    })
}

pub fn dispatch_mux_session_destroy(handle: i64) -> String {
    json_catch_unwind(|| {
        drop_mux_session_handle(handle);
        Ok(json!({}))
    })
}

pub fn dispatch_mux_session_on_frame(handle: i64, summary_json: &str) -> String {
    dispatch_with_mux_session(handle, |session| {
        let summary = parse_mux_frame_summary(summary_json.as_bytes())
            .map_err(|_| bad_request("invalid mux frame json"))?;
        Ok(command_json(session.on_frame(summary)))
    })
}

pub fn dispatch_mux_session_on_executor_rejected(handle: i64, correlation_id: &str) -> String {
    dispatch_with_mux_session(handle, |session| {
        Ok(command_json(session.on_executor_rejected(correlation_id)))
    })
}

pub fn dispatch_mux_session_on_request_completed(handle: i64, correlation_id: &str) -> String {
    dispatch_with_mux_session(handle, |session| {
        session.on_request_completed(correlation_id);
        Ok(json!({}))
    })
}

pub fn dispatch_mux_session_is_cancelled(handle: i64, correlation_id: &str) -> String {
    dispatch_with_mux_session(handle, |session| {
        Ok(json!({ "cancelled": session.is_cancelled(correlation_id) }))
    })
}

fn dispatch_with_mux_session(
    handle: i64,
    body: impl FnOnce(&mut MuxSessionCore) -> DispatchResult,
) -> String {
    match with_mux_session(handle, |session| json_catch_unwind(|| body(session))) {
        Ok(envelope) => envelope,
        Err(rejection) => invalid_session_envelope(rejection),
    }
}

fn invalid_session_envelope(rejection: Rejection) -> String {
    json_catch_unwind(|| Err(rejection))
}

fn command_json(command: MuxCommand) -> Value {
    match command {
        MuxCommand::Dispatch { correlation_id } => {
            json!({ "command": "dispatch", "correlationId": correlation_id })
        }
        MuxCommand::WritePong { correlation_id } => {
            json!({ "command": "writePong", "correlationId": correlation_id })
        }
        MuxCommand::WriteBadRequest {
            correlation_id,
            message,
        } => {
            json!({
                "command": "writeBadRequest",
                "correlationId": correlation_id,
                "message": message,
            })
        }
        MuxCommand::WriteBusy { correlation_id } => {
            json!({ "command": "writeBusy", "correlationId": correlation_id })
        }
        MuxCommand::MarkCancelled { correlation_id } => {
            json!({ "command": "markCancelled", "correlationId": correlation_id })
        }
        MuxCommand::Close => json!({ "command": "close" }),
        MuxCommand::Ignore => json!({ "command": "ignore" }),
    }
}
