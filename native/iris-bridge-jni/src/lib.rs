#![allow(unsafe_code, reason = "JNI export ABI 표면")]

mod handles;

use std::panic::{AssertUnwindSafe, catch_unwind};

use iris_bridge_core::server::reply_hook::{REPLY_HOOK_TTL_MS, sign_prepared, verify as reply_verify};
use iris_bridge_core::server::token::validate_request;
use iris_bridge_core::server::{Admit, PathFacts, Rejection};
use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jint, jlong};
use serde::Deserialize;
use serde_json::{Value, json};

use crate::handles::{BridgeCoreContext, drop_handle, into_handle, with_context};

pub const ABI_VERSION: jint = 1;

const ERROR_BAD_REQUEST: &str = "BAD_REQUEST";
const ERROR_PANIC: &str = "PANIC";

type DispatchResult = Result<Value, Rejection>;

#[derive(Deserialize)]
struct TokenRequest {
    action: Option<String>,
    #[serde(rename = "protocolVersion")]
    protocol_version: Option<i32>,
    token: Option<String>,
}

fn ok_envelope(extra: Value) -> Value {
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

fn json_catch_unwind(body: impl FnOnce() -> DispatchResult) -> String {
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

fn bad_request(message: &'static str) -> Rejection {
    Rejection::new(ERROR_BAD_REQUEST, message)
}

fn dispatch_validate_request_token(context: &BridgeCoreContext, request_json: &str) -> String {
    json_catch_unwind(|| {
        let request: TokenRequest =
            serde_json::from_str(request_json).map_err(|_| bad_request("request JSON invalid"))?;
        let _ = request.action;
        validate_request(
            context.security_mode,
            &context.bridge_token,
            request.token.as_deref(),
            request.protocol_version.unwrap_or_default(),
        )?;
        Ok(json!({}))
    })
}

fn dispatch_validate_request_token_handle(handle: jlong, request_json: &str) -> String {
    match with_context(handle, |context| {
        dispatch_validate_request_token(context, request_json)
    }) {
        Ok(envelope) => envelope,
        Err(rejection) => json_catch_unwind(|| Err(rejection)),
    }
}

fn dispatch_verify_leases(
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

fn dispatch_dedupe_admit(context: &BridgeCoreContext, key: &str, now_ms: i64) -> String {
    json_catch_unwind(|| {
        let extra = match context.dedupe_ledger.admit(key, now_ms) {
            Admit::Fresh => json!({ "state": "fresh" }),
            Admit::InFlight => json!({ "state": "inFlight" }),
            Admit::Cached(response_json) => {
                json!({ "state": "cached", "responseJson": response_json })
            }
        };
        Ok(extra)
    })
}

fn dispatch_dedupe_complete(
    context: &BridgeCoreContext,
    key: &str,
    response_json: &str,
    now_ms: i64,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        context.dedupe_ledger.complete(key, response_json, now_ms);
    }));
}

fn dispatch_handshake_on_hello(
    context: &BridgeCoreContext,
    frame_json: &str,
    now_ms: i64,
) -> String {
    json_catch_unwind(|| {
        let frame = context.open_handshake_session(frame_json, now_ms)?;
        Ok(json!({ "frameJson": frame }))
    })
}

fn dispatch_handshake_on_client_proof(context: &BridgeCoreContext, frame_json: &str) -> String {
    json_catch_unwind(|| {
        context.resolve_client_proof(frame_json)?;
        Ok(json!({}))
    })
}

fn dispatch_reply_hook_sign(
    bridge_token: &str,
    room_id: i64,
    message_text: &str,
    session_id: &str,
    created_at_epoch_ms: i64,
    mentions_hash: Option<&str>,
) -> Option<String> {
    sign_prepared(
        bridge_token,
        room_id,
        message_text,
        session_id,
        created_at_epoch_ms,
        mentions_hash,
    )
}

