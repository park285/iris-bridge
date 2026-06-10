use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::{jboolean, jlong};

use crate::dispatch::{
    dispatch_image_lease_facts_json, dispatch_image_lease_rejection_is_state_error,
    dispatch_validate_request_token_handle, dispatch_verify_leases, invalid_handle_envelope,
};
use crate::handles::with_context;
use crate::marshal::{catch_jstring, read_string, return_string};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeValidateRequestToken<
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
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniLease_nativeVerifyLeases<
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
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniLease_nativeBuildImageLeaseFacts<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    canonical_paths_json: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let canonical_paths = read_string(env, &canonical_paths_json);
        return_string(env, &dispatch_image_lease_facts_json(&canonical_paths))
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniLease_nativeImageLeaseRejectionIsStateError<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    message: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let message = read_string(&mut env, &message);
        dispatch_image_lease_rejection_is_state_error(&message)
    }));
    jboolean::from(outcome.unwrap_or(false))
}
