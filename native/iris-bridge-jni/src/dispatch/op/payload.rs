use iris_bridge_core_lib::server::Rejection;
use serde_json::{Value, json};

use crate::handles::{BridgeCoreContext, with_context};

use super::super::envelope::{bad_request, invalid_handle_envelope, json_catch_unwind};

pub(super) fn parse_payload(payload: &str) -> Result<Value, Rejection> {
    let value: Value =
        serde_json::from_str(payload).map_err(|_| bad_request("dispatch payload JSON invalid"))?;
    if value.is_object() {
        Ok(value)
    } else {
        Err(bad_request("dispatch payload must be object"))
    }
}

pub(super) fn unknown_op() -> String {
    json_catch_unwind(|| Err(bad_request("unknown bridge core dispatch op")))
}

pub(super) fn value_json(value: impl serde::Serialize) -> String {
    json_catch_unwind(|| Ok(json!({ "value": value })))
}

pub(super) fn with_context_envelope(
    payload: &Value,
    body: impl FnOnce(&BridgeCoreContext) -> Result<String, Rejection>,
) -> String {
    let handle = match req_i64(payload, "handle") {
        Ok(handle) => handle,
        Err(rejection) => return json_catch_unwind(|| Err(rejection)),
    };
    match with_context(handle, body) {
        Ok(Ok(envelope)) => envelope,
        Ok(Err(rejection)) | Err(rejection) => invalid_handle_envelope(&rejection),
    }
}

pub(super) fn req_value<'a>(payload: &'a Value, key: &str) -> Result<&'a Value, Rejection> {
    payload
        .get(key)
        .filter(|value| !value.is_null())
        .ok_or_else(|| bad_request("dispatch payload invalid"))
}

pub(super) fn req_str<'a>(payload: &'a Value, key: &str) -> Result<&'a str, Rejection> {
    req_value(payload, key)?
        .as_str()
        .ok_or_else(|| bad_request("dispatch payload invalid"))
}

pub(super) fn opt_str<'a>(payload: &'a Value, key: &str) -> Option<&'a str> {
    payload.get(key).filter(|value| !value.is_null())?.as_str()
}

pub(super) fn req_bool(payload: &Value, key: &str) -> Result<bool, Rejection> {
    req_value(payload, key)?
        .as_bool()
        .ok_or_else(|| bad_request("dispatch payload invalid"))
}

pub(super) fn req_i64(payload: &Value, key: &str) -> Result<i64, Rejection> {
    req_value(payload, key)?
        .as_i64()
        .ok_or_else(|| bad_request("dispatch payload invalid"))
}

pub(super) fn opt_i64(payload: &Value, key: &str) -> Option<i64> {
    payload.get(key).filter(|value| !value.is_null())?.as_i64()
}

pub(super) fn req_i32(payload: &Value, key: &str) -> Result<i32, Rejection> {
    i32::try_from(req_i64(payload, key)?).map_err(|_| bad_request("dispatch payload invalid"))
}

pub(super) fn opt_i32(payload: &Value, key: &str) -> Option<i32> {
    i32::try_from(opt_i64(payload, key)?).ok()
}

pub(super) fn string_vec(payload: &Value, key: &str) -> Result<Vec<String>, Rejection> {
    req_value(payload, key)?
        .as_array()
        .ok_or_else(|| bad_request("dispatch payload invalid"))?
        .iter()
        .map(|value| {
            value
                .as_str()
                .map(str::to_owned)
                .ok_or_else(|| bad_request("dispatch payload invalid"))
        })
        .collect()
}

pub(super) fn bool_vec(payload: &Value, key: &str) -> Result<Vec<bool>, Rejection> {
    req_value(payload, key)?
        .as_array()
        .ok_or_else(|| bad_request("dispatch payload invalid"))?
        .iter()
        .map(|value| {
            value
                .as_bool()
                .ok_or_else(|| bad_request("dispatch payload invalid"))
        })
        .collect()
}
