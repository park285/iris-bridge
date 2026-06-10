use jni::JNIEnv;
use jni::objects::{JClass, JString};
use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::sys::{jboolean, jlong};

use crate::dispatch::{
    dispatch_request_admission, dispatch_request_dedupe_key, dispatch_request_requires_request_id,
    invalid_handle_envelope,
};
use crate::handles::with_context;
use crate::marshal::{
    catch_jstring, read_optional_string, read_string, return_optional_string, return_string,
};

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeValidateRequestAdmission<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    handle: jlong,
    action: JString<'local>,
    request_id: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let action = read_string(env, &action);
        let request_id = read_optional_string(env, &request_id);
        let envelope = match with_context(handle, |_| {
            dispatch_request_admission(&action, request_id.as_deref())
        }) {
            Ok(envelope) => envelope,
            Err(rejection) => invalid_handle_envelope(&rejection),
        };
        return_string(env, &envelope)
    })
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeRequestRequiresRequestId<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    action: JString<'local>,
) -> jboolean {
    let outcome = catch_unwind(AssertUnwindSafe(|| {
        let action = read_string(&mut env, &action);
        dispatch_request_requires_request_id(&action)
    }));
    jboolean::from(outcome.unwrap_or(true))
}

#[unsafe(no_mangle)]
pub extern "system" fn Java_party_qwer_iris_imagebridge_runtime_core_BridgeCoreJniRequest_nativeRequestDedupeKey<
    'local,
>(
    mut env: JNIEnv<'local>,
    _class: JClass<'local>,
    action: JString<'local>,
    request_id: JString<'local>,
) -> jni::sys::jstring {
    catch_jstring(&mut env, |env| {
        let action = read_string(env, &action);
        let request_id = read_optional_string(env, &request_id);
        return_optional_string(
            env,
            dispatch_request_dedupe_key(&action, request_id.as_deref()),
        )
    })
}
