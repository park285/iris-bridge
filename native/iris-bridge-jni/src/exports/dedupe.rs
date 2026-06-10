use std::panic::{AssertUnwindSafe, catch_unwind};

use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jlong;

use crate::dispatch::{dispatch_dedupe_admit, dispatch_dedupe_complete, invalid_handle_envelope};
use crate::handles::with_context;
use crate::marshal::{catch_jstring, read_string, return_string};

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
