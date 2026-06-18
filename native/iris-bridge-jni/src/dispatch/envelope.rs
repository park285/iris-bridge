use std::panic::{AssertUnwindSafe, catch_unwind};

use iris_bridge_core_lib::server::{ERROR_BAD_REQUEST, Rejection};
use serde_json::{Value, json};

const ERROR_PANIC: &str = "PANIC";

pub type DispatchResult = Result<Value, Rejection>;

pub(super) fn ok_envelope(extra: Value) -> Value {
    let mut envelope = json!({ "ok": true });
    if let (Some(object), Value::Object(fields)) = (envelope.as_object_mut(), extra) {
        for (key, value) in fields {
            object.insert(key, value);
        }
    }
    envelope
}

fn error_envelope(error_code: &str, error: &str) -> Value {
    json!({ "ok": false, "errorCode": error_code, "error": error })
}

fn rejection_envelope(rejection: &Rejection) -> Value {
    error_envelope(rejection.error_code, &rejection.message)
}

pub fn json_catch_unwind(body: impl FnOnce() -> DispatchResult) -> String {
    let outcome = catch_unwind(AssertUnwindSafe(body));
    let envelope = match outcome {
        Ok(Ok(value)) => ok_envelope(value),
        Ok(Err(rejection)) => rejection_envelope(&rejection),
        Err(_) => error_envelope(ERROR_PANIC, "panic across JNI boundary"),
    };
    serde_json::to_string(&envelope).unwrap_or_else(|_| {
        r#"{"ok":false,"errorCode":"PANIC","error":"envelope serialization failed"}"#.to_owned()
    })
}

pub(super) fn bad_request(message: &'static str) -> Rejection {
    Rejection::new(ERROR_BAD_REQUEST, message)
}

pub fn invalid_handle_envelope(rejection: &Rejection) -> String {
    serde_json::to_string(&rejection_envelope(rejection)).unwrap_or_else(|_| {
        r#"{"ok":false,"errorCode":"INVALID_HANDLE","error":"invalid BridgeCoreContext handle"}"#
            .to_owned()
    })
}