#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin ReplyHookSignatureProtocol.verify와 동결된 인자 계약"
)]
fn dispatch_reply_hook_verify(
    bridge_token: &str,
    room_id: i64,
    message_text: &str,
    session_id: Option<&str>,
    created_at_epoch_ms: Option<i64>,
    mentions_hash: Option<&str>,
    signature: Option<&str>,
    now_epoch_ms: i64,
) -> bool {
    reply_verify(
        bridge_token,
        room_id,
        message_text,
        session_id,
        created_at_epoch_ms,
        mentions_hash,
        signature,
        now_epoch_ms,
        REPLY_HOOK_TTL_MS,
    )
}

fn dispatch_mentions_hash_from_json(mentions_json: Option<&str>) -> Option<String> {
    iris_bridge_core::server::mentions_hash::from_mentions_json(mentions_json)
}

fn dispatch_mentions_hash_from_attachment(attachment_text: Option<&str>) -> Option<String> {
    iris_bridge_core::server::mentions_hash::from_attachment(attachment_text)
}

#[derive(Deserialize)]
struct PathFactsJson {
    canonical_path: String,
    sha256_hex: String,
    byte_length: usize,
    last_modified_epoch_ms: i64,
}

fn decode_path_facts(facts_json: &str) -> Result<Vec<PathFacts>, Rejection> {
    let raw: Vec<PathFactsJson> =
        serde_json::from_str(facts_json).map_err(|_| bad_request("path facts JSON invalid"))?;
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

fn read_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> String {
    env.get_string(value)
        .map_or_else(|_| String::new(), Into::into)
}

fn read_optional_string(env: &mut JNIEnv<'_>, value: &JString<'_>) -> Option<String> {
    if value.is_null() {
        return None;
    }
    env.get_string(value).ok().map(Into::into)
}

fn return_string(env: &JNIEnv<'_>, value: &str) -> jni::sys::jstring {
    env.new_string(value)
        .map_or(std::ptr::null_mut(), |java| {
            jni::objects::JObject::from(java).into_raw()
        })
}

fn return_optional_string(env: &JNIEnv<'_>, value: Option<String>) -> jni::sys::jstring {
    value.map_or(std::ptr::null_mut(), |text| return_string(env, &text))
}

fn catch_jstring(
    env: &mut JNIEnv<'_>,
    body: impl FnOnce(&mut JNIEnv<'_>) -> jni::sys::jstring,
) -> jni::sys::jstring {
    catch_unwind(AssertUnwindSafe(|| body(env))).unwrap_or_else(|_| {
        return_string(
            env,
            r#"{"ok":false,"errorCode":"PANIC","error":"panic across JNI boundary"}"#,
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeCreateContext<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mode: JString<'local>,
    token: JString<'local>,
    require_handshake_raw: JString<'local>,
) -> jlong {
    let result = catch_unwind(AssertUnwindSafe(|| {
        let mode = read_optional_string(&mut env, &mode);
        let token = read_string(&mut env, &token);
        let require = read_optional_string(&mut env, &require_handshake_raw);
        into_handle(BridgeCoreContext::new(
            mode.as_deref(),
            &token,
            require.as_deref(),
        ))
    }));
    result.unwrap_or(0)
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeDestroyContext(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| drop_handle(handle)));
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeHandshakeOnHello<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    frame_json: JString<'local>,
    now_ms: jlong,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let frame = read_string(env, &frame_json);
        let envelope = match with_context(handle, |context| {
            dispatch_handshake_on_hello(context, &frame, now_ms)
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeHandshakeOnClientProof<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    frame_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let frame = read_string(env, &frame_json);
        let envelope = match with_context(handle, |context| {
            dispatch_handshake_on_client_proof(context, &frame)
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeValidateRequestToken<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    request_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let request = read_string(env, &request_json);
        let envelope = dispatch_validate_request_token_handle(handle, &request);
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeVerifyLeases<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    room_id: jlong,
    request_id: JString<'local>,
    leases_json: JString<'local>,
    facts_json: JString<'local>,
    now_ms: jlong,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let request_id = read_string(env, &request_id);
        let leases = read_string(env, &leases_json);
        let facts = read_string(env, &facts_json);
        let envelope = match with_context(handle, |context| {
            dispatch_verify_leases(context, room_id, &request_id, &leases, &facts, now_ms)
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeDedupeAdmit<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    key: JString<'local>,
    now_ms: jlong,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let key = read_string(env, &key);
        let envelope = match with_context(handle, |context| {
            dispatch_dedupe_admit(context, &key, now_ms)
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeDedupeComplete<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    key: JString<'local>,
    response_json: JString<'local>,
    now_ms: jlong,
) {
    let _ = catch_unwind(AssertUnwindSafe(|| {
        let key = read_string(&mut env, &key);
        let response = read_string(&mut env, &response_json);
        let _ = with_context(handle, |context| {
            dispatch_dedupe_complete(context, &key, &response, now_ms);
        });
    }));
}

#[unsafe(no_mangle)]
#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin BridgeCore.nativeReplyHookSign external fun과 동결된 인자 계약"
)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeReplyHookSign<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    bridge_token: JString<'local>,
    room_id: jlong,
    message_text: JString<'local>,
    session_id: JString<'local>,
    created_at_epoch_ms: jlong,
    mentions_hash: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let token = read_string(env, &bridge_token);
        let text = read_string(env, &message_text);
        let session = read_string(env, &session_id);
        let mentions = read_optional_string(env, &mentions_hash);
        let signature = dispatch_reply_hook_sign(
            &token,
            room_id,
            &text,
            &session,
            created_at_epoch_ms,
            mentions.as_deref(),
        );
        return_optional_string(env, signature)
    })
}

#[unsafe(no_mangle)]
#[allow(
    clippy::too_many_arguments,
    reason = "Kotlin BridgeCore.nativeReplyHookVerify external fun과 동결된 인자 계약"
)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeReplyHookVerify<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    bridge_token: JString<'local>,
    room_id: jlong,
    message_text: JString<'local>,
    session_id: JString<'local>,
    created_at_epoch_ms: jlong,
    mentions_hash: JString<'local>,
    signature: JString<'local>,
    now_epoch_ms: jlong,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let token = read_string(&mut env, &bridge_token);
        let text = read_string(&mut env, &message_text);
        let session = read_optional_string(&mut env, &session_id);
        let mentions = read_optional_string(&mut env, &mentions_hash);
        let signature = read_optional_string(&mut env, &signature);
        dispatch_reply_hook_verify(
            &token,
            room_id,
            &text,
            session.as_deref(),
            Some(created_at_epoch_ms),
            mentions.as_deref(),
            signature.as_deref(),
            now_epoch_ms,
        )
    }));
    jboolean::from(outcome.unwrap_or(false))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeMentionsHashFromJson<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    mentions_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let mentions = read_optional_string(env, &mentions_json);
        return_optional_string(env, dispatch_mentions_hash_from_json(mentions.as_deref()))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeMentionsHashFromAttachment<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    attachment_text: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let attachment = read_optional_string(env, &attachment_text);
        return_optional_string(
            env,
            dispatch_mentions_hash_from_attachment(attachment.as_deref()),
        )
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeRequireHandshake(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
    handle: jlong,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        with_context(handle, |context| context.require_handshake).unwrap_or(true)
    }));
    jboolean::from(outcome.unwrap_or(true))
}

#[unsafe(no_mangle)]
pub const extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCore_nativeAbiVersion(
    _env: JNIEnv<'_>,
    _class: JClass<'_>,
) -> jint {
    ABI_VERSION
}

fn invalid_handle_envelope(rejection: &Rejection) -> String {
    serde_json::to_string(&rejection_envelope(rejection)).unwrap_or_else(|_| {
        r#"{"ok":false,"errorCode":"INVALID_HANDLE","error":"invalid BridgeCoreContext handle"}"#
            .to_owned()
    })
}

#[cfg(test)]
#[path = "tests/lib.rs"]
mod tests;
