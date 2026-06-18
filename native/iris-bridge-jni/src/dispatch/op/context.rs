use serde_json::{Value, json};

use crate::ABI_VERSION;
use crate::handles::{BridgeCoreContext, drop_handle, into_handle};

use super::super::{
    dispatch_bridge_protocol_contract_json, dispatch_handshake_on_client_proof,
    dispatch_handshake_on_hello,
};
use super::payload::{opt_str, req_i64, req_str, unknown_op, value_json, with_context_envelope};

pub(super) fn dispatch(op: &str, payload: &Value) -> String {
    match op {
        "context.abiVersion" => value_json(ABI_VERSION),
        "context.create" => context_create(payload),
        "context.destroy" => super::super::json_catch_unwind(|| {
            drop_handle(req_i64(payload, "handle")?);
            Ok(json!({}))
        }),
        "context.requireHandshake" => with_context_envelope(payload, |context| {
            Ok(super::super::json_catch_unwind(|| {
                Ok(json!({ "value": context.require_handshake }))
            }))
        }),
        "context.handshakeOnHello" => with_context_envelope(payload, |context| {
            Ok(dispatch_handshake_on_hello(
                context,
                req_str(payload, "frameJson")?,
                req_i64(payload, "nowMs")?,
                req_str(payload, "socketName")?,
            ))
        }),
        "context.handshakeOnClientProof" => with_context_envelope(payload, |context| {
            Ok(dispatch_handshake_on_client_proof(
                context,
                req_str(payload, "frameJson")?,
            ))
        }),
        _ => unknown_op(),
    }
}

pub(super) fn dispatch_protocol(op: &str) -> String {
    match op {
        "protocol.contractJson" => dispatch_bridge_protocol_contract_json(),
        _ => unknown_op(),
    }
}

fn context_create(payload: &Value) -> String {
    super::super::json_catch_unwind(|| {
        let context = BridgeCoreContext::new(
            opt_str(payload, "mode"),
            req_str(payload, "token")?,
            opt_str(payload, "requireHandshakeRaw"),
        );
        let require_handshake = context.require_handshake;
        let handle = into_handle(context);
        Ok(json!({ "handle": handle, "requireHandshake": require_handshake }))
    })
}
